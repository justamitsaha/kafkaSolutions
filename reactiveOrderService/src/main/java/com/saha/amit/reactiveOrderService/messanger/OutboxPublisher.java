package com.saha.amit.reactiveOrderService.messanger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import com.saha.amit.reactiveOrderService.model.OutboxStatus;
import com.saha.amit.reactiveOrderService.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OrderOutboxRepository outboxRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${app.kafka.outbox.poll-interval:PT1S}")
    private Duration pollInterval;

    @Value("${app.kafka.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.kafka.outbox.max-attempts:5}")
    private int maxAttempts;

    private Disposable subscription;

    @Value("${order.use-protobuf:false}")
    private boolean useProtobuf;

    /**
     * Initializes the background outbox polling subscription upon startup.
     * Note: We use ApplicationReadyEvent instead of @PostConstruct to prevent background threads from starting
     * and requesting bean factory singletons before context initialization completes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("Starting OutboxPublisher with pollInterval={} batchSize={}", pollInterval, batchSize);
        subscription = Flux.interval(Duration.ZERO, pollInterval)
                .concatMap(tick -> publishPendingRecords())
                .onErrorContinue((ex, record) -> log.error("Outbox dispatch errored: {}", ex.getMessage()))
                .subscribe();
    }

    /**
     * Periodically queries the database for pending outbox events and triggers their Kafka publication.
     * @return
     */
    public Mono<Void> publishPendingRecords() {
        return outboxRepository.findNextBatch(batchSize)
                .collectList()
                .doOnNext(list -> {
                    if (!list.isEmpty()) {
                        log.info("🔍 Found {} pending outbox records to publish", list.size());
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .doOnNext(orderOutboxEntity -> log.debug("Dispatching outbox record id={} attempt={}", orderOutboxEntity.getId(), orderOutboxEntity.getAttempts()))
                .flatMap(this::publishOutboxRecord, 1)
                .then();
    }

    /**
     * Disposes the background poll subscription on application shutdown.
     */
    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    /**
     * Publishes a single outbox database record to Kafka and updates its status accordingly.
     * @param entity
     * @return
     */
    private Mono<Void> publishOutboxRecord(OrderOutboxEntity entity) {
        return Mono.defer(() -> {
            OrderEvent event = deserialize(entity);
            return orderEventPublisher.publish(event, useProtobuf)
                    .then(updateStatus(entity, OutboxStatus.PUBLISHED, null))
                    .doOnSuccess(ignored -> {
                        log.info("Outbox record id={} published successfully", entity.getId());
                        meterRegistry.counter("order.outbox.published").increment();
                    })
                    .doOnError(ex -> {
                        log.error("Failed to publish outbox record id={}: {}", entity.getId(), ex.getMessage());
                        meterRegistry.counter("order.outbox.failed").increment();
                    })
                    .onErrorResume(ex -> updateForRetry(entity, ex));
        });
    }

    /**
     * Helper method to deserialize the JSON outbox payload back to a domain OrderEvent object.
     * @param entity
     * @return
     */
    private OrderEvent deserialize(OrderOutboxEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), OrderEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize outbox payload for id=" + entity.getId(), e);
        }
    }

    /**
     * Updates the status, last error, and attempt count of the outbox record in the database.
     * @param entity
     * @param status
     * @param lastError
     * @return
     */
    private Mono<Void> updateStatus(OrderOutboxEntity entity, OutboxStatus status, String lastError) {
        entity.setStatus(status);
        entity.setLastError(lastError);
        entity.setAvailableAt(Instant.now());
        entity.setAttempts(entity.getAttempts() + 1);
        return outboxRepository.save(entity).then();
    }

    /**
     * Schedules the next retry interval or marks the outbox entity as permanently failed if retry count is exhausted.
     * @param entity
     * @param ex
     * @return
     */
    private Mono<Void> updateForRetry(OrderOutboxEntity entity, Throwable ex) {
        entity.setAttempts(entity.getAttempts() + 1);
        entity.setLastError(ex.getMessage());
        entity.setStatus(OutboxStatus.FAILED);

        if (entity.getAttempts() >= maxAttempts) {
            log.error("Outbox entry {} exceeded max attempts, marking as FAILED", entity.getId());
            entity.setAvailableAt(Instant.now().plus(Duration.ofHours(1)));
            return outboxRepository.save(entity).then();
        }

        Duration backoff = Duration.ofSeconds((long) Math.min(60, Math.pow(2, entity.getAttempts())));
        entity.setAvailableAt(Instant.now().plus(backoff));
        log.warn("Outbox entry {} failed (attempt {}), retrying after {}", entity.getId(), entity.getAttempts(), backoff);
        return outboxRepository.save(entity).then();
    }
}

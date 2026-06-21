package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryEventPublisher {

    private static final String RETRY_ATTEMPT_HEADER = "retry-attempt";

    private final @Qualifier("jsonKafkaSender") KafkaSender<String, OrderEvent> kafkaSender;
    private final MeterRegistry meterRegistry;

    @Value("${app.kafka.topic.order.retry}")
    private String retryTopic;

    @Value("${app.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    public Mono<Void> scheduleRetry(OrderEvent event, int currentAttempt) {
        if (currentAttempt >= maxAttempts) {
            log.warn("Max retry attempts reached for eventId={}", event.eventId());
            return Mono.empty();
        }

        int nextAttempt = currentAttempt + 1;
        Duration backoff = calculateBackoff(nextAttempt);
        log.info("Scheduling retry attempt {} for eventId={} after {}ms", nextAttempt, event.eventId(), backoff.toMillis());

        meterRegistry.counter("order.retry.scheduled", "attempt", String.valueOf(nextAttempt)).increment();

        SenderRecord<String, OrderEvent, OrderEvent> record = SenderRecord.create(createRetryRecord(event, nextAttempt), event);

        return Mono.delay(backoff)
                .thenMany(kafkaSender.send(Mono.just(record)))
                .doOnNext(result -> {
                    if (result.exception() == null) {
                        meterRegistry.counter("order.retry.published", "attempt", String.valueOf(nextAttempt)).increment();
                        log.info("Retry attempt {} published for eventId={}", nextAttempt, event.eventId());
                    } else {
                        meterRegistry.counter("order.retry.publish.failure").increment();
                        log.error("Retry publish failed for eventId={} attempt={} due to {}", event.eventId(), nextAttempt, result.exception().getMessage());
                    }
                })
                .then();
    }

    private ProducerRecord<String, OrderEvent> createRetryRecord(OrderEvent event, int attempt) {
        ProducerRecord<String, OrderEvent> record = new ProducerRecord<>(retryTopic, event.eventId(), event);
        record.headers().add(RETRY_ATTEMPT_HEADER, String.valueOf(attempt).getBytes());
        return record;
    }

    private Duration calculateBackoff(int attempt) {
        long delaySeconds = (long) Math.min(60, Math.pow(2, attempt));
        return Duration.ofSeconds(delaySeconds);
    }
}

package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
//@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String RETRY_ATTEMPT_HEADER = "retry-attempt";

    private final KafkaReceiver<String, OrderEvent> kafkaReceiver;
    private final KafkaReceiver<String, OrderEvent> retryKafkaReceiver;
    private final KafkaReceiver<String, com.saha.amit.reactiveOrderService.proto.OrderEventMessage> protobufKafkaReceiver;
    private final DltPublisher dltPublisher;
    private final RetryEventPublisher retryEventPublisher;
    private final MeterRegistry meterRegistry;

    @Value("${app.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${order.use-protobuf:false}")
    private boolean useProtobuf;

    private Disposable mainSubscription;
    private Disposable retrySubscription;
    private Disposable protoSubscription;

    /**
     * Initializes the consumer with the main JSON receiver, retry receiver, protobuf receiver, DLT publisher, retry publisher, and meter registry.
     * @param kafkaReceiver
     * @param retryKafkaReceiver
     * @param protobufKafkaReceiver
     * @param dltPublisher
     * @param retryEventPublisher
     * @param meterRegistry
     */
    public OrderEventConsumer(@Qualifier("jsonKafkaReceiver")KafkaReceiver<String, OrderEvent> kafkaReceiver,
                              @Qualifier("retryKafkaReceiver")KafkaReceiver<String, OrderEvent> retryKafkaReceiver,
                              @Qualifier("protobufKafkaReceiver")KafkaReceiver<String, com.saha.amit.reactiveOrderService.proto.OrderEventMessage> protobufKafkaReceiver,
                              DltPublisher dltPublisher,
                              RetryEventPublisher retryEventPublisher,
                              MeterRegistry meterRegistry) {
        this.kafkaReceiver = kafkaReceiver;
        this.retryKafkaReceiver = retryKafkaReceiver;
        this.protobufKafkaReceiver = protobufKafkaReceiver;
        this.dltPublisher = dltPublisher;
        this.retryEventPublisher = retryEventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Subscribes to the appropriate Kafka topics (JSON or Protobuf main topics, and retry topic) upon application startup.
     * Note: We use ApplicationReadyEvent instead of @PostConstruct to prevent background threads from starting
     * and requesting bean factory singletons before context initialization completes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("Starting OrderEventConsumer with maxAttempts={}, useProtobuf={}", maxAttempts, useProtobuf);
        if (useProtobuf) {
            this.protoSubscription = subscribeProto(protobufKafkaReceiver);
        } else {
            this.mainSubscription = subscribe(kafkaReceiver, false);
        }
        this.retrySubscription = subscribe(retryKafkaReceiver, true);
    }

    /**
     * Disposes active Kafka subscriptions and resource handles on application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        if (mainSubscription != null) mainSubscription.dispose();
        if (retrySubscription != null) retrySubscription.dispose();
        if (protoSubscription != null) protoSubscription.dispose();
    }

    /**
     * Helper subscription template to receive and process messages from JSON-based topics.
     * @param receiver
     * @param isRetryTopic
     * @return
     */
    private Disposable subscribe(KafkaReceiver<String, OrderEvent> receiver, boolean isRetryTopic) {
        return receiver.receive()
                .concatMap(record -> processRecord(record, isRetryTopic).thenReturn(record))
                .doOnError(ex -> log.error("Error consuming JSON Kafka events", ex))
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5)))
                .subscribe();
    }

    /**
     * Helper subscription template to receive and process messages from Protobuf-based topics.
     * @param receiver
     * @return
     */
    private Disposable subscribeProto(KafkaReceiver<String, com.saha.amit.reactiveOrderService.proto.OrderEventMessage> receiver) {
        return receiver.receive()
                .concatMap(record -> {
                    com.saha.amit.reactiveOrderService.proto.OrderEventMessage msg = record.value();
                    log.info("Received Protobuf eventId={} from topic={}", msg.getEventId(), record.topic());
                    // For demo purposes, we just acknowledge Protobuf messages
                    return Mono.fromRunnable(record.receiverOffset()::acknowledge).thenReturn(record);
                })
                .doOnError(ex -> log.error("Error consuming Protobuf Kafka events", ex))
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5)))
                .subscribe();
    }

    /**
     * Processes an individual Kafka record, invokes business logic, updates metrics, logs status, and manages failover/retries.
     * @param record
     * @param isRetryTopic
     * @return
     */
    private Mono<Void> processRecord(ReceiverRecord<String, OrderEvent> record, boolean isRetryTopic) {
        OrderEvent event = record.value();
        int attempt = resolveAttempt(record, isRetryTopic);

        log.info("Received eventId={} from topic={} partition={} offset={} attempt={} retryTopic={}",
                event.eventId(), record.topic(), record.partition(), record.offset(), attempt, isRetryTopic);

        return handleBusinessLogic(event)
                .doOnSuccess(ignored -> meterRegistry.counter("order.consumer.processed").increment())
                .doOnError(ex -> meterRegistry.counter("order.consumer.failed").increment())
                .onErrorResume(ex -> handleFailure(record, event, attempt, ex))
                .then(Mono.fromRunnable(record.receiverOffset()::acknowledge));
    }

    /**
     * Executes local business logic checks and validations on the deserialized domain order event.
     * @param event
     * @return
     */
    private Mono<Void> handleBusinessLogic(OrderEvent event) {
        return Mono.defer(() -> {
            if (event.amount() == null || event.amount() <= 0) {
                return Mono.error(new IllegalArgumentException("Invalid amount: " + event.amount()));
            }
            log.info("Processing order event for orderId={} customerId={} amount={}", event.orderId(), event.customerId(), event.amount());
            return Mono.empty();
        });
    }

    /**
     * Handles failures during event processing by routing to the retry topic or dead-letter topic depending on the attempt count.
     * @param record
     * @param event
     * @param attempt
     * @param ex
     * @return
     */
    private Mono<Void> handleFailure(ReceiverRecord<String, OrderEvent> record,
                                     OrderEvent event,
                                     int attempt,
                                     Throwable ex) {
        log.warn("Failed to process eventId={} attempt={} reason={}", event.eventId(), attempt, ex.getMessage());

        if (attempt >= maxAttempts) {
            log.error("Exhausted retries for eventId={}, routing to DLT", event.eventId());
            return dltPublisher.sendToDlt(
                    event,
                    "consumer",
                    ex.getClass().getSimpleName(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
        }

        return retryEventPublisher.scheduleRetry(event, attempt);
    }

    /**
     * Resolves the current attempt index for the record by inspecting the Kafka headers.
     * @param record
     * @param isRetryTopic
     * @return
     */
    private int resolveAttempt(ReceiverRecord<String, OrderEvent> record, boolean isRetryTopic) {
        if (!isRetryTopic) {
            return 0;
        }

        return Optional.ofNullable(record.headers().lastHeader(RETRY_ATTEMPT_HEADER))
                .map(header -> new String(header.value(), StandardCharsets.UTF_8))
                .map(Integer::parseInt)
                .orElse(1);
    }
}

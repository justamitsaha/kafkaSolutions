package com.saha.amit.orderServiceFunctions.functions;

import com.saha.amit.orderServiceFunctions.exception.BadRequestException;
import com.saha.amit.orderServiceFunctions.model.ErrorInfo;
import com.saha.amit.orderServiceFunctions.model.OrderEvent;
import com.saha.amit.orderServiceFunctions.model.OrderRequest;
import com.saha.amit.orderServiceFunctions.model.OrderStatus;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reactive Kafka functions with manual acknowledgment and production-grade patterns.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderFunctions {

    private final Validator validator;
    private final StreamBridge streamBridge; // optional imperative publishing

    // In-memory hot sink to broadcast processed events to any reactive subscribers (e.g., SSE if you add one)
    private final Sinks.Many<OrderEvent> processedEventsSink = Sinks.many().multicast().onBackpressureBuffer();

    private final AtomicLong processedCounter = new AtomicLong();
    private final AtomicLong failedCounter = new AtomicLong();

    /**
     * HTTP → Function → Kafka (producer)
     * POST /ingestOrders with JSON body (single or array). Returns 200 immediately while Kafka publish happens reactively.
     * Binds to: ingestOrders-out-0 → orders.v1
     */
    @Bean
    public Function<Flux<OrderRequest>, Flux<Message<OrderEvent>>> ingestOrders() {
        return requestFlux -> requestFlux
                .switchIfEmpty(Flux.error(new IllegalArgumentException("Request body is empty")))
                .map(req -> {
                    // --- Validate DTO ---
                    var violations = validator.validate(req);
                    if (!violations.isEmpty()) {
                        var first = violations.iterator().next();
                        var evt = OrderEvent.builder()
                                .orderId(req.getOrderId())
                                .customerId(req.getCustomerId())
                                .status(OrderStatus.VALIDATION_FAILED)
                                .error(ErrorInfo.builder()
                                        .code("VALIDATION_ERROR")
                                        .message(first.getPropertyPath() + ": " + first.getMessage())
                                        .build())
                                .build();
                        log.warn("Validation failed for orderId={}: {}", req.getOrderId(), first);
                        return buildEventMessage(evt, req.getCustomerId(), Map.of("validation", true));
                    }

                    // --- Build event ---
                    var evt = OrderEvent.builder()
                            .orderId(req.getOrderId())
                            .customerId(req.getCustomerId())
                            .status(OrderStatus.RECEIVED)
                            .build();

                    return buildEventMessage(evt, req.getCustomerId(), Map.of());
                })
                .onErrorResume(ex -> {
                    // Catch JSON parse / deserialization errors
                    if (ex instanceof org.springframework.core.codec.DecodingException ||
                            ex.getCause() instanceof com.fasterxml.jackson.databind.JsonMappingException) {
                        log.error("Invalid JSON input: {}", ex.getMessage());
                        return Flux.error(new BadRequestException("Malformed JSON request"));
                    }
                    log.error("Unexpected error in ingestOrders: {}", ex.getMessage(), ex);
                    return Flux.error(ex);
                });
    }

    private Message<OrderEvent> buildEventMessage(OrderEvent evt, String key, Map<String, Object> extraHeaders) {
        log.info("Producing event: orderId={} status={} key={}", evt.getOrderId(), evt.getStatus(), key);
        MessageBuilder<OrderEvent> builder = MessageBuilder.withPayload(evt)
                .setHeader(KafkaHeaders.KEY, key);
        extraHeaders.forEach(builder::setHeader);
        return builder.build();
    }


    /**
     * Kafka → Function (consumer) with MANUAL ack.
     * Binds to: orders-in-0 ← orders.v1
     * If processing fails, we DON'T ack; binder will retry according to backoff, then route to DLT after maxAttempts.
     */
    @Bean
    public Consumer<Flux<Message<OrderEvent>>> orders() {
        return flux -> flux
                .doOnSubscribe(s -> log.info("[orders] subscribed"))
                .doOnNext(this::handleIncomingMessage)
                .doOnError(err -> log.error("[orders] stream error: {}", err.getMessage(), err))
                .subscribe();
    }

    private void handleIncomingMessage(Message<OrderEvent> msg) {
        var payload = msg.getPayload();
        var headers = msg.getHeaders();
        Acknowledgment ack = headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
        ConsumerRecord<?, ?> rec = headers.get(KafkaHeaders.RAW_DATA, ConsumerRecord.class);

        try {
            log.info("Processing order: id={} status={} partition={} offset={} key={}",
                    payload.getOrderId(),
                    payload.getStatus(),
                    rec != null ? rec.partition() : null,
                    rec != null ? rec.offset() : null,
                    rec != null ? rec.key() : null);

            // --- Business logic here ---
            if (payload.getStatus() == OrderStatus.VALIDATION_FAILED) {
                throw new IllegalArgumentException("Downstream refused invalid order");
            }

            // Broadcast to the sink for live subscribers (optional)
            processedEventsSink.tryEmitNext(payload);
            processedCounter.incrementAndGet();

            // Ack ONLY after business success
            if (ack != null) {
                ack.acknowledge();
                log.debug("Acked partition={} offset={}", rec != null ? rec.partition() : null, rec != null ? rec.offset() : null);
            }
        } catch (Exception e) {
            failedCounter.incrementAndGet();
            log.error("Processing failed for orderId={}: {}", payload.getOrderId(), e.getMessage(), e);
            // Intentionally do not ack → binder will retry & eventually send to DLT
        }
    }


    public Flux<OrderEvent> getProcessedEventsStream() {
        return processedEventsSink.asFlux();
    }


    /**
     * Optional: Imperative publishing path for non-Function use-cases.
     * Call this method from services/controllers if ever needed.
     */
    public boolean publishOrderEvent(OrderEvent event) {
        return streamBridge.send("ingestOrders-out-0", buildEventMessage(event, event.getCustomerId(), Map.of("source", "imperative")));
    }
}
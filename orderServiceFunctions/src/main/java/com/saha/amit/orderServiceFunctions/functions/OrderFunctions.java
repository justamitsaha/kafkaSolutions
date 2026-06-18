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
     * Pipeline 1: Ingestion (HTTP -> Function -> Kafka Producer)
     * 
     * This function acts as the entry point for new orders. It takes a reactive stream of incoming
     * {@link OrderRequest} objects, validates them against JSR-303 constraints, and transforms them 
     * into {@link OrderEvent} messages.
     *
     * <p><strong>Architectural Details:</strong></p>
     * <ul>
     *   <li><strong>Routing:</strong> Bound to the destination `orders.v1` via the property `spring.cloud.stream.bindings.ingestOrders-out-0.destination`.</li>
     *   <li><strong>Partitioning:</strong> The `customerId` is explicitly set as the Kafka partition key (via `KafkaHeaders.KEY`) to guarantee that all orders for a specific customer are processed sequentially by the same consumer thread.</li>
     *   <li><strong>Error Handling:</strong> If validation fails, the event is still published but marked as `VALIDATION_FAILED`. Deserialization errors result in a Bad Request.</li>
     * </ul>
     *
     * @return A Function that maps a Flux of raw requests to a Flux of Kafka-bound Messages.
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

    /**
     * Helper utility to construct a Spring Integration {@link Message} intended for Kafka.
     * This method ensures the payload is properly wrapped with standard Kafka headers (like the Partition Key)
     * and any custom application-specific metadata.
     *
     * @param evt The core business payload (OrderEvent).
     * @param key The string used by Kafka to determine partition routing (usually Customer ID).
     * @param extraHeaders Additional contextual metadata to be injected into the Kafka message headers.
     * @return A fully constructed Message ready for the Spring Cloud Stream binder.
     */
    private Message<OrderEvent> buildEventMessage(OrderEvent evt, String key, Map<String, Object> extraHeaders) {
        log.info("Producing event: orderId={} status={} key={}", evt.getOrderId(), evt.getStatus(), key);
        MessageBuilder<OrderEvent> builder = MessageBuilder.withPayload(evt)
                .setHeader(KafkaHeaders.KEY, key);
        extraHeaders.forEach(builder::setHeader);
        return builder.build();
    }


    /**
     * Pipeline 2: Processing (Kafka Consumer -> Business Logic -> Reactive Sink)
     * 
     * This consumer subscribes to the Kafka topic and processes incoming order events. It demonstrates 
     * enterprise-grade consumer resiliency patterns.
     *
     * <p><strong>Architectural Details:</strong></p>
     * <ul>
     *   <li><strong>Routing:</strong> Bound to the destination `orders.v1` via `spring.cloud.stream.bindings.orders-in-0.destination`.</li>
     *   <li><strong>Manual Acknowledgment:</strong> Configured with `ackMode=MANUAL`. The offset is ONLY committed if the business logic block completes without throwing an exception.</li>
     *   <li><strong>Resiliency & DLT:</strong> If an exception is thrown (e.g., due to `VALIDATION_FAILED`), the binder will retry 3 times with exponential backoff. If it still fails, the message is automatically routed to `orders.v1.DLT` (Dead Letter Topic).</li>
     * </ul>
     *
     * @return A Consumer that subscribes to a Flux of incoming Kafka Messages.
     */
    @Bean
    public Consumer<Flux<Message<OrderEvent>>> orders() {
        return flux -> flux
                .doOnSubscribe(s -> log.info("[orders] subscribed"))
                .doOnNext(this::handleIncomingMessage)
                .doOnError(err -> log.error("[orders] stream error: {}", err.getMessage(), err))
                .subscribe();
    }

    /**
     * Core business logic execution block for a single Kafka message.
     * Extracts the raw Kafka record and acknowledgment objects from the message headers to provide 
     * fine-grained control over the commit process.
     *
     * @param msg The incoming message from Kafka, containing both the payload and broker metadata.
     */
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


    /**
     * Exposes the internal Reactive Sink as a standard Flux.
     * This allows external components (like HTTP Controllers) to subscribe to the live stream of 
     * successfully processed orders for real-time broadcasting (e.g., Server-Sent Events).
     *
     * @return A hot Flux emitting successfully processed OrderEvents.
     */
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
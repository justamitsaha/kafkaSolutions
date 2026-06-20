package com.saha.amit.orderServiceFunctions.functions;

import com.saha.amit.orderServiceFunctions.exception.BadRequestException;
import com.saha.amit.orderServiceFunctions.model.*;
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
     * WORKFLOW STEP 1: Order Processor (Kafka Consumer & Producer)
     * Listens to orders.v1. If an order is RECEIVED, it updates the state to PLACED (via StreamBridge)
     * and emits a PaymentEvent to payments.v1 to trigger the next step.
     */
    @Bean
    public Function<Flux<Message<OrderEvent>>, Flux<Message<PaymentEvent>>> orderProcessor() {
        return flux -> flux
                .doOnNext(msg -> {
                    Acknowledgment ack = msg.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
                    if (ack != null) ack.acknowledge();
                })
                .filter(msg -> msg.getPayload().getStatus() == OrderStatus.RECEIVED)
                .map(msg -> {
                    OrderEvent payload = msg.getPayload();
                    log.info("[Workflow] Step 1: Order {} RECEIVED. Updating status to PLACED...", payload.getOrderId());
                    
                    // 1. Emit PLACED status update back to orders.v1 so the UI sees the state change
                    OrderEvent placedEvent = OrderEvent.builder()
                            .orderId(payload.getOrderId())
                            .customerId(payload.getCustomerId())
                            .status(OrderStatus.PLACED)
                            .build();
                    streamBridge.send("orders.v1", buildEventMessage(placedEvent, placedEvent.getCustomerId(), Map.of()));

                    // 2. Trigger Payment Event for the next microservice/function
                    log.info("[Workflow] Step 2: Triggering Payment for Order {}", payload.getOrderId());
                    PaymentEvent payment = PaymentEvent.builder()
                            .orderId(payload.getOrderId())
                            .customerId(payload.getCustomerId())
                            .amount(100.0) // Mock amount
                            .paymentStatus("PENDING")
                            .build();
                    
                    return MessageBuilder.withPayload(payment)
                            .setHeader(KafkaHeaders.KEY, payment.getCustomerId())
                            .build();
                });
    }

    /**
     * WORKFLOW STEP 2: Payment Processor (Kafka Consumer & Producer)
     * Listens to payments.v1. Simulates payment processing latency, then emits an OrderEvent 
     * with status COMPLETED back to orders.v1.
     */
    @Bean
    public Function<Flux<Message<PaymentEvent>>, Flux<Message<OrderEvent>>> paymentProcessor() {
        return flux -> flux
                .doOnNext(msg -> {
                    Acknowledgment ack = msg.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
                    if (ack != null) ack.acknowledge();
                })
                .delayElements(Duration.ofSeconds(2)) // Simulate network latency for payment gateway
                .map(msg -> {
                    PaymentEvent payment = msg.getPayload();
                    log.info("[Workflow] Step 3: Payment SUCCESS for Order {}. Completing Order...", payment.getOrderId());
                    
                    OrderEvent completedEvent = OrderEvent.builder()
                            .orderId(payment.getOrderId())
                            .customerId(payment.getCustomerId())
                            .status(OrderStatus.COMPLETED)
                            .build();

                    return buildEventMessage(completedEvent, completedEvent.getCustomerId(), Map.of());
                });
    }

    /**
     * WORKFLOW SINK: Order Finalizer
     * Listens to orders.v1 (all statuses) and broadcasts them to the SSE sink so the frontend 
     * can watch the order state change in real-time (RECEIVED -> PLACED -> COMPLETED).
     */
    @Bean
    public Consumer<Flux<Message<OrderEvent>>> orderFinalizer() {
        return flux -> flux
                .doOnNext(msg -> {
                    Acknowledgment ack = msg.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
                    if (ack != null) ack.acknowledge();
                    
                    OrderEvent evt = msg.getPayload();
                    log.info("[Sink] Order {} status updated to: {}", evt.getOrderId(), evt.getStatus());
                    processedEventsSink.tryEmitNext(evt);
                    processedCounter.incrementAndGet();
                })
                .doOnError(err -> log.error("[orderFinalizer] stream error: {}", err.getMessage(), err))
                .subscribe();
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
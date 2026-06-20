package com.saha.amit.orderServiceFunctions.controller;

import com.saha.amit.orderServiceFunctions.functions.OrderFunctions;
import com.saha.amit.orderServiceFunctions.model.OrderEvent;
import com.saha.amit.orderServiceFunctions.model.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final Function<Flux<OrderRequest>, Flux<Message<OrderEvent>>> ingestOrders;
    private final OrderFunctions orderFunctions;
    private final StreamBridge streamBridge;

    /**
     * REST Endpoint for ingesting new orders into the Event-Driven pipeline.
     * 
     * <p>Instead of manually using a KafkaTemplate, this controller delegates the incoming 
     * HTTP payload to the {@code ingestOrders} Spring Cloud Function to validate and build the message,
     * and then publishes it to the Kafka topic using {@code StreamBridge}.</p>
     *
     * @param orders A JSON list of incoming Order requests.
     * @return A reactive Flux echoing back the generated OrderEvents.
     */
    @PostMapping("/ingest")
    public Flux<OrderEvent> ingestOrdersHttp(@RequestBody List<OrderRequest> orders) {
        log.info("Received {} orders via HTTP", orders.size());

        return ingestOrders.apply(Flux.fromIterable(orders))
                .doOnNext(msg -> {
                    log.info("Publishing event to Kafka: {}", msg.getPayload().getOrderId());
                    streamBridge.send("ingestOrders-out-0", msg);
                })
                .map(Message::getPayload);
    }

    /**
     * REST Endpoint exposing a real-time, unidirectional data stream to the client.
     * 
     * <p>Uses Server-Sent Events (SSE) {@code text/event-stream} to push data to the browser or client 
     * the moment a Kafka message is successfully processed by the consumer logic.</p>
     *
     * @return A hot Flux stream that remains open until the client disconnects.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderEvent> streamOrders() {
        log.info("Client subscribed to live order stream");
        return orderFunctions.getProcessedEventsStream();
    }
}

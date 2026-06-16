package com.saha.amit.orderServiceFunctions.controller;

import com.saha.amit.orderServiceFunctions.functions.OrderFunctions;
import com.saha.amit.orderServiceFunctions.model.OrderEvent;
import com.saha.amit.orderServiceFunctions.model.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Ingest orders from HTTP → Kafka
     * Accepts a JSON array of OrderRequest objects
     */
    @PostMapping("/ingest")
    public Flux<OrderEvent> ingestOrdersHttp(@RequestBody List<OrderRequest> orders) {
        log.info("Received {} orders via HTTP", orders.size());

        return ingestOrders.apply(Flux.fromIterable(orders))
                .map(Message::getPayload);
    }

    /**
     * Stream processed order events via SSE
     * GET /api/orders/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderEvent> streamOrders() {
        log.info("Client subscribed to live order stream");
        return orderFunctions.getProcessedEventsStream();
    }
}


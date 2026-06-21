package com.saha.amit.reactiveOrderService.controller;

import com.saha.amit.reactiveOrderService.dto.OrderRequest;
import com.saha.amit.reactiveOrderService.dto.OrderResponse;
import com.saha.amit.reactiveOrderService.messanger.OutboxPublisher;
import com.saha.amit.reactiveOrderService.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@RefreshScope
public class OrderController {

    Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OutboxPublisher outboxPublisher;

    @Value("${order.discount:5}")
    private String discountPercentage;

    @PostMapping
    public Mono<OrderResponse> placeOrder(@RequestBody OrderRequest req) {
        logger.info("Received order placement request: {}", req);
        req.setAmount(req.getAmount() - (req.getAmount() * Integer.parseInt(discountPercentage) / 100));
        logger.info("Applied discount of {}%, new amount: {}", discountPercentage, req.getAmount());
        // Simulate order processing and response
        return orderService.placeOrder(req.getCustomerId(), req.getAmount())
                .map(event -> new OrderResponse(
                        event.orderId(),
                        event.customerId(),
                        event.amount(),
                        event.status()
                ));
    }

    @PostMapping("/outbox/publish")
    public Mono<ResponseEntity<Map<String, String>>> triggerOutboxPublish() {
        logger.info("Manual outbox publish triggered via API");
        return outboxPublisher.publishPendingRecords()
                .thenReturn(ResponseEntity.ok(Map.of("message", "Outbox publishing triggered successfully")));
    }

    @GetMapping
    public Flux<OrderResponse> getOrders(@RequestParam(value = "customerId", required = false) String customerId) {
        logger.info("Received order retrieval request for customerId: {}", customerId);
        return orderService.getOrdersByCustomer(customerId)
                //.delayElements(Duration.ofSeconds(5))   // This will cause network timeout from gateway due to
                .doOnNext(e -> logger.info("Order retrieved: {}", e))
                .map(e -> new OrderResponse(e.getOrderId(), e.getCustomerId(), e.getAmount(), e.getStatus()));
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<OrderResponse>> getOrderById(@PathVariable("orderId") String orderId) {
        return orderService.getOrderById(orderId)
                .map(e -> new OrderResponse(e.getOrderId(), e.getCustomerId(), e.getAmount(), e.getStatus()))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}

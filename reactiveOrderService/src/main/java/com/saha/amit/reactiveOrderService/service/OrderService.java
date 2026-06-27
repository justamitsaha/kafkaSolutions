package com.saha.amit.reactiveOrderService.service;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.model.OrderEntity;
import com.saha.amit.reactiveOrderService.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OutboxService outboxService;
    private final OrderRepository orderRepository;

    /**
     * Checks for valid event ands passes control to OutboxService to persist the order and outbox event in a single transaction.
     * @param customerId
     * @param amount
     * @return
     */
    public Mono<OrderEvent> placeOrder(String customerId, Double amount) {
        if (amount == null || amount <= 0) {
            log.error("Invalid amount provided: {}", amount);
            return Mono.error(new IllegalArgumentException("Amount must be greater than zero"));
        }
        return outboxService.persistOrderAndOutbox(customerId, amount)
                .doOnSuccess(event -> log.info("Order {} persisted in Outbox tables and queued for publishing via outbox", event.orderId()));
    }

    /**
     * Retrieves an order entity by its unique order identifier.
     * @param orderId
     * @return
     */
    public Mono<OrderEntity> getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Retrieves all order entities stored in the system.
     * @return
     */
    public Flux<OrderEntity> getAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * Retrieves all order entities associated with a specific customer identifier.
     * @param customerId
     * @return
     */
    public Flux<OrderEntity> getOrdersByCustomer(String customerId) {
        log.info("Inside getOrdersByCustomer for customerId: {}", customerId);
        return orderRepository.findByCustomerId(customerId);
    }
}

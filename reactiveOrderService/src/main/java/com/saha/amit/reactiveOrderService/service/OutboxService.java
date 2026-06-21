package com.saha.amit.reactiveOrderService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.model.OrderEntity;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import com.saha.amit.reactiveOrderService.repository.CustomOrderRepositoryImpl;
import com.saha.amit.reactiveOrderService.repository.OrderOutboxRepository;
import com.saha.amit.reactiveOrderService.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;
    private final CustomOrderRepositoryImpl customOrderRepository;

    public Mono<OrderEvent> persistOrderAndOutbox(String customerId, Double amount) {
        String orderId = UUID.randomUUID().toString();
        OrderEvent event = OrderEvent.create(orderId, customerId, amount, "PLACED");

        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setAmount(amount);
        order.setStatus(event.status());
        order.setCreatedAt(Instant.now().toEpochMilli());

//        return transactionalOperator.transactional(
//                orderRepository.insert(order)
//                        .then(outboxRepository.insert(buildOutboxEntity(orderId, event)))
//                        .doOnSuccess(ignored -> log.info("Order {} persisted and added to outbox", orderId))
//        ).thenReturn(event);

        return transactionalOperator.transactional(
                customOrderRepository.insertOrder(order)
                        .then(customOrderRepository.insertOutbox(buildOutboxEntity(orderId, event)))
                        .doOnSuccess( orderOutboxEntity ->
                                log.info("✅ Order {} and Outbox {} persisted successfully",
                                        order.getOrderId(), orderOutboxEntity.getId()))
        ).thenReturn(event);
    }

    private OrderOutboxEntity buildOutboxEntity(String aggregateId, OrderEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return OrderOutboxEntity.pending(aggregateId, event.getClass().getSimpleName(), payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize order event for outbox", e);
        }
    }
}

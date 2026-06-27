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


    /**
     * This method uses TransactionalOperator to persist data in outbox and order table an event is also created and returned to the caller.
     * Same event is serialized and persisted in outbox table for further processing by outbox publisher.
     * @param customerId
     * @param amount
     * @return
     */
    public Mono<OrderEvent> persistOrderAndOutbox(String customerId, Double amount) {
        String orderId = UUID.randomUUID().toString();
        OrderEvent event = OrderEvent.create(orderId, customerId, amount, "PLACED");

        OrderEntity order = new OrderEntity(orderId, customerId, amount, event.status());

        /*Since we are creating uuid and not using auto increment ReactiveCrudRepository will think it's a update
        and fail hence we have o use custom insert like below.
        If you want to use ReactiveCrudRepository then you can use auto increment id and let db generate it.
        return transactionalOperator.transactional(
                orderRepository.save(order)
                        .then(outboxRepository.save(buildOutboxEntity(orderId, event)))
                        .doOnSuccess(ignored -> log.info("Order {} persisted and added to outbox", orderId))
        ).thenReturn(event);*/

        return transactionalOperator.transactional(
                customOrderRepository.insertOrder(order)
                        .then(customOrderRepository.insertOutbox(buildOutboxEntity(orderId, event)))
                        .doOnSuccess( orderOutboxEntity ->
                                log.info("✅ Order {} and Outbox {} persisted successfully",
                                        order.getOrderId(), orderOutboxEntity.getId()))
        ).thenReturn(event);
    }

    /**
     * This generates OutboxEntity from OrderEvent and aggregateId. It serializes the event to json and persists in outbox table.
     * @param aggregateId
     * @param event
     * @return
     */
    private OrderOutboxEntity buildOutboxEntity(String aggregateId, OrderEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            return OrderOutboxEntity.pending(aggregateId, event.getClass().getSimpleName(), payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize order event for outbox", e);
        }
    }
}

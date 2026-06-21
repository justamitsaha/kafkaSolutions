package com.saha.amit.reactiveOrderService.repository;



import com.saha.amit.reactiveOrderService.model.OrderEntity;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import reactor.core.publisher.Mono;

public interface CustomOrderRepository {

    Mono<OrderEntity> insertOrder(OrderEntity order);

    Mono<OrderOutboxEntity> insertOutbox(OrderOutboxEntity outbox);
}

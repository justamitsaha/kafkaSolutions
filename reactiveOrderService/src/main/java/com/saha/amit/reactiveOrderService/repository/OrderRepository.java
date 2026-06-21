package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, String> {
    Flux<OrderEntity> findByCustomerId(String customerId);
}

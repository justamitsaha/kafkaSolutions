package com.saha.amit.nonReactiveOrderService.repository;

import com.saha.amit.nonReactiveOrderService.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}

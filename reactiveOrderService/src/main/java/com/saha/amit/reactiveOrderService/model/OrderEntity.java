package com.saha.amit.reactiveOrderService.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("orders")
@NoArgsConstructor
public class OrderEntity {

    @Id
    private String orderId;

    private String customerId;
    private Double amount;
    private String status;
    private Long createdAt;

    public OrderEntity(String orderId, String customerId, Double amount, String status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now().toEpochMilli();
    }
}


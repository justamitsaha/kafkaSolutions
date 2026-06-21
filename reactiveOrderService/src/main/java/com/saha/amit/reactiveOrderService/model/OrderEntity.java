package com.saha.amit.reactiveOrderService.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("orders")
public class OrderEntity {

    @Id
    private String orderId;

    private String customerId;
    private Double amount;
    private String status;
    private Long createdAt;
}


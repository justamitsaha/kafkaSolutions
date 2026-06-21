package com.saha.amit.reactiveOrderService.dto;

import lombok.Data;

@Data
public class OrderRequest {
    private String customerId;
    private Double amount;
}
package com.saha.amit.reactiveOrderService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String customerId;
    private Double amount;
    private String status;
}

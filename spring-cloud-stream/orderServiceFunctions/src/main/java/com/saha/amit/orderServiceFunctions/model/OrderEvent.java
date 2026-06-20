package com.saha.amit.orderServiceFunctions.model;


import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderEvent {
    private String orderId;
    private String customerId;
    private OrderStatus status;
    private ErrorInfo error;
}
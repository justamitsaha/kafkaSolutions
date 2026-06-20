package com.saha.amit.orderServiceFunctions.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private String orderId;
    private String customerId;
    private double amount;
    private String paymentStatus;
}

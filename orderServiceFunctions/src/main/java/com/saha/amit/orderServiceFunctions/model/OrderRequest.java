package com.saha.amit.orderServiceFunctions.model;


import jakarta.validation.constraints.*;
import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequest {
    @NotBlank
    private String orderId;


    @NotBlank
    private String customerId;


    @NotBlank
    private String customerName;


    @Positive
    private double amount;
}
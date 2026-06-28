package com.saha.amit.nonReactiveOrderService.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "OrderRequest", description = "Payload required to create an order")
public class OrderRequest {

    @NotBlank
    @Schema(description = "Unique identifier of the customer placing the order", example = "cust-123")
    private String customerId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Total monetary value of the order", example = "199.99", minimum = "0", exclusiveMinimum = true)
    private Double amount;
}

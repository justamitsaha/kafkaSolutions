package com.saha.amit.nonReactiveOrderService.dto;

import com.saha.amit.nonReactiveOrderService.events.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(name = "OrderResponse", description = "Response returned after processing an order request")
public class OrderResponse {
    @Schema(description = "Generated identifier of the order", example = "b7fe6c0d-2b92-4f5e-9e68-5adf68e69c45")
    private String orderId;

    @Schema(description = "Identifier of the customer for whom the order was created", example = "cust-123")
    private String customerId;

    @Schema(description = "Order amount", example = "199.99")
    private Double amount;

    @Schema(description = "Resulting status of the order", example = "SUCCESS")
    private Status status;
}

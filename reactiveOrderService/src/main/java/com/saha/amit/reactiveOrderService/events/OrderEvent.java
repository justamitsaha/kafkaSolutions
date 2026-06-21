package com.saha.amit.reactiveOrderService.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event model for Kafka
 */
public record OrderEvent(
        String eventId,
        String orderId,
        String customerId,
        String status,
        Double amount,
        Long timestamp
) {
    public static OrderEvent create(String orderId, String customerId, Double amount, String status) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                orderId,
                customerId,
                status,
                amount,
                Instant.now().toEpochMilli()
        );
    }
}


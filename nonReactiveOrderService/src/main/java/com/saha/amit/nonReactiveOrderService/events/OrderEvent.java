package com.saha.amit.nonReactiveOrderService.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event model for Kafka
 */
public record OrderEvent(
        String eventId,
        String orderId,
        String customerId,
        Status status,
        Double amount,
        Long timestamp
) {
    public static OrderEvent create(String orderId, String customerId, Double amount, Status status) {
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


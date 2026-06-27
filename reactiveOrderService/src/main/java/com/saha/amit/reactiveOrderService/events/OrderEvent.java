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
    /**
     * Static factory method to instantiate a new OrderEvent with a random event UUID and current timestamp.
     * @param orderId
     * @param customerId
     * @param amount
     * @param status
     * @return
     */
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


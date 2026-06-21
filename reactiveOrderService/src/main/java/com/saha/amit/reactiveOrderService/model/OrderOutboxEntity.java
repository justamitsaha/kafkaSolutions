package com.saha.amit.reactiveOrderService.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("order_outbox")
public class OrderOutboxEntity {

    @Id
    private String id;

    @Column("aggregate_id")
    private String aggregateId;

    @Column("event_type")
    private String eventType;

    private String payload;

    private OutboxStatus status;

    @Column("created_at")
    private Instant createdAt;

    @Column("available_at")
    private Instant availableAt;

    @Column("last_error")
    private String lastError;

    private int attempts;

    public static OrderOutboxEntity pending(String aggregateId, String eventType, String payload) {
        OrderOutboxEntity entity = new OrderOutboxEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPayload(payload);
        entity.setStatus(OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setAvailableAt(Instant.now());
        entity.setAttempts(0);
        entity.setLastError(null);
        return entity;
    }
}

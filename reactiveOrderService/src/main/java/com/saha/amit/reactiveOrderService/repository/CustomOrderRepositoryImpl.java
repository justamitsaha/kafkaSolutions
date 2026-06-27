package com.saha.amit.reactiveOrderService.repository;


import com.saha.amit.reactiveOrderService.model.OrderEntity;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@Slf4j
@RequiredArgsConstructor
public class CustomOrderRepositoryImpl implements CustomOrderRepository {

    private final DatabaseClient databaseClient;

    /**
     * Executes a raw SQL INSERT to save a new OrderEntity to the orders table.
     * This avoids R2DBC's auto-save update check for manually-assigned UUID primary keys.
     * @param order
     * @return
     */
    @Override
    public Mono<OrderEntity> insertOrder(OrderEntity order) {
        String sql = """
                    INSERT INTO orders (
                        order_id, customer_id, amount, status, created_at
                    ) VALUES (
                        :order_id, :customer_id, :amount, :status, :created_at
                    )
                """;

        log.debug("🟦 Inserting order: {}", order);

        return databaseClient.sql(sql)
                .bind("order_id", order.getOrderId())
                .bind("customer_id", order.getCustomerId())
                .bind("amount", order.getAmount())
                .bind("status", order.getStatus())
                .bind("created_at", order.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .doOnNext(count -> log.info("✅ Inserted {} order(s) with ID {}", count, order.getOrderId()))
                .thenReturn(order);
    }

    /**
     * Executes a raw SQL INSERT to save a new OrderOutboxEntity to the order_outbox table.
     * Maps the status enum to string and handles nullable error messages.
     * @param outbox
     * @return
     */
    @Override
    public Mono<OrderOutboxEntity> insertOutbox(OrderOutboxEntity outbox) {
        String sql = """
                    INSERT INTO order_outbox (
                        id, aggregate_id, event_type, payload, status, created_at,
                        available_at, last_error, attempts
                    ) VALUES (
                        :id, :aggregate_id, :event_type, :payload, :status,
                        :created_at, :available_at, :last_error, :attempts
                    )
                """;

        log.debug("🟨 Inserting outbox: {}", outbox);

        return databaseClient.sql(sql)
                .bind("id", outbox.getId())
                .bind("aggregate_id", outbox.getAggregateId())
                .bind("event_type", outbox.getEventType())
                .bind("payload", outbox.getPayload())
                .bind("status", outbox.getStatus().name())
                .bind("created_at", outbox.getCreatedAt())
                .bind("available_at", outbox.getAvailableAt())
                //In R2DBC, unlike JDBC, bind() cannot accept nulls — you must use: .bindNull("columnName", SQLDataType.class)
                .bind("last_error", (outbox.getLastError() != null) ? outbox.getLastError() : "")
                .bind("attempts", outbox.getAttempts())
                .fetch()
                .rowsUpdated()
                .doOnNext(count -> log.info("✅ Inserted {} outbox record(s) for aggregate {}", count, outbox.getAggregateId()))
                .thenReturn(outbox);
    }
}

package com.saha.amit.reactiveOrderService.repository;

import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface OrderOutboxRepository extends ReactiveCrudRepository<OrderOutboxEntity, UUID> {

    @Query("""
            SELECT * FROM order_outbox
            WHERE status <> 'PUBLISHED'
              AND available_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT :batchSize
            """)
    Flux<OrderOutboxEntity> findNextBatch(@Param("batchSize") int batchSize);
}

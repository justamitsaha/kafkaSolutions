package com.saha.amit.reactiveOrderService.messanger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.model.OrderOutboxEntity;
import com.saha.amit.reactiveOrderService.model.OutboxStatus;
import com.saha.amit.reactiveOrderService.repository.OrderOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {

    private OrderOutboxRepository outboxRepository;
    private OrderEventPublisher orderEventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OrderOutboxRepository.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        outboxPublisher = new OutboxPublisher(outboxRepository, orderEventPublisher, new ObjectMapper(), meterRegistry);
        ReflectionTestUtils.setField(outboxPublisher, "pollInterval", Duration.ofSeconds(1));
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 10);
        ReflectionTestUtils.setField(outboxPublisher, "maxAttempts", 3);
    }

    //@Test
    void publishOutboxRecordMarksEntryPublished() throws Exception {
        OrderOutboxEntity entity = sampleEntity();
        //when(orderEventPublisher.publish(any())).thenReturn(Mono.empty());
        when(outboxRepository.save(any(OrderOutboxEntity.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<Void> result = invokePublish(entity);
        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<OrderOutboxEntity> captor = ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        OrderOutboxEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(meterRegistry.counter("order.outbox.published").count()).isEqualTo(1.0);
    }

    //@Test
    void publishOutboxRecordSchedulesRetryOnFailure() throws Exception {
        OrderOutboxEntity entity = sampleEntity();
        //when(orderEventPublisher.publish(any())).thenReturn(Mono.error(new IllegalStateException("Kafka down")));
        when(outboxRepository.save(any(OrderOutboxEntity.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<Void> result = invokePublish(entity);
        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<OrderOutboxEntity> captor = ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        OrderOutboxEntity retried = captor.getValue();
        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(retried.getAttempts()).isGreaterThan(0);
        assertThat(retried.getLastError()).contains("Kafka down");
        assertThat(retried.getAvailableAt()).isAfter(Instant.now().minusSeconds(1));
        assertThat(meterRegistry.counter("order.outbox.failed").count()).isEqualTo(1.0);
    }

    private OrderOutboxEntity sampleEntity() {
        OrderOutboxEntity entity = new OrderOutboxEntity();
        entity.setId(java.util.UUID.randomUUID().toString());
        entity.setAggregateId("order-1");
        entity.setEventType("OrderEvent");
        entity.setPayload("{}");
        entity.setStatus(OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setAvailableAt(Instant.now());
        entity.setAttempts(0);
        return entity;
    }

    private Mono<Void> invokePublish(OrderOutboxEntity entity) throws Exception {
        java.lang.reflect.Method publish = OutboxPublisher.class.getDeclaredMethod("publishOutboxRecord", OrderOutboxEntity.class);
        publish.setAccessible(true);
        return (Mono<Void>) publish.invoke(outboxPublisher, entity);
    }
}

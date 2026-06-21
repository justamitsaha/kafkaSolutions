package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryEventPublisherTest {

    private KafkaSender<String, OrderEvent> kafkaSender;
    private SimpleMeterRegistry meterRegistry;
    private RetryEventPublisher retryPublisher;

    @BeforeEach
    void setUp() {
        kafkaSender = mock(KafkaSender.class);
        meterRegistry = new SimpleMeterRegistry();
        retryPublisher = new RetryEventPublisher(kafkaSender, meterRegistry);
        ReflectionTestUtils.setField(retryPublisher, "retryTopic", "order.events.retry");
        ReflectionTestUtils.setField(retryPublisher, "maxAttempts", 3);
    }

    //@Test
    void scheduleRetryDelaysAndPublishes() {
        OrderEvent event = OrderEvent.create("order-1", "cust", 100.0, "FAILED");
        SenderResult<OrderEvent> senderResult = mock(SenderResult.class);
        when(senderResult.exception()).thenReturn(null);
        when(kafkaSender.send(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Publisher<SenderRecord<String, OrderEvent, OrderEvent>> publisherParam = invocation.getArgument(0);
            return Flux.from(publisherParam).map(record -> senderResult);
        });

        VirtualTimeScheduler.getOrSet();

        StepVerifier.withVirtualTime(() -> retryPublisher.scheduleRetry(event, 0))
                .thenAwait(Duration.ofSeconds(2))
                .verifyComplete();

        verify(kafkaSender, times(1)).send(any());
        assertThat(meterRegistry.counter("order.retry.scheduled", "attempt", "1").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("order.retry.published", "attempt", "1").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("order.retry.publish.failure").count()).isZero();
    }

    //@Test
    void scheduleRetryStopsAfterMaxAttempts() {
        OrderEvent event = OrderEvent.create("order-1", "cust", 100.0, "FAILED");
        StepVerifier.create(retryPublisher.scheduleRetry(event, 3))
                .verifyComplete();

        verifyNoInteractions(kafkaSender);
        assertThat(meterRegistry.find("order.retry.scheduled").counter()).isNull();
    }
}

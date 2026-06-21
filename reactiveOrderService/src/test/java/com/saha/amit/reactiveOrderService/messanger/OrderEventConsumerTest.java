package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderEventConsumerTest {

    private KafkaReceiver<String, OrderEvent> mainReceiver;
    private KafkaReceiver<String, OrderEvent> retryReceiver;
    private KafkaReceiver<String, com.saha.amit.reactiveOrderService.proto.OrderEventMessage> protoReceiver;
    private DltPublisher dltPublisher;
    private RetryEventPublisher retryEventPublisher;
    private SimpleMeterRegistry meterRegistry;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        mainReceiver = mock(KafkaReceiver.class);
        retryReceiver = mock(KafkaReceiver.class);
        protoReceiver = mock(KafkaReceiver.class);
        dltPublisher = mock(DltPublisher.class);
        retryEventPublisher = mock(RetryEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();
        consumer = new OrderEventConsumer(mainReceiver, retryReceiver, protoReceiver, dltPublisher, retryEventPublisher, meterRegistry);
        ReflectionTestUtils.setField(consumer, "maxAttempts", 3);
    }

    //@Test
    void processRecordAcknowledgesOnSuccess() throws Exception {
        OrderEvent event = OrderEvent.create("order-1", "cust", 10.0, "PLACED");
        ReceiverRecord<String, OrderEvent> record = mockRecord(event, false);

        Method process = OrderEventConsumer.class.getDeclaredMethod("processRecord", ReceiverRecord.class, boolean.class);
        process.setAccessible(true);

        Mono<Void> result = (Mono<Void>) process.invoke(consumer, record, false);

        StepVerifier.create(result).verifyComplete();
        verify(record.receiverOffset()).acknowledge();
        assertThat(meterRegistry.counter("order.consumer.processed").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("order.consumer.failed").count()).isZero();
        verifyNoInteractions(dltPublisher, retryEventPublisher);
    }

    //@Test
    void processRecordRoutesToRetryOnFailure() throws Exception {
        OrderEvent event = OrderEvent.create("order-1", "cust", -5.0, "PLACED");
        ReceiverRecord<String, OrderEvent> record = mockRecord(event, true);
        when(retryEventPublisher.scheduleRetry(any(), anyInt())).thenReturn(Mono.empty());

        Method process = OrderEventConsumer.class.getDeclaredMethod("processRecord", ReceiverRecord.class, boolean.class);
        process.setAccessible(true);

        Mono<Void> result = (Mono<Void>) process.invoke(consumer, record, false);

        StepVerifier.create(result).verifyComplete();
        verify(retryEventPublisher).scheduleRetry(eq(event), eq(0));
        verify(record.receiverOffset()).acknowledge();
        assertThat(meterRegistry.counter("order.consumer.failed").count()).isEqualTo(1.0);
    }

    @SuppressWarnings("unchecked")
    private ReceiverRecord<String, OrderEvent> mockRecord(OrderEvent event, boolean includeHeader) {
        ReceiverRecord<String, OrderEvent> record = mock(ReceiverRecord.class);
        ReceiverOffset offset = mock(ReceiverOffset.class);
        when(record.value()).thenReturn(event);
        when(record.topic()).thenReturn("order.events");
        when(record.partition()).thenReturn(0);
        when(record.offset()).thenReturn(10L);
        when(record.receiverOffset()).thenReturn(offset);
        doNothing().when(offset).acknowledge();

        if (includeHeader) {
            when(record.headers()).thenReturn(new org.apache.kafka.common.header.internals.RecordHeaders());
        }
        return record;
    }
}

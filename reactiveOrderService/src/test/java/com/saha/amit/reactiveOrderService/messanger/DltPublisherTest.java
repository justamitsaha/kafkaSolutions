package com.saha.amit.reactiveOrderService.messanger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DltPublisherTest {

    private KafkaSender<String, byte[]> kafkaSender;
    private DltPublisher dltPublisher;

    @BeforeEach
    void setUp() {
        kafkaSender = mock(KafkaSender.class);
        dltPublisher = new DltPublisher(kafkaSender, new ObjectMapper());
        ReflectionTestUtils.setField(dltPublisher, "dltTopic", "order.events.dlt");
    }

    //@Test
    void sendToDltPublishesSerializedPayloadWithHeaders() {
        OrderEvent event = OrderEvent.create("order-1", "cust-9", 55.0, "FAILED");

        SenderResult<String> senderResult = mock(SenderResult.class);
        when(senderResult.exception()).thenReturn(null);

        AtomicReference<SenderRecord<String, byte[], String>> capturedRecord = new AtomicReference<>();
        when(kafkaSender.send(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Publisher<SenderRecord<String, byte[], String>> publisher = invocation.getArgument(0);
            return Flux.from(publisher)
                    .doOnNext(capturedRecord::set)
                    .map(r -> senderResult);
        });

        StepVerifier.create(dltPublisher.sendToDlt(event, "consumer", "IllegalStateException", "order.events", 1, 10L))
                .verifyComplete();

        SenderRecord<String, byte[], String> senderRecord = capturedRecord.get();
        assertThat(senderRecord).isNotNull();

        assertThat(senderRecord.topic()).isEqualTo("order.events.dlt");
        assertThat(new String(senderRecord.headers().lastHeader("failure-type").value())).isEqualTo("consumer");
        assertThat(new String(senderRecord.headers().lastHeader("failure-reason").value())).isEqualTo("IllegalStateException");
        assertThat(new String(senderRecord.headers().lastHeader("source-topic").value())).isEqualTo("order.events");
        assertThat(new String(senderRecord.headers().lastHeader("source-partition").value())).isEqualTo("1");
        assertThat(new String(senderRecord.headers().lastHeader("source-offset").value())).isEqualTo("10");
    }
}

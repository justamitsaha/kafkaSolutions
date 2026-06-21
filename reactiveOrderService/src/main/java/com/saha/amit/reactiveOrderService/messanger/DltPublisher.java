package com.saha.amit.reactiveOrderService.messanger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class DltPublisher {

    private final @Qualifier("byteKafkaSender") KafkaSender<String, byte[]> kafkaSender;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.order.dlt}")
    private String dltTopic;

    public Mono<Void> sendToDlt(OrderEvent event,
                                String failureType,
                                String reason,
                                String sourceTopic,
                                Integer partition,
                                Long offset) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(event))
                .flatMapMany(payload -> kafkaSender
                        .send(Mono.just(buildSenderRecord(event, payload, failureType, reason, sourceTopic, partition, offset)))
                        .doOnNext(this::logResult))
                .then();
    }

    private SenderRecord<String, byte[], String> buildSenderRecord(OrderEvent event,
                                                                   byte[] payload,
                                                                   String failureType,
                                                                   String reason,
                                                                   String sourceTopic,
                                                                   Integer partition,
                                                                   Long offset) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(dltTopic, event.customerId(), payload);
        record.headers()
                .add("failure-type", encode(failureType))
                .add("failure-reason", encode(reason))
                .add("source-topic", encode(sourceTopic))
                .add("source-partition", encode(partition != null ? partition.toString() : "-1"))
                .add("source-offset", encode(offset != null ? offset.toString() : "-1"));
        return SenderRecord.create(record, event.eventId());
    }

    private byte[] encode(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private void logResult(SenderResult<String> result) {
        if (result.exception() == null) {
            log.warn("EventId={} routed to DLT topic={}", result.correlationMetadata(), dltTopic);
        } else {
            log.error("Failed to publish eventId={} to DLT topic={} due to {}", result.correlationMetadata(), dltTopic, result.exception().getMessage());
        }
    }
}

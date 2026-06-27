package com.saha.amit.reactiveOrderService.messanger;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.events.OrderEventProtoMapper;
import com.saha.amit.reactiveOrderService.proto.OrderEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.time.Duration;

@Slf4j
@Service
public class OrderEventPublisher {

    private final KafkaSender<String, OrderEvent> jsonKafkaSender;
    private final KafkaSender<String, OrderEventMessage> protobufKafkaSender;
    private final DltPublisher dltPublisher;
    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter failureCounter;

    @Value("${app.kafka.topic.order}")
    private String orderTopic;

    @Value("${app.kafka.topic.order.proto}")
    private String orderProtoTopic;

    /**
     * Initializes the publisher with JSON and Protobuf Kafka senders, the DLT publisher, and registers success/failure metric counters.
     * @param jsonKafkaSender
     * @param protobufKafkaSender
     * @param dltPublisher
     * @param meterRegistry
     */
    public OrderEventPublisher(
            @Qualifier("jsonKafkaSender") KafkaSender<String, OrderEvent> jsonKafkaSender,
            @Qualifier("protobufKafkaSender") KafkaSender<String, OrderEventMessage> protobufKafkaSender,
            DltPublisher dltPublisher,
            MeterRegistry meterRegistry) {

        this.jsonKafkaSender = jsonKafkaSender;
        this.protobufKafkaSender = protobufKafkaSender;
        this.dltPublisher = dltPublisher;
        this.meterRegistry = meterRegistry;
        this.successCounter = meterRegistry.counter("order.publisher.success");
        this.failureCounter = meterRegistry.counter("order.publisher.failure");
    }

    /**
     * Publishes an event as either JSON or Protobuf based on the given flag.
     *
     * @param event        The domain OrderEvent.
     * @param useProtobuf  If true, publish as Protobuf; otherwise, publish as JSON.
     */
    public Mono<Void> publish(OrderEvent event, boolean useProtobuf) {
        if (useProtobuf) {
            return publishAsProtobuf(event);
        } else {
            return publishAsJson(event);
        }
    }

    /**
     * Publishes the order event payload serialized in JSON format to the orders event topic.
     * @param event
     * @return
     */
    private Mono<Void> publishAsJson(OrderEvent event) {
        SenderRecord<String, OrderEvent, OrderEvent> record =
                SenderRecord.create(new ProducerRecord<>(orderTopic, event.eventId(), event), event);
        log.info("Publishing event as JSON to topic {}: {}", orderTopic, event);

        return jsonKafkaSender.send(Mono.just(record))
                .doOnSubscribe(s -> log.info("Kafka send subscribed"))
                .doOnNext(r -> log.info("Kafka ack received"))
                .doOnError(e -> log.error("Kafka send error", e))
                .doOnCancel(() -> log.warn("Kafka send cancelled"))
                .timeout(Duration.ofSeconds(10))
                .flatMap(this::handleJsonResult)
                .then();
    }

    /**
     * Maps the order event to a Protobuf message and publishes it to the orders protobuf topic.
     * @param event
     * @return
     */
    private Mono<Void> publishAsProtobuf(OrderEvent event) {
        OrderEventMessage message = OrderEventProtoMapper.toProto(event);
        log.info("Publishing event as Protobuf to topic {}: {}", orderProtoTopic, message);

        SenderRecord<String, OrderEventMessage, OrderEventMessage> record =
                SenderRecord.create(new ProducerRecord<>(orderProtoTopic, event.eventId(), message), message);

        return protobufKafkaSender.send(Mono.just(record))
                .doOnSubscribe(s -> log.info("Kafka send Protobuf subscribed"))
                .doOnNext(r -> log.info("Kafka Protobuf ack received"))
                .doOnError(e -> log.error("Kafka Protobuf send error", e))
                .doOnCancel(() -> log.warn("Kafka Protobuf send cancelled"))
                .timeout(Duration.ofSeconds(10))
                .flatMap(this::handleProtobufResult)
                .then();
    }

    /**
     * Handles the asynchronous Kafka result for JSON publication, logging status and updating performance metrics.
     * @param result
     * @return
     */
    private Mono<Void> handleJsonResult(SenderResult<OrderEvent> result) {
        if (result.exception() == null) {
            successCounter.increment();
            RecordMetadata metadata = result.recordMetadata();
            log.info("✅ [JSON] Published eventId={} topic={} partition={} offset={}",
                    result.correlationMetadata().eventId(), metadata.topic(), metadata.partition(), metadata.offset());
            return Mono.empty();
        } else {
            failureCounter.increment();
            log.error("❌ [JSON] Failed to publish event: {}", result.exception().getMessage());
            return Mono.error(result.exception());
        }
    }

    /**
     * Handles the asynchronous Kafka result for Protobuf publication, logging status and updating performance metrics.
     * @param result
     * @return
     */
    private Mono<Void> handleProtobufResult(SenderResult<OrderEventMessage> result) {
        if (result.exception() == null) {
            successCounter.increment();
            RecordMetadata metadata = result.recordMetadata();
            log.info("✅ [PROTOBUF] Published eventId={} topic={} partition={} offset={}",
                    result.correlationMetadata().getEventId(), metadata.topic(), metadata.partition(), metadata.offset());
            return Mono.empty();
        } else {
            failureCounter.increment();
            log.error("❌ [PROTOBUF] Failed to publish event: {}", result.exception().getMessage());
            return Mono.error(result.exception());
        }
    }
}


package com.saha.amit.nonReactiveOrderService.service;

import com.saha.amit.nonReactiveOrderService.dto.OrderRequest;
import com.saha.amit.nonReactiveOrderService.dto.OrderResponse;
import com.saha.amit.nonReactiveOrderService.events.OrderEvent;
import com.saha.amit.nonReactiveOrderService.events.Status;
import com.saha.amit.nonReactiveOrderService.exception.OrderProcessingException;
import com.saha.amit.nonReactiveOrderService.model.OrderEntity;
import com.saha.amit.nonReactiveOrderService.repository.OrderRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OrderService {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final OrderRepository orderRepository;
    private final String orderTopic;

    public OrderService(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                        OrderRepository orderRepository,
                        @Value("${order.topic-name}") String orderTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRepository = orderRepository;
        this.orderTopic = orderTopic;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        OrderEvent event = OrderEvent.create(orderId, request.getCustomerId(), request.getAmount(), Status.SUCCESS);

        try {
            SendResult<String, OrderEvent> sendResult = kafkaTemplate.send(orderTopic, orderId, event).get();
            // Manual acknowledgment: blocking on the future ensures the broker confirms the write
            log.debug("Order event sent: partition={}, offset={}",
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());

            OrderEntity entity = new OrderEntity();
            entity.setOrderId(orderId);
            entity.setCustomerId(request.getCustomerId());
            entity.setAmount(request.getAmount());
            entity.setStatus(Status.SUCCESS);
            entity.setCreatedAt(Instant.now().toEpochMilli());
            orderRepository.save(entity);

            return new OrderResponse(orderId, request.getCustomerId(), request.getAmount(), Status.SUCCESS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new OrderProcessingException("Interrupted while waiting for Kafka acknowledgment", ie);
        } catch (ExecutionException ee) {
            throw new OrderProcessingException("Failed to send order event to Kafka", ee.getCause());
        }
    }
}

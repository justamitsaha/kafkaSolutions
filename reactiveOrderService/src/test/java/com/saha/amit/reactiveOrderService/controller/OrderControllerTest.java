package com.saha.amit.reactiveOrderService.controller;

import com.saha.amit.reactiveOrderService.dto.OrderRequest;
import com.saha.amit.reactiveOrderService.dto.OrderResponse;
import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.messanger.OutboxPublisher;
import com.saha.amit.reactiveOrderService.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderControllerTest {

    private OrderService orderService;
    private OutboxPublisher outboxPublisher;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        outboxPublisher = mock(OutboxPublisher.class);
        controller = new OrderController(orderService, outboxPublisher);
        ReflectionTestUtils.setField(controller, "discountPercentage", "0");
    }

    //@Test
    void placeOrderReturnsResponseMono() {
        OrderRequest request = new OrderRequest();
        request.setCustomerId("cust-1");
        request.setAmount(42.0);

        OrderEvent event = OrderEvent.create("order-1", "cust-1", 42.0, "PLACED");
        when(orderService.placeOrder("cust-1", 42.0)).thenReturn(Mono.just(event));

        Mono<OrderResponse> response = controller.placeOrder(request);

        StepVerifier.create(response)
                .assertNext(orderResponse -> {
                    assertThat(orderResponse.getCustomerId()).isEqualTo("cust-1");
                    assertThat(orderResponse.getAmount()).isEqualTo(42.0);
                })
                .verifyComplete();

        verify(orderService).placeOrder("cust-1", 42.0);
    }
}

package com.saha.amit.reactiveOrderService.service;

import com.saha.amit.reactiveOrderService.events.OrderEvent;
import com.saha.amit.reactiveOrderService.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OutboxService outboxService;
    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        outboxService = mock(OutboxService.class);
        orderRepository = mock(OrderRepository.class);
        orderService = new OrderService(outboxService, orderRepository);
    }

    //@Test
    void placeOrderDelegatesToOutboxAndReturnsEvent() {
        OrderEvent event = OrderEvent.create("order-1", "cust-1", 99.0, "PLACED");
        when(outboxService.persistOrderAndOutbox("cust-1", 99.0)).thenReturn(Mono.just(event));

        StepVerifier.create(orderService.placeOrder("cust-1", 99.0))
                .expectNextMatches(result -> {
                    assertThat(result.customerId()).isEqualTo("cust-1");
                    assertThat(result.amount()).isEqualTo(99.0);
                    return true;
                })
                .verifyComplete();

        ArgumentCaptor<String> customerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> amountCaptor = ArgumentCaptor.forClass(Double.class);
        verify(outboxService).persistOrderAndOutbox(customerCaptor.capture(), amountCaptor.capture());
        assertThat(customerCaptor.getValue()).isEqualTo("cust-1");
        assertThat(amountCaptor.getValue()).isEqualTo(99.0);
    }

    //@Test
    void placeOrderRejectsInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder("cust-1", 0.0).block());
        verifyNoInteractions(outboxService);
    }
}

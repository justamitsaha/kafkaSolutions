package com.saha.amit.reactiveOrderService.events;

import com.saha.amit.reactiveOrderService.proto.OrderEventMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventProtoMapperTest {

    //@Test
    void roundTripConversionMaintainsFields() {
        OrderEvent event = OrderEvent.create("order-123", "cust-1", 50.5, "PLACED");

        OrderEventMessage proto = OrderEventProtoMapper.toProto(event);
        OrderEvent converted = OrderEventProtoMapper.fromProto(proto);

        assertThat(converted.orderId()).isEqualTo(event.orderId());
        assertThat(converted.customerId()).isEqualTo(event.customerId());
        assertThat(converted.amount()).isEqualTo(event.amount());
        assertThat(converted.status()).isEqualTo(event.status());
        assertThat(converted.timestamp()).isEqualTo(event.timestamp());
    }
}

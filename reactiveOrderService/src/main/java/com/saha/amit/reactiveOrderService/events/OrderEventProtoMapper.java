package com.saha.amit.reactiveOrderService.events;

import com.saha.amit.reactiveOrderService.proto.OrderEventMessage;

public final class OrderEventProtoMapper {

    private OrderEventProtoMapper() {
    }

    public static OrderEventMessage toProto(OrderEvent event) {
        return OrderEventMessage.newBuilder()
                .setEventId(nullSafe(event.eventId()))
                .setOrderId(nullSafe(event.orderId()))
                .setCustomerId(nullSafe(event.customerId()))
                .setStatus(nullSafe(event.status()))
                .setAmount(event.amount() != null ? event.amount() : 0.0)
                .setTimestamp(event.timestamp() != null ? event.timestamp() : 0L)
                .build();
    }

    public static OrderEvent fromProto(OrderEventMessage message) {
        return new OrderEvent(
                emptyToNull(message.getEventId()),
                emptyToNull(message.getOrderId()),
                emptyToNull(message.getCustomerId()),
                emptyToNull(message.getStatus()),
                message.getAmount(),
                message.getTimestamp()
        );
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}

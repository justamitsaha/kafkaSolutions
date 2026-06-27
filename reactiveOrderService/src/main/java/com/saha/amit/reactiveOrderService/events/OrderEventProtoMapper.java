package com.saha.amit.reactiveOrderService.events;

import com.saha.amit.reactiveOrderService.proto.OrderEventMessage;

public final class OrderEventProtoMapper {

    private OrderEventProtoMapper() {
    }

    /**
     * Converts a domain OrderEvent record into its corresponding compiled Protobuf OrderEventMessage.
     * @param event
     * @return
     */
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

    /**
     * Converts a Protobuf OrderEventMessage into its corresponding domain OrderEvent record.
     * @param message
     * @return
     */
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

    /**
     * Helper to return an empty string if the input value is null.
     * @param value
     * @return
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Helper to return null if the input string is empty.
     * @param value
     * @return
     */
    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}

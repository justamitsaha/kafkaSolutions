package com.saha.amit.nonReactiveOrderService.exception;

public class OrderProcessingException extends RuntimeException {
    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public OrderProcessingException(String message) {
        super(message);
    }
}

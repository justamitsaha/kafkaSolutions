package com.saha.amit.functionsSample.configuration;

import com.saha.amit.functionsSample.dto.OrderRequest;
import com.saha.amit.functionsSample.dto.OrderResponse;
import com.saha.amit.functionsSample.service.MyFunctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Configuration
public class BaseFunctionsConfig {

    private final static Logger log = LoggerFactory.getLogger(BaseFunctionsConfig.class);
    private final MyFunctionService service;

    public BaseFunctionsConfig(MyFunctionService service) {
        this.service = service;
    }

    /** TYPE: java.util.function.Function (Standard Sync) */
    @Bean
    public Function<String, String> sanitizeText() {
        return service::sanitizeText;
    }

    /** TYPE: java.util.function.Function (Standard Sync) */
    @Bean
    public Function<String, String> maskSensitiveData() {
        return service::maskData;
    }

    /** TYPE: java.util.function.Supplier (Standard Sync) */
    @Bean
    public Supplier<UUID> generateEventId() {
        return UUID::randomUUID;
    }

    /** TYPE: java.util.function.Consumer (Standard Sync) */
    @Bean
    public Consumer<String> auditEvent() {
        return message -> log.info("BASE AUDIT LOG: {}", message);
    }

    /** TYPE: java.util.function.Function (POJO/Domain) */
    @Bean
    public Function<OrderRequest, OrderResponse> processOrder() {
        return request -> new OrderResponse(request.getOrderId(), 
                request.getQuantity() > 0 ? "ACCEPTED" : "REJECTED");
    }
}

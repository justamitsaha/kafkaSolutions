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
public class MyFunctions {

    private final static Logger log = LoggerFactory.getLogger(MyFunctions.class);

    private final MyFunctionService myFunctionService;

    public MyFunctions(MyFunctionService myFunctionService) {
        this.myFunctionService = myFunctionService;
    }

    // Function: input → output
    @Bean
    public Function<String, String> uppercase() {
        log.info("Inside uppercase function bean");
        return myFunctionService::uppercase;
    }

    // Supplier: produces values (no input)
    @Bean
    public Supplier<UUID> randomUuid() {
        log.info("Inside randomUuid supplier bean");
        return () -> UUID.randomUUID();
    }

    // Consumer: consumes values (no output)
    @Bean
    public Consumer<String> logger() {
        log.info("Inside logger consumer bean");
        return message -> System.out.println("Received: " + message);
    }

    @Bean
    public Function<String, String> reverse() {
        log.info("Inside reverse function bean");
        return myFunctionService::reverse;
    }

    @Bean
    public Function<OrderRequest, OrderResponse> processOrder() {
        return request -> {
            System.out.println("Processing order: " + request.getOrderId() +
                    ", item=" + request.getItem() +
                    ", qty=" + request.getQuantity());

            String status = request.getQuantity() > 0 ? "CONFIRMED" : "REJECTED";
            return new OrderResponse(request.getOrderId(), status);
        };
    }

    @Bean
    public Function<OrderRequest, OrderRequest> validateOrder() {
        return request -> {
            if (request.getItem() == null || request.getItem().isBlank()) {
                throw new IllegalArgumentException("Item cannot be empty!");
            }
            return request;
        };
    }

}


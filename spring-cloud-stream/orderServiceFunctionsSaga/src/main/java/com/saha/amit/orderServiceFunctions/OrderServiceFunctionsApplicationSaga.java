package com.saha.amit.orderServiceFunctions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceFunctionsApplicationSaga {
    private final static Logger logger = LoggerFactory.getLogger(OrderServiceFunctionsApplicationSaga.class);

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceFunctionsApplicationSaga.class, args);
    }

}
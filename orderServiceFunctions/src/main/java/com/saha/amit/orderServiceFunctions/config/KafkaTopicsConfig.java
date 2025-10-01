package com.saha.amit.orderServiceFunctions.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicsConfig {
    @Bean
    NewTopic ordersV1() {
        return new NewTopic("orders.v1", 3, (short) 1);
    }
    @Bean
    NewTopic ordersV1DLT() {
        return new NewTopic("orders.v1.DLT", 3, (short) 1);
    }
}
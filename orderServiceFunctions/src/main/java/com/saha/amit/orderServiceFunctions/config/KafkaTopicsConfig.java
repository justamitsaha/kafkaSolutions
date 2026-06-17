package com.saha.amit.orderServiceFunctions.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
This configuration class defines Kafka topics used in the application.
It is defaulting to 3 partitions and a replication factor of 1 for simplicity.
In a production environment, you should adjust these settings based on your requirements.
Also, it defaults the value to localhost:9092 causing issues
 */
//@Configuration
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
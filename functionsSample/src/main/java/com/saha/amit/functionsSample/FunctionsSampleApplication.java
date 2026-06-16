package com.saha.amit.functionsSample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FunctionsSampleApplication {
    private final static Logger logger = LoggerFactory.getLogger(FunctionsSampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(FunctionsSampleApplication.class, args);
    }
}

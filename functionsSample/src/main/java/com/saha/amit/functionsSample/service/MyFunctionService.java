package com.saha.amit.functionsSample.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MyFunctionService {

    private final static Logger log = LoggerFactory.getLogger(MyFunctionService.class);

    public String uppercase(String input) {
        log.info("Inside uppercase function bean");
        return input.toUpperCase();
    }

    public String reverse(String input) {
        log.info("Inside reverse function bean");
        return new StringBuilder(input).reverse().toString();
    }
}

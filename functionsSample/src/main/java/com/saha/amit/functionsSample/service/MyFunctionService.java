package com.saha.amit.functionsSample.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class MyFunctionService {

    private final static Logger log = LoggerFactory.getLogger(MyFunctionService.class);

    // --- Standard Sync Logic ---

    public String sanitizeText(String input) {
        if (input == null) return "";
        return input.trim().toUpperCase();
    }

    public String maskData(String input) {
        if (input == null || input.length() < 4) return "****";
        return "****" + new StringBuilder(input.substring(input.length() - 4)).reverse().toString();
    }

    // --- Reactive & Async Logic ---

    public Flux<String> processStream(Flux<String> inputFlux) {
        return inputFlux
                .map(String::trim)
                .map(String::toLowerCase)
                .doOnNext(word -> log.info("Reactive processing: {}", word));
    }

    public Flux<String> heartbeat() {
        return Flux.interval(Duration.ofSeconds(10))
                .map(i -> "HEARTBEAT_ACK_" + i)
                .doOnNext(s -> log.info("Generating heartbeat: {}", s));
    }

    public Mono<String> asyncStandardize(Mono<String> inputMono) {
        return inputMono
                .map(s -> "ASYNC_PROCESSED_" + s.toUpperCase())
                .delayElement(Duration.ofMillis(500));
    }

    public Flux<String> expand(String input) {
        return Flux.fromArray(input.split(""))
                .map(charStr -> "CHAR_" + charStr);
    }

    // --- Message Logic ---

    public Message<String> wrapAndProcess(Message<String> input) {
        String payload = input.getPayload();
        log.info("Processing Message with payload: {}", payload);
        return MessageBuilder.withPayload(payload.toUpperCase())
                .setHeader("X-Audit-ID", java.util.UUID.randomUUID().toString())
                .build();
    }
}

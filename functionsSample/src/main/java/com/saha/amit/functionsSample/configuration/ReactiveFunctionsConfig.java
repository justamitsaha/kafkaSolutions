package com.saha.amit.functionsSample.configuration;

import com.saha.amit.functionsSample.service.MyFunctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Configuration
public class ReactiveFunctionsConfig {

    private final static Logger log = LoggerFactory.getLogger(ReactiveFunctionsConfig.class);
    private final MyFunctionService service;

    public ReactiveFunctionsConfig(MyFunctionService service) {
        this.service = service;
    }

    /** TYPE: Function<Flux, Flux> (Reactive Stream) */
    @Bean
    public Function<Flux<String>, Flux<String>> reactiveStreamProcess() {
        return service::processStream;
    }

    /** TYPE: Function<Mono, Mono> (Async Single) */
    @Bean
    public Function<Mono<String>, Mono<String>> asyncSingleProcess() {
        return service::asyncStandardize;
    }

    /** TYPE: Supplier<Flux> (Reactive Producer) */
    @Bean
    public Supplier<Flux<String>> reactiveHeartbeatSupplier() {
        return service::heartbeat;
    }

    /** TYPE: Consumer<Flux> (Reactive Sink) */
    @Bean
    public Consumer<Flux<String>> reactiveBatchConsumer() {
        return flux -> flux.subscribe(s -> log.info("REACTIVE BATCH LOG: {}", s));
    }

    /** TYPE: Function<String, Flux> (One-to-Many) */
    @Bean
    public Function<String, Flux<String>> splitAndExpand() {
        return service::expand;
    }
}

package com.saha.amit.functionsSample.configuration;

import com.saha.amit.functionsSample.service.MyFunctionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@Configuration
public class ContextFunctionsConfig {

    private final MyFunctionService service;
    private final BaseFunctionsConfig baseFunctions;

    public ContextFunctionsConfig(MyFunctionService service, BaseFunctionsConfig baseFunctions) {
        this.service = service;
        this.baseFunctions = baseFunctions;
    }

    /** UTILITY: Accesses and manipulates HTTP/Kafka Headers */
    @Bean
    public Function<Message<String>, Message<String>> processWithHeaders() {
        return service::wrapAndProcess;
    }

    /** UTILITY: Reactive stream processing with metadata preserved */
    @Bean
    public Function<Flux<Message<String>>, Flux<Message<String>>> reactiveMessageProcess() {
        return flux -> flux.map(service::wrapAndProcess);
    }

    /** UTILITY: Programmatic Chaining (Logic Pipelining) */
    @Bean
    public Function<String, String> sanitizeAndMask() {
        return baseFunctions.sanitizeText().andThen(baseFunctions.maskSensitiveData());
    }
}

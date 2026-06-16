# Spring Cloud Function: A General Guide

This module demonstrates **Spring Cloud Function**, a powerful framework that allows developers to write business logic as standard Java functions while the framework handles the "plumbing" (HTTP endpoints, messaging headers, cloud triggers).

## 💡 What is Spring Cloud Function?

Spring Cloud Function promotes a **Function-as-a-Service (FaaS)** programming model. Instead of writing complex Controllers or Message Listeners, you write simple Java beans that implement one of the three core functional interfaces:

1.  **`Function<I, O>`**: Accepts an input and returns an output. (Maps to `POST`).
2.  **`Supplier<O>`**: Accepts no input and returns an output. (Maps to `GET`).
3.  **`Consumer<I>`**: Accepts an input and returns nothing. (Maps to `POST`).

---

## 🚀 How Functions Become Endpoints

The magic of Spring Cloud Function is that it automatically "adapts" these beans to external triggers.

### 1. Automatic REST Mapping
When you add the `spring-cloud-function-web` dependency, Spring Boot scans your context for functional beans and automatically creates HTTP endpoints (running on port **8081** for this module):
- A bean named `sanitizeText` becomes reachable at `POST /sanitizeText`.
- A bean named `generateEventId` becomes reachable at `GET /generateEventId`.

### 2. Function Composition
You can chain functions together without writing extra code by using the `|` operator in configuration or via `.andThen()` in Java.

---

## 🗺 API Endpoints & Functional Signatures

| Endpoint | Method | Java Signature | Logic Description | Test Script |
| :--- | :--- | :--- | :--- | :--- |
| `/sanitizeText` | `POST` | `Function<String, String>` | Trims and converts text to uppercase. | [base_test](./test_base_functions.sh) |
| `/generateEventId` | `GET` | `Supplier<UUID>` | Generates a new random UUID. | [base_test](./test_base_functions.sh) |
| `/auditEvent` | `POST` | `Consumer<String>` | Logs the input string to the audit log. | [base_test](./test_base_functions.sh) |
| `/processOrder` | `POST` | `Function<Req, Res>` | POJO-based domain logic. | [base_test](./test_base_functions.sh) |
| `/reactiveStreamProcess` | `POST` | `Function<Flux, Flux>` | Processes streams of data asynchronously. | [reactive_test](./test_reactive_functions.sh) |
| `/reactiveHeartbeatSupplier`| `GET` | `Supplier<Flux>` | Streams continuous heartbeat events (SSE). | [reactive_test](./test_reactive_functions.sh) |
| `/reactiveBatchConsumer` | `POST` | `Consumer<Flux>` | Consumes and logs batches of data. | [reactive_test](./test_reactive_functions.sh) |
| `/asyncSingleProcess` | `POST` | `Function<Mono, Mono>` | Non-blocking processing of a single item. | [reactive_test](./test_reactive_functions.sh) |
| `/splitAndExpand` | `POST` | `Function<S, Flux>` | **One-to-Many**: Splits a string into characters. | [reactive_test](./test_reactive_functions.sh) |
| `/processWithHeaders` | `POST` | `Function<Msg, Msg>`| Demonstrates header manipulation. | [utility_test](./test_utility_functions.sh) |
| `/reactiveMessageProcess`| `POST` | `Function<FluxMsg, FluxMsg>` | Reactive processing with headers. | [utility_test](./test_utility_functions.sh) |
| `/sanitizeAndMask` | `POST` | `Function<S, S>` | **Programmatic Chaining**: Sanitizes then Masks. | [utility_test](./test_utility_functions.sh) |
| `/sanitizeText\|auditEvent`| `POST`| `Pipeline` | **Declarative Chaining**: Sanitizes then Audits. | [utility_test](./test_utility_functions.sh) |

---

## 🌟 Key Benefits

### 1. Decoupling Logic from Delivery
Your business logic doesn't know (and doesn't care) if it's being triggered by an HTTP REST call, a Kafka message, or an AWS Lambda event. You write the logic once, and it works across all delivery methods.

### 2. Zero Boilerplate
You no longer need to write `@RestController`, `@PostMapping`, or `@RequestBody` annotations for every single operation. If it's a Bean and it's a Function, it's an endpoint.

### 3. Cloud Portability (Write Once, Run Anywhere)
Spring Cloud Function provides adapters for all major cloud providers.

### 4. Simplified Testing
Since your logic is just a standard Java Function, you can unit test it without starting a web server or a messaging broker.

---

## 🛠 Summary of the Model

While the three core interfaces are the foundation, Spring Cloud Function is extremely flexible regarding their signatures.

### 1. The Core Three (Synchronous)
| Interface | Purpose | HTTP Method | Example |
| :--- | :--- | :--- | :--- |
| **`Function<I, O>`** | Transform data | `POST` | `String` → `String` (`sanitizeText`) |
| **`Supplier<O>`** | Produce data | `GET` | `void` → `UUID` (`generateEventId`) |
| **`Consumer<I>`** | Sink/Save data | `POST` | `String` → `void` (`auditEvent`) |

### 2. Reactive Signatures (Non-Blocking)
For high-throughput or streaming data, you can use **Project Reactor** types.
- `Function<Flux<I>, Flux<O>>`: Processes a stream of inputs and returns a stream of outputs.
- `Function<Mono<I>, Mono<O>>`: Handles a single asynchronous result.

### 3. Message-Based Signatures
If you need access to **Metadata** (like headers), wrap types in `Message<T>`.

### 4. Advanced: Multi-Input & Multi-Output
Using Project Reactor's `Tuple` classes, you can define functions that take or return multiple streams.

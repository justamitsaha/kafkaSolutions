# Order Service: Production EDA Guide

This module demonstrates a **Production-Grade Event-Driven Architecture (EDA)** using **Spring Cloud Stream**, **Spring Cloud Function**, and **Apache Kafka**. It builds upon the functional patterns explored in `functionsSample` to create a resilient, scalable order processing system.

## 🏗 System Architecture & Data Flow (Saga Pattern)

The service implements a multi-stage **Event Choreography (Saga)** workflow, transitioning an order through various states via Kafka topics.

### 1. Ingestion Pipeline (`HTTP -> orders.v1`)
- **Trigger**: An HTTP `POST` request to `/api/orders/ingest`.
- **Logic**: Validates the payload. Passes validation: sets status to `RECEIVED`. Fails validation: sets status to `VALIDATION_FAILED`.
- **Output**: Publishes to `orders.v1` using `customerId` as the partition key.

### 2. Workflow Step 1: Order Processor (`orders.v1 -> payments.v1`)
- **Function**: `orderProcessor`
- **Trigger**: Consumes `RECEIVED` events from `orders.v1`.
- **Logic**: 
    - Updates order state to `PLACED` and emits this back to `orders.v1`.
    - Generates a `PaymentEvent` (status `PENDING`).
    - Uses **Manual Acknowledgment** (`ackMode=MANUAL`).
- **Output**: Publishes the `PaymentEvent` to the `payments.v1` topic.

### 3. Workflow Step 2: Payment Processor (`payments.v1 -> orders.v1`)
- **Function**: `paymentProcessor`
- **Trigger**: Consumes events from `payments.v1`.
- **Logic**: Simulates payment processing latency (2 seconds). Upon success, it updates the original order state.
- **Output**: Publishes an `OrderEvent` with status `COMPLETED` back to `orders.v1`.

### 4. Workflow Sink: Order Finalizer (`orders.v1 -> SSE Sink`)
- **Function**: `orderFinalizer`
- **Trigger**: Consumes ALL events from `orders.v1` (i.e., `RECEIVED`, `PLACED`, `COMPLETED`).
- **Logic**: Acknowledges the offset and broadcasts the real-time state change to an internal **Reactive Hot Sink**.
- **Output**: Broadcasts the state to active Server-Sent Event (SSE) subscribers via `GET /api/orders/stream`.

---

## 🗺 API Endpoints

| Endpoint | Method | Technical Pattern | Description | Test Script |
| :--- | :--- | :--- | :--- | :--- |
| `/api/orders/ingest` | `POST` | `Flux<Req> -> Flux<Msg>`| Ingests orders into the Kafka system. | [test_order_service.sh](./test_order_service.sh) |
| `/api/orders/stream` | `GET` | **Hot Sink (SSE)** | Live stream of processed order events. | [test_order_service.sh](./test_order_service.sh) |
| `/swagger-ui/index.html`| `GET` | OpenAPI/Swagger | Interactive documentation. | N/A |

---

## 🌟 Production Patterns Demonstrated

### 1. Manual Acknowledgment (`ackMode=MANUAL`)
Unlike standard auto-commit, this service uses manual ACKs. This ensures "At-Least-Once" delivery semantics: we only tell Kafka we're done *after* our business logic has finished successfully.

### 2. Dead Letter Topic (DLT)
Any message that fails processing after **3 retry attempts** (configured with exponential backoff) is automatically moved to the `orders.v1.DLT` topic for manual inspection and recovery.

### 3. Smart Partitioning Strategy
The system explicitly uses `customerId` as the Kafka partition key (configured via `spring.cloud.stream.bindings.ingestOrders-out-0.producer.partitionKeyExpression=payload.customerId`).

**Why this is the proper approach:**
*   **Guaranteed Ordering**: Kafka guarantees strict message ordering *only within a single partition*. By hashing the `customerId`, all events for a specific customer always land on the same partition. This ensures that if Customer A creates an order, updates it, and then cancels it, the consumer processes those events in the exact chronological sequence.
*   **Stateful Processing Readiness**: If the architecture evolves to use Kafka Streams for aggregations (e.g., "Total spend per customer"), having all data for one customer on a single partition avoids expensive cross-network data shuffling.

**Potential Pitfalls to Monitor:**
*   **The "Whale Customer" (Data Skew)**: If one massive B2B customer generates 80% of your traffic, one partition (and its single consumer thread) will handle 80% of the load, causing a bottleneck while other consumers sit idle. If this occurs, a composite key (e.g., `customerId + date`) might be required.

### 4. Reactive Hot Sink
The service maintains a `Sinks.Many<OrderEvent>` which acts as a bridge between the asynchronous Kafka consumer and the real-time HTTP SSE stream.

---

## 🤔 The Spring Cloud Stream Advantage

A core goal of this module is to demonstrate how Spring Cloud Stream simplifies Event-Driven Architecture compared to traditional Spring Kafka.

### The "Old Way" (Traditional Spring Kafka)
You manage the broker interactions directly.
```text
Controller -> KafkaTemplate.send() -> Kafka
Kafka -> @KafkaListener -> Business Logic
```

### The "New Way" (Spring Cloud Stream)
You manage **only** business functions. The framework handles the boilerplate.
```text
Controller -> Function() -> Binder -> Kafka
Kafka -> Binder -> Consumer()
```

#### How it works in code:
Instead of writing explicit `KafkaTemplate.send()` or `@KafkaListener` logic, you simply write pure Java functions:

```java
// Producer
@Bean
public Function<Flux<OrderRequest>, Flux<Message<OrderEvent>>> ingestOrders() { ... }

// Consumer
@Bean
public Consumer<Flux<Message<OrderEvent>>> orders() { ... }
```

Then, you map these functions to Kafka topics using `application.properties`:
```properties
# Route the OUTPUT (-out-0) of the ingestOrders function to orders.v1
spring.cloud.stream.bindings.ingestOrders-out-0.destination=orders.v1

# Route data from orders.v1 into the INPUT (-in-0) of the orders consumer
spring.cloud.stream.bindings.orders-in-0.destination=orders.v1
```

#### Key Advantages:
1.  **Zero Boilerplate**: No need to instantiate producers, manage consumer loops, or write serialization logic.
2.  **Broker Agnostic**: The Java code has zero Kafka imports (except for specific headers). You could switch to RabbitMQ simply by changing a dependency in `pom.xml`.
3.  **Simplified Testing**: You can unit test your Kafka logic simply by calling `ingestOrders().apply(...)` without needing a running Kafka cluster.

---

## 🏗 Topic Management Strategy (`KafkaTopicsConfig.java`)

In the source code, you will find `KafkaTopicsConfig.java` with its `@Configuration` annotation **commented out**. 

While Spring Boot allows you to programmatically create topics via `NewTopic` beans, this is generally considered an **anti-pattern for production environments** for several reasons:

1.  **Configuration Override Risk**: If the application auto-creates a topic, it might create it with default settings (e.g., Replication Factor = 1), overriding the cluster defaults or failing to meet high-availability requirements (like our required Replication Factor of 3).
2.  **Separation of Concerns**: Infrastructure (topics, partitions, retention policies) should be managed by Infrastructure-as-Code (IaC) tools like Terraform or dedicated Kafka Ops teams, not the application code.
3.  **Connection Issues**: Auto-creation logic sometimes relies on default bootstrap-server properties, which can lead to connection timeouts if not perfectly aligned with the environment variables.

**Best Practice**: We rely on external scripts (like `doc/kafka.sh`) or IaC to provision topics *before* the application starts.

---

## 🛠 Infrastructure Requirements

This module requires the 3-node Kafka cluster defined in the root directory.

- **Main Topic**: `orders.v1` (3 partitions, 3 replicas)
- **Workflow Topic**: `payments.v1` (3 partitions, 3 replicas)
- **Error Topic**: `orders.v1.DLT`
- **Consumer Group**: `order-processors`

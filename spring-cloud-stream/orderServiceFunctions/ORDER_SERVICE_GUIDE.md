# Order Service: Production EDA Guide

This module demonstrates a **Production-Grade Event-Driven Architecture (EDA)** using **Spring Cloud Stream**, **Spring Cloud Function**, and **Apache Kafka**. It builds upon the functional patterns explored in `functionsSample` to create a resilient, scalable order processing system.

## 🏗 System Architecture & Data Flow

The service is divided into two main reactive pipelines:

### 1. Ingestion Pipeline (`HTTP -> Kafka`)
- **Trigger**: An HTTP `POST` request to `/api/orders/ingest`.
- **Logic**:
    - Receives a batch of `OrderRequest` objects.
    - Performs **JSR-303 Validation** (e.g., non-empty fields).
    - If validation fails: Sets status to `VALIDATION_FAILED`.
    - If validation passes: Sets status to `RECEIVED`.
    - Wraps the data in a `Message<OrderEvent>` with the `customerId` as the **Kafka Partition Key**.
- **Output**: Publishes to the `orders.v1` Kafka topic.

### 2. Processing Pipeline (`Kafka -> Logic -> Sink`)
- **Trigger**: Consumes events from the `orders.v1` Kafka topic.
- **Logic**:
    - **Filtering**: Specifically checks for `VALIDATION_FAILED` status. If found, it throws an exception to trigger the retry/DLT mechanism.
    - **State Update**: Successfully processed orders are emitted to an internal **Reactive Hot Sink**.
    - **Manual Acknowledgment**: The system only commits the Kafka offset *after* business logic succeeds.
- **Output**: Broadcasts the processed event to any active Server-Sent Event (SSE) subscribers.

---

## 🕵️‍♂️ How to Verify the Flow

To truly understand the asynchronous nature and broker portability of this architecture, you can monitor and verify the pipelines under either **Apache Kafka** or **RabbitMQ** modes.

---

### Option 1: Verification in Kafka Mode

Ensure the application is running with the Kafka profile active (`--spring.profiles.active=kafka`).

#### Step 1: Open the Monitors
Open two separate terminal windows:
*   **Terminal 1 (SSE Stream)**:
    ```bash
    curl -N 'http://localhost:8080/api/orders/stream'
    ```
*   **Terminal 2 (Kafka Topic Consumer)**: Run from the project root:
    ```bash
    docker exec -it kafka1 kafka-console-consumer \
      --bootstrap-server kafka1:19092 \
      --topic orders.v1 \
      --property print.key=true \
      --property print.headers=true
    ```

#### Step 2: Ingest a Valid Order
In a third terminal, post a valid order payload:
```bash
curl -X POST 'http://localhost:8080/api/orders/ingest' \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"ORD-SUCCESS", "customerId":"CUST-1", "customerName":"John Doe", "amount":99.99}]'
```
*   **What you should see**:
    1.  **API Response**: Immediate `200 OK` listing the received events.
    2.  **Terminal 2 (Kafka)**: The raw JSON message appears in `orders.v1` with the key `CUST-1`.
    3.  **Application Logs**: Logs `Processing order: id=ORD-SUCCESS...` followed by `Acked partition=X offset=Y`, indicating manual offset commitment.
    4.  **Terminal 1 (SSE Stream)**: Pushes the event `data:{"orderId":"ORD-SUCCESS"...}` in real-time.

#### Step 3: Ingest an Invalid Order (Triggering the DLT)
Post an invalid order (missing the `customerName` field and a negative `amount`):
```bash
curl -X POST 'http://localhost:8080/api/orders/ingest' \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"ORD-FAIL", "customerId":"CUST-2", "customerName":"", "amount":-5.00}]'
```
*   **What you should see**:
    1.  **Application Logs**: Throws `IllegalArgumentException: Downstream refused invalid order`. The server skips manual ACK, attempts processing 3 times (using exponential backoff), and then stops.
    2.  **Terminal 1 (SSE Stream)**: Nothing is emitted.
    3.  **DLT Verification**: Run the console consumer on the dead-letter topic:
        ```bash
        docker exec -it kafka1 kafka-console-consumer --bootstrap-server kafka1:19092 --topic orders.v1.DLT --from-beginning
        ```
        The failed `ORD-FAIL` payload is visible.

---

### Option 2: Verification in RabbitMQ Mode

Ensure the application is running with the RabbitMQ profile active (`--spring.profiles.active=rabbitmq`).

#### Step 1: Open the Monitors
*   **Terminal 1 (SSE Stream)**:
    ```bash
    curl -N 'http://localhost:8080/api/orders/stream'
    ```
*   **Browser (RabbitMQ Management Dashboard)**:
    Open [http://localhost:15672](http://localhost:15672) (Login: `guest` / `guest`).
    *   Navigate to the **Exchanges** tab to verify `orders.v1` has been automatically created.
    *   Navigate to the **Queues** tab to see that the binder has provisioned the `orders.v1.order-processors` queue and the `orders.v1.order-processors.dlq` queue.

#### Step 2: Ingest a Valid Order
In another terminal, post a valid order payload:
```bash
curl -X POST 'http://localhost:8080/api/orders/ingest' \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"ORD-SUCCESS-RABBIT", "customerId":"CUST-1", "customerName":"John Doe", "amount":99.99}]'
```
*   **What you should see**:
    1.  **API Response**: Immediate `200 OK`.
    2.  **Terminal 1 (SSE Stream)**: Pushes the event `data:{"orderId":"ORD-SUCCESS-RABBIT"...}` in real-time.
    3.  **RabbitMQ Management**: The message count spikes and goes back to zero immediately in `orders.v1.order-processors` as the consumer processes and acknowledges the message.

#### Step 3: Ingest an Invalid Order (Triggering the DLQ)
Post an invalid order:
```bash
curl -X POST 'http://localhost:8080/api/orders/ingest' \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"ORD-FAIL-RABBIT", "customerId":"CUST-2", "customerName":"", "amount":-5.00}]'
```
*   **What you should see**:
    1.  **Application Logs**: Exception is thrown. Spring Cloud Stream retries processing 3 times.
    2.  **Terminal 1 (SSE Stream)**: Nothing is emitted.
    3.  **RabbitMQ Management (DLQ Verification)**:
        *   In the **Queues** tab, you will see `1` message sitting inside the `orders.v1.order-processors.dlq` queue.
        *   Click on the queue name `orders.v1.order-processors.dlq`, scroll down to the **Get messages** section, and click **Get Message(s)** to inspect the payload, custom headers, and the failure exception stack trace injected by the binder.

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

## 🔌 Multi-Binder Portability (Kafka vs. RabbitMQ)

One of the greatest advantages of using **Spring Cloud Stream** is binder abstraction. Because the Java codebase contains zero broker-specific dependencies or code, you can switch the underlying message broker at runtime purely through configuration.

We have configured `orderServiceFunctions` to support both **Apache Kafka** and **RabbitMQ** using Spring Boot Profiles:

### 1. The Configuration Files
- [application.properties](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/spring-cloud-stream/orderServiceFunctions/src/main/resources/application.properties): Contains common, broker-agnostic settings (application name, scan paths, function definitions).
- [application-kafka.properties](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/spring-cloud-stream/orderServiceFunctions/src/main/resources/application-kafka.properties): Active when using `-Dspring.profiles.active=kafka` (default). Configures Kafka brokers, manual offsets (`MANUAL`), and partition counting.
- [application-rabbitmq.properties](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/spring-cloud-stream/orderServiceFunctions/src/main/resources/application-rabbitmq.properties): Active when using `-Dspring.profiles.active=rabbitmq`. Configures RabbitMQ connection details, automatic acknowledgments (`AUTO`), and binds DLQ/DLX exchanges.

### 2. Running with Apache Kafka
Make sure your Kafka cluster is up (`docker-compose -f doc/docker-compose.yaml up -d`), then run:
```bash
mvn spring-boot:run -pl spring-cloud-stream/orderServiceFunctions -Dspring-boot.run.arguments="--spring.profiles.active=kafka"
```

### 3. Running with RabbitMQ
Start the local RabbitMQ broker:
```bash
docker-compose -f doc/docker-compose-rabbitmq.yaml up -d
```
Then run the application:
```bash
mvn spring-boot:run -pl spring-cloud-stream/orderServiceFunctions -Dspring-boot.run.arguments="--spring.profiles.active=rabbitmq"
```
The RabbitMQ binder will automatically provision the necessary topic exchanges (`orders.v1`), queues (`orders.v1.order-processors`), and dead-letter routing headers on startup without writing a single line of Java code.

---

## 🛠 Infrastructure Requirements

Depending on the active profile, this module requires:

*   **Kafka Mode**: The 3-node Kafka cluster defined in [docker-compose.yaml](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/doc/docker-compose.yaml).
*   **RabbitMQ Mode**: The RabbitMQ container (AMQP on port `5672`, Management UI on `15672`) defined in [docker-compose-rabbitmq.yaml](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/doc/docker-compose-rabbitmq.yaml).

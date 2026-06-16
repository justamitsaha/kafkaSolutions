# Project Overview: Kafka Solutions

This project demonstrates an event-driven architecture using **Java 21**, **Spring Boot 3**, and **Apache Kafka**. It is divided into two primary modules: a production-grade order service and a sample functional utility service.

---

## 📦 Module: `orderServiceFunctions`
This is the core business module handling order lifecycles through reactive Kafka streams.

### 🛣 API Endpoints
| Method | Endpoint | Input | Action |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/orders/ingest` | `List<OrderRequest>` | Validates orders and publishes to Kafka topic `orders.v1`. |
| `GET` | `/api/orders/stream` | N/A | **Server-Sent Events (SSE)**: Streams processed orders to the client in real-time. |

### 🧠 Business Logic & State Flow
1.  **Ingestion Phase (`ingestOrders`):**
    *   **Validation**: Uses JSR-303 (Jakarta Validation).
    *   **State Change**: 
        *   If validation fails: Sets status to `VALIDATION_FAILED` and adds error details.
        *   If validation passes: Sets status to `RECEIVED`.
    *   **Output**: Message is sent to Kafka with `customerId` as the partition key.
2.  **Processing Phase (`orders` consumer):**
    *   **Filter**: If an incoming event has `VALIDATION_FAILED`, the consumer throws an exception (triggering Kafka retries and eventually the DLT).
    *   **Action**: Valid orders are broadcasted to the internal reactive sink for the `/stream` endpoint.
    *   **Acknowledgment**: Manual offset commit only happens after successful processing.

### 🛠 Error Handling
- **Retries**: 3 attempts with exponential backoff (1s to 10s).
- **Dead Letter Topic**: Failed messages are moved to `orders.v1.DLT`.
- **Manual ACKs**: Offset is committed only *after* the consumer logic completes.

---

## 🧪 Module: `functionsSample`
A collection of utility functions exposed as HTTP endpoints to demonstrate the FaaS (Function-as-a-Service) model within Spring.

### 🛣 API Endpoints & Logic
| Endpoint | Method | Logic |
| :--- | :--- | :--- |
| `/uppercase` | `POST` | Converts the request body string to uppercase. |
| `/reverse` | `POST` | Reverses the request body string. |
| `/randomUuid` | `GET` | Generates and returns a new random UUID string. |
| `/logger` | `POST` | Consumes a string and prints it to the server console. |
| `/processOrder` | `POST` | **State Logic**: If `quantity > 0`, returns `CONFIRMED`; otherwise, returns `REJECTED`. |
| `/validateOrder` | `POST` | **Validation**: Throws `IllegalArgumentException` if the `item` field is null or blank. |

---

## 🏗 Infrastructure & Dependency Requirements

Different modules have different operational requirements.

### 📦 `orderServiceFunctions` Requirements
This module requires a full Kafka environment and the following stack:

*   **Infrastructure (External)**:
    *   **Kafka Cluster**: Minimum 3-node cluster for replication (defined in `doc/docker-compose.yaml`).
    *   **Topics**:
        *   `orders.v1`: 3 partitions, replication factor 3.
        *   `orders.v1.DLT`: Dead letter topic for failures.
*   **Key Dependencies**:
    *   `spring-cloud-starter-stream-kafka`: For Kafka connectivity.
    *   `spring-boot-starter-webflux`: Reactive stack for SSE and REST.
    *   `spring-boot-starter-validation`: JSR-303 validation for order payloads.
    *   `springdoc-openapi-starter-webflux-ui`: Swagger documentation.

### 🧪 `functionsSample` Requirements
This is a lightweight module with no external infrastructure dependencies.

*   **Infrastructure (External)**:
    *   **None**: Runs as a standalone HTTP server. No Kafka or Database required.
*   **Key Dependencies**:
    *   `spring-cloud-function-web`: Exposes Java functions as HTTP endpoints.
    *   `spring-boot-starter-webflux`: Provides the underlying Netty server.
    *   `springdoc-openapi-starter-webflux-ui`: Swagger documentation.

---

## ⚙️ Configuration & Portability

The infrastructure and application are parameterized for flexibility.

### Running Kafka on a Different Host
By default, the project assumes Kafka is running on `localhost`. To run it on a remote machine (e.g., your previous `192.168.0.143` setup):

1. **Docker Compose**: Set the `KAFKA_HOST` variable before starting:
   ```bash
   $env:KAFKA_HOST="192.168.0.143"; docker-compose -f doc/docker-compose.yaml up -d
   ```
2. **Spring Boot**: Pass the `KAFKA_BROKERS` property:
   ```bash
   mvn spring-boot:run -pl orderServiceFunctions -Dspring-boot.run.arguments="--spring.cloud.stream.kafka.binder.brokers=192.168.0.143:9092,192.168.0.143:9093,192.168.0.143:9094"
   ```

---

## 🔧 Getting Started

### 🌐 The "Two Worlds" Problem
Kafka is configured with two separate listeners to handle different network environments. Understanding this is critical for connectivity:

1.  **World 1: The Windows Host (Your Java App)**
    *   **Address**: `localhost:9092`
    *   **Context**: Your IDE or terminal running on Windows.
    *   **Mechanism**: Docker Desktop forwards these ports from your machine into the container.

2.  **World 2: Docker Internal (CLI Tools)**
    *   **Address**: `kafka1:19092`
    *   **Context**: Any `docker exec` command or communication between microservices inside Docker.
    *   **Mechanism**: Uses the internal Docker bridge network and container names.

> **Warning**: Never use `localhost` inside a `docker exec` command (e.g., `--bootstrap-server localhost:9092`). This will fail because `localhost` inside a container refers to that specific container, not your machine.

---

### 🚀 Execution Steps
1. **Start Infrastructure**:
   ```bash
   docker-compose -f doc/docker-compose.yaml up -d
   ```
2. **Run Order Service**:
   ```bash
   mvn spring-boot:run -pl orderServiceFunctions
   ```
3. **Run Sample Service**:
   ```bash
   mvn spring-boot:run -pl functionsSample
   ```

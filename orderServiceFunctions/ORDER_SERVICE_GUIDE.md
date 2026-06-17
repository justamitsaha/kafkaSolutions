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

### 3. Smart Partitioning
By using `customerId` as the partition key, the system guarantees that all orders for a specific customer are processed in the **exact order they were received**, even when scaled across multiple instances.

### 4. Reactive Hot Sink
The service maintains a `Sinks.Many<OrderEvent>` which acts as a bridge between the asynchronous Kafka consumer and the real-time HTTP SSE stream.

---

## 🛠 Infrastructure Requirements

This module requires the 3-node Kafka cluster defined in the root directory.

- **Main Topic**: `orders.v1` (3 partitions, 3 replicas)
- **Error Topic**: `orders.v1.DLT`
- **Consumer Group**: `order-processors`

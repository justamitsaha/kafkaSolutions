# Reactive Kafka Order Service

Reactive Spring Boot service that accepts HTTP order requests, persists them via the transactional outbox pattern, and publishes events to Kafka using Reactor Kafka with retry and dead-letter safeguards.

---

## Architecture Summary
- `configuration`: Kafka client factories and shared infrastructure beans (senders, receivers, serializers).
- `controller`: Reactive REST API (`OrderController`) that accepts order requests.
- `dto`: External API request/response contracts.
- `events`: Immutable Kafka event models (`OrderEvent`) plus mappers for Protobuf.
- `messanger`: Reactive Kafka components (outbox dispatcher, main & retry consumers, DLQ publisher).
- `model`: R2DBC entities for orders and outbox storage.
- `repository`: Reactive repositories for orders and the outbox queue.
- `service`: Business services (`OrderService`, `OutboxService`) coordinating persistence and messaging.
- `resources`: `application.properties`, schema DDL, Protobuf schema, helper scripts (`kafka.sh`).

---

## Tech Stack
- Java 21, Spring Boot 3 (WebFlux, Actuator).
- Project Reactor & Reactor Kafka.
- R2DBC (MySQL driver) for reactive persistence.
- Micrometer + Prometheus registry, OpenTelemetry OTLP exporter.
- Confluent Protobuf serializer & Schema Registry ready configuration.
- Testcontainers (Kafka, Postgres) for integration testing.

---

## How It Works
1. `OrderController` receives a POST `/orders` request and delegates to `OrderService`.
2. `OrderService` validates input and asks `OutboxService` to persist the order and a serialized `OrderEvent` in the `order_outbox` table within a reactive transaction.
3. `OutboxPublisher` polls pending outbox rows, deserializes events, and publishes them through `OrderEventPublisher`.
4. `OrderEventPublisher` sends the event to the `order.events` topic with idempotent producer settings and Micrometer counters. Send failures are routed to the DLT and trigger outbox retries.
5. `OrderEventConsumer` processes messages from the main topic. Failures are sent to `RetryEventPublisher`, which schedules exponential back-off delivery to the `order.events.retry` topic. After configurable attempts, messages move to the DLT.
6. `DltPublisher` records full failure metadata on the `order.events.dlt` topic for offline inspection.

---

## Kafka Setup Guide
Use the scripts in the root `doc/` directory as a reference for topic creation and running a local Docker cluster:

To run a local stack with a Kafka broker and Schema Registry, use `doc/docker-compose-kafka-schema-registry.yaml`:

```bash
# From the project root
docker compose -f doc/docker-compose-kafka-schema-registry.yaml up -d
```

Verify or create topics using the instructions in [OrderService_Logic.md](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/reactiveOrderService/OrderService_Logic.md).

The application expects a Kafka cluster plus a Schema Registry reachable at `http://localhost:8081` when using Protobuf payloads.

---

## Configuration
Key properties in `src/main/resources/application.properties`:

- `spring.kafka.bootstrap-servers`: Kafka broker list.
- `spring.kafka.consumer.*` & `spring.kafka.producer.*`: Reactive Kafka tuning (acks, retries, batching).
- `app.kafka.topic.order`, `.retry`, `.dlt`, `.proto`: Topic names for main, retry, dead-letter, and Protobuf traffic.
- `app.kafka.schema-registry-url`: Schema Registry endpoint used by the Protobuf serializer/deserializer.
- `app.kafka.retry.max-attempts`: Number of retry attempts before sending to DLT.
- `app.kafka.outbox.*`: Poll interval, batch size, and retry guardrails for the outbox dispatcher.
- `spring.r2dbc.*`: Reactive database connection for orders/outbox tables.
- `management.endpoints.web.exposure.include`: Enables health, info, metrics, and Prometheus scrape endpoints.

`schema.sql` provisions `orders` and `order_outbox` tables; run it once against your database prior to booting the service.

---

## Running the Application

To run the application locally:

```bash
# Start local Kafka, Schema Registry, and Postgres dependencies from the project root
docker compose -f doc/docker-compose-kafka-schema-registry.yaml up -d

# Build and run the service
mvn clean spring-boot:run
```

### Verification
```bash
# Optional: verify Prometheus scrape endpoint
curl http://localhost:8080/actuator/prometheus
```

---

## Extending Features
- **Outbox pattern**: Extend `OrderOutboxEntity` with additional metadata (trace IDs, payload type) or plug in Debezium/CDC for cross-service replication.
- **Retry & DLQ**: Tune `app.kafka.retry.max-attempts` and `RetryEventPublisher` back-off strategy to match SLA needs. Add consumers on `order.events.dlt` for alerting or replay tooling.
- **Schema Management**: Switch producers/consumers to the Protobuf beans (`protobufKafkaSender`/`protobufKafkaReceiver`) or introduce new message contracts under `src/main/proto`.
- **Observability**: Wire OTLP exporter endpoints, add domain-specific Micrometer timers, or scrape Kafka consumer lag via Prometheus/Grafana.

---

## Future Enhancements
- CQRS read models fed from Kafka Streams or ksqlDB.
- Saga orchestration with additional reactive services.
- Event sourcing snapshots and audit trail APIs.
- Automated topic provisioning and ACL management via IaC pipelines.

---

## Contributing
1. Create a feature branch per change set.
2. Add or update reactive components under the appropriate package (producer, consumer, service, or repository).
3. When introducing new topics or configs, document them in `README.md`, `kafka.sh`, and the `protobuff` scripts.
4. Prefer Reactor-first flows: avoid blocking calls, use `Mono`/`Flux` operators, and keep transactional boundaries within `TransactionalOperator`.
5. Add tests (unit or Testcontainers-based integration) when behaviour changes, then run `mvn test`.
6. Open a PR describing the scenario, config changes, and validation steps.

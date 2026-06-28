# Non-Reactive Order Service

A Spring Boot module demonstrating a traditional, blocking, synchronous Event-Driven Architecture (EDA) using standard JDBC transactions and blocking Kafka operations.

---

## 🏗 Architecture & Design

In contrast to the reactive outbox pattern shown in [reactiveOrderService](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/reactiveOrderService), this module handles writes synchronously inside a single blocking execution thread:

1. **Endpoint**: [OrderController](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/nonReactiveOrderService/src/main/java/com/saha/amit/nonReactiveOrderService/controller/OrderController.java) accepts standard HTTP POST requests.
2. **Business & Transaction Context**: [OrderService.createOrder](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/nonReactiveOrderService/src/main/java/com/saha/amit/nonReactiveOrderService/service/OrderService.java) is annotated with `@Transactional`.
3. **Blocking Publish**: Publishes the event to Kafka and blocks on the confirmation future (`.get()`).
4. **Synchronous Persist**: Inserts the order record in the database via standard Spring Data JPA ([OrderRepository](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/nonReactiveOrderService/src/main/java/com/saha/amit/nonReactiveOrderService/repository/OrderRepository.java)) and commits the transaction.

---

## 🛠 Operational Guide

### 📋 Prerequisites
- Ensure both the Kafka broker cluster and the MySQL database are running. Use the local Compose configuration:
  ```bash
  docker compose -f nonReactiveOrderService/docker-compose.yaml up -d
  ```

### 🚀 Running the Service
Run the service from the project root using Maven:
```bash
mvn spring-boot:run -pl nonReactiveOrderService
```

### 🧪 Verifying the Endpoint
Send a valid order request:
```bash
curl -X POST 'http://localhost:8080/api/orders' \
  -H 'Content-Type: application/json' \
  -d '{"customerId": "CUST-999", "amount": 250.00}'
```

---

## 🕵️‍♂️ Transaction Rollback & Dual-Write Issues

This design exposes transactions to typical consistency vulnerabilities (such as dirty event publication when the database write fails after Kafka succeeds).

For detailed failure scenario walkthroughs, step-by-step verification commands, and SQL queries to verify rollback states, see the main [Non-Reactive Order Service Guide](file:///C:/Amit/Work/code/Java/event_driven/kafkaSolutions/doc/non_reactive_order_service.md).

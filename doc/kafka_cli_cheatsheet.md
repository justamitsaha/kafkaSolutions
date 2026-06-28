# 🛠️ Apache Kafka CLI Commands: Cheat Sheet

This document compiles the most common CLI operations for Kafka brokers. These commands can be executed either inside the broker container or directly from your host terminal using `docker exec`.

---

## 🚪 Running Commands inside the Docker Container

To open an interactive bash session inside the primary Kafka broker container:
```bash
docker exec -it kafka1 bash
```
Once inside, you can run all commands directly without prefixing them with `docker exec`.

---

## 📁 Topic Management

### 1. Create a Topic
Create a new topic named `test-topic` with 3 partitions and a replication factor of 3:
```bash
docker exec -it kafka1 kafka-topics \
  --bootstrap-server kafka1:19092 \
  --create \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 3
```

### 2. List All Topics
```bash
docker exec -it kafka1 kafka-topics \
  --bootstrap-server kafka1:19092 \
  --list
```

### 3. Describe a Topic
Show partition counts, replication factors, replicas, and In-Sync Replicas (ISR) for a topic:
```bash
docker exec -it kafka1 kafka-topics \
  --bootstrap-server kafka1:19092 \
  --describe \
  --topic test-topic
```

### 4. Alter Topic Partition Count
Increase the partition count of an existing topic to `5`:
```bash
docker exec -it kafka1 kafka-topics \
  --bootstrap-server kafka1:19092 \
  --alter \
  --topic test-topic \
  --partitions 5
```
> [!WARNING]
> You can increase the number of partitions on a topic, but you **cannot decrease** them because Kafka logs are append-only.

---

## 📤 Producing Messages

### 1. Simple Producer (Value Only)
Start an interactive console producer to publish text messages:
```bash
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic test-topic
```
*   Type your message and press `Enter` to publish. Press `Ctrl+C` to exit.

### 2. Key-Value Producer (Hashing Partition Key)
Produce messages with keys to ensure ordering. Below, we set the key-value separator to `-` (e.g., typing `KeyA-Hello World` routes using `KeyA` as the partition key):
```bash
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic test-topic \
  --property parse.key=true \
  --property key.separator=-
```

---

## 📥 Consuming Messages

### 1. Simple Consumer (From Beginning)
Start a console consumer to tail a topic and print all historical messages:
```bash
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic test-topic \
  --from-beginning
```

### 2. Print Key and Headers
Consume and display message keys and custom metadata headers alongside values:
```bash
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic test-topic \
  --from-beginning \
  --property print.key=true \
  --property print.headers=true \
  --property key.separator=" | "
```

### 3. Consume as Part of a Consumer Group
Spin up a consumer instance belonging to a specific group named `order-group`:
```bash
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic test-topic \
  --group order-group
```

---

## 👥 Consumer Group Diagnostics

### 1. List All Active Consumer Groups
```bash
docker exec -it kafka1 kafka-consumer-groups \
  --bootstrap-server kafka1:19092 \
  --list
```

### 2. Describe Consumer Group (Checking Lag)
View offset positions, partition assignments, client hostnames, and **Consumer Lag** (the number of messages backlog waiting to be processed):
```bash
docker exec -it kafka1 kafka-consumer-groups \
  --bootstrap-server kafka1:19092 \
  --describe \
  --group order-group
```

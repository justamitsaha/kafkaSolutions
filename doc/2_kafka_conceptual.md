# 🗄️ Apache Kafka: Architecture, Internals, and Spring Integration

This document covers Apache Kafka's distributed broker architecture, durability properties, metadata governance, and the detailed mechanics of Spring Boot's producer/consumer frameworks.

---

## 🏗️ Core Kafka Terminology & Distributed Architecture

Apache Kafka is designed as a distributed, partitioned, replicated commit log.

*   **Cluster**: A set of servers (brokers) working together to store and process events.
*   **Broker**: A single Kafka server. A cluster consists of multiple brokers. Each broker is a bootstrap server (connecting to one connects you to the entire cluster).
*   **Topic**: A logical category or stream name to which messages are published. Analogous to a database table but without structural constraints.
*   **Partition**: Topics are split into multiple partitions distributed across brokers. A partition is an ordered, immutable sequence of records continuously appended to a structured log file.
    *   **Ordering Guarantee**: Strict message ordering is guaranteed *only* at the individual partition level, not across the entire topic.
*   **Offset**: A unique sequential integer assigned to each record in a partition. Used by consumers to track their read position.
*   **Consumer Group**: A group of consumers working together to read from a topic. Kafka coordinates the assignment of partitions to the consumers in the group, enabling scale-out processing.

```
Topic A
 ├── Partition 0  [Msg 0][Msg 1][Msg 2][Msg 3]  --> Assigned to Consumer Instance 1
 ├── Partition 1  [Msg 0][Msg 1]                --> Assigned to Consumer Instance 2
 └── Partition 2  [Msg 0][Msg 1][Msg 2]         --> Assigned to Consumer Instance 3
```

---

## 🔀 Message Structure & Partitioning Strategy

Producers send events to Kafka as key-value pairs wrapped with optional headers:

### 1. Key (Optional)
Used for partition routing and message grouping.
*   **If Key is Null**: The producer uses a round-robin (or batching) strategy to distribute messages evenly across all partitions.
*   **If Key is Not Null**: The default partitioner hashes the key using a formula (e.g., `MurmurHash3(key) % total_partitions`) to determine the target partition. **This ensures all events with the same key always land on the same partition, guaranteeing chronological ordering.**

### 2. Value
The actual payload containing business details.

### 3. Serialization / Deserialization
Kafka only accepts raw bytes. Producers must serialize objects to bytes, and consumers must deserialize bytes back to objects. Common formats include:
*   **String / JSON**: E.g., Jackson. Easy to read but heavy in payload size.
*   **Avro / Protobuf**: Binary formats requiring schemas. Highly efficient and compact, backed by the **Confluent Schema Registry** to manage schema compatibility rules.

---

## 🛡️ Partition Replication, Leaders, and Durability

Kafka replicates partition logs across multiple brokers to ensure high availability and prevent data loss.

### 1. Leader vs. Followers
For each partition, one broker is assigned as the **Leader**, and the remaining brokers act as **Followers (Replicas)**.
*   **Leader**: Handles all read and write requests from clients.
*   **Followers**: Replicate data from the leader. If a follower stays up-to-date with the leader, it is marked as an **In-Sync Replica (ISR)**.

### 2. Acknowledgments (`acks`)
Producers can configure the level of write guarantees they require:
*   `acks=0`: The producer does not wait for any acknowledgment from the broker. Maximum speed, potential data loss.
*   `acks=1`: The producer waits for the partition leader to acknowledge the write. Minimal data loss if the leader fails before replicating to followers.
*   `acks=all` (or `-1`): The producer waits for the leader and all In-Sync Replicas to write the record. Guarantees zero data loss.

---

## 🪵 The Commit Log & Retention Policies

When a broker receives a message, it appends the bytes directly to a physical log file (`.log`) on the server disk.
*   **Log Location**: Configured via `log.dirs` in the broker's properties (commonly `/var/lib/kafka/data/`).
*   **Append-Only**: Writes are highly optimized sequential I/O operations.
*   **Retention**: Log records are kept based on configuration policies, irrespective of consumption:
    *   `log.retention.hours`: Default is 168 hours (7 days).
    *   `log.retention.bytes`: Maximum log size per partition.

---

## 🗳️ Metadata Management: ZooKeeper vs. KRaft

```mermaid
graph TD
    subgraph ZooKeeper Era (Kafka 2.X)
        ZK[ZooKeeper Cluster] <-->|Leader Election & Metadata| KB1[Kafka Broker 1]
        ZK <--> KB2[Kafka Broker 2]
        Client2[Kafka Client] -.->|Queries Metadata| ZK
    end

    subgraph KRaft Era (Kafka 3.X / 4.0)
        KB3[Kafka Controller Broker] <-->|Metadata Log / Raft| KB4[Kafka Broker 4]
        KB3 <--> KB5[Kafka Broker 5]
    end
```

### 1. ZooKeeper (Deprecated)
Historically managed broker memberships, partition leaders, and topic configurations.
*   **Drawbacks**: ZooKeeper introduces scalability bottlenecks (limited to around 100,000 partitions in a cluster) and requires running a separate configuration-management cluster.
*   **Rule**: Modern Kafka clients should **never** connect directly to ZooKeeper ports. Connect only to Kafka bootstrap brokers.

### 2. KRaft (Kafka Raft Metadata Mode)
Replaces ZooKeeper by storing metadata directly inside Kafka itself using a Raft consensus algorithm.
*   **Benefits**: Scales to millions of partitions, simplifies operations, speeds up controller recovery times, and uses a single security model.
*   **Status**: Production-ready since Kafka 3.3.1. ZooKeeper is deprecated and removed in Kafka 4.0.

---

## 🚀 Spring Boot Kafka Integration: Producer Internals

When you call `KafkaTemplate.send()`, the message goes through several components inside the client thread before hitting the network:

```
[KafkaTemplate.send]
        │
        ▼
   [Serializer] (Key/Value converted to bytes)
        │
        ▼
   [Partitioner] (Calculates target partition)
        │
        ▼
[Record Accumulator] ──► [Record Batch 1] [Record Batch 2]
        │                       (batch.size)
        ▼
   [Sender Thread] (Fires when batch is full OR linger.ms is met)
        │
        ▼
  [Kafka Broker]
```

### Key Performance Tuning Properties:
*   `batch.size`: The size in bytes to buffer before sending a batch of messages to a partition.
*   `buffer.memory`: Total memory bytes the producer can use to buffer records waiting to be sent.
*   `linger.ms`: The time in milliseconds to wait for additional messages to fill the batch before sending it. Setting this higher increases batching efficiency at the cost of slight latency.

### Three Common Coding Approaches to Send Messages:
1.  **Non-blocking (CompletableFuture)**:
    ```java
    kafkaTemplate.send("topic", key, value)
                 .whenComplete((result, ex) -> { ... });
    ```
2.  **Blocking (.get)**:
    ```java
    kafkaTemplate.send("topic", key, value).get(3, TimeUnit.SECONDS);
    ```
3.  **Using ProducerRecord (Adding Custom Headers)**:
    ```java
    ProducerRecord<String, String> record = new ProducerRecord<>("topic", null, key, value, headers);
    kafkaTemplate.send(record);
    ```

---

## 🎧 Spring Boot Kafka Integration: Consumer Internals

Consumers poll Kafka brokers in a single-threaded loop. In Spring, this loop is handled by container classes.

### 1. Container Hierarchy
*   `KafkaMessageListenerContainer`: Manages a single consumer thread polling partitions.
*   `ConcurrentMessageListenerContainer`: Spins up multiple `KafkaMessageListenerContainer` instances to read from partitions concurrently. Configured via `factory.setConcurrency(n)`.

### 2. Offset Commit Modes (`AckMode`)
To control when offsets are committed back to the internal `__consumer_offsets` topic:
*   `RECORD`: Commits offset after the listener finishes processing a single record.
*   `BATCH` (Default): Commits offset when all records returned by the active `poll()` batch have been processed.
*   `MANUAL`: The listener receives an `Acknowledgment` object and is responsible for calling `acknowledgment.acknowledge()` to commit offsets manually.

### 3. Error Handling and Retries
Spring Boot provides the `DefaultErrorHandler` to manage exceptions during consumer cycles.
*   **Fixed Backoff**: Retries consumption after a static interval (e.g., retry 3 times, waiting 1 second between attempts).
*   **Exponential Backoff**: Doubles the wait time between each retry attempt.
*   **Retry Listeners**: Allows registration of custom hooks to log or monitor failed delivery attempts.

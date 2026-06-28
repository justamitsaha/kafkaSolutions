# ==============================================================================
# Kafka Setup & Management Reference (Manual Execution Commands)
# ==============================================================================
# This file contains the raw commands to set up, monitor, test, and clean up
# Kafka topics for each microservice. You can copy and run these manually.
#
# ⚠️ REPLICATION FACTOR NOTE:
# - If running the single-broker stack (docker-compose-kafka-schema-registry.yaml):
#   Change '--replication-factor 3' to '--replication-factor 1' in all commands.
# - If running the three-broker stack (docker-compose.yaml):
#   Keep '--replication-factor 3' as is.
# ==============================================================================

# ==============================================================================
# 0. GENERAL UTILITIES
# ==============================================================================

# Test connection from Windows Host to Kafka Broker (Run in PowerShell)
# Test-NetConnection -ComputerName localhost -Port 9092

# List all active topics in the cluster (Wrapped for readability)
  docker exec -it kafka1 kafka-topics --list --bootstrap-server kafka1:19092

# Describe a specific topic (replace 'orders.v1' with the topic name)
docker exec -it kafka1 kafka-topics \
  --describe \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1


# ==============================================================================
# 1. SPRING CLOUD STREAM MICROSERVICE (Order Ingestion & Saga Workflow)
# ==============================================================================

# --- Setup: Create Topics ---
# Create orders.v1
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic orders.v1 \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create orders.v1.DLT (Dead Letter Topic)
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic orders.v1.DLT \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create payments.v1 (Saga Payment Processing)
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic payments.v1 \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# --- Monitoring: Consume Topics ---
# Consume orders.v1 (Main Events)
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume orders.v1.DLT (Dead Letters)
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1.DLT \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume payments.v1 (Payment Events)
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic payments.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# --- Manual Testing: Produce Topics ---
# Produce manually to orders.v1 (Sample JSON: {"orderId":"abc","customerId":"xyz","status":"RECEIVED"})
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1

# --- Cleanup: Delete Topics ---
# docker exec -it kafka1 kafka-topics --delete --topic orders.v1 --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic orders.v1.DLT --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic payments.v1 --bootstrap-server kafka1:19092


# ==============================================================================
# 2. REACTIVE ORDER MICROSERVICE
# ==============================================================================

# --- Setup: Create Topics ---
# Create order.events (Main JSON Events)
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic order.events \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create order.events.retry (Non-blocking Backoff Retry Topic)
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic order.events.retry \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create order.events.dlt (Exhausted Retry Dead Letter Topic)
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic order.events.dlt \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create order.events.proto (Protobuf Encoded Events)
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic order.events.proto \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# --- Monitoring: Consume Topics ---
# Consume order.events (Main JSON)
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic order.events \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume order.events.retry
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic order.events.retry \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume order.events.dlt
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic order.events.dlt \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume order.events.proto
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic order.events.proto \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# --- Manual Testing: Produce Topics ---
# Produce manually to order.events (Sample JSON: {"orderId":"123","customerId":"456","price":99.99,"status":"CREATED"})
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic order.events

# --- Cleanup: Delete Topics ---
# docker exec -it kafka1 kafka-topics --delete --topic order.events --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic order.events.retry --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic order.events.dlt --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic order.events.proto --bootstrap-server kafka1:19092


# ==============================================================================
# 3. SMS NOTIFICATION MICROSERVICE
# ==============================================================================

# --- Setup: Create Topics ---
# Create sms.events
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic sms.events \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create sms.events.retry
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic sms.events.retry \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create sms.events.dlt
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic sms.events.dlt \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# --- Monitoring: Consume Topics ---
# Consume sms.events
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic sms.events \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume sms.events.retry
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic sms.events.retry \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume sms.events.dlt
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic sms.events.dlt \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# --- Cleanup: Delete Topics ---
# docker exec -it kafka1 kafka-topics --delete --topic sms.events --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic sms.events.retry --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic sms.events.dlt --bootstrap-server kafka1:19092


# ==============================================================================
# 4. EMAIL NOTIFICATION MICROSERVICE
# ==============================================================================

# --- Setup: Create Topics ---
# Create email.events
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic email.events \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create email.events.retry
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic email.events.retry \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# Create email.events.dlt
docker exec -it kafka1 kafka-topics \
  --create \
  --if-not-exists \
  --topic email.events.dlt \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

# --- Monitoring: Consume Topics ---
# Consume email.events
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic email.events \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume email.events.retry
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic email.events.retry \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Consume email.events.dlt
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic email.events.dlt \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# --- Cleanup: Delete Topics ---
# docker exec -it kafka1 kafka-topics --delete --topic email.events --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic email.events.retry --bootstrap-server kafka1:19092
# docker exec -it kafka1 kafka-topics --delete --topic email.events.dlt --bootstrap-server kafka1:19092

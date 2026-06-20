#1. Create orders.v1
docker exec -it kafka1 kafka-topics \
  --create \
  --topic orders.v1 \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

#2. Create orders.v1.DLT (Dead Letter Topic)
docker exec -it kafka1 kafka-topics \
  --create \
  --topic orders.v1.DLT \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

#3. Create payments.v1 (Saga Workflow Topic)
docker exec -it kafka1 kafka-topics \
  --create \
  --topic payments.v1 \
  --bootstrap-server kafka1:19092 \
  --partitions 3 \
  --replication-factor 3

#4. Verify topics
docker exec -it kafka1 kafka-topics --list --bootstrap-server kafka1:19092

docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic orders.v1
docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic orders.v1.DLT
docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic payments.v1


# --- Consumers for Monitoring ---
# Monitor Orders
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true

# Monitor Payments
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic payments.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true


# --- Producers for Manual Testing ---
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1
# Sample JSON: {"orderId":"abc","customerId":"xyz","status":"RECEIVED"}


# Test connection from windows host to kafka broker
# Using localhost because of Docker port mapping
Test-NetConnection -ComputerName localhost -Port 9092


# --- Cleanup (Delete Topics) ---
docker exec -it kafka1 kafka-topics \
  --delete \
  --topic orders.v1 \
  --bootstrap-server kafka1:19092

docker exec -it kafka1 kafka-topics \
  --delete \
  --topic orders.v1.DLT \
  --bootstrap-server kafka1:19092

docker exec -it kafka1 kafka-topics \
  --delete \
  --topic payments.v1 \
  --bootstrap-server kafka1:19092



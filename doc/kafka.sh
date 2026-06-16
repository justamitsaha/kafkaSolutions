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

#3. Verify topics
docker exec -it kafka1 kafka-topics --list --bootstrap-server kafka1:19092

docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic orders.v1
docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic orders.v1.DLT


docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1 \
  --group order-consumer-group \
  --from-beginning


docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1

# Sample JSON: {"orderId":"abc","customerId":"xyz","status":"RECEIVED"}

docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic orders.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true


# Test connection from windows host to kafka broker
# Using localhost because of Docker port mapping
Test-NetConnection -ComputerName localhost -Port 9092

# Delete topics
docker exec -it kafka1 kafka-topics \
  --delete \
  --topic orders.v1 \
  --bootstrap-server kafka1:19092

# Delete old orders.v1.DLT
docker exec -it kafka1 kafka-topics \
  --delete \
  --topic orders.v1.DLT \
  --bootstrap-server kafka1:19092



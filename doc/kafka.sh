#1. Create orders.v1
docker exec -it kafka1 kafka-topics \
  --create \
  --topic orders.v1 \
  --bootstrap-server kafka1:9092 \
  --partitions 6 \
  --replication-factor 3

#2. Create orders.v1.DLT (Dead Letter Topic)
docker exec -it kafka1 kafka-topics \
  --create \
  --topic orders.v1.DLT \
  --bootstrap-server kafka1:9092 \
  --partitions 6 \
  --replication-factor 3

#3. Verify topics
docker exec -it kafka1 kafka-topics \
  --list \
  --bootstrap-server kafka1:9092

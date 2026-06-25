#!/bin/bash

BROKER="kafka1:19092"   # Bootstrap via internal listener inside the container
REPLICATION=1           # Set replication to 1 for single-broker compatibility
PARTITIONS=3

# ========== Order Events ==========
docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic order.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic order.events.retry \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic order.events.dlt \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic order.events.proto \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

# ========== SMS Events ==========
docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic sms.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic sms.events.retry \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic sms.events.dlt \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

# ========== Email Events ==========
docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic email.events \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic email.events.retry \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

docker exec -it kafka1 kafka-topics \
  --create --if-not-exists \
  --bootstrap-server $BROKER \
  --topic email.events.dlt \
  --partitions $PARTITIONS \
  --replication-factor $REPLICATION

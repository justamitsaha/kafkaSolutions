#!/usr/bin/env bash
set -euo pipefail

BROKER="${BROKER:-kafka1:9092}"
CLIENT_BROKER="${CLIENT_BROKER:-kafka1:19092}"

create_topic() {
  local topic="$1"
  local partitions="${2:-3}"
  local replication="${3:-3}"

  docker exec -it kafka1 kafka-topics \
    --create \
    --topic "${topic}" \
    --bootstrap-server "${BROKER}" \
    --partitions "${partitions}" \
    --replication-factor "${replication}" || true
}

case "${1:-help}" in
  topic-create)
    create_topic "order.events"
    create_topic "order.events.retry" 3 3
    create_topic "order.events.dlt" 3 3
    create_topic "order.events.proto" 3 3
    ;;
  topic-list)
    docker exec -it kafka1 kafka-topics --list --bootstrap-server "${BROKER}"
    ;;
  topic-describe)
    docker exec -it kafka1 kafka-topics --describe --bootstrap-server "${CLIENT_BROKER}" --topic "${2:-order.events}"
    ;;
  consume)
    docker exec -it kafka1 kafka-console-consumer \
      --bootstrap-server "${CLIENT_BROKER}" \
      --topic "${2:-order.events}" \
      --from-beginning \
      --property print.key=true \
      --property print.value=true \
      --property print.headers=true
    ;;
  start)
    echo "Use docker-compose or your platform scripts to start Kafka + Schema Registry."
    ;;
  *)
    echo "Usage: $0 {topic-create|topic-list|topic-describe <topic>|consume <topic>|start}"
    ;;
esac

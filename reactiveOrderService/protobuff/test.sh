chmod +x create-topics.sh
docker compose up -d
 ./create-topics.sh
#3. Verify topics
docker exec -it kafka1 kafka-topics --list --bootstrap-server kafka1:9092

curl -X POST http://192.168.0.143:8081/subjects/order.events-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{
    "schemaType": "PROTOBUF",
    "schema": "syntax = \"proto3\"; package com.example.order; message OrderEvent { string eventId = 1; string orderId = 2; string customerId = 3; string status = 4; double amount = 5; int64 timestamp = 6; }"
  }'

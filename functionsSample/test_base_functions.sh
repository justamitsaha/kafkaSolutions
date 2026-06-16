#!/bin/bash
# Port: 8081 (functionsSample)

echo "--- Testing Base Functional Interfaces (Sync) ---"

# 1. sanitizeText (Function<String, String>)
echo -e "\n[POST /sanitizeText]"
curl -X POST 'http://localhost:8081/sanitizeText' \
  -H 'Content-Type: text/plain' \
  -d '  hello world  '

# 2. generateEventId (Supplier<UUID>)
echo -e "\n\n[GET /generateEventId]"
curl -X GET 'http://localhost:8081/generateEventId'

# 3. auditEvent (Consumer<String>)
echo -e "\n\n[POST /auditEvent]"
curl -X POST 'http://localhost:8081/auditEvent' \
  -H 'Content-Type: text/plain' \
  -d 'Base system audit test'

# 4. processOrder (Function<OrderRequest, OrderResponse>)
echo -e "\n\n[POST /processOrder]"
curl -X POST 'http://localhost:8081/processOrder' \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"ORD-001", "item":"Tablet", "quantity":10}'

echo -e "\n\n--- Base Tests Complete ---"

#!/bin/bash
# Port: 8080 (reactiveOrderService)

echo "=== Testing Reactive Order Service (Outbox Pattern) ==="

# Define colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 1. Check existing orders
echo -e "\n[STEP 1] Fetching all existing orders..."
curl -s -X GET 'http://localhost:8080/orders' | json_pp 2>/dev/null || curl -s -X GET 'http://localhost:8080/orders'
echo ""

# 2. Ingest a VALID order (should succeed)
echo -e "\n[STEP 2] Placing a VALID order for CUST-100 (POST /orders)..."
RESPONSE=$(curl -s -X POST 'http://localhost:8080/orders' \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-100", "amount":200.00}')

echo "Response payload:"
echo "$RESPONSE" | json_pp 2>/dev/null || echo "$RESPONSE"

# Extract Order ID if json_pp is available or using simple grep/sed
ORDER_ID=$(echo "$RESPONSE" | grep -o '"orderId":"[^"]*' | grep -o '[^"]*$')
if [ -z "$ORDER_ID" ]; then
  # Fallback extraction
  ORDER_ID=$(echo "$RESPONSE" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')
fi

echo -e "Extracted Order ID: ${GREEN}$ORDER_ID${NC}"

# 3. Retrieve the created order by ID
if [ ! -z "$ORDER_ID" ]; then
  echo -e "\n[STEP 3] Fetching the newly created order by ID (GET /orders/$ORDER_ID)..."
  curl -s -X GET "http://localhost:8080/orders/$ORDER_ID" | json_pp 2>/dev/null || curl -s -X GET "http://localhost:8080/orders/$ORDER_ID"
  echo ""
fi

# 4. Filter orders by customer ID
echo -e "\n[STEP 4] Filtering orders for customer 'CUST-100' (GET /orders?customerId=CUST-100)..."
curl -s -X GET 'http://localhost:8080/orders?customerId=CUST-100' | json_pp 2>/dev/null || curl -s -X GET 'http://localhost:8080/orders?customerId=CUST-100'
echo ""

# 5. Ingest an INVALID order (should trigger validation error)
echo -e "\n[STEP 5] Testing validation failure by placing order with invalid amount (POST /orders)..."
curl -s -i -X POST 'http://localhost:8080/orders' \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-ERR", "amount":-50.00}'
echo ""

# 6. Trigger manual outbox publishing API
echo -e "\n[STEP 6] Triggering outbox publisher manually (POST /orders/outbox/publish)..."
curl -s -X POST 'http://localhost:8080/orders/outbox/publish' | json_pp 2>/dev/null || curl -s -X POST 'http://localhost:8080/orders/outbox/publish'
echo ""

# 7. Provide advice on Kafka consumer retry / DLT verification
echo -e "\n========================================================"
echo -e "Verification complete. To check consumer retry & DLT: "
echo -e "1. Run Kafka consumer on the DLT topic:"
echo -e "   docker exec -it kafka1 kafka-console-consumer --bootstrap-server kafka1:19092 --topic order.events.dlt --from-beginning"
echo -e "2. Push a poison event directly to Kafka's main topic:"
echo -e "   echo 'POISON:{\"eventId\":\"POISON-EVT\",\"orderId\":\"POISON-ORD\",\"customerId\":\"CUST-POISON\",\"amount\":-10.0,\"status\":\"PLACED\"}' | \\"
echo -e "   docker exec -i kafka1 kafka-console-producer --bootstrap-server kafka1:19092 --topic order.events --property parse.key=true --property key.separator=:"
echo -e "3. Watch application logs for 3 exponential backoff retries on 'order.events.retry' followed by the DLT route."
echo -e "========================================================"

#!/bin/bash
# Port: 8080 (orderServiceFunctions)

echo "--- Testing Production Order Service (EDA) ---"

# 1. Start SSE Stream in the background (using a subshell)
echo -e "\n[STEP 1] Subscribing to live order stream (GET /api/orders/stream)..."
echo "Note: The stream will stay open. We will ingest data in Step 2."
curl -s -N 'http://localhost:8080/api/orders/stream' &
STREAM_PID=$!
sleep 2

# 2. Ingest Valid Orders
echo -e "\n\n[STEP 2] Ingesting VALID orders (POST /api/orders/ingest)..."
curl -X POST 'http://localhost:8080/api/orders/ingest' \
  -H 'Content-Type: application/json' \
  -d '[
    {"orderId":"ORD-100", "customerId":"CUST-A", "item":"Graphics Card", "quantity":1},
    {"orderId":"ORD-101", "customerId":"CUST-A", "item":"Monitor", "quantity":2}
  ]'

sleep 2

# 3. Ingest INVALID Orders (Triggering DLT/Validation Logic)
echo -e "\n\n[STEP 3] Ingesting INVALID orders (Missing Item)..."
curl -X POST 'http://localhost:8080/api/orders/ingest' \
  -H 'Content-Type: application/json' \
  -d '[
    {"orderId":"ORD-ERR", "customerId":"CUST-B", "item":"", "quantity":-5}
  ]'

sleep 5
echo -e "\n\n[DONE] Cleaning up stream subscription..."
kill $STREAM_PID
echo "Check the server logs to see manual ACKs and Partition Keys in action."

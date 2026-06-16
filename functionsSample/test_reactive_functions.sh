#!/bin/bash
# Port: 8081 (functionsSample)

echo "--- Testing Reactive & Async Patterns ---"

# 1. reactiveStreamProcess (Function<Flux, Flux>)
echo -e "\n[POST /reactiveStreamProcess]"
curl -X POST 'http://localhost:8081/reactiveStreamProcess' \
  -H 'Content-Type: text/plain' \
  -d $'stream_line_1\nstream_line_2'

# 2. asyncSingleProcess (Function<Mono, Mono>)
echo -e "\n\n[POST /asyncSingleProcess]"
curl -X POST 'http://localhost:8081/asyncSingleProcess' \
  -H 'Content-Type: text/plain' \
  -d 'non-blocking-test'

# 3. reactiveHeartbeatSupplier (Supplier<Flux>)
echo -e "\n\n[GET /reactiveHeartbeatSupplier]"
echo "Note: This is a stream (SSE). Press Ctrl+C to stop after a few events."
curl -v -X GET 'http://localhost:8081/reactiveHeartbeatSupplier' --limit-rate 1k

# 4. reactiveBatchConsumer (Consumer<Flux>)
echo -e "\n\n[POST /reactiveBatchConsumer]"
curl -X POST 'http://localhost:8081/reactiveBatchConsumer' \
  -H 'Content-Type: text/plain' \
  -d $'batch_item_A\nbatch_item_B'

# 5. splitAndExpand (Function<String, Flux>)
echo -e "\n\n[POST /splitAndExpand]"
curl -X POST 'http://localhost:8081/splitAndExpand' \
  -H 'Content-Type: text/plain' \
  -d 'XYZ'

echo -e "\n\n--- Reactive Tests Complete ---"

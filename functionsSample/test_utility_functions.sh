#!/bin/bash
# Port: 8081 (functionsSample)

echo "--- Testing Utility & Context Patterns ---"

# 1. processWithHeaders (Function<Message, Message>)
echo -e "\n[POST /processWithHeaders]"
curl -X POST 'http://localhost:8081/processWithHeaders' \
  -v \
  -H 'X-Custom-Auth: session-secret-123' \
  -d 'payload with metadata'

# 2. reactiveMessageProcess (Function<Flux<Message>, Flux<Message>>)
echo -e "\n\n[POST /reactiveMessageProcess]"
curl -X POST 'http://localhost:8081/reactiveMessageProcess' \
  -v \
  -H 'X-Batch-ID: 555' \
  -d 'batch payload'

# 3. sanitizeAndMask (Programmatic Chaining)
echo -e "\n\n[POST /sanitizeAndMask]"
curl -X POST 'http://localhost:8081/sanitizeAndMask' \
  -H 'Content-Type: text/plain' \
  -d '  user_password_to_be_masked  '

# 4. Declarative Chaining (sanitizeText | auditEvent)
echo -e "\n\n[POST /sanitizeText|auditEvent]"
curl -X POST 'http://localhost:8081/sanitizeText|auditEvent' \
  -H 'Content-Type: text/plain' \
  -d '  pipeline test log  '

echo -e "\n\n--- Utility Tests Complete ---"

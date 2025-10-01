#http://localhost:8080/randomUuid

curl -X 'GET' \
  'http://localhost:8080/randomUuid' \
  -H 'accept: */*'

#http://localhost:8080/reverse
curl -X 'POST' \
  'http://localhost:8080/reverse' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '"string"'

#http://localhost:8080/reverse/fgfhghghhg
curl -X 'GET' \
  'http://localhost:8080/reverse/fgfhghghhg' \
  -H 'accept: application/json'

#http://localhost:8080/uppercase
curl -X 'POST' \
  'http://localhost:8080/uppercase' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '"string"'

#http://localhost:8080/uppercase/amit
curl -X 'GET' \
  'http://localhost:8080/uppercase/amit' \
  -H 'accept: application/json'

#http://localhost:8080/processOrder
  curl -X POST http://localhost:8080/processOrder \
       -H "Content-Type: application/json" \
       -d '{"orderId":"101", "item":"Laptop", "quantity":2}'


#Why you see those warnings
#You’re calling:
#curl http://localhost:8080/uppercase/amit
#spring-cloud-function-web does not work like a Spring MVC controller.
#It doesn’t support path variables like /uppercase/amit.
#Instead, it treats the whole path after / as a function name.
#So in your case it thinks:
#Function name = uppercase/amit ❌ (doesn’t exist)
#Logs warning:
#Failed to locate function 'uppercase/amit'
#Then it falls back and actually executes the right function (that’s why you still get a result

#With Spring Cloud Function Web:
#POST to /functionName
#Put the input in the request body.

#✅ Corrected curl Commands
#1. randomUuid (Supplier → GET, no body)
curl -X GET http://localhost:8080/randomUuid \
     -H "accept: application/json"
#👉 Returns a UUID string.

#2. reverse (Function<String,String> → POST with body)
curl -X POST http://localhost:8080/reverse \
     -H "Content-Type: text/plain" \
     -d "string"
#👉 Returns "gnirts".
#⚠️ Fix: remove GET /reverse/fgfhghghhg — path variables don’t work. Always POST with body.

#3. uppercase (Function<String,String> → POST with body)
curl -X POST http://localhost:8080/uppercase \
     -H "Content-Type: text/plain" \
     -d "amit"
#👉 Returns "AMIT".
#⚠️ Fix: remove GET /uppercase/amit — not valid for SCF.

#4. processOrder (Function<OrderRequest,OrderResponse> → JSON in, JSON out)
curl -X POST http://localhost:8080/processOrder \
     -H "Content-Type: application/json" \
     -d '{"orderId":"101", "item":"Laptop", "quantity":2}'
#👉 Returns:
#{"orderId":"101","status":"CONFIRMED"}


#About spring.cloud.function.definition: uppercase|reverse
#That property tells Spring Cloud Function:
#“When I don’t specify a function, use this function pipeline.”
#So:
curl -X POST http://localhost:8080/ \
     -H "Content-Type: text/plain" \
     -d "amit"
#👉 Output:
#TIMA
#(uppercase → reverse)
#But if you explicitly call /uppercase/amit, it confuses the router and you get warnings.
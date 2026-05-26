# Yuno Payment Orchestration Platform — Complete Technical Documentation

---

## 1. Functional Requirements

### FR-001: Create Payment
- The API accepts payment requests with amount, currency, payment method, and customer information.
- CARD payments are primarily routed through Provider A.
- UPI payments are primarily routed through Provider B.
- After successful creation, the system returns the payment ID along with the current payment status.
- Payment information is persisted before making any external provider call to ensure reliability and traceability.
- Every payment state transition is recorded as part of the audit trail for monitoring and debugging purposes.

### FR-002: Fetch Payment
- Payments can be fetched using the unique payment ID.
- The response includes complete payment details such as current status, provider used, retry count, and timestamps.
- The API also returns the full audit history of payment events in chronological order.
- If the payment ID does not exist, the API returns an HTTP 404 response.

### FR-003: Payment Routing
- CARD payments are primarily routed through Provider A.
- UPI payments are primarily routed through Provider B.
- If Provider A fails for a CARD payment, the system attempts failover to Provider B.
- If Provider B fails for a UPI payment, the system attempts failover to Provider A.

### FR-004: Retry Mechanism
- Transient provider failures such as timeouts or network issues are retried up to 3 times.
- Non-retryable failures such as card decline or insufficient funds are not retried.
- Retry attempts use exponential backoff with a base delay of 100ms.
- The total retry count is tracked and stored with the payment record.

### FR-005: Idempotency
- Payment creation requests require an `X-Idempotency-Key` header.
- Duplicate requests with the same idempotency key return the previously generated response.
- The same payment request is never processed more than once.
- Idempotency responses are cached for 24 hours.
- Concurrent duplicate requests are handled safely using Redis-based distributed locking.

### FR-006: Payment Status Tracking
- The application maintains a payment state machine to track the payment lifecycle.
- Invalid payment state transitions are rejected.
- Every state transition is recorded as a payment event for auditing and debugging.
- Payment outcome events are designed to support asynchronous event publishing for downstream consumers.

### FR-007: Provider Health
- The application exposes provider health information including circuit breaker status and failure metrics.
- Circuit breakers open automatically when the failure threshold exceeds 50% within a 10-call sliding window.
- Circuit breakers automatically transition back to recovery mode after 30 seconds.
---

## 2. Non-Functional Requirements

### NFR-001: Performance
| Metric | Target |
|---|---|
| API response time (p95) | < 500ms |
| Idempotency check (cache hit) | < 10ms |
| DB write (payment creation) | < 50ms |
| Provider call (mock) | 50–300ms simulated |
| Kafka event publish | < 20ms (async, non-blocking) |

### NFR-002: Scalability
- Each microservice SHALL be horizontally scalable independently
- The system SHALL support stateless application instances (session state in Redis/DB only)
- Database connection pooling SHALL be configured via HikariCP (max 10 connections per instance)
- Kafka consumers SHALL support concurrent processing (concurrency: 3 partitions)

### NFR-003: Availability
- The system SHALL degrade gracefully if the idempotency service (Redis) is unavailable
- The system SHALL degrade gracefully if Kafka is unavailable (payment still processes, event publish fails silently)
- Circuit breaker SHALL prevent cascading failures when a provider is degraded

### NFR-004: Security
- All API endpoints SHALL require a valid API key via X-API-Key header
- Rate limiting SHALL be enforced at 100 requests per minute per client
- Sensitive data (card numbers, CVV) SHALL never appear in logs
- Container images SHALL run as non-root users

### NFR-005: Observability
- Every request SHALL carry a Correlation ID (generated if not provided)
- All log lines SHALL include the Correlation ID via MDC for distributed tracing
- All services SHALL expose Prometheus metrics via /actuator/prometheus
- All services SHALL expose health checks via /actuator/health

### NFR-006: Data Integrity
- Payment table SHALL enforce unique constraint on idempotency_key
- Database schema changes SHALL be managed exclusively via Flyway versioned migrations
- No `ddl-auto=create` or `ddl-auto=update` in any environment
- All monetary values SHALL be stored as NUMERIC(19,4) to avoid floating-point errors

### NFR-007: Maintainability
- Code SHALL follow SOLID principles
- Each microservice SHALL own its own database schema
- Shared contracts SHALL be in common-lib only
- Architecture decisions SHALL be documented in ADR format

---

## 3. High-Level System Overview

Yuno is a payment orchestration layer. In the real world, merchants integrate once with Yuno's API and Yuno handles routing to 400+ payment providers. This project implements a simplified version of that core orchestration logic.

```
MERCHANT APP
     │
     ▼
[PAYMENT GATEWAY]  ← Single entry point for all merchants
     │              Handles: Auth, Rate Limiting, Request Validation
     ▼
[PAYMENT PROCESSOR] ← Core business logic
     │               Handles: Orchestration, State Machine, DB Persistence
     │               Uses: Flyway migrations, JPA, Kafka
     ├──────────────────────────────────────────────┐
     ▼                                              ▼
[IDEMPOTENCY SERVICE]                    [PROVIDER SERVICE]
  Redis-backed                            Handles: Routing, Retry,
  Distributed Lock                        Circuit Breaker, Failover
  24-hour TTL cache                       Provider A (CARD)
                                          Provider B (UPI)
     │
     ▼ (Kafka Event)
[NOTIFICATION SERVICE]
  Webhook delivery to merchant
  Dead Letter Queue on failure
```

### Payment Lifecycle

```
1. Merchant sends POST /api/v1/payments with X-Idempotency-Key
2. Gateway validates API key, rate limit, request body
3. Gateway forwards to Processor
4. Processor checks idempotency (Redis → DB double-check)
5. Processor saves payment as INITIATED
6. Processor transitions: INITIATED → PROCESSING → ROUTING → PROVIDER_CALLED
7. Provider Service routes to correct provider, applies retry + circuit breaker
8. On success: Payment → SUCCESS, event saved, Kafka event published
9. On failure after retries: Failover to alternate provider
10. On total failure: Payment → PERMANENTLY_FAILED
11. Response returned to merchant with paymentId + status
12. Notification Service delivers webhook to merchant asynchronously
```

---

## 4. Integration Points

### INT-001: Gateway → Processor
| Property | Value |
|---|---|
| Protocol | HTTP/REST |
| Endpoint | POST /api/v1/internal/payments/process |
| Direction | Synchronous |
| Timeout | 30 seconds |
| Error handling | 503 returned to client if processor unavailable |

### INT-002: Processor → Provider Service
| Property | Value |
|---|---|
| Protocol | HTTP/REST |
| Endpoint | POST /api/v1/internal/providers/process |
| Direction | Synchronous |
| Timeout | 30 seconds |
| Error handling | Graceful failure — payment marked FAILED |

### INT-003: Processor → Idempotency Service
| Property | Value |
|---|---|
| Protocol | HTTP/REST |
| Endpoints | POST /check, POST /store |
| Direction | Synchronous |
| Error handling | Graceful degradation — continues without idempotency on failure |

### INT-004: Processor → Kafka
| Property | Value |
|---|---|
| Protocol | Apache Kafka |
| Topic | payment-events |
| Direction | Async, fire-and-forget |
| Message key | paymentId (for partition ordering) |
| Error handling | Logs error, does not affect payment outcome |

### INT-005: Notification Service → Kafka
| Property | Value |
|---|---|
| Protocol | Apache Kafka Consumer |
| Topic | payment-events |
| Consumer Group | notification-service-group |
| Acknowledgment | Manual (MANUAL ack mode) |
| Concurrency | 3 consumer threads |

### INT-006: Idempotency Service → Redis
| Property | Value |
|---|---|
| Protocol | Redis (Lettuce client) |
| Operations | GET, SET NX PX (atomic lock), SET with TTL, DEL |
| TTL | 24 hours for idempotency keys, 5 seconds for locks |
| Error handling | RuntimeException propagated, caller handles gracefully |

---

## 5. Input and Output Parameters

### API: POST /api/v1/payments

#### Request Headers
| Header | Required | Description |
|---|---|---|
| X-API-Key | Yes | API key for authentication |
| X-Idempotency-Key | Yes | Unique key to prevent duplicate payments (UUID recommended) |
| X-Correlation-Id | No | Request tracing ID (auto-generated if not provided) |
| Content-Type | Yes | Must be application/json |

#### Request Body
| Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| amount | BigDecimal | Yes | > 0, max 15 digits, 4 decimal places | Payment amount |
| currency | String | Yes | Exactly 3 characters (ISO 4217) | e.g. INR, USD, EUR |
| paymentMethod | Enum | Yes | CARD or UPI | Determines routing |
| customerId | String | Yes | Not blank | Merchant's customer identifier |
| metadata | Map<String,String> | No | Optional key-value pairs | e.g. orderId, productId |

#### Response Body (201 Created)
| Field | Type | Description |
|---|---|---|
| success | boolean | Always true on 2xx |
| data.paymentId | UUID String | Unique payment identifier |
| data.status | Enum | Current payment status |
| data.amount | BigDecimal | Payment amount |
| data.currency | String | ISO currency code |
| data.paymentMethod | Enum | CARD or UPI |
| data.provider | Enum | PROVIDER_A or PROVIDER_B |
| data.idempotencyHit | boolean | true if this was a duplicate request |
| data.correlationId | UUID String | Request correlation ID |
| data.createdAt | ISO-8601 Timestamp | Payment creation time |

#### Error Responses
| HTTP Status | Error Code | Scenario |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid request body |
| 401 | UNAUTHORIZED | Missing or invalid API key |
| 409 | IDEMPOTENCY_CONFLICT | Concurrent request with same key |
| 429 | RATE_LIMIT_EXCEEDED | Too many requests |
| 503 | SERVICE_UNAVAILABLE | Downstream service down |

---

### API: GET /api/v1/payments/{paymentId}

#### Path Parameters
| Parameter | Type | Description |
|---|---|---|
| paymentId | UUID String | The payment ID from create response |

#### Response Body (200 OK)
| Field | Type | Description |
|---|---|---|
| data.paymentId | UUID String | Unique payment identifier |
| data.amount | BigDecimal | Payment amount |
| data.currency | String | ISO currency code |
| data.paymentMethod | Enum | CARD or UPI |
| data.status | Enum | Current payment status |
| data.provider | Enum | Provider that processed the payment |
| data.failureReason | String | Failure reason if status is FAILED |
| data.retryCount | int | Number of retry attempts made |
| data.correlationId | String | Original request correlation ID |
| data.events | List | Audit trail of all state transitions |
| data.createdAt | Timestamp | Payment creation time |
| data.updatedAt | Timestamp | Last update time |

---

## 6. Test Case Documentation

### 6.1 Sanity Test Cases
*Sanity tests verify the core happy path works end to end.*

| TC-ID | Test Case | Input | Expected Output | Priority |
|---|---|---|---|---|
| SAN-001 | Create CARD payment successfully | Valid CARD request + valid API key | 201, status=SUCCESS, provider=PROVIDER_A | P0 |
| SAN-002 | Create UPI payment successfully | Valid UPI request + valid API key | 201, status=SUCCESS, provider=PROVIDER_B | P0 |
| SAN-003 | Fetch existing payment | Valid paymentId | 200, full payment details | P0 |
| SAN-004 | All services health check | GET /actuator/health on each service | 200, status=UP on all | P0 |
| SAN-005 | Docker Compose startup | docker-compose up | All 5 services healthy within 60s | P0 |

---

### 6.2 Regression Test Cases
*Regression tests verify existing functionality is not broken by changes.*

| TC-ID | Test Case | Input | Expected Output | Priority |
|---|---|---|---|---|
| REG-001 | Idempotency — same key returns same response | Same X-Idempotency-Key sent twice | Both return same paymentId, second has idempotencyHit=true | P0 |
| REG-002 | State machine rejects invalid transitions | Force SUCCESS → PROCESSING | InvalidPaymentStateException thrown | P0 |
| REG-003 | Payment events audit trail is ordered | Fetch payment with events | Events returned in ascending createdAt order | P1 |
| REG-004 | CARD always routes to Provider A first | CARD payment request | providerUsed=PROVIDER_A if success | P0 |
| REG-005 | UPI always routes to Provider B first | UPI payment request | providerUsed=PROVIDER_B if success | P0 |
| REG-006 | Retry count stored correctly | Payment that retries 2 times | retryCount=2 in DB | P1 |
| REG-007 | Flyway migration runs on startup | Fresh DB + service start | All 5 migration versions applied | P0 |
| REG-008 | Correlation ID propagated across services | Request with X-Correlation-Id | Same ID appears in all service logs | P1 |

---

### 6.3 Integration Test Cases
*Integration tests verify that services work correctly together.*

| TC-ID | Test Case | Services Involved | Expected Output |
|---|---|---|---|
| INT-001 | Full payment flow end to end | Gateway → Processor → Idempotency → Provider | Payment created, stored in DB, event in Kafka |
| INT-002 | Idempotency service down — payment still processes | Gateway → Processor → (Idempotency DOWN) → Provider | Payment processes successfully, idempotency skipped gracefully |
| INT-003 | Provider service down — payment fails gracefully | Gateway → Processor → (Provider DOWN) | Payment marked FAILED, 503 not propagated as unhandled error |
| INT-004 | Kafka down — payment still succeeds | Gateway → Processor → Provider → (Kafka DOWN) | Payment SUCCESS, Kafka publish failure logged but not fatal |
| INT-005 | Concurrent requests same idempotency key | 2 simultaneous POST requests, same key | Exactly one payment created, one returns cached response |
| INT-006 | Failover triggered on Provider A failure | CARD payment when Provider A circuit OPEN | Payment routes to Provider B, failoverUsed=true |
| INT-007 | Notification service consumes Kafka event | Payment completes → Kafka → Notification | Webhook delivery logged in notification service |
| INT-008 | Analytics endpoint reflects processed payments | POST 10 payments, GET /analytics | totalPayments=10, correct byMethod breakdown |

---

### 6.4 Negative Test Cases
*Negative tests verify the system handles invalid input and failure scenarios correctly.*

| TC-ID | Test Case | Input | Expected Output |
|---|---|---|---|
| NEG-001 | Missing API key | Request without X-API-Key | 401 UNAUTHORIZED |
| NEG-002 | Invalid API key | X-API-Key: invalid-key | 401 UNAUTHORIZED |
| NEG-003 | Missing idempotency key | Request without X-Idempotency-Key | 400 VALIDATION_ERROR |
| NEG-004 | Amount = 0 | amount: 0 | 400 VALIDATION_ERROR |
| NEG-005 | Negative amount | amount: -100 | 400 VALIDATION_ERROR |
| NEG-006 | Invalid currency | currency: "ABCD" | 400 VALIDATION_ERROR |
| NEG-007 | Invalid payment method | paymentMethod: "CRYPTO" | 400 VALIDATION_ERROR |
| NEG-008 | Missing customerId | customerId: "" | 400 VALIDATION_ERROR |
| NEG-009 | Fetch non-existent payment | GET /payments/non-existent-uuid | 404 PAYMENT_NOT_FOUND |
| NEG-010 | Fetch invalid UUID format | GET /payments/not-a-uuid | 404 PAYMENT_NOT_FOUND |
| NEG-011 | Rate limit exceeded | 101 requests in 60 seconds | 429 RATE_LIMIT_EXCEEDED with Retry-After header |
| NEG-012 | Amount with too many decimals | amount: 100.12345 | 400 VALIDATION_ERROR |
| NEG-013 | Currency with 2 chars | currency: "IN" | 400 VALIDATION_ERROR |
| NEG-014 | Null payment method | paymentMethod: null | 400 VALIDATION_ERROR |
| NEG-015 | Concurrent duplicate idempotency key | 2 simultaneous requests, same key | One succeeds, one gets 409 or cached response |
| NEG-016 | Payment with missing amount | No amount field | 400 VALIDATION_ERROR |
| NEG-017 | Provider returns non-retryable error | CARD_DECLINED failure | Payment FAILED immediately, no retry |
| NEG-018 | All providers fail after retries | Both providers fail | Payment PERMANENTLY_FAILED |

---

## 7. Performance Considerations

### 7.1 Database Performance

**Indexes Applied (V4, V5 migrations):**
```sql
-- Critical for payment status queries
CREATE INDEX CONCURRENTLY idx_payments_status ON payments(status);

-- Critical for routing queries
CREATE INDEX CONCURRENTLY idx_payments_payment_method ON payments(payment_method);

-- Critical for idempotency lookup (also covered by UNIQUE constraint)
-- idx already implicit from UNIQUE on idempotency_key

-- For audit trail queries
CREATE INDEX CONCURRENTLY idx_payment_events_payment_id ON payment_events(payment_id);

-- For time-range queries
CREATE INDEX CONCURRENTLY idx_payments_created_at ON payments(created_at DESC);
```

**Why CONCURRENTLY?**
Regular `CREATE INDEX` locks the table. `CONCURRENTLY` builds the index without locking, safe for production with live traffic.

**HikariCP Connection Pool Settings:**
```yaml
maximum-pool-size: 10      # Max concurrent DB connections per instance
minimum-idle: 2             # Always-ready connections
connection-timeout: 30000   # 30s before giving up getting a connection
idle-timeout: 600000        # Remove idle connections after 10 minutes
max-lifetime: 1800000       # Recycle connections after 30 minutes
```

### 7.2 Redis Performance

- Fast-path idempotency check runs WITHOUT acquiring a lock (read-only, no contention)
- Lock is only acquired when cache misses (new payments only)
- Lock TTL = 5 seconds (prevents deadlock if service crashes mid-processing)
- Idempotency TTL = 24 hours (covers all reasonable retry windows)

### 7.3 Kafka Performance

**Producer Settings:**
```
acks=all         → All replicas must confirm (durability over speed)
retries=3        → Auto-retry on publish failure
idempotence=true → Exactly-once producer semantics
```

**Consumer Settings:**
```
enable-auto-commit=false    → Manual acknowledgment (no message loss)
auto-offset-reset=earliest  → Process all messages from beginning if new group
concurrency=3               → 3 consumer threads (matches partition count)
```

### 7.4 Resilience4j Settings Explained

```
Sliding window: 10 calls
Failure threshold: 50%
→ Circuit opens after 5 failures in last 10 calls

Wait in open state: 30 seconds
→ All calls fail fast for 30s (protects provider from overload)

Half-open test calls: 3
→ 3 test calls before deciding to close or stay open

Retry max attempts: 3
Retry wait: 100ms
→ Total max wait on retries: ~300ms before failover
```

### 7.5 Metrics Exposed (Prometheus)

Access at: `GET /actuator/prometheus`

Key metrics to monitor:
```
# Payment processing
http_server_requests_seconds{uri="/api/v1/payments"}
http_server_requests_seconds{uri="/api/v1/payments/{paymentId}"}

# JVM health
jvm_memory_used_bytes
jvm_gc_pause_seconds

# DB pool
hikaricp_connections_active
hikaricp_connections_pending

# Circuit breaker
resilience4j_circuitbreaker_state
resilience4j_circuitbreaker_failure_rate
```

---

## 8. Vibe Coding — AI Prompts Used During Development

Architecture and Design Approach

AI assistance was used during the early design phase for:
- brainstorming payment orchestration patterns
- evaluating modular monolith vs microservices
- retry and failover strategy ideas
- Redis idempotency approaches
- observability and resilience patterns


## Audit & Event Tracking
- Payment lifecycle events are stored for traceability and debugging.
- Each payment transition is persisted as an audit event.
- Event tracking helps support observability and operational troubleshooting.

*End of Documentation*

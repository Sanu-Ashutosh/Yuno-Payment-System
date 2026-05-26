# ADR-002: Two-Layer Idempotency Strategy

## Status
Accepted

## Context
Payment systems require strict idempotency to prevent duplicate charges. A single-layer approach (Redis only) risks data inconsistency on Redis eviction. A DB-only approach is slow.

## Decision
Two-layer idempotency:
1. **Layer 1 (Redis)**: Fast lookup (< 5ms) for 24-hour TTL
2. **Layer 2 (DB)**: Permanent idempotency_key unique constraint

**Flow:**
1. Fast path: Check Redis (no lock) → if hit, return cached
2. Acquire distributed Redis lock (SET NX PX)
3. Double-check Redis after lock (prevents race conditions)
4. Check DB for idempotency_key
5. Process payment
6. Store in Redis + DB

## Consequences
**Positive:**
- Sub-millisecond response for duplicate requests
- Race condition protection via distributed lock
- Permanent record in DB for auditing

**Negative:**
- Two network calls for new payments
- Redis availability dependency (graceful degradation implemented)

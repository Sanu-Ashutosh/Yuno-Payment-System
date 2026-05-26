# ADR-003: Retry and Failover Strategy

## Status
Accepted

## Context
Payment providers are unreliable. A missing retry strategy means lost revenue. A missing failover strategy means complete outages when one provider is down.

## Decision
Three-tier resilience using Resilience4j:

**Tier 1 - Retry (same provider):**
- Max 3 attempts with 100ms exponential backoff
- Only for retryable errors (TIMEOUT, NETWORK_ERROR)
- Non-retryable errors (CARD_DECLINED) fail immediately

**Tier 2 - Failover (different provider):**
- CARD (Provider A fails) → retry on Provider B
- UPI (Provider B fails) → retry on Provider A

**Tier 3 - Circuit Breaker:**
- Opens after 50% failure rate in 10-call sliding window
- Stays open for 30 seconds
- Half-open state: 3 test calls before fully closing

## Payment State Machine
INITIATED → PROCESSING → ROUTING → PROVIDER_CALLED → SUCCESS/FAILED/RETRYING → FAILOVER_TRIGGERED → PERMANENTLY_FAILED

## Consequences
Maximises payment success rate while protecting providers from overload.

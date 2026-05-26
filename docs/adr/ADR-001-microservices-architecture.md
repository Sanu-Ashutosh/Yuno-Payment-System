# ADR-001: Microservices Architecture with Multi-Module Maven

## Status
Accepted

## Context
The payment orchestration platform requires independent scalability of components (routing engine, provider connectors, idempotency store) and independent deployability for teams working on different concerns.

## Decision
We use a multi-module Maven project with 5 independent Spring Boot microservices:
- **payment-gateway-service**: API gateway, auth, rate limiting
- **payment-processor-service**: Core orchestration logic + DB
- **provider-service**: Payment provider connectors with circuit breakers
- **idempotency-service**: Redis-backed idempotency
- **notification-service**: Async webhook delivery via Kafka

## Consequences
**Positive:**
- Independent deployment and scaling per service
- Fault isolation (provider failures don't affect gateway)
- Team independence (different teams own different services)
- Technology flexibility per service

**Negative:**
- Higher operational complexity
- Network latency between services
- Distributed tracing required for debugging

## Alternatives Considered
- Monolith: Easier to develop but limits scalability
- Hexagonal Architecture: More complex for this scope

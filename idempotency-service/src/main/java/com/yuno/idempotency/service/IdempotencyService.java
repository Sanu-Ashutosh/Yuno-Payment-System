package com.yuno.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.common.constants.AppConstants;
import com.yuno.common.dto.CreatePaymentResponse;
import com.yuno.common.dto.IdempotencyCheckResponse;
import com.yuno.common.exception.IdempotencyConflictException;
import com.yuno.idempotency.lock.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final RedisDistributedLock distributedLock;
    private final ObjectMapper objectMapper;

    public IdempotencyCheckResponse check(String idempotencyKey) {
        // Fast path: check cache without lock
        String cached = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
        if (cached != null) {
            log.info("Idempotency cache HIT (fast path). key={}", idempotencyKey);
            return IdempotencyCheckResponse.hit(deserialize(cached));
        }

        // Slow path: acquire lock and double-check
        boolean locked = distributedLock.tryLock(idempotencyKey);
        if (!locked) {
            log.warn("Concurrent request with same idempotency key. key={}", idempotencyKey);
            throw new IdempotencyConflictException(
                    "Concurrent request detected for key: " + idempotencyKey);
        }

        try {
            // Double-check after acquiring lock
            cached = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
            if (cached != null) {
                log.info("Idempotency cache HIT (after lock). key={}", idempotencyKey);
                return IdempotencyCheckResponse.hit(deserialize(cached));
            }
            log.debug("Idempotency MISS. key={}", idempotencyKey);
            return IdempotencyCheckResponse.miss();
        } finally {
            distributedLock.unlock(idempotencyKey);
        }
    }

    public void store(String idempotencyKey, CreatePaymentResponse response) {
        try {
            String serialized = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                    IDEMPOTENCY_PREFIX + idempotencyKey,
                    serialized,
                    Duration.ofHours(AppConstants.IDEMPOTENCY_TTL_HOURS));
            log.info("Idempotency result stored. key={}, ttl={}h",
                    idempotencyKey, AppConstants.IDEMPOTENCY_TTL_HOURS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response for idempotency store. key={}", idempotencyKey, e);
        }
    }

    private CreatePaymentResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, CreatePaymentResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize idempotency cached response", e);
            return null;
        }
    }
}

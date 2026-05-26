package com.yuno.idempotency.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private static final String LOCK_PREFIX = "lock:idempotency:";
    private static final long DEFAULT_TTL_MS = 5000L;

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(String key) {
        return tryLock(key, DEFAULT_TTL_MS);
    }

    public boolean tryLock(String key, long ttlMs) {
        String lockKey = LOCK_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofMillis(ttlMs));
        boolean result = Boolean.TRUE.equals(acquired);
        if (result) {
            log.debug("Distributed lock acquired. key={}", key);
        } else {
            log.debug("Failed to acquire distributed lock. key={}", key);
        }
        return result;
    }

    public void unlock(String key) {
        redisTemplate.delete(LOCK_PREFIX + key);
        log.debug("Distributed lock released. key={}", key);
    }
}

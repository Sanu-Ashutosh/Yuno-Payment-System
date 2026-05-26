package com.yuno.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuno.common.dto.CreatePaymentResponse;
import com.yuno.common.dto.IdempotencyCheckResponse;
import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.enums.ProviderType;
import com.yuno.idempotency.lock.RedisDistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisDistributedLock distributedLock;
    @Mock private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate, distributedLock, objectMapper);
    }

    @Test
    void check_fastPath_cachHit_returnsExisting() throws Exception {
        CreatePaymentResponse cached = buildResponse();
        String json = objectMapper.writeValueAsString(cached);
        when(valueOperations.get(anyString())).thenReturn(json);

        IdempotencyCheckResponse result = idempotencyService.check("test-key");

        assertThat(result.isExists()).isTrue();
        assertThat(result.getCachedResponse()).isNotNull();
        verify(distributedLock, never()).tryLock(any());
    }

    @Test
    void check_cacheMiss_acquiresLock_returnsMiss() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(distributedLock.tryLock(anyString())).thenReturn(true);

        IdempotencyCheckResponse result = idempotencyService.check("new-key");

        assertThat(result.isExists()).isFalse();
        verify(distributedLock).tryLock("new-key");
        verify(distributedLock).unlock("new-key");
    }

    @Test
    void store_savesToRedisWithTTL() throws Exception {
        CreatePaymentResponse response = buildResponse();
        doNothing().when(valueOperations).set(anyString(), anyString(), any());

        idempotencyService.store("test-key", response);

        verify(valueOperations).set(contains("test-key"), anyString(), any());
    }

    private CreatePaymentResponse buildResponse() {
        return CreatePaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString())
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .provider(ProviderType.PROVIDER_A)
                .idempotencyHit(false)
                .createdAt(Instant.now())
                .build();
    }
}

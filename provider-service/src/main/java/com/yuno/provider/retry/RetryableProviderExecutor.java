package com.yuno.provider.retry;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import com.yuno.common.enums.ProviderType;
import com.yuno.common.exception.ProviderException;
import com.yuno.provider.connector.PaymentProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RetryableProviderExecutor {

    @Retry(name = "providerRetry", fallbackMethod = "retryFallback")
    @CircuitBreaker(name = "providerCircuitBreaker", fallbackMethod = "circuitBreakerFallback")
    public ProviderProcessResponse executeWithRetry(PaymentProvider provider,
                                                     ProviderProcessRequest request,
                                                     AtomicInteger retryCounter) {
        try {
            ProviderProcessResponse response = provider.process(request);
            return ProviderProcessResponse.builder()
                    .success(true)
                    .providerTransactionId(response.getProviderTransactionId())
                    .providerUsed(response.getProviderUsed())
                    .failoverUsed(response.isFailoverUsed())
                    .retryCount(retryCounter.get())
                    .processingTimeMs(response.getProcessingTimeMs())
                    .build();
        } catch (ProviderException e) {
            if (e.isRetryable()) {
                retryCounter.incrementAndGet();
                log.warn("Retryable provider failure. provider={}, attempt={}, reason={}",
                        provider.getProviderType(), retryCounter.get(), e.getMessage());
                throw e;
            }
            log.warn("Non-retryable provider failure. provider={}, reason={}",
                    provider.getProviderType(), e.getMessage());
            return ProviderProcessResponse.failure(e.getMessage(), provider.getProviderType(), retryCounter.get());
        }
    }

    public ProviderProcessResponse retryFallback(PaymentProvider provider,
                                                   ProviderProcessRequest request,
                                                   AtomicInteger retryCounter,
                                                   Exception ex) {
        log.error("All retries exhausted for provider={}. paymentId={}",
                provider.getProviderType(), request.getPaymentId());
        return ProviderProcessResponse.failure(
                "Provider failed after max retries: " + ex.getMessage(),
                provider.getProviderType(), retryCounter.get());
    }

    public ProviderProcessResponse circuitBreakerFallback(PaymentProvider provider,
                                                            ProviderProcessRequest request,
                                                            AtomicInteger retryCounter,
                                                            Exception ex) {
        log.error("Circuit breaker OPEN for provider={}. paymentId={}",
                provider.getProviderType(), request.getPaymentId());
        return ProviderProcessResponse.failure(
                "Provider circuit breaker is OPEN: " + provider.getProviderType(),
                provider.getProviderType(), retryCounter.get());
    }
}

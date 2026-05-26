package com.yuno.provider.connector;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import com.yuno.common.enums.ProviderType;
import com.yuno.common.exception.ProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ProviderBConnector implements PaymentProvider {

    @Value("${providers.provider-b.success-rate:0.80}")
    private double successRate;

    @Value("${providers.provider-b.min-latency-ms:50}")
    private int minLatencyMs;

    @Value("${providers.provider-b.max-latency-ms:200}")
    private int maxLatencyMs;

    private final Random random = new Random();
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);

    @Override
    public ProviderProcessResponse process(ProviderProcessRequest request) {
        log.info("ProviderB processing payment. paymentId={}, amount={} {}",
                request.getPaymentId(), request.getAmount(), request.getCurrency());

        simulateNetworkLatency();
        totalCalls.incrementAndGet();

        if (random.nextDouble() < successRate) {
            successCalls.incrementAndGet();
            String txId = "PB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("ProviderB SUCCESS. paymentId={}, txId={}", request.getPaymentId(), txId);
            return ProviderProcessResponse.success(txId, ProviderType.PROVIDER_B, false, 0, 0L);
        } else {
            String reason = pickFailureReason();
            log.warn("ProviderB FAILED. paymentId={}, reason={}", request.getPaymentId(), reason);
            throw new ProviderException("PROVIDER_B", reason, isRetryable(reason));
        }
    }

    @Override
    public ProviderType getProviderType() { return ProviderType.PROVIDER_B; }

    @Override
    public boolean isHealthy() { return true; }

    @Override
    public double getSuccessRate() {
        long total = totalCalls.get();
        return total == 0 ? 1.0 : (double) successCalls.get() / total;
    }

    private void simulateNetworkLatency() {
        try {
            int latency = minLatencyMs + random.nextInt(maxLatencyMs - minLatencyMs);
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String pickFailureReason() {
        String[] reasons = { "VPA_NOT_FOUND", "UPI_LIMIT_EXCEEDED", "PROVIDER_TIMEOUT", "BANK_UNAVAILABLE" };
        return reasons[random.nextInt(reasons.length)];
    }

    private boolean isRetryable(String reason) {
        return reason.equals("PROVIDER_TIMEOUT") || reason.equals("BANK_UNAVAILABLE");
    }
}

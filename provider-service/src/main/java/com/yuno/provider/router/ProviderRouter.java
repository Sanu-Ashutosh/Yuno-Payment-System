package com.yuno.provider.router;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import com.yuno.common.enums.PaymentMethod;
import com.yuno.provider.connector.PaymentProvider;
import com.yuno.provider.connector.ProviderAConnector;
import com.yuno.provider.connector.ProviderBConnector;
import com.yuno.provider.retry.RetryableProviderExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderRouter {

    private final ProviderAConnector providerA;
    private final ProviderBConnector providerB;
    private final RetryableProviderExecutor retryExecutor;

    public ProviderProcessResponse route(ProviderProcessRequest request) {
        PaymentProvider primary = selectPrimary(request.getPaymentMethod());
        PaymentProvider fallback = selectFallback(request.getPaymentMethod());

        log.info("Routing payment. paymentId={}, method={}, primary={}, fallback={}",
                request.getPaymentId(), request.getPaymentMethod(),
                primary.getProviderType(), fallback.getProviderType());

        AtomicInteger retryCounter = new AtomicInteger(0);

        // Attempt primary provider
        ProviderProcessResponse primaryResult =
                retryExecutor.executeWithRetry(primary, request, retryCounter);

        if (primaryResult.isSuccess()) {
            return primaryResult;
        }

        // Failover to secondary provider
        log.warn("Primary provider failed. Triggering failover. paymentId={}, primary={}, failover={}",
                request.getPaymentId(), primary.getProviderType(), fallback.getProviderType());

        AtomicInteger failoverCounter = new AtomicInteger(0);
        ProviderProcessResponse failoverResult =
                retryExecutor.executeWithRetry(fallback, request, failoverCounter);

        return ProviderProcessResponse.builder()
                .success(failoverResult.isSuccess())
                .providerTransactionId(failoverResult.getProviderTransactionId())
                .providerUsed(failoverResult.getProviderUsed())
                .failoverUsed(true)
                .retryCount(retryCounter.get() + failoverCounter.get())
                .failureReason(failoverResult.getFailureReason())
                .processingTimeMs(failoverResult.getProcessingTimeMs())
                .build();
    }

    private PaymentProvider selectPrimary(PaymentMethod method) {
        return method == PaymentMethod.CARD ? providerA : providerB;
    }

    private PaymentProvider selectFallback(PaymentMethod method) {
        return method == PaymentMethod.CARD ? providerB : providerA;
    }
}

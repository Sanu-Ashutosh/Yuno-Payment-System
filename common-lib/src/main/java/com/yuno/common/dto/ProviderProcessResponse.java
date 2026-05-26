package com.yuno.common.dto;

import com.yuno.common.enums.ProviderType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class ProviderProcessResponse {
    private final boolean success;
    private final String providerTransactionId;
    private final ProviderType providerUsed;
    private final boolean failoverUsed;
    private final int retryCount;
    private final String failureReason;
    private final long processingTimeMs;

    public static ProviderProcessResponse success(String txId, ProviderType provider, boolean failover, int retries, long ms) {
        return ProviderProcessResponse.builder()
                .success(true).providerTransactionId(txId)
                .providerUsed(provider).failoverUsed(failover)
                .retryCount(retries).processingTimeMs(ms).build();
    }

    public static ProviderProcessResponse failure(String reason, ProviderType lastProvider, int retries) {
        return ProviderProcessResponse.builder()
                .success(false).failureReason(reason)
                .providerUsed(lastProvider).retryCount(retries).build();
    }
}

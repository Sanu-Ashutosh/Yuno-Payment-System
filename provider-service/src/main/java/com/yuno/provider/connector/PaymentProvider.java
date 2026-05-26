package com.yuno.provider.connector;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import com.yuno.common.enums.ProviderType;

public interface PaymentProvider {
    ProviderProcessResponse process(ProviderProcessRequest request);
    ProviderType getProviderType();
    boolean isHealthy();
    double getSuccessRate();
}

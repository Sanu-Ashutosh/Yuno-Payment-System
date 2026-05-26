package com.yuno.common.dto;

import com.yuno.common.enums.PaymentMethod;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.math.BigDecimal;

@Getter
@Builder
@Jacksonized
public class ProviderProcessRequest {
    private final String paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentMethod paymentMethod;
    private final String correlationId;
}

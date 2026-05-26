package com.yuno.common.dto;

import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.enums.ProviderType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Jacksonized
public class CreatePaymentResponse {
    private final String paymentId;
    private final PaymentStatus status;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentMethod paymentMethod;
    private final ProviderType provider;
    private final String correlationId;
    private final boolean idempotencyHit;
    private final Instant createdAt;
}

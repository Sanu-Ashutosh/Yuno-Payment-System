package com.yuno.common.dto;

import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.enums.ProviderType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Jacksonized
public class PaymentDetailsResponse {
    private final String paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus status;
    private final ProviderType provider;
    private final String failureReason;
    private final int retryCount;
    private final String correlationId;
    private final List<PaymentEventDto> events;
    private final Instant createdAt;
    private final Instant updatedAt;
}

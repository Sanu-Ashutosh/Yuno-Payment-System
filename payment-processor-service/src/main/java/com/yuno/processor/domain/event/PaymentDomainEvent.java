package com.yuno.processor.domain.event;

import com.yuno.common.enums.EventType;
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
public class PaymentDomainEvent {
    private final String eventId;
    private final String paymentId;
    private final EventType eventType;
    private final PaymentStatus currentStatus;
    private final PaymentMethod paymentMethod;
    private final ProviderType provider;
    private final BigDecimal amount;
    private final String currency;
    private final String customerId;
    private final String correlationId;
    private final String failureReason;
    private final Instant occurredAt;
}

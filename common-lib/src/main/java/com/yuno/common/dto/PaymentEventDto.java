package com.yuno.common.dto;

import com.yuno.common.enums.EventType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.time.Instant;

@Getter
@Builder
@Jacksonized
public class PaymentEventDto {
    private final String eventId;
    private final EventType eventType;
    private final String payload;
    private final Instant createdAt;
}

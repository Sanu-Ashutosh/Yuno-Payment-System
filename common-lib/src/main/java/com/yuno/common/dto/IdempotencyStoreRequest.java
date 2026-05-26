package com.yuno.common.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class IdempotencyStoreRequest {
    private final String idempotencyKey;
    private final CreatePaymentResponse response;
}

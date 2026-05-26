package com.yuno.common.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class IdempotencyCheckResponse {
    private final boolean exists;
    private final CreatePaymentResponse cachedResponse;

    public static IdempotencyCheckResponse hit(CreatePaymentResponse response) {
        return IdempotencyCheckResponse.builder().exists(true).cachedResponse(response).build();
    }

    public static IdempotencyCheckResponse miss() {
        return IdempotencyCheckResponse.builder().exists(false).build();
    }
}

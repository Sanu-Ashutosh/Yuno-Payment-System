package com.yuno.common.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class IdempotencyCheckRequest {
    private final String idempotencyKey;
}

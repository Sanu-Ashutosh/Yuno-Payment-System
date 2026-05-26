package com.yuno.common.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiError {
    private final String code;
    private final String message;
    private final Object details;

    public static ApiError of(String code, String message) {
        return ApiError.builder().code(code).message(message).build();
    }
}

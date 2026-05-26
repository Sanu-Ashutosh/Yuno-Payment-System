package com.yuno.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final ApiError error;
    private final String correlationId;
    @Builder.Default
    private final Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return ApiResponse.<T>builder().success(true).data(data).correlationId(correlationId).build();
    }

    public static <T> ApiResponse<T> failure(ApiError error, String correlationId) {
        return ApiResponse.<T>builder().success(false).error(error).correlationId(correlationId).build();
    }
}

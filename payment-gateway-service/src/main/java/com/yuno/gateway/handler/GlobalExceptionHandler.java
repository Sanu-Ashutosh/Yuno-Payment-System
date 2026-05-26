package com.yuno.gateway.handler;

import com.yuno.common.constants.AppConstants;
import com.yuno.common.exception.*;
import com.yuno.common.response.ApiError;
import com.yuno.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (existing, replacement) -> existing));
        ApiError error = ApiError.builder().code("VALIDATION_ERROR")
                .message("Request validation failed").details(errors).build();
        return ResponseEntity.badRequest().body(ApiResponse.failure(error, correlationId()));
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(HttpClientErrorException.NotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ApiError.of("NOT_FOUND", "Payment not found"), correlationId()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ResourceAccessException ex) {
        log.error("Downstream service unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(ApiError.of("SERVICE_UNAVAILABLE",
                        "Payment processing service is temporarily unavailable"), correlationId()));
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ApiResponse<Void>> handleServerError(HttpServerErrorException ex) {
        log.error("Downstream service error. status={}", ex.getStatusCode(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.failure(ApiError.of("UPSTREAM_ERROR",
                        "An error occurred in the payment processing service"), correlationId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ApiError.of("INTERNAL_ERROR",
                        "An unexpected error occurred"), correlationId()));
    }

    private String correlationId() {
        return MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
    }
}

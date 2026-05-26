package com.yuno.processor.api;

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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ApiError.of("PAYMENT_NOT_FOUND", ex.getMessage()), cid()));
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidState(InvalidPaymentStateException ex) {
        log.error("Invalid state transition", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ApiError.of("INVALID_STATE", ex.getMessage()), cid()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (e, r) -> e));
        ApiError error = ApiError.builder().code("VALIDATION_ERROR")
                .message("Validation failed").details(errors).build();
        return ResponseEntity.badRequest().body(ApiResponse.failure(error, cid()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception in processor service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ApiError.of("INTERNAL_ERROR",
                        "An unexpected error occurred"), cid()));
    }

    private String cid() { return MDC.get(AppConstants.CORRELATION_ID_MDC_KEY); }
}

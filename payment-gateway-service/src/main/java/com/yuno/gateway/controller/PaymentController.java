package com.yuno.gateway.controller;

import com.yuno.common.constants.AppConstants;
import com.yuno.common.dto.CreatePaymentRequest;
import com.yuno.common.dto.CreatePaymentResponse;
import com.yuno.common.dto.PaymentDetailsResponse;
import com.yuno.common.response.ApiResponse;
import com.yuno.gateway.client.PaymentProcessorClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentProcessorClient processorClient;

    @PostMapping
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = AppConstants.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestHeader(value = AppConstants.CORRELATION_ID_HEADER, required = false) String correlationId) {

        String resolvedCorrelationId = MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
        log.info("Payment create request received. method={}, amount={}, correlationId={}",
                request.getPaymentMethod(), request.getAmount(), resolvedCorrelationId);

        ApiResponse<CreatePaymentResponse> response =
                processorClient.createPayment(request, idempotencyKey, resolvedCorrelationId);

        HttpStatus status = response.getData() != null &&
                response.getData().isIdempotencyHit() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDetailsResponse>> getPayment(
            @PathVariable String paymentId) {

        String correlationId = MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
        log.info("Payment fetch request. paymentId={}, correlationId={}", paymentId, correlationId);

        ApiResponse<PaymentDetailsResponse> response = processorClient.getPayment(paymentId, correlationId);
        return ResponseEntity.ok(response);
    }
}

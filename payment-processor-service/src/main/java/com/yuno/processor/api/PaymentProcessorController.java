package com.yuno.processor.api;

import com.yuno.common.constants.AppConstants;
import com.yuno.common.dto.*;
import com.yuno.common.exception.PaymentNotFoundException;
import com.yuno.common.response.ApiResponse;
import com.yuno.processor.application.orchestrator.PaymentOrchestrator;
import com.yuno.processor.infrastructure.persistence.mapper.PaymentMapper;
import com.yuno.processor.infrastructure.persistence.repository.PaymentEventRepository;
import com.yuno.processor.infrastructure.persistence.repository.PaymentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/payments")
@RequiredArgsConstructor
public class PaymentProcessorController {

    private final PaymentOrchestrator orchestrator;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentMapper paymentMapper;

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> processPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(AppConstants.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestHeader(value = AppConstants.CORRELATION_ID_HEADER, required = false) String correlationId) {

        String resolvedCorrelationId = correlationId != null
                ? correlationId : MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);

        CreatePaymentResponse response = orchestrator.processPayment(request, idempotencyKey, resolvedCorrelationId);
        HttpStatus status = response.isIdempotencyHit() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status)
                .body(ApiResponse.success(response, resolvedCorrelationId));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDetailsResponse>> getPayment(
            @PathVariable String paymentId,
            @RequestHeader(value = AppConstants.CORRELATION_ID_HEADER, required = false) String correlationId) {

        UUID id;
        try {
            id = UUID.fromString(paymentId);
        } catch (IllegalArgumentException e) {
            throw new PaymentNotFoundException(paymentId);
        }

        return paymentRepository.findById(id)
                .map(payment -> {
                    var events = paymentEventRepository.findByPaymentIdOrderByCreatedAtAsc(id);
                    payment.setEvents(events);
                    PaymentDetailsResponse response = paymentMapper.toDetailsResponse(payment);
                    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
                })
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}

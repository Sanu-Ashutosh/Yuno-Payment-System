package com.yuno.processor.application.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.common.constants.AppConstants;
import com.yuno.common.dto.*;
import com.yuno.common.enums.EventType;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.processor.domain.event.PaymentDomainEvent;
import com.yuno.processor.domain.statemachine.PaymentStateMachine;
import com.yuno.processor.infrastructure.client.IdempotencyServiceClient;
import com.yuno.processor.infrastructure.client.ProviderServiceClient;
import com.yuno.processor.infrastructure.messaging.PaymentEventPublisher;
import com.yuno.processor.infrastructure.persistence.entity.PaymentEntity;
import com.yuno.processor.infrastructure.persistence.entity.PaymentEventEntity;
import com.yuno.processor.infrastructure.persistence.mapper.PaymentMapper;
import com.yuno.processor.infrastructure.persistence.repository.PaymentEventRepository;
import com.yuno.processor.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrchestrator {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentStateMachine stateMachine;
    private final ProviderServiceClient providerServiceClient;
    private final IdempotencyServiceClient idempotencyServiceClient;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreatePaymentResponse processPayment(CreatePaymentRequest request,
                                                 String idempotencyKey,
                                                 String correlationId) {
        log.info("Processing payment. method={}, amount={}, currency={}, correlationId={}",
                request.getPaymentMethod(), request.getAmount(), request.getCurrency(), correlationId);

        // ── Step 1: Two-layer idempotency check ──────────────────────────────
        IdempotencyCheckResponse idempotencyResult = checkIdempotency(idempotencyKey, correlationId);
        if (idempotencyResult.isExists()) {
            log.info("Idempotency hit. key={}, correlationId={}", idempotencyKey, correlationId);
            return buildIdempotencyHitResponse(idempotencyResult.getCachedResponse());
        }

        // ── Step 2: Check DB for existing payment (double-check layer) ───────
        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Found existing payment in DB. idempotencyKey={}", idempotencyKey);
            CreatePaymentResponse response = paymentMapper.toCreateResponse(existingPayment.get());
            storeIdempotencyResult(idempotencyKey, response);
            return buildIdempotencyHitResponse(response);
        }

        // ── Step 3: Persist payment as INITIATED ─────────────────────────────
        PaymentEntity payment = createInitialPayment(request, idempotencyKey, correlationId);
        paymentRepository.save(payment);
        saveEvent(payment, EventType.PAYMENT_INITIATED, "Payment initiated");
        log.info("Payment persisted. paymentId={}, correlationId={}", payment.getId(), correlationId);

        // ── Step 4: Transition to PROCESSING ─────────────────────────────────
        transitionStatus(payment, PaymentStatus.PROCESSING);

        // ── Step 5: Transition to ROUTING ────────────────────────────────────
        transitionStatus(payment, PaymentStatus.ROUTING);
        saveEvent(payment, EventType.ROUTING_DECIDED,
                "Routing to provider for method: " + request.getPaymentMethod());

        // ── Step 6: Call Provider Service ────────────────────────────────────
        transitionStatus(payment, PaymentStatus.PROVIDER_CALLED);
        saveEvent(payment, EventType.PROVIDER_CALLED, "Calling payment provider");

        ProviderProcessResponse providerResult = callProvider(payment);

        // ── Step 7: Update payment based on provider result ──────────────────
        updatePaymentFromProviderResult(payment, providerResult);
        paymentRepository.save(payment);

        // ── Step 8: Build response ────────────────────────────────────────────
        CreatePaymentResponse response = paymentMapper.toCreateResponse(payment);

        // ── Step 9: Store in idempotency service ─────────────────────────────
        storeIdempotencyResult(idempotencyKey, response);

        // ── Step 10: Publish domain event to Kafka ────────────────────────────
        publishDomainEvent(payment);

        log.info("Payment processed. paymentId={}, status={}, provider={}, correlationId={}",
                payment.getId(), payment.getStatus(), payment.getProvider(), correlationId);

        return response;
    }

    private IdempotencyCheckResponse checkIdempotency(String key, String correlationId) {
        try {
            return idempotencyServiceClient.check(key);
        } catch (Exception e) {
            log.warn("Idempotency service check failed, proceeding without cache. correlationId={}", correlationId, e);
            return IdempotencyCheckResponse.miss();
        }
    }

    private void storeIdempotencyResult(String key, CreatePaymentResponse response) {
        try {
            idempotencyServiceClient.store(key, response);
        } catch (Exception e) {
            log.warn("Failed to store idempotency result. key={}", key, e);
        }
    }

    private PaymentEntity createInitialPayment(CreatePaymentRequest request,
                                                String idempotencyKey, String correlationId) {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .customerId(request.getCustomerId())
                .metadata(request.getMetadata())
                .correlationId(correlationId)
                .status(PaymentStatus.INITIATED)
                .retryCount(0)
                .build();
    }

    private ProviderProcessResponse callProvider(PaymentEntity payment) {
        try {
            return providerServiceClient.process(
                    ProviderProcessRequest.builder()
                            .paymentId(payment.getId().toString())
                            .amount(payment.getAmount())
                            .currency(payment.getCurrency())
                            .paymentMethod(payment.getPaymentMethod())
                            .correlationId(payment.getCorrelationId())
                            .build());
        } catch (Exception e) {
            log.error("Provider service call failed. paymentId={}", payment.getId(), e);
            return ProviderProcessResponse.failure("Provider service unavailable", null, 0);
        }
    }

    private void updatePaymentFromProviderResult(PaymentEntity payment, ProviderProcessResponse result) {
        payment.setProvider(result.getProviderUsed());
        payment.setRetryCount(result.getRetryCount());
        payment.setProviderTransactionId(result.getProviderTransactionId());

        if (result.isSuccess()) {
            transitionStatus(payment, PaymentStatus.SUCCESS);
            saveEvent(payment, EventType.PAYMENT_SUCCESS,
                    "Payment successful via " + result.getProviderUsed()
                    + (result.isFailoverUsed() ? " (failover)" : ""));
        } else {
            boolean wasRetried = result.getRetryCount() > 0;
            boolean wasFailover = result.isFailoverUsed();
            payment.setFailureReason(result.getFailureReason());

            PaymentStatus finalStatus = wasRetried || wasFailover
                    ? PaymentStatus.PERMANENTLY_FAILED : PaymentStatus.FAILED;
            transitionStatus(payment, finalStatus);
            saveEvent(payment, EventType.PAYMENT_FAILED,
                    "Payment failed after " + result.getRetryCount() + " retries. Reason: "
                    + result.getFailureReason());
        }
    }

    private void transitionStatus(PaymentEntity payment, PaymentStatus newStatus) {
        stateMachine.validateTransition(payment.getStatus(), newStatus);
        payment.setStatus(newStatus);
    }

    private void saveEvent(PaymentEntity payment, EventType eventType, String payload) {
        PaymentEventEntity event = PaymentEventEntity.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .eventType(eventType)
                .payload(payload)
                .build();
        paymentEventRepository.save(event);
    }

    private void publishDomainEvent(PaymentEntity payment) {
        try {
            eventPublisher.publish(PaymentDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .paymentId(payment.getId().toString())
                    .eventType(payment.getStatus() == PaymentStatus.SUCCESS
                            ? EventType.PAYMENT_SUCCESS : EventType.PAYMENT_FAILED)
                    .currentStatus(payment.getStatus())
                    .paymentMethod(payment.getPaymentMethod())
                    .provider(payment.getProvider())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .customerId(payment.getCustomerId())
                    .correlationId(payment.getCorrelationId())
                    .failureReason(payment.getFailureReason())
                    .occurredAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish domain event. paymentId={}", payment.getId(), e);
        }
    }

    private CreatePaymentResponse buildIdempotencyHitResponse(CreatePaymentResponse cached) {
        return CreatePaymentResponse.builder()
                .paymentId(cached.getPaymentId())
                .status(cached.getStatus())
                .amount(cached.getAmount())
                .currency(cached.getCurrency())
                .paymentMethod(cached.getPaymentMethod())
                .provider(cached.getProvider())
                .correlationId(cached.getCorrelationId())
                .idempotencyHit(true)
                .createdAt(cached.getCreatedAt())
                .build();
    }
}

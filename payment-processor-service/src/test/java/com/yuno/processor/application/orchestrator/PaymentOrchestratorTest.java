package com.yuno.processor.application.orchestrator;

import com.yuno.common.dto.*;
import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.enums.ProviderType;
import com.yuno.processor.domain.statemachine.PaymentStateMachine;
import com.yuno.processor.infrastructure.client.IdempotencyServiceClient;
import com.yuno.processor.infrastructure.client.ProviderServiceClient;
import com.yuno.processor.infrastructure.messaging.PaymentEventPublisher;
import com.yuno.processor.infrastructure.persistence.entity.PaymentEntity;
import com.yuno.processor.infrastructure.persistence.mapper.PaymentMapper;
import com.yuno.processor.infrastructure.persistence.repository.PaymentEventRepository;
import com.yuno.processor.infrastructure.persistence.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOrchestratorTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentStateMachine stateMachine;
    @Mock private ProviderServiceClient providerServiceClient;
    @Mock private IdempotencyServiceClient idempotencyServiceClient;
    @Mock private PaymentEventPublisher eventPublisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentOrchestrator orchestrator;

    private CreatePaymentRequest request;
    private final String idempotencyKey = "test-idem-key-001";
    private final String correlationId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        request = CreatePaymentRequest.builder()
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .customerId("cust_123")
                .build();
    }

    @Test
    void processPayment_idempotencyHit_returnsCachedResponse() {
        CreatePaymentResponse cached = CreatePaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString())
                .status(PaymentStatus.SUCCESS)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(PaymentMethod.CARD)
                .provider(ProviderType.PROVIDER_A)
                .correlationId(correlationId)
                .idempotencyHit(false)
                .createdAt(Instant.now())
                .build();

        when(idempotencyServiceClient.check(idempotencyKey))
                .thenReturn(IdempotencyCheckResponse.hit(cached));

        CreatePaymentResponse result = orchestrator.processPayment(request, idempotencyKey, correlationId);

        assertThat(result.isIdempotencyHit()).isTrue();
        assertThat(result.getPaymentId()).isEqualTo(cached.getPaymentId());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_newPayment_successPath() {
        when(idempotencyServiceClient.check(any())).thenReturn(IdempotencyCheckResponse.miss());
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderProcessResponse providerSuccess = ProviderProcessResponse.builder()
                .success(true)
                .providerTransactionId("PA-TXID-001")
                .providerUsed(ProviderType.PROVIDER_A)
                .retryCount(0)
                .build();
        when(providerServiceClient.process(any())).thenReturn(providerSuccess);

        CreatePaymentResponse mockResponse = CreatePaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString())
                .status(PaymentStatus.SUCCESS)
                .amount(request.getAmount())
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .provider(ProviderType.PROVIDER_A)
                .idempotencyHit(false)
                .createdAt(Instant.now())
                .build();
        when(paymentMapper.toCreateResponse(any())).thenReturn(mockResponse);
        doNothing().when(stateMachine).validateTransition(any(), any());

        CreatePaymentResponse result = orchestrator.processPayment(request, idempotencyKey, correlationId);

        assertThat(result).isNotNull();
        assertThat(result.isIdempotencyHit()).isFalse();
        verify(paymentRepository, atLeast(1)).save(any());
        verify(providerServiceClient, times(1)).process(any());
    }

    @Test
    void processPayment_providerFails_marksPaymentFailed() {
        when(idempotencyServiceClient.check(any())).thenReturn(IdempotencyCheckResponse.miss());
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(stateMachine).validateTransition(any(), any());

        ProviderProcessResponse providerFailure = ProviderProcessResponse.failure(
                "CARD_DECLINED", ProviderType.PROVIDER_A, 3);
        when(providerServiceClient.process(any())).thenReturn(providerFailure);

        CreatePaymentResponse failedResponse = CreatePaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString())
                .status(PaymentStatus.PERMANENTLY_FAILED)
                .amount(request.getAmount())
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyHit(false)
                .createdAt(Instant.now())
                .build();
        when(paymentMapper.toCreateResponse(any())).thenReturn(failedResponse);

        CreatePaymentResponse result = orchestrator.processPayment(request, idempotencyKey, correlationId);

        assertThat(result).isNotNull();
        verify(paymentRepository, atLeast(1)).save(any(PaymentEntity.class));
    }

    @Test
    void processPayment_idempotencyServiceDown_continuesGracefully() {
        when(idempotencyServiceClient.check(any())).thenThrow(new RuntimeException("Redis down"));
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(stateMachine).validateTransition(any(), any());

        ProviderProcessResponse providerSuccess = ProviderProcessResponse.builder()
                .success(true).providerTransactionId("TX-001")
                .providerUsed(ProviderType.PROVIDER_A).retryCount(0).build();
        when(providerServiceClient.process(any())).thenReturn(providerSuccess);

        CreatePaymentResponse mockResponse = CreatePaymentResponse.builder()
                .paymentId(UUID.randomUUID().toString()).status(PaymentStatus.SUCCESS)
                .amount(request.getAmount()).currency("INR")
                .paymentMethod(PaymentMethod.CARD).idempotencyHit(false)
                .createdAt(Instant.now()).build();
        when(paymentMapper.toCreateResponse(any())).thenReturn(mockResponse);

        // Should not throw - graceful degradation
        CreatePaymentResponse result = orchestrator.processPayment(request, idempotencyKey, correlationId);
        assertThat(result).isNotNull();
    }
}

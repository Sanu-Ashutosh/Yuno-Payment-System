package com.yuno.gateway.client;

import com.yuno.common.dto.CreatePaymentRequest;
import com.yuno.common.dto.CreatePaymentResponse;
import com.yuno.common.dto.PaymentDetailsResponse;
import com.yuno.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class PaymentProcessorClient {

    private final RestClient restClient;

    public PaymentProcessorClient(@Value("${services.processor.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ApiResponse<CreatePaymentResponse> createPayment(
            CreatePaymentRequest request, String idempotencyKey, String correlationId) {
        log.info("Calling processor service. correlationId={}", correlationId);
        return restClient.post()
                .uri("/api/v1/internal/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .header("X-Correlation-Id", correlationId)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public ApiResponse<PaymentDetailsResponse> getPayment(String paymentId, String correlationId) {
        log.info("Fetching payment from processor. paymentId={}, correlationId={}", paymentId, correlationId);
        return restClient.get()
                .uri("/api/v1/internal/payments/{paymentId}", paymentId)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}

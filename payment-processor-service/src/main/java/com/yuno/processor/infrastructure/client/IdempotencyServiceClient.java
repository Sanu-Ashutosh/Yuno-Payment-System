package com.yuno.processor.infrastructure.client;

import com.yuno.common.dto.CreatePaymentResponse;
import com.yuno.common.dto.IdempotencyCheckRequest;
import com.yuno.common.dto.IdempotencyCheckResponse;
import com.yuno.common.dto.IdempotencyStoreRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class IdempotencyServiceClient {

    private final RestClient restClient;

    public IdempotencyServiceClient(@Value("${services.idempotency.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public IdempotencyCheckResponse check(String idempotencyKey) {
        log.debug("Checking idempotency. key={}", idempotencyKey);
        return restClient.post()
                .uri("/api/v1/internal/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .body(IdempotencyCheckRequest.builder().idempotencyKey(idempotencyKey).build())
                .retrieve()
                .body(IdempotencyCheckResponse.class);
    }

    public void store(String idempotencyKey, CreatePaymentResponse response) {
        log.debug("Storing idempotency result. key={}", idempotencyKey);
        restClient.post()
                .uri("/api/v1/internal/idempotency/store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(IdempotencyStoreRequest.builder()
                        .idempotencyKey(idempotencyKey).response(response).build())
                .retrieve()
                .toBodilessEntity();
    }
}

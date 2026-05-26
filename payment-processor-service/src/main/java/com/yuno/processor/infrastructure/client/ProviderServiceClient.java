package com.yuno.processor.infrastructure.client;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ProviderServiceClient {

    private final RestClient restClient;

    public ProviderServiceClient(@Value("${services.provider.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ProviderProcessResponse process(ProviderProcessRequest request) {
        log.info("Calling provider service. paymentId={}, method={}",
                request.getPaymentId(), request.getPaymentMethod());
        return restClient.post()
                .uri("/api/v1/internal/providers/process")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProviderProcessResponse.class);
    }
}

package com.yuno.provider.api;

import com.yuno.common.response.ApiResponse;
import com.yuno.provider.connector.ProviderAConnector;
import com.yuno.provider.connector.ProviderBConnector;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderHealthController {

    private final ProviderAConnector providerA;
    private final ProviderBConnector providerB;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProvidersHealth() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("providerCircuitBreaker");

        Map<String, Object> providerAHealth = new LinkedHashMap<>();
        providerAHealth.put("status", providerA.isHealthy() ? "HEALTHY" : "DEGRADED");
        providerAHealth.put("successRate", String.format("%.1f%%", providerA.getSuccessRate() * 100));
        providerAHealth.put("circuitBreakerState", cb.getState().toString());
        providerAHealth.put("supportedMethods", "CARD");

        Map<String, Object> providerBHealth = new LinkedHashMap<>();
        providerBHealth.put("status", providerB.isHealthy() ? "HEALTHY" : "DEGRADED");
        providerBHealth.put("successRate", String.format("%.1f%%", providerB.getSuccessRate() * 100));
        providerBHealth.put("circuitBreakerState", cb.getState().toString());
        providerBHealth.put("supportedMethods", "UPI");

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("PROVIDER_A", providerAHealth);
        health.put("PROVIDER_B", providerBHealth);

        return ResponseEntity.ok(ApiResponse.success(health, null));
    }
}

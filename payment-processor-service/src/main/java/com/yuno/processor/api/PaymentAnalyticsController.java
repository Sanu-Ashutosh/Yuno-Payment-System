package com.yuno.processor.api;

import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.PaymentStatus;
import com.yuno.common.enums.ProviderType;
import com.yuno.common.response.ApiResponse;
import com.yuno.processor.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/analytics")
@RequiredArgsConstructor
public class PaymentAnalyticsController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStats() {
        long total = paymentRepository.count();
        long success = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long failed = paymentRepository.countByStatus(PaymentStatus.FAILED);
        long permanentlyFailed = paymentRepository.countByStatus(PaymentStatus.PERMANENTLY_FAILED);

        String successRate = total > 0
                ? String.format("%.2f%%", (double) success / total * 100) : "N/A";

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPayments", total);
        stats.put("successRate", successRate);
        stats.put("byStatus", Map.of(
                "SUCCESS", success,
                "FAILED", failed,
                "PERMANENTLY_FAILED", permanentlyFailed,
                "PROCESSING", paymentRepository.countByStatus(PaymentStatus.PROCESSING)
        ));
        stats.put("byMethod", Map.of(
                "CARD", paymentRepository.countByPaymentMethod(PaymentMethod.CARD),
                "UPI", paymentRepository.countByPaymentMethod(PaymentMethod.UPI)
        ));
        stats.put("byProvider", Map.of(
                "PROVIDER_A", paymentRepository.countByProvider(ProviderType.PROVIDER_A),
                "PROVIDER_B", paymentRepository.countByProvider(ProviderType.PROVIDER_B)
        ));
        stats.put("avgRetryCount", paymentRepository.avgRetryCount());

        return ResponseEntity.ok(ApiResponse.success(stats, null));
    }
}

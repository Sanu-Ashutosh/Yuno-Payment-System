package com.yuno.provider.router;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import com.yuno.common.enums.PaymentMethod;
import com.yuno.common.enums.ProviderType;
import com.yuno.provider.connector.ProviderAConnector;
import com.yuno.provider.connector.ProviderBConnector;
import com.yuno.provider.retry.RetryableProviderExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderRouterTest {

    @Mock private ProviderAConnector providerA;
    @Mock private ProviderBConnector providerB;
    @Mock private RetryableProviderExecutor retryExecutor;
    @InjectMocks private ProviderRouter router;

    private ProviderProcessRequest cardRequest() {
        return ProviderProcessRequest.builder()
                .paymentId("payment-001")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CARD)
                .correlationId("corr-001")
                .build();
    }

    private ProviderProcessRequest upiRequest() {
        return ProviderProcessRequest.builder()
                .paymentId("payment-002")
                .amount(new BigDecimal("200.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.UPI)
                .correlationId("corr-002")
                .build();
    }

    @Test
    void route_cardPayment_usesProviderA_onSuccess() {
        ProviderProcessResponse success = ProviderProcessResponse.builder()
                .success(true).providerTransactionId("PA-TX-001")
                .providerUsed(ProviderType.PROVIDER_A).retryCount(0).build();

        when(retryExecutor.executeWithRetry(eq(providerA), any(), any())).thenReturn(success);

        ProviderProcessResponse result = router.route(cardRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_A);
        assertThat(result.isFailoverUsed()).isFalse();
        verify(retryExecutor, times(1)).executeWithRetry(eq(providerA), any(), any());
        verify(retryExecutor, never()).executeWithRetry(eq(providerB), any(), any());
    }

    @Test
    void route_upiPayment_usesProviderB_onSuccess() {
        ProviderProcessResponse success = ProviderProcessResponse.builder()
                .success(true).providerTransactionId("PB-TX-001")
                .providerUsed(ProviderType.PROVIDER_B).retryCount(0).build();

        when(retryExecutor.executeWithRetry(eq(providerB), any(), any())).thenReturn(success);

        ProviderProcessResponse result = router.route(upiRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_B);
        verify(retryExecutor, times(1)).executeWithRetry(eq(providerB), any(), any());
        verify(retryExecutor, never()).executeWithRetry(eq(providerA), any(), any());
    }

    @Test
    void route_primaryFails_triggersFailover() {
        ProviderProcessResponse primaryFailure = ProviderProcessResponse.builder()
                .success(false).failureReason("PROVIDER_TIMEOUT")
                .providerUsed(ProviderType.PROVIDER_A).retryCount(3).build();

        ProviderProcessResponse failoverSuccess = ProviderProcessResponse.builder()
                .success(true).providerTransactionId("PB-TX-001")
                .providerUsed(ProviderType.PROVIDER_B).retryCount(0).build();

        when(retryExecutor.executeWithRetry(eq(providerA), any(), any())).thenReturn(primaryFailure);
        when(retryExecutor.executeWithRetry(eq(providerB), any(), any())).thenReturn(failoverSuccess);

        ProviderProcessResponse result = router.route(cardRequest());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailoverUsed()).isTrue();
        assertThat(result.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_B);
    }

    @Test
    void route_bothProvidersFail_returnsFailed() {
        ProviderProcessResponse failure = ProviderProcessResponse.builder()
                .success(false).failureReason("ALL_PROVIDERS_DOWN")
                .providerUsed(ProviderType.PROVIDER_A).retryCount(3).build();

        when(retryExecutor.executeWithRetry(any(), any(), any())).thenReturn(failure);

        ProviderProcessResponse result = router.route(cardRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailoverUsed()).isTrue();
    }
}

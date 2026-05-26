package com.yuno.common.constants;

public final class AppConstants {
    private AppConstants() {}
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long IDEMPOTENCY_TTL_HOURS = 24;
}

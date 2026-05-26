package com.yuno.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.common.constants.AppConstants;
import com.yuno.common.response.ApiError;
import com.yuno.common.response.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    @Value("${rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String clientKey = httpRequest.getHeader(AppConstants.API_KEY_HEADER);
        if (clientKey == null) clientKey = httpRequest.getRemoteAddr();

        RateLimitBucket bucket = buckets.computeIfAbsent(clientKey, k -> new RateLimitBucket());

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded. client={}", clientKey);
            httpResponse.setStatus(429);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            httpResponse.setHeader("Retry-After", "60");
            ApiResponse<Void> errorResponse = ApiResponse.failure(
                    ApiError.of("RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later."),
                    MDC.get(AppConstants.CORRELATION_ID_MDC_KEY));
            httpResponse.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        chain.doFilter(request, response);
    }

    private class RateLimitBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().getEpochSecond();

        boolean tryConsume() {
            long now = Instant.now().getEpochSecond();
            if (now - windowStart >= 60) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= requestsPerMinute;
        }
    }
}

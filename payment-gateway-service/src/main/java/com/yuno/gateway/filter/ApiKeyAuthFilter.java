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
import java.util.Set;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements Filter {

    @Value("${security.api-keys}")
    private Set<String> validApiKeys;

    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip actuator endpoints
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpRequest.getHeader(AppConstants.API_KEY_HEADER);
        if (apiKey == null || !validApiKeys.contains(apiKey)) {
            log.warn("Unauthorized request - invalid or missing API key. path={}", path);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> errorResponse = ApiResponse.failure(
                    ApiError.of("UNAUTHORIZED", "Invalid or missing API key"),
                    MDC.get(AppConstants.CORRELATION_ID_MDC_KEY));
            httpResponse.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        chain.doFilter(request, response);
    }
}

package com.yuno.common.exception;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String clientId) {
        super("Rate limit exceeded for client: " + clientId);
    }
}

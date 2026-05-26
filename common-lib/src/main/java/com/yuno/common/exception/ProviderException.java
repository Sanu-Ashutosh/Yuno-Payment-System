package com.yuno.common.exception;

public class ProviderException extends RuntimeException {
    private final String providerName;
    private final boolean retryable;

    public ProviderException(String providerName, String message, boolean retryable) {
        super(message);
        this.providerName = providerName;
        this.retryable = retryable;
    }

    public ProviderException(String providerName, String message) {
        this(providerName, message, true);
    }

    public String getProviderName() { return providerName; }
    public boolean isRetryable() { return retryable; }
}

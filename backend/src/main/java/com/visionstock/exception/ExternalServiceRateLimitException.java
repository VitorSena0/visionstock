package com.visionstock.exception;

public class ExternalServiceRateLimitException extends RuntimeException {

    private final Integer retryAfterSeconds;

    public ExternalServiceRateLimitException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }

    public ExternalServiceRateLimitException(String message, Integer retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}


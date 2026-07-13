package com.devmatch.exception;

public class AiReviewRateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public AiReviewRateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

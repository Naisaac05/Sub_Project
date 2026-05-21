package com.devmatch.service.ai;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntToLongFunction;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class AiRetrySupport {

    static final int DEFAULT_READ_TIMEOUT_SECONDS = 30;
    static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 400L;
    private static final long MAX_BACKOFF_MILLIS = 2_000L;

    private AiRetrySupport() {
    }

    static Duration boundedReadTimeout(int seconds) {
        return Duration.ofSeconds(seconds <= 0 ? DEFAULT_READ_TIMEOUT_SECONDS : seconds);
    }

    static <T> Optional<T> executeWithRetry(Supplier<Optional<T>> operation) {
        return executeWithRetry(
                operation,
                AiRetrySupport::sleep,
                AiRetrySupport::fullJitterDelayMillis
        );
    }

    static <T> Optional<T> executeWithRetry(
            Supplier<Optional<T>> operation,
            LongConsumer sleeper,
            IntToLongFunction delayMillis
    ) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (RestClientException ex) {
                if (attempt >= MAX_ATTEMPTS || !isRetryable(ex)) {
                    return Optional.empty();
                }
                sleeper.accept(delayMillis.applyAsLong(attempt));
            }
        }
        return Optional.empty();
    }

    static boolean isRetryable(RestClientException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        return true;
    }

    private static long fullJitterDelayMillis(int attempt) {
        long cap = Math.min(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS * (1L << Math.max(attempt - 1, 0)));
        return ThreadLocalRandom.current().nextLong(cap + 1);
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

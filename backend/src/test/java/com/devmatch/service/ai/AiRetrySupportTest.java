package com.devmatch.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiRetrySupportTest {

    @Test
    void boundedReadTimeoutUsesThirtySecondsWhenConfiguredAsInfinite() {
        assertThat(AiRetrySupport.boundedReadTimeout(0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(AiRetrySupport.boundedReadTimeout(-1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(AiRetrySupport.boundedReadTimeout(25)).isEqualTo(Duration.ofSeconds(25));
    }

    @Test
    void retriesTransientFailuresWithBoundedAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        Optional<String> result = AiRetrySupport.executeWithRetry(
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new ResourceAccessException("temporary timeout");
                    }
                    return Optional.of("ok");
                },
                ignored -> {
                },
                ignored -> 0L
        );

        assertThat(result).contains("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryNonRetryableClientErrors() {
        AtomicInteger attempts = new AtomicInteger();

        Optional<String> result = AiRetrySupport.executeWithRetry(
                () -> {
                    attempts.incrementAndGet();
                    throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
                },
                ignored -> {
                },
                ignored -> 0L
        );

        assertThat(result).isEmpty();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void classifiesRetryableAndNonRetryableExceptions() {
        assertThat(AiRetrySupport.isRetryable(new ResourceAccessException("timeout"))).isTrue();
        assertThat(AiRetrySupport.isRetryable(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))).isTrue();
        assertThat(AiRetrySupport.isRetryable(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
        assertThat(AiRetrySupport.isRetryable(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))).isFalse();
        assertThat(AiRetrySupport.isRetryable(new RestClientException("unknown"))).isTrue();
    }
}

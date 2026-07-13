package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.exception.AiReviewRateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiReviewRateLimitServiceTest {

    @Test
    void rejectsUserAfterConfiguredLimitAndReturnsRetryAfter() {
        AtomicLong now = new AtomicLong(1_000L);
        AiReviewRateLimitService service = new AiReviewRateLimitService(properties(2, 10, 60), now::get);

        assertDoesNotThrow(() -> service.check("42", "127.0.0.1"));
        assertDoesNotThrow(() -> service.check("42", "127.0.0.1"));
        AiReviewRateLimitExceededException exception = assertThrows(
                AiReviewRateLimitExceededException.class,
                () -> service.check("42", "127.0.0.1")
        );

        assertEquals(60, exception.getRetryAfterSeconds());
    }

    @Test
    void rejectsSharedIpEvenWhenUsersAreDifferent() {
        AiReviewRateLimitService service = new AiReviewRateLimitService(properties(10, 2, 60), () -> 1_000L);

        service.check("1", "10.0.0.1");
        service.check("2", "10.0.0.1");

        assertThrows(AiReviewRateLimitExceededException.class, () -> service.check("3", "10.0.0.1"));
    }

    @Test
    void startsNewWindowAfterExpiry() {
        AtomicLong now = new AtomicLong(1_000L);
        AiReviewRateLimitService service = new AiReviewRateLimitService(properties(1, 10, 60), now::get);

        service.check("42", "127.0.0.1");
        now.addAndGet(60_000L);

        assertDoesNotThrow(() -> service.check("42", "127.0.0.1"));
    }

    @Test
    void usesRedisRetryAfterForDistributedLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any())).thenReturn(java.util.List.of(13L, 42L));
        AiReviewRateLimitService service = new AiReviewRateLimitService(properties(12, 60, 60), redis);

        AiReviewRateLimitExceededException exception = assertThrows(
                AiReviewRateLimitExceededException.class,
                () -> service.check("42", null)
        );

        assertEquals(42, exception.getRetryAfterSeconds());
    }

    private AiReviewProperties properties(int perUser, int perIp, int windowSeconds) {
        return new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new AiReviewProperties.RateLimit(true, perUser, perIp, windowSeconds),
                true,
                45,
                "",
                ""
        );
    }
}

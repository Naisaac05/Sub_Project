package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.exception.AiReviewRateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiReviewRateLimitService {

    private static final int MAX_BUCKETS_BEFORE_CLEANUP = 10_000;
    private static final Logger log = LoggerFactory.getLogger(AiReviewRateLimitService.class);
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {count, ttl}
            """, List.class);

    private final AiReviewProperties properties;
    private final StringRedisTemplate redis;
    private final TimeSource timeSource;
    private final Map<String, WindowCounter> buckets = new ConcurrentHashMap<>();

    @Autowired
    public AiReviewRateLimitService(AiReviewProperties properties, StringRedisTemplate redis) {
        this(properties, redis, System::currentTimeMillis);
    }

    AiReviewRateLimitService(AiReviewProperties properties, TimeSource timeSource) {
        this(properties, null, timeSource);
    }

    private AiReviewRateLimitService(
            AiReviewProperties properties,
            StringRedisTemplate redis,
            TimeSource timeSource
    ) {
        this.properties = properties;
        this.redis = redis;
        this.timeSource = timeSource;
    }

    public void check(String userKey, String ipKey) {
        AiReviewProperties.RateLimit rateLimit = properties.rateLimit();
        if (!rateLimit.enabled()) {
            return;
        }

        int windowSeconds = Math.max(1, rateLimit.windowSeconds());
        long nowMillis = timeSource.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        int retryAfterSeconds = 0;

        if (userKey != null && !userKey.isBlank() && rateLimit.perUserPerMinute() > 0) {
            retryAfterSeconds = Math.max(
                    retryAfterSeconds,
                    check("user:" + userKey, rateLimit.perUserPerMinute(), windowSeconds, windowMillis, nowMillis)
            );
        }
        if (ipKey != null && !ipKey.isBlank() && rateLimit.perIpPerMinute() > 0) {
            retryAfterSeconds = Math.max(
                    retryAfterSeconds,
                    check("ip:" + ipKey, rateLimit.perIpPerMinute(), windowSeconds, windowMillis, nowMillis)
            );
        }

        if (buckets.size() > MAX_BUCKETS_BEFORE_CLEANUP) {
            cleanupExpired(nowMillis, windowMillis);
        }

        if (retryAfterSeconds > 0) {
            throw new AiReviewRateLimitExceededException(
                    "AI review requests are temporarily limited. Please retry shortly.",
                    retryAfterSeconds
            );
        }
    }

    private int check(
            String key,
            int perMinuteLimit,
            int windowSeconds,
            long windowMillis,
            long nowMillis
    ) {
        int scaledLimit = Math.max(1, (int) Math.ceil(perMinuteLimit * windowSeconds / 60.0));
        if (redis == null) {
            return checkLocalBucket(key, scaledLimit, windowMillis, nowMillis);
        }
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of("devmatch:rate-limit:ai-review:" + key),
                    String.valueOf(windowSeconds)
            );
            if (result == null || result.size() < 2 || result.get(0) <= scaledLimit) {
                return 0;
            }
            return Math.max(1, result.get(1).intValue());
        } catch (DataAccessException exception) {
            log.warn("Redis rate limit check failed; using local fallback", exception);
            return checkLocalBucket(key, scaledLimit, windowMillis, nowMillis);
        }
    }

    private int checkLocalBucket(String key, int limit, long windowMillis, long nowMillis) {
        WindowCounter counter = buckets.computeIfAbsent(key, ignored -> new WindowCounter(nowMillis, 0));
        synchronized (counter) {
            if (nowMillis - counter.windowStartMillis >= windowMillis) {
                counter.windowStartMillis = nowMillis;
                counter.count = 0;
            }
            if (counter.count >= limit) {
                long retryAfterMillis = Math.max(1000L, windowMillis - (nowMillis - counter.windowStartMillis));
                return (int) Math.ceil(retryAfterMillis / 1000.0);
            }
            counter.count += 1;
            return 0;
        }
    }

    private void cleanupExpired(long nowMillis, long windowMillis) {
        Iterator<Map.Entry<String, WindowCounter>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WindowCounter> entry = iterator.next();
            WindowCounter counter = entry.getValue();
            synchronized (counter) {
                if (nowMillis - counter.windowStartMillis >= windowMillis) {
                    iterator.remove();
                }
            }
        }
    }

    interface TimeSource {
        long currentTimeMillis();
    }

    private static final class WindowCounter {
        private long windowStartMillis;
        private int count;

        private WindowCounter(long windowStartMillis, int count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}

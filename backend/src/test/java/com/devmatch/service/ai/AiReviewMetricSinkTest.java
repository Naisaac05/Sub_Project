package com.devmatch.service.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewMetricSinkTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiReviewMetricSink sink = new AiReviewMetricSink(registry);

    @Test
    void streamLifecycleEventsIncrementMicrometerCounters() {
        sink.streamCompleted("corr-1", 20L, 100L, "FREE_QUESTION", 123L, 456L, 2, 42);
        sink.streamDisconnected("corr-2", 20L, 100L, "FREE_QUESTION", "timeout", 90L, 234L, 12);
        sink.streamPartialFailed("corr-3", 20L, 100L, "FREE_QUESTION", "ollama_timeout", 345L, 1, 8);

        assertThat(registry.counter("ai.review.stream.lifecycle", "status", "completed", "mode", "FREE_QUESTION").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("ai.review.stream.lifecycle", "status", "disconnected", "mode", "FREE_QUESTION", "reason", "timeout").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("ai.review.stream.lifecycle", "status", "partial_failed", "mode", "FREE_QUESTION", "reason", "ollama_timeout").count())
                .isEqualTo(1.0);
    }

    @Test
    void firstTokenAndStreamDurationAreRecordedAsTimers() {
        sink.streamFirstToken("corr-1", 20L, 100L, "FREE_QUESTION", 123L);
        sink.streamCompleted("corr-1", 20L, 100L, "FREE_QUESTION", 123L, 456L, 2, 42);

        assertThat(registry.timer("ai.review.stream.first_token.latency", "mode", "FREE_QUESTION").count())
                .isEqualTo(1);
        assertThat(registry.timer("ai.review.stream.first_token.latency", "mode", "FREE_QUESTION").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(123.0);
        assertThat(registry.timer("ai.review.stream.duration", "status", "completed", "mode", "FREE_QUESTION").count())
                .isEqualTo(1);
        assertThat(registry.timer("ai.review.stream.duration", "status", "completed", "mode", "FREE_QUESTION").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(456.0);
    }

    @Test
    void fallbackToSyncIncrementsTaggedCounter() {
        sink.fallbackToSync("streaming_disabled", 20L, 100L, "FREE_QUESTION");

        assertThat(registry.counter("ai.review.fallback.sync", "reason", "streaming_disabled", "mode", "FREE_QUESTION").count())
                .isEqualTo(1.0);
    }
}

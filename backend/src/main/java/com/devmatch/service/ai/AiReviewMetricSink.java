package com.devmatch.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiReviewMetricSink {

    private static final Logger log = LoggerFactory.getLogger(AiReviewMetricSink.class);
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public AiReviewMetricSink(MeterRegistry registry) {
        this.registry = registry;
    }

    public void pythonEvent(String correlationId, String route, boolean fallbackUsed, boolean retrievalMiss, boolean candidateCaptured) {
        log.info(
                "metric.ai_review.python_event correlationId={} route={} fallbackUsed={} retrievalMiss={} candidateCaptured={}",
                correlationId,
                route,
                fallbackUsed,
                retrievalMiss,
                candidateCaptured
        );
    }

    public void candidateBacklog(String operation, long pending) {
        log.info("metric.ai_review.candidate_backlog operation={} pending={}", operation, pending);
    }

    public void ollamaGeneration(String model, boolean cacheHit, boolean success) {
        log.info("metric.ai_review.ollama_generation model={} cacheHit={} success={}", model, cacheHit, success);
    }

    public void streamFirstToken(String correlationId, Long sessionId, Long questionId, String mode, long firstTokenLatencyMs) {
        if (firstTokenLatencyMs >= 0) {
            timer("ai.review.stream.first_token.latency", "mode", tag(mode))
                    .record(Duration.ofMillis(firstTokenLatencyMs));
        }
        log.info(
                "metric.ai_review.stream_first_token correlationId={} sessionId={} questionId={} mode={} first_token_latency_ms={}",
                correlationId,
                sessionId,
                questionId,
                mode,
                firstTokenLatencyMs
        );
    }

    public void streamCompleted(
            String correlationId,
            Long sessionId,
            Long questionId,
            String mode,
            long firstTokenLatencyMs,
            long streamDurationMs,
            int chunkCount,
            int responseChars
    ) {
        counter("ai.review.stream.lifecycle", "status", "completed", "mode", tag(mode)).increment();
        recordDuration("completed", mode, null, streamDurationMs);
        log.info(
                "metric.ai_review.stream_completed correlationId={} sessionId={} questionId={} mode={} stream_completed=true first_token_latency_ms={} stream_duration_ms={} chunkCount={} responseChars={}",
                correlationId,
                sessionId,
                questionId,
                mode,
                firstTokenLatencyMs,
                streamDurationMs,
                chunkCount,
                responseChars
        );
    }

    public void streamDisconnected(
            String correlationId,
            Long sessionId,
            Long questionId,
            String mode,
            String reason,
            long firstTokenLatencyMs,
            long streamDurationMs,
            int responseChars
    ) {
        counter("ai.review.stream.lifecycle", "status", "disconnected", "mode", tag(mode), "reason", tag(reason)).increment();
        recordDuration("disconnected", mode, reason, streamDurationMs);
        log.info(
                "metric.ai_review.stream_disconnected correlationId={} sessionId={} questionId={} mode={} stream_disconnected=true reason={} first_token_latency_ms={} stream_duration_ms={} responseChars={}",
                correlationId,
                sessionId,
                questionId,
                mode,
                reason,
                firstTokenLatencyMs,
                streamDurationMs,
                responseChars
        );
    }

    public void streamPartialFailed(
            String correlationId,
            Long sessionId,
            Long questionId,
            String mode,
            String reason,
            long streamDurationMs,
            int chunkCount,
            int responseChars
    ) {
        counter("ai.review.stream.lifecycle", "status", "partial_failed", "mode", tag(mode), "reason", tag(reason)).increment();
        recordDuration("partial_failed", mode, reason, streamDurationMs);
        log.info(
                "metric.ai_review.stream_partial_failed correlationId={} sessionId={} questionId={} mode={} stream_partial_failed=true reason={} stream_duration_ms={} chunkCount={} responseChars={}",
                correlationId,
                sessionId,
                questionId,
                mode,
                reason,
                streamDurationMs,
                chunkCount,
                responseChars
        );
    }

    public void fallbackToSync(String reason, Long sessionId, Long questionId, String mode) {
        counter("ai.review.fallback.sync", "reason", tag(reason), "mode", tag(mode)).increment();
        log.info(
                "metric.ai_review.fallback_to_sync correlationId={} sessionId={} questionId={} mode={} reason={} fallback_to_sync_count=1",
                "n/a",
                sessionId,
                questionId,
                mode,
                reason
        );
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).publishPercentileHistogram().register(registry);
    }

    private void recordDuration(String status, String mode, String reason, long streamDurationMs) {
        if (streamDurationMs < 0) {
            return;
        }
        if (reason == null) {
            timer("ai.review.stream.duration", "status", status, "mode", tag(mode))
                    .record(Duration.ofMillis(streamDurationMs));
            return;
        }
        timer("ai.review.stream.duration", "status", status, "mode", tag(mode), "reason", tag(reason))
                .record(Duration.ofMillis(streamDurationMs));
    }

    private String tag(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}

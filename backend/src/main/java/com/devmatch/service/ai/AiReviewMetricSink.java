package com.devmatch.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiReviewMetricSink {

    private static final Logger log = LoggerFactory.getLogger(AiReviewMetricSink.class);

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

    public void streamMetric(
            String metricName,
            Long sessionId,
            Long userId,
            String mode,
            Long questionId,
            String clientRequestId,
            Long durationMs,
            String errorMessage
    ) {
        log.info(
                "metric.ai_review.stream_event metricName={} sessionId={} userId={} mode={} questionId={} clientRequestId={} durationMs={} errorMessage={}",
                metricName,
                sessionId,
                userId,
                mode,
                questionId,
                clientRequestId != null ? clientRequestId : "null",
                durationMs != null ? durationMs : 0L,
                errorMessage != null ? errorMessage : "none"
        );
    }
}


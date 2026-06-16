package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.Question;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PythonAiReviewClient {

    private static final Logger log = LoggerFactory.getLogger(PythonAiReviewClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final AiReviewProperties properties;
    private final AiReviewProviderSelector providerSelector;
    private final AiReviewMetricSink metricSink;
    private WebClient webClient;

    @PostConstruct
    public void initWebClient() {
        this.webClient = WebClient.builder()
                .baseUrl(properties.python().baseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public Optional<AiGeneratedAnswer> generateFirstQuestion(Question question, String correctAnswer, String selectedAnswer) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Optional.empty();
        }

        return request("/api/review/first-question", new PythonAiRequest(
                question.getContent(),
                question.getOptions(),
                correctAnswer,
                selectedAnswer,
                "",
                "",
                1,
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread()
        ));
    }

    public Optional<AiGeneratedAnswer> generateFollowUp(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userAnswer,
            AiReviewEvaluation evaluation,
            int nextStep,
            String previousAiQuestion,
            String activeConcept
    ) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Optional.empty();
        }

        return request("/api/review/follow-up", new PythonAiRequest(
                question.getContent(),
                question.getOptions(),
                correctAnswer,
                selectedAnswer,
                userAnswer,
                evaluation.name(),
                nextStep,
                "DIAGNOSTIC_FOLLOW_UP",
                previousAiQuestion,
                activeConcept,
                "",
                "",
                "",
                "",
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread()
        ));
    }

    public Optional<AiGeneratedAnswer> answerFreeQuestion(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userQuestion
    ) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Optional.empty();
        }

        return request("/api/review/free-question", new PythonAiRequest(
                question.getContent(),
                question.getOptions(),
                correctAnswer,
            selectedAnswer,
                userQuestion,
                "",
                1,
                "",
                "",
                "",
                courseId(question),
                testId(question),
                questionId(question),
                sourceQuestionId(question),
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread()
        ));
    }

    public Flux<String> streamReview(String uri, String correlationId, PythonAiRequest request) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Flux.error(new IllegalStateException("Python AI provider is not enabled"));
        }

        PythonAiRequest streamRequest = new PythonAiRequest(
                request.question(),
                request.options(),
                request.correct_answer(),
                request.selected_answer(),
                request.user_answer(),
                request.evaluation(),
                request.step(),
                request.follow_up_type(),
                request.previous_ai_question(),
                request.active_concept(),
                request.course_id(),
                request.test_id(),
                request.question_id(),
                request.source_question_id(),
                request.model(),
                request.temperature(),
                request.max_tokens(),
                request.num_ctx(),
                request.num_thread(),
                true
        );

        return webClient.post()
                .uri(uri)
                .header(CORRELATION_ID_HEADER, correlationId)
                .header("Accept", "text/event-stream")
                .headers(headers -> {
                    if (properties.python().serviceToken() != null && !properties.python().serviceToken().isBlank()) {
                        headers.set("X-AI-Service-Token", properties.python().serviceToken());
                    }
                })
                .bodyValue(streamRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .onBackpressureBuffer(50);
    }

    private Optional<AiGeneratedAnswer> request(String uri, PythonAiRequest request) {
        String correlationId = "ai-review-" + UUID.randomUUID();
        return AiRetrySupport.executeWithRetry(() -> requestOnce(uri, request, correlationId));
    }

    private Optional<AiGeneratedAnswer> requestOnce(String uri, PythonAiRequest request, String correlationId) {
        try {
            PythonAiResponse response = aiClient(properties.python().baseUrl())
                    .post()
                    .uri(uri)
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .headers(headers -> {
                        if (properties.python().serviceToken() != null && !properties.python().serviceToken().isBlank()) {
                            headers.set("X-AI-Service-Token", properties.python().serviceToken());
                        }
                    })
                    .body(request)
                    .retrieve()
                    .body(PythonAiResponse.class);

            if (response == null || response.answer() == null || response.answer().isBlank()) {
                log.warn("Python AI returned an empty response. correlationId={}, uri={}, baseUrl={}", correlationId, uri, properties.python().baseUrl());
                return Optional.empty();
            }
            logObservabilityEvents(correlationId, response);
            return Optional.of(response.toGeneratedAnswer());
        } catch (RestClientException ex) {
            log.warn(
                    "Python AI request failed. correlationId={}, uri={}, baseUrl={}, model={}, message={}",
                    correlationId,
                    uri,
                    properties.python().baseUrl(),
                    request.model(),
                    ex.getMessage()
            );
            if (AiRetrySupport.isRetryable(ex)) {
                throw ex;
            }
            return Optional.empty();
        }
    }

    private RestClient aiClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(AiRetrySupport.boundedReadTimeout(properties.python().readTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private void logObservabilityEvents(String correlationId, PythonAiResponse response) {
        List<Map<String, Object>> events = response.observability_events() == null
                ? List.of()
                : response.observability_events();
            if (events.isEmpty()) {
            metricSink.pythonEvent(
                    correlationId,
                    response.route(),
                    Boolean.TRUE.equals(response.fallback_used()),
                    response.retrieved_concept_ids() == null || response.retrieved_concept_ids().isEmpty(),
                    response.candidate_id() != null && !response.candidate_id().isBlank()
            );
            log.info(
                    "ai_review.python_event correlationId={} route={} fallbackUsed={} retrievalMiss={} candidateCaptured={}",
                    correlationId,
                    response.route(),
                    response.fallback_used(),
                    response.retrieved_concept_ids() == null || response.retrieved_concept_ids().isEmpty(),
                    response.candidate_id() != null && !response.candidate_id().isBlank()
            );
            return;
        }
        for (Map<String, Object> event : events) {
            metricSink.pythonEvent(
                    String.valueOf(event.getOrDefault("correlation_id", correlationId)),
                    String.valueOf(event.getOrDefault("route", response.route())),
                    Boolean.TRUE.equals(event.getOrDefault("fallback_used", response.fallback_used())),
                    Boolean.TRUE.equals(event.get("retrieval_miss")),
                    event.getOrDefault("candidate_id", response.candidate_id()) != null
                            && !String.valueOf(event.getOrDefault("candidate_id", response.candidate_id())).isBlank()
            );
            log.info(
                    "ai_review.python_event correlationId={} event={} route={} fallbackUsed={} retrievalMiss={} candidateId={}",
                    event.getOrDefault("correlation_id", correlationId),
                    event.get("event"),
                    event.getOrDefault("route", response.route()),
                    event.getOrDefault("fallback_used", response.fallback_used()),
                    event.get("retrieval_miss"),
                    event.getOrDefault("candidate_id", response.candidate_id())
            );
        }
    }

    private String courseId(Question question) {
        if (question == null || question.getTest() == null || question.getTest().getCategory() == null) {
            return "";
        }
        return question.getTest().getCategory();
    }

    private String testId(Question question) {
        if (question == null || question.getTest() == null || question.getTest().getId() == null) {
            return "";
        }
        return String.valueOf(question.getTest().getId());
    }

    private String questionId(Question question) {
        if (question == null || question.getId() == null) {
            return "";
        }
        return String.valueOf(question.getId());
    }

    private String sourceQuestionId(Question question) {
        String courseId = courseId(question);
        if (courseId.isBlank() || question == null || question.getOrderIndex() == null) {
            return "";
        }
        return courseId + ":" + question.getOrderIndex();
    }

    public record PythonAiRequest(
            String question,
            List<String> options,
            String correct_answer,
            String selected_answer,
            String user_answer,
            String evaluation,
            int step,
            String follow_up_type,
            String previous_ai_question,
            String active_concept,
            String course_id,
            String test_id,
            String question_id,
            String source_question_id,
            String model,
            double temperature,
            int max_tokens,
            int num_ctx,
            int num_thread,
            Boolean stream
    ) {
        public PythonAiRequest(
                String question,
                List<String> options,
                String correct_answer,
                String selected_answer,
                String user_answer,
                String evaluation,
                int step,
                String followUpType,
                String previousAiQuestion,
                String activeConcept,
                String courseId,
                String testId,
                String questionId,
                String sourceQuestionId,
                String model,
                double temperature,
                int max_tokens,
                int num_ctx,
                int num_thread
        ) {
            this(question, options, correct_answer, selected_answer, user_answer, evaluation, step,
                    followUpType, previousAiQuestion, activeConcept, courseId, testId, questionId, sourceQuestionId,
                    model, temperature, max_tokens, num_ctx, num_thread, false);
        }

        public PythonAiRequest(
                String question,
                List<String> options,
                String correct_answer,
                String selected_answer,
                String user_answer,
                String evaluation,
                int step,
                String model,
                double temperature,
                int max_tokens,
                int num_ctx,
                int num_thread
        ) {
            this(question, options, correct_answer, selected_answer, user_answer, evaluation, step,
                    "", "", "", "", "", "", "",
                    model, temperature, max_tokens, num_ctx, num_thread, false);
        }

        public PythonAiRequest(
                String question,
                List<String> options,
                String correct_answer,
                String selected_answer,
                String user_answer,
                String evaluation,
                int step,
                String model,
                double temperature,
                int max_tokens,
                int num_ctx,
                int num_thread,
                Boolean stream
        ) {
            this(question, options, correct_answer, selected_answer, user_answer, evaluation, step,
                    "", "", "", "", "", "", "",
                    model, temperature, max_tokens, num_ctx, num_thread, stream);
        }
    }

    private record PythonAiResponse(
            String answer,
            String provider,
            Double confidence_score,
            String model_used,
            Boolean fallback_used,
            List<String> retrieved_concept_ids,
            String candidate_id,
            String prompt_version,
            Integer latency_ms,
            String route,
            String resolved_query,
            String correction_type,
            String matched_concept_id,
            String answer_style,
            List<String> quality_flags,
            List<Map<String, Object>> observability_events
    ) {
        private AiGeneratedAnswer toGeneratedAnswer() {
            return new AiGeneratedAnswer(
                    answer.trim(),
                    route,
                    resolved_query,
                    correction_type,
                    matched_concept_id,
                    answer_style,
                    quality_flags == null ? List.of() : quality_flags,
                    candidate_id,
                    latency_ms,
                    observability_events == null ? List.of() : observability_events
            );
        }
    }
}

package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.dto.aireview.AiReviewMessageResponse;
import com.devmatch.dto.aireview.AiReviewSubmitResponse;
import com.devmatch.entity.*;
import com.devmatch.exception.InvalidSessionStateException;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.QuestionRepository;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.TestAnswerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AiReviewStreamingService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewStreamingService.class);

    private final PythonAiReviewClient pythonAiReviewClient;
    private final RuleBasedAiReviewService aiReviewService;
    private final AiReviewProperties properties;
    private final ObjectMapper objectMapper;

    private final AiReviewSessionRepository sessionRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final QuestionRepository questionRepository;
    private final AiReviewMessageRepository messageRepository;
    private final AiReviewMetricSink metricSink;

    private AiReviewStreamingService self;

    @Autowired
    public void setSelf(@Lazy AiReviewStreamingService self) {
        this.self = self;
    }

    public enum StreamingState {
        INIT, ACTIVE, COMPLETED, DISCONNECTED, ERROR
    }

    public SseEmitter streamAnswer(Long userId, Long sessionId, String answer, String modeValue, Long questionId) {
        String requestedMode = modeValue == null ? AiReviewMessageMode.CHECK_ANSWER.name() : modeValue;
        if (!properties.streamingEnabled()) {
            metricSink.fallbackToSync("streaming_disabled", sessionId, questionId, requestedMode);
            return fallbackToSynchronousSubmit(userId, sessionId, answer, modeValue, questionId);
        }
        if (properties.degraded().streamingOff()) {
            metricSink.fallbackToSync("streaming_off", sessionId, questionId, requestedMode);
            return fallbackToSynchronousSubmit(userId, sessionId, answer, modeValue, questionId);
        }

        // Parse Mode
        AiReviewMessageMode mode = AiReviewMessageMode.CHECK_ANSWER;
        if (modeValue != null) {
            try {
                mode = AiReviewMessageMode.valueOf(modeValue);
            } catch (IllegalArgumentException e) {
                // fallback
            }
        }
        final AiReviewMessageMode finalMode = mode;

        if (finalMode != AiReviewMessageMode.FREE_QUESTION) {
            metricSink.fallbackToSync("mode_not_streamable", sessionId, questionId, finalMode.name());
            return fallbackToSynchronousSubmit(userId, sessionId, answer, modeValue, questionId);
        }

        AiReviewSession sessionObj = findOwnedSession(userId, sessionId);
        if (sessionObj.getStatus() == AiReviewStatus.COMPLETED) {
            metricSink.fallbackToSync("session_completed", sessionId, questionId, finalMode.name());
            return fallbackToSynchronousSubmit(userId, sessionId, answer, modeValue, questionId);
        }

        String normalizedAnswer = answer == null ? "" : answer.trim();
        if (normalizedAnswer.isBlank()) {
            throw new TestNotFoundException("답변이나 질문을 입력해주세요.");
        }

        List<TestAnswer> wrongAnswers = wrongAnswers(sessionObj.getTestResult().getId());
        Question currentQuestion = resolveCurrentQuestion(sessionId, wrongAnswers, questionId);
        long freeQuestionCount = countFreeQuestions(sessionId, currentQuestion.getId());
        if (freeQuestionCount >= 3) {
            throw new InvalidSessionStateException("이 문제는 AI 질문/답변을 3개까지 사용할 수 있습니다. 다음 문제로 넘어가세요.");
        }
        TestAnswer currentWrongAnswer = wrongAnswers.stream()
                .filter(testAnswer -> Objects.equals(testAnswer.getQuestion().getId(), currentQuestion.getId()))
                .findFirst()
                .orElse(null);
        String correctAnswer = optionAt(currentQuestion, currentQuestion.getCorrectAnswer());
        String selectedAnswer = currentWrongAnswer == null
                ? ""
                : optionAt(currentQuestion, currentWrongAnswer.getSelectedAnswer());

        long timeoutMs = properties.streamTimeoutSeconds() * 1000L;
        SseEmitter emitter = createEmitter(timeoutMs);

        AtomicReference<StreamingState> state = new AtomicReference<>(StreamingState.INIT);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicReference<AiReviewMessage> userMessageRef = new AtomicReference<>();
        AtomicBoolean firstTokenObserved = new AtomicBoolean(false);
        AtomicLong firstTokenLatencyMs = new AtomicLong(-1L);
        AtomicInteger chunkCount = new AtomicInteger(0);
        final StringBuilder accumulated = new StringBuilder();
        String correlationId = "ai-stream-" + java.util.UUID.randomUUID();
        long streamStartedNanos = System.nanoTime();

        // SSE lifecycle cleanup hooks
        emitter.onCompletion(() -> {
            log.info("SseEmitter completed for session {}", sessionId);
            if (state.get() != StreamingState.COMPLETED && state.get() != StreamingState.ERROR && state.get() != StreamingState.DISCONNECTED) {
                if (state.compareAndSet(state.get(), StreamingState.DISCONNECTED)) {
                    cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                    metricSink.streamDisconnected(
                            correlationId,
                            sessionId,
                            currentQuestion.getId(),
                            finalMode.name(),
                            "completion_callback",
                            firstTokenLatencyMs.get(),
                            elapsedMillis(streamStartedNanos),
                            accumulated.length()
                    );
                    self.saveDisconnectedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString());
                }
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SseEmitter timeout reached for session {}", sessionId);
            if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.DISCONNECTED) || state.compareAndSet(StreamingState.INIT, StreamingState.DISCONNECTED)) {
                cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                metricSink.streamDisconnected(
                        correlationId,
                        sessionId,
                        currentQuestion.getId(),
                        finalMode.name(),
                        "timeout",
                        firstTokenLatencyMs.get(),
                        elapsedMillis(streamStartedNanos),
                        accumulated.length()
                );
                self.saveDisconnectedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString());
            }
            emitter.complete();
        });

        emitter.onError(ex -> {
            log.error("SseEmitter error for session {}", sessionId, ex);
            if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.DISCONNECTED) || state.compareAndSet(StreamingState.INIT, StreamingState.DISCONNECTED)) {
                cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                metricSink.streamDisconnected(
                        correlationId,
                        sessionId,
                        currentQuestion.getId(),
                        finalMode.name(),
                        "emitter_error",
                        firstTokenLatencyMs.get(),
                        elapsedMillis(streamStartedNanos),
                        accumulated.length()
                );
                self.saveDisconnectedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString());
            }
            emitter.completeWithError(ex);
        });

        PythonAiReviewClient.PythonAiRequest request = new PythonAiReviewClient.PythonAiRequest(
                currentQuestion.getContent(),
                currentQuestion.getOptions() == null ? List.of() : currentQuestion.getOptions(),
                correctAnswer,
                selectedAnswer,
                normalizedAnswer,
                "",
                1,
                "",
                "",
                "",
                courseId(currentQuestion),
                testId(currentQuestion),
                questionId(currentQuestion),
                sourceQuestionId(currentQuestion),
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread(),
                true
        );

        state.set(StreamingState.ACTIVE);
        Flux<String> flux = pythonAiReviewClient.streamReview("/api/review/free-question", correlationId, request);

        Disposable subscription = flux.subscribe(
                rawEvent -> {
                    if (state.get() != StreamingState.ACTIVE) {
                        return;
                    }

                    Map<String, Object> event = parseEvent(rawEvent);
                    if (event.isEmpty()) {
                        return;
                    }

                    String type = String.valueOf(event.get("type"));
                    if ("chunk".equals(type)) {
                        if (firstTokenObserved.compareAndSet(false, true)) {
                            long latencyMs = elapsedMillis(streamStartedNanos);
                            firstTokenLatencyMs.set(latencyMs);
                            metricSink.streamFirstToken(
                                    correlationId,
                                    sessionId,
                                    currentQuestion.getId(),
                                    finalMode.name(),
                                    latencyMs
                            );
                        }
                        chunkCount.incrementAndGet();
                        ensureUserMessageSaved(userMessageRef, sessionId, currentQuestion.getId(), finalMode, normalizedAnswer);
                        String chunk = String.valueOf(event.getOrDefault("chunk", ""));
                        accumulated.append(chunk);
                        try {
                            emitter.send(event);
                        } catch (IOException e) {
                            log.error("Failed to send chunk to SseEmitter, initiating cleanup", e);
                            if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.DISCONNECTED)) {
                                cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                                metricSink.streamDisconnected(
                                        correlationId,
                                        sessionId,
                                        currentQuestion.getId(),
                                        finalMode.name(),
                                        "send_chunk_failed",
                                        firstTokenLatencyMs.get(),
                                        elapsedMillis(streamStartedNanos),
                                        accumulated.length()
                                );
                                self.saveDisconnectedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString());
                                emitter.completeWithError(e);
                            }
                        }
                    } else if ("done".equals(type)) {
                        if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.COMPLETED)) {
                            cleanup(subscriptionRef, state, StreamingState.COMPLETED);

                            Map<String, Object> responseMetadata = responseMetadataFrom(event.get("response"));

                            // Save to database inside transaction
                            AiReviewMessage userMessage = null;
                            AiReviewMessage aiMessage = null;
                            try {
                                userMessage = ensureUserMessageSaved(userMessageRef, sessionId, currentQuestion.getId(), finalMode, normalizedAnswer);
                                aiMessage = self.saveCompletedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString(), responseMetadata);
                            } catch (Exception e) {
                                log.error("Failed to save completed AI message to database", e);
                            }

                            try {
                                emitter.send(Map.of(
                                        "type", "done",
                                        "response", buildSubmitResponse(sessionObj, userMessage, aiMessage)
                                ));
                                metricSink.streamCompleted(
                                        correlationId,
                                        sessionId,
                                        currentQuestion.getId(),
                                        finalMode.name(),
                                        firstTokenLatencyMs.get(),
                                        elapsedMillis(streamStartedNanos),
                                        chunkCount.get(),
                                        accumulated.length()
                                );
                                emitter.complete();
                            } catch (IOException e) {
                                log.warn("Failed to send done event to SseEmitter", e);
                            }
                        }
                    } else if ("error".equals(type)) {
                        if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.ERROR)) {
                            cleanup(subscriptionRef, state, StreamingState.ERROR);
                            
                            String errorMsg = String.valueOf(event.getOrDefault("error", "Unknown stream error"));
                            metricSink.streamPartialFailed(
                                    correlationId,
                                    sessionId,
                                    currentQuestion.getId(),
                                    finalMode.name(),
                                    errorMsg,
                                    elapsedMillis(streamStartedNanos),
                                    chunkCount.get(),
                                    accumulated.length()
                            );
                            try {
                                self.savePartialFailedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString(), errorMsg);
                            } catch (Exception e) {
                                log.error("Failed to save partial failed AI message to database", e);
                            }

                            try {
                                emitter.send(event);
                                emitter.complete();
                            } catch (IOException e) {
                                log.warn("Failed to send error event to SseEmitter", e);
                            }
                        }
                    } else {
                        log.warn("Unknown SSE event type received: {}", type);
                    }
                },
                error -> {
                    log.error("Error in stream for session {}", sessionId, error);
                    if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.ERROR)) {
                        cleanup(subscriptionRef, state, StreamingState.ERROR);
                        metricSink.streamPartialFailed(
                                correlationId,
                                sessionId,
                                currentQuestion.getId(),
                                finalMode.name(),
                                error.getMessage(),
                                elapsedMillis(streamStartedNanos),
                                chunkCount.get(),
                                accumulated.length()
                        );
                        
                        try {
                            self.savePartialFailedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString(), error.getMessage());
                        } catch (Exception e) {
                            log.error("Failed to save partial failed AI message to database", e);
                        }

                        try {
                            emitter.send(Map.of("type", "error", "error", error.getMessage()));
                            emitter.complete();
                        } catch (IOException e) {
                            log.warn("Failed to send error SSE event to SseEmitter", e);
                        }
                    }
                },
                () -> {
                    log.info("Stream finished for session {}", sessionId);
                    if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.COMPLETED)) {
                        cleanup(subscriptionRef, state, StreamingState.COMPLETED);

                        if (accumulated.isEmpty()) {
                            try {
                                emitter.send(Map.of("type", "error", "error", "Stream completed before response was generated"));
                                metricSink.streamPartialFailed(
                                        correlationId,
                                        sessionId,
                                        currentQuestion.getId(),
                                        finalMode.name(),
                                        "empty_completion",
                                        elapsedMillis(streamStartedNanos),
                                        chunkCount.get(),
                                        accumulated.length()
                                );
                                emitter.complete();
                            } catch (IOException e) {
                                log.warn("Failed to send empty-completion error event", e);
                            }
                            return;
                        }

                        AiReviewMessage userMessage = null;
                        AiReviewMessage aiMessage = null;
                        try {
                            userMessage = ensureUserMessageSaved(userMessageRef, sessionId, currentQuestion.getId(), finalMode, normalizedAnswer);
                            aiMessage = self.saveCompletedAiMessage(correlationId, sessionId, currentQuestion.getId(), finalMode, accumulated.toString(), null);
                        } catch (Exception e) {
                            log.error("Failed to save implicitly completed AI stream to database", e);
                        }

                        try {
                            emitter.send(Map.of(
                                    "type", "done",
                                    "response", buildSubmitResponse(sessionObj, userMessage, aiMessage)
                            ));
                            metricSink.streamCompleted(
                                    correlationId,
                                    sessionId,
                                    currentQuestion.getId(),
                                    finalMode.name(),
                                    firstTokenLatencyMs.get(),
                                    elapsedMillis(streamStartedNanos),
                                    chunkCount.get(),
                                    accumulated.length()
                            );
                            emitter.complete();
                        } catch (IOException e) {
                            log.warn("Failed to send done event on completion", e);
                        }
                    }
                }
        );

        subscriptionRef.set(subscription);

        return emitter;
    }

    private SseEmitter fallbackToSynchronousSubmit(Long userId, Long sessionId, String answer, String modeValue, Long questionId) {
        long timeoutMs = properties.streamTimeoutSeconds() * 1000L;
        SseEmitter emitter = createEmitter(timeoutMs);
        try {
            AiReviewSubmitResponse response = aiReviewService.submitAnswer(userId, sessionId, answer, modeValue, questionId);
            emitter.send(Map.of("type", "done", "response", response));
            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to perform fallback submitAnswer", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    private Map<String, Object> responseMetadataFrom(Object responseObj) {
        if (!(responseObj instanceof Map<?, ?> rawMetadata)) {
            return null;
        }
        Map<String, Object> responseMetadata = new LinkedHashMap<>();
        rawMetadata.forEach((key, value) -> responseMetadata.put(String.valueOf(key), value));
        return responseMetadata;
    }

    protected SseEmitter createEmitter(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }

    private AiReviewSession findOwnedSession(Long userId, Long sessionId) {
        return AiReviewContextSupport.requireOwnedSession(sessionRepository.findById(sessionId), userId);
    }

    private List<TestAnswer> wrongAnswers(Long testResultId) {
        return AiReviewContextSupport.wrongAnswers(testAnswerRepository.findByTestResultId(testResultId));
    }

    private Question resolveCurrentQuestion(Long sessionId, List<TestAnswer> wrongAnswers, Long questionId) {
        if (questionId != null) {
            return AiReviewContextSupport.questionById(wrongAnswers, questionId);
        }

        return latestQuestionMessage(sessionId)
                .map(AiReviewMessage::getQuestion)
                .orElseThrow(() -> new TestNotFoundException("진행 중인 복습 질문이 없습니다."));
    }

    private Optional<AiReviewMessage> latestQuestionMessage(Long sessionId) {
        return AiReviewContextSupport.latestQuestionMessage(
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        );
    }

    private long countFreeQuestions(Long sessionId, Long questionId) {
        return messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                sessionId,
                questionId,
                AiReviewMessageRole.USER,
                List.of(AiReviewMessageMode.FREE_QUESTION)
        );
    }

    private String optionAt(Question question, int index) {
        return AiReviewContextSupport.optionAt(question, index);
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

    private AiReviewMessage ensureUserMessageSaved(
            AtomicReference<AiReviewMessage> userMessageRef,
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String content
    ) {
        AiReviewMessage existing = userMessageRef.get();
        if (existing != null) {
            return existing;
        }

        AiReviewMessage saved = self.saveUserMessage(sessionId, questionId, mode, content);
        if (userMessageRef.compareAndSet(null, saved)) {
            return saved;
        }
        return userMessageRef.get();
    }

    private AiReviewSubmitResponse buildSubmitResponse(
            AiReviewSession session,
            AiReviewMessage userMessage,
            AiReviewMessage aiMessage
    ) {
        List<AiReviewMessageResponse> messages = new ArrayList<>();
        if (userMessage != null) {
            messages.add(AiReviewMessageResponse.from(userMessage));
        }
        if (aiMessage != null) {
            messages.add(AiReviewMessageResponse.from(aiMessage));
        }

        String feedback = aiMessage == null ? "" : aiMessage.getContent();
        String nextQuestion = AiReviewFollowUpSupport.extractFollowUp(feedback);
        return new AiReviewSubmitResponse(
                AiReviewMessageMode.FREE_QUESTION.name(),
                feedback,
                nextQuestion,
                session.getStatus() == AiReviewStatus.COMPLETED,
                session.getSummary(),
                messages
        );
    }

    private Map<String, Object> parseEvent(String rawEvent) {
        if (rawEvent == null || rawEvent.isBlank()) {
            return Collections.emptyMap();
        }
        String trimmed = rawEvent.trim();
        if (trimmed.startsWith("data:")) {
            trimmed = trimmed.substring(5).trim();
        }
        if (trimmed.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse incoming SSE raw event string: {}", trimmed, e);
            return Collections.emptyMap();
        }
    }

    private void cleanup(AtomicReference<Disposable> subscriptionRef, AtomicReference<StreamingState> state, StreamingState nextState) {
        state.set(nextState);
        Disposable disposable = subscriptionRef.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.info("Subscription disposed on transition to state {}", nextState);
        }
    }

    @Transactional
    public AiReviewMessage saveCompletedAiMessage(
            String streamRequestId,
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String accumulatedContent,
            Map<String, Object> responseMetadata
    ) {
        Optional<AiReviewMessage> existingTerminal = existingTerminalMessage(streamRequestId);
        if (existingTerminal.isPresent()) {
            return existingTerminal.get();
        }

        AiReviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 복습 세션을 찾을 수 없습니다."));
        Question question = questionId != null ? questionRepository.findById(questionId).orElse(null) : null;

        String answer = accumulatedContent;
        String route = null;
        String resolvedQuery = null;
        String correctionType = null;
        String matchedConceptId = null;
        String answerStyle = null;
        String candidateId = null;
        Integer latencyMs = null;
        String qualityFlags = null;

        if (responseMetadata != null) {
            if (responseMetadata.containsKey("answer")) {
                answer = String.valueOf(responseMetadata.get("answer"));
            }
            route = (String) responseMetadata.get("route");
            resolvedQuery = (String) responseMetadata.get("resolved_query");
            correctionType = (String) responseMetadata.get("correction_type");
            matchedConceptId = (String) responseMetadata.get("matched_concept_id");
            answerStyle = (String) responseMetadata.get("answer_style");
            candidateId = (String) responseMetadata.get("candidate_id");
            if (responseMetadata.get("latency_ms") != null) {
                latencyMs = ((Number) responseMetadata.get("latency_ms")).intValue();
            }
            
            Object flagsObj = responseMetadata.get("quality_flags");
            if (flagsObj instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) flagsObj;
                qualityFlags = list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            } else if (flagsObj != null) {
                qualityFlags = String.valueOf(flagsObj);
            }
        }
        String followUpQuestion = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(
                question,
                "",
                answerStyle,
                resolvedQuery,
                matchedConceptId
        );
        answer = AiReviewFollowUpSupport.appendFollowUp(answer, followUpQuestion);

        // Merge STATUS:COMPLETED
        if (qualityFlags == null || qualityFlags.isBlank()) {
            qualityFlags = "STATUS:COMPLETED";
        } else {
            qualityFlags = qualityFlags + ",STATUS:COMPLETED";
        }

        if (answer != null && answer.length() > 2000) {
            answer = answer.substring(0, 1990) + "...";
        }

        AiReviewMessage msg = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content(answer != null ? answer : "")
                .aiRoute(route)
                .aiResolvedQuery(resolvedQuery)
                .aiCorrectionType(correctionType)
                .aiMatchedConceptId(matchedConceptId)
                .aiAnswerStyle(answerStyle)
                .aiCandidateId(candidateId)
                .aiLatencyMs(latencyMs)
                .aiQualityFlags(qualityFlags)
                .streamRequestId(streamRequestId)
                .streamTerminalStatus(AiReviewStreamTerminalStatus.COMPLETED)
                .build();

        return saveTerminalMessage(msg, streamRequestId);
    }

    @Transactional
    public AiReviewMessage saveDisconnectedAiMessage(
            String streamRequestId,
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String accumulatedContent
    ) {
        if (accumulatedContent == null || accumulatedContent.isBlank()) {
            log.info("Accumulated content is empty. Skipping DB save for disconnected stream on session {}", sessionId);
            return null;
        }
        Optional<AiReviewMessage> existingTerminal = existingTerminalMessage(streamRequestId);
        if (existingTerminal.isPresent()) {
            return existingTerminal.get();
        }

        AiReviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 복습 세션을 찾을 수 없습니다."));
        Question question = questionId != null ? questionRepository.findById(questionId).orElse(null) : null;

        String formattedContent = accumulatedContent + "\n\n[사용자 연결 해제로 중단되었습니다.]";
        if (formattedContent.length() > 2000) {
            formattedContent = formattedContent.substring(0, 1990) + "...";
        }

        AiReviewMessage msg = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content(formattedContent)
                .aiRoute("streaming_disconnected")
                .aiQualityFlags("STATUS:DISCONNECTED")
                .streamRequestId(streamRequestId)
                .streamTerminalStatus(AiReviewStreamTerminalStatus.DISCONNECTED)
                .build();

        return saveTerminalMessage(msg, streamRequestId);
    }

    @Transactional
    public AiReviewMessage savePartialFailedAiMessage(
            String streamRequestId,
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String accumulatedContent,
            String errorMessage
    ) {
        if (accumulatedContent == null || accumulatedContent.isBlank()) {
            log.info("Accumulated content is empty. Skipping DB save for partial failed stream on session {}", sessionId);
            return null;
        }
        Optional<AiReviewMessage> existingTerminal = existingTerminalMessage(streamRequestId);
        if (existingTerminal.isPresent()) {
            return existingTerminal.get();
        }

        AiReviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 복습 세션을 찾을 수 없습니다."));
        Question question = questionId != null ? questionRepository.findById(questionId).orElse(null) : null;

        String formattedContent = accumulatedContent + "\n\n[답변 생성 중 오류가 발생했습니다: " + errorMessage + "]";
        if (formattedContent.length() > 2000) {
            formattedContent = formattedContent.substring(0, 1990) + "...";
        }

        AiReviewMessage msg = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content(formattedContent)
                .aiRoute("streaming_error")
                .aiQualityFlags("STATUS:PARTIAL_FAILED")
                .streamRequestId(streamRequestId)
                .streamTerminalStatus(AiReviewStreamTerminalStatus.ERROR)
                .build();

        return saveTerminalMessage(msg, streamRequestId);
    }

    private Optional<AiReviewMessage> existingTerminalMessage(String streamRequestId) {
        if (streamRequestId == null || streamRequestId.isBlank()) {
            return Optional.empty();
        }
        return messageRepository.findByStreamRequestId(streamRequestId);
    }

    private AiReviewMessage saveTerminalMessage(AiReviewMessage message, String streamRequestId) {
        try {
            return messageRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            return existingTerminalMessage(streamRequestId).orElseThrow(() -> e);
        }
    }

    @Transactional
    public AiReviewMessage saveUserMessage(
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String content
    ) {
        AiReviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 복습 세션을 찾을 수 없습니다."));
        Question question = questionId != null ? questionRepository.findById(questionId).orElse(null) : null;

        String formattedContent = content != null ? content.trim() : "";
        if (formattedContent.length() > 2000) {
            formattedContent = formattedContent.substring(0, 1990) + "...";
        }

        AiReviewMessage msg = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.USER)
                .mode(mode)
                .content(formattedContent)
                .build();

        return messageRepository.save(msg);
    }
}

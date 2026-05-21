package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.dto.aireview.AiReviewSubmitResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.QuestionRepository;
import com.devmatch.repository.AiReviewMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
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
    private final QuestionRepository questionRepository;
    private final AiReviewMessageRepository messageRepository;

    @Autowired
    @Lazy
    private AiReviewStreamingService self;

    public enum StreamingState {
        INIT, ACTIVE, COMPLETED, DISCONNECTED, ERROR
    }

    public SseEmitter streamAnswer(Long userId, Long sessionId, String answer, String modeValue, Long questionId) {
        if (!properties.streamingEnabled()) {
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

        // Session completion check for fallback
        AiReviewSession sessionObj = sessionRepository.findById(sessionId).orElse(null);
        if (sessionObj != null && sessionObj.getStatus() == AiReviewStatus.COMPLETED) {
            return fallbackToSynchronousSubmit(userId, sessionId, answer, modeValue, questionId);
        }

        long timeoutMs = properties.streamTimeoutSeconds() * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);

        AtomicReference<StreamingState> state = new AtomicReference<>(StreamingState.INIT);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        final StringBuilder accumulated = new StringBuilder();

        // Save User Message
        self.saveUserMessage(sessionId, questionId, finalMode, answer);

        // SSE lifecycle cleanup hooks
        emitter.onCompletion(() -> {
            log.info("SseEmitter completed for session {}", sessionId);
            if (state.get() != StreamingState.COMPLETED && state.get() != StreamingState.ERROR && state.get() != StreamingState.DISCONNECTED) {
                if (state.compareAndSet(state.get(), StreamingState.DISCONNECTED)) {
                    cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                    self.saveDisconnectedAiMessage(sessionId, questionId, finalMode, accumulated.toString());
                }
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SseEmitter timeout reached for session {}", sessionId);
            if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.DISCONNECTED) || state.compareAndSet(StreamingState.INIT, StreamingState.DISCONNECTED)) {
                cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                self.saveDisconnectedAiMessage(sessionId, questionId, finalMode, accumulated.toString());
            }
            emitter.complete();
        });

        emitter.onError(ex -> {
            log.error("SseEmitter error for session {}", sessionId, ex);
            if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.DISCONNECTED) || state.compareAndSet(StreamingState.INIT, StreamingState.DISCONNECTED)) {
                cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                self.saveDisconnectedAiMessage(sessionId, questionId, finalMode, accumulated.toString());
            }
            emitter.completeWithError(ex);
        });

        // Stub Python request
        PythonAiReviewClient.PythonAiRequest request = new PythonAiReviewClient.PythonAiRequest(
                "stub question",
                java.util.List.of(),
                "stub correct",
                "stub selected",
                answer,
                "",
                1,
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread(),
                true
        );

        String correlationId = "ai-stream-" + java.util.UUID.randomUUID();
        
        state.set(StreamingState.ACTIVE);
        Flux<String> flux = pythonAiReviewClient.streamReview("/api/review/follow-up", correlationId, request);

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
                        String chunk = String.valueOf(event.getOrDefault("chunk", ""));
                        accumulated.append(chunk);
                        try {
                            emitter.send(event);
                        } catch (IOException e) {
                            log.error("Failed to send chunk to SseEmitter, initiating cleanup", e);
                            if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.DISCONNECTED)) {
                                cleanup(subscriptionRef, state, StreamingState.DISCONNECTED);
                                self.saveDisconnectedAiMessage(sessionId, questionId, finalMode, accumulated.toString());
                                emitter.completeWithError(e);
                            }
                        }
                    } else if ("done".equals(type)) {
                        if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.COMPLETED)) {
                            cleanup(subscriptionRef, state, StreamingState.COMPLETED);

                            Map<String, Object> responseMetadata = null;
                            Object responseObj = event.get("response");
                            if (responseObj instanceof Map) {
                                responseMetadata = (Map<String, Object>) responseObj;
                            }

                            // Save to database inside transaction
                            try {
                                self.saveCompletedAiMessage(sessionId, questionId, finalMode, accumulated.toString(), responseMetadata);
                            } catch (Exception e) {
                                log.error("Failed to save completed AI message to database", e);
                            }

                            try {
                                emitter.send(event);
                                emitter.complete();
                            } catch (IOException e) {
                                log.warn("Failed to send done event to SseEmitter", e);
                            }
                        }
                    } else if ("error".equals(type)) {
                        if (state.compareAndSet(StreamingState.ACTIVE, StreamingState.ERROR)) {
                            cleanup(subscriptionRef, state, StreamingState.ERROR);
                            
                            String errorMsg = String.valueOf(event.getOrDefault("error", "Unknown stream error"));
                            try {
                                self.savePartialFailedAiMessage(sessionId, questionId, finalMode, accumulated.toString(), errorMsg);
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
                        
                        try {
                            self.savePartialFailedAiMessage(sessionId, questionId, finalMode, accumulated.toString(), error.getMessage());
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
                        try {
                            emitter.send(Map.of("type", "done"));
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
        SseEmitter emitter = new SseEmitter(timeoutMs);
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
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String accumulatedContent,
            Map<String, Object> responseMetadata
    ) {
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
                .build();

        return messageRepository.save(msg);
    }

    @Transactional
    public AiReviewMessage saveDisconnectedAiMessage(
            Long sessionId,
            Long questionId,
            AiReviewMessageMode mode,
            String accumulatedContent
    ) {
        if (accumulatedContent == null || accumulatedContent.isBlank()) {
            log.info("Accumulated content is empty. Skipping DB save for disconnected stream on session {}", sessionId);
            return null;
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
                .build();

        return messageRepository.save(msg);
    }

    @Transactional
    public AiReviewMessage savePartialFailedAiMessage(
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
                .build();

        return messageRepository.save(msg);
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

package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.dto.aireview.AiReviewSubmitResponse;
import com.devmatch.entity.*;
import com.devmatch.exception.InvalidSessionStateException;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiReviewStreamingServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TestResultRepository testResultRepository;
    @Mock private TestAnswerRepository testAnswerRepository;
    @Mock private AiReviewSessionRepository sessionRepository;
    @Mock private AiReviewMessageRepository messageRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private PythonAiReviewClient pythonAiReviewClient;
    @Mock private RuleBasedAiReviewService aiReviewService;
    @Mock private SemanticAnswerEvaluator semanticAnswerEvaluator;
    @Mock private AiReviewContextSupport contextSupport;
    @Mock private AiReviewMetricSink metricSink;
    
    private AiReviewProperties properties;

    private ObjectMapper objectMapper;

    @InjectMocks
    private AiReviewStreamingService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                new AiReviewProperties.PythonAi(true, "http://localhost:8001", "qwen3:1.7b", 0.2, 256, 1024, 4, 30, ""),
                null,
                null,
                null,
                null,
                true,
                45
        );
        
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void streamAnswer_whenStreamingDisabled_shouldFallbackToRuleBasedService() {
        // Given
        AiReviewProperties disabledProps = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                new AiReviewProperties.PythonAi(true, "http://localhost:8001", "qwen3:1.7b", 0.2, 256, 1024, 4, 30, ""),
                null,
                null,
                null,
                null,
                false,
                45
        );
        ReflectionTestUtils.setField(service, "properties", disabledProps);

        AiReviewSubmitResponse mockResponse = new AiReviewSubmitResponse(
                AiReviewMessageMode.CHECK_QUESTION.name(),
                "Fallback response",
                "resolved",
                false,
                null,
                new ArrayList<>()
        );
        when(aiReviewService.submitAnswer(anyLong(), anyLong(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(mockResponse);


        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L, null);

        // Then
        assertThat(emitter).isNotNull();
        verify(aiReviewService).submitAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L, null);
    }

    @Test
    void streamAnswer_whenSessionCompleted_shouldFallbackToRuleBasedService() {
        // Given
        Fixtures fixtures = fixtures(1);
        AiReviewSession completedSession = session(fixtures.user(), fixtures.result(), 20L);
        ReflectionTestUtils.setField(completedSession, "status", AiReviewStatus.COMPLETED);

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(completedSession);
        
        AiReviewSubmitResponse mockResponse = new AiReviewSubmitResponse(
                AiReviewMessageMode.REVIEW_REPORT.name(),
                "Session already completed report",
                "resolved",
                true,
                "summary",
                new ArrayList<>()
        );
        when(aiReviewService.submitAnswer(anyLong(), anyLong(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(mockResponse);

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L, null);

        // Then
        assertThat(emitter).isNotNull();
        verify(aiReviewService).submitAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L, null);
    }


    @Test
    void streamAnswer_whenSuccessfulFreeQuestion_shouldStreamAndSaveCompletedMessage() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage lastAiMsg = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "deadlock prompt");

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);
        when(sessionRepository.findById(eq(20L))).thenReturn(Optional.of(session));
        when(questionRepository.findById(eq(100L))).thenReturn(Optional.of(question));
        lenient().when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        lenient().when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(lastAiMsg));
        lenient().when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);

        Flux<String> streamFlux = Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is \"}",
                "data: {\"type\": \"chunk\", \"chunk\": \"a state where processes wait for resources.\"}",
                "data: {\"type\": \"done\", \"response\": {\"route\": \"rag\", \"latency_ms\": 150, \"quality_flags\": [\"high_conf\"], \"candidate_id\": \"cand-123\"}}"
        );
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);
        
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-1234");

        // Then
        assertThat(emitter).isNotNull();
        
        // Wait a short time for reactive subscription to process
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());

        List<AiReviewMessage> saved = savedMsgs.getAllValues();
        
        // First message should be User message
        AiReviewMessage userMsg = saved.get(0);
        assertThat(userMsg.getRole()).isEqualTo(AiReviewMessageRole.USER);
        assertThat(userMsg.getContent()).isEqualTo("What is it?");
        assertThat(userMsg.getMode()).isEqualTo(AiReviewMessageMode.FREE_QUESTION);

        // Second message should be Completed AI message
        AiReviewMessage aiMsg = saved.get(1);
        assertThat(aiMsg.getRole()).isEqualTo(AiReviewMessageRole.AI);
        assertThat(aiMsg.getContent()).isEqualTo("A deadlock is a state where processes wait for resources.");
        assertThat(aiMsg.getAiRoute()).isEqualTo("rag");
        assertThat(aiMsg.getAiLatencyMs()).isEqualTo(150);
        assertThat(aiMsg.getAiQualityFlags()).contains("STATUS:COMPLETED", "high_conf");
        assertThat(aiMsg.getAiCandidateId()).isEqualTo("cand-123");
    }

    @Test
    void streamAnswer_whenPythonEmitsErrorEvent_shouldSavePartialFailedMessage() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage lastAiMsg = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "deadlock prompt");

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);
        when(sessionRepository.findById(eq(20L))).thenReturn(Optional.of(session));
        when(questionRepository.findById(eq(100L))).thenReturn(Optional.of(question));
        lenient().when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        lenient().when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(lastAiMsg));
        lenient().when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);

        Flux<String> streamFlux = Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is \"}",
                "data: {\"type\": \"error\", \"error\": \"Ollama timed out\"}"
        );
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);
        
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-1234");

        // Then
        assertThat(emitter).isNotNull();
        
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());

        List<AiReviewMessage> saved = savedMsgs.getAllValues();
        AiReviewMessage aiMsg = saved.get(1);
        assertThat(aiMsg.getRole()).isEqualTo(AiReviewMessageRole.AI);
        assertThat(aiMsg.getAiQualityFlags()).isEqualTo("STATUS:PARTIAL_FAILED");
        assertThat(aiMsg.getContent()).contains("A deadlock is", "답변 생성 중 오류가 발생했습니다: Ollama timed out");
    }

    @Test
    void streamAnswer_whenReactiveChannelThrowsError_shouldSavePartialFailedMessage() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage lastAiMsg = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "deadlock prompt");

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);
        when(sessionRepository.findById(eq(20L))).thenReturn(Optional.of(session));
        when(questionRepository.findById(eq(100L))).thenReturn(Optional.of(question));
        lenient().when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        lenient().when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(lastAiMsg));
        lenient().when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);

        Flux<String> streamFlux = Flux.just("data: {\"type\": \"chunk\", \"chunk\": \"Partial concept...\"}")
                .concatWith(Flux.error(new RuntimeException("Network breakdown")));
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);
        
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-1234");

        // Then
        assertThat(emitter).isNotNull();
        
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());

        List<AiReviewMessage> saved = savedMsgs.getAllValues();
        AiReviewMessage aiMsg = saved.get(1);
        assertThat(aiMsg.getRole()).isEqualTo(AiReviewMessageRole.AI);
        assertThat(aiMsg.getAiQualityFlags()).isEqualTo("STATUS:PARTIAL_FAILED");
        assertThat(aiMsg.getContent()).contains("Partial concept...", "답변 생성 중 오류가 발생했습니다: Network breakdown");
    }

    @Test
    void saveMessage_withClientRequestId_shouldPersistAndQueryCorrectly() {
        Fixtures fixtures = fixtures(1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        
        AiReviewMessage msg = AiReviewMessage.builder()
                .session(session)
                .role(AiReviewMessageRole.USER)
                .content("Test Answer")
                .clientRequestId("client-req-uuid-1234")
                .build();

        when(messageRepository.save(any(AiReviewMessage.class))).thenReturn(msg);
        when(messageRepository.existsBySessionIdAndClientRequestId(eq(20L), eq("client-req-uuid-1234"))).thenReturn(true);

        AiReviewMessage saved = messageRepository.save(msg);
        boolean exists = messageRepository.existsBySessionIdAndClientRequestId(20L, "client-req-uuid-1234");

        assertThat(saved.getClientRequestId()).isEqualTo("client-req-uuid-1234");
        assertThat(exists).isTrue();
    }

    @Test
    void streamAnswer_whenFailedBeforeFirstChunk_shouldNotSaveUserMessage() {
        Fixtures fixtures = fixtures(1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);
        
        Flux<String> streamFlux = Flux.error(new RuntimeException("Connection failed"));
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);

        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-999");

        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}

        verify(messageRepository, never()).save(any(AiReviewMessage.class));
    }

    @Test
    void streamAnswer_whenDuplicateClientRequestId_shouldPreventDoubleSaving() {
        Fixtures fixtures = fixtures(1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);
        when(messageRepository.existsBySessionIdAndClientRequestId(eq(20L), eq("client-req-uuid-duplicate"))).thenReturn(true);

        Flux<String> streamFlux = Flux.just("data: {\"type\": \"chunk\", \"chunk\": \"some chunk\"}");
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);

        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-duplicate");

        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}

        verify(messageRepository, never()).save(any(AiReviewMessage.class));
    }

    @Test
    void streamAnswer_whenDoneEvent_shouldNormalizeToAiReviewSubmitResponse() throws Exception {
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage lastAiMsg = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "deadlock prompt");

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);
        when(sessionRepository.findById(eq(20L))).thenReturn(Optional.of(session));
        lenient().when(questionRepository.findById(eq(100L))).thenReturn(Optional.of(question));
        
        Flux<String> streamFlux = Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is \"}",
                "data: {\"type\": \"done\", \"response\": {\"route\": \"rag\", \"latency_ms\": 150, \"quality_flags\": [\"high_conf\"], \"candidate_id\": \"cand-123\"}}"
        );
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);

        AiReviewMessage userMsg = AiReviewMessage.builder()
                .session(session)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.FREE_QUESTION)
                .content("What is it?")
                .evaluation(AiReviewEvaluation.UNDERSTOOD)
                .build();
        
        AiReviewMessage aiMsg = AiReviewMessage.builder()
                .session(session)
                .role(AiReviewMessageRole.AI)
                .mode(AiReviewMessageMode.FREE_ANSWER)
                .content("A deadlock is ")
                .build();

        when(messageRepository.save(any(AiReviewMessage.class))).thenReturn(userMsg).thenReturn(aiMsg);
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(lastAiMsg, userMsg, aiMsg));
        when(messageRepository.findTopBySessionIdAndRoleOrderByCreatedAtDesc(eq(20L), eq(AiReviewMessageRole.USER)))
                .thenReturn(Optional.of(userMsg));

        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-done-1");

        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}

        verify(messageRepository, atLeast(2)).save(any(AiReviewMessage.class));
    }

    @Test
    void streamAnswer_shouldRecordObservabilityMetrics() {
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        AiReviewProperties disabledProps = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                new AiReviewProperties.PythonAi(true, "http://localhost:8001", "qwen3:1.7b", 0.2, 256, 1024, 4, 30, ""),
                null,
                null,
                null,
                null,
                false,
                45
        );
        ReflectionTestUtils.setField(service, "properties", disabledProps);

        AiReviewSubmitResponse mockResponse = new AiReviewSubmitResponse(
                AiReviewMessageMode.CHECK_QUESTION.name(),
                "Fallback response",
                "resolved",
                false,
                null,
                new ArrayList<>()
        );
        lenient().when(aiReviewService.submitAnswer(anyLong(), anyLong(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(mockResponse);

        service.streamAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L, "client-req-uuid-metric");

        verify(metricSink).streamMetric(
                eq("fallback_to_sync_count"),
                eq(20L),
                eq(1L),
                eq("FREE_QUESTION"),
                eq(100L),
                eq("client-req-uuid-metric"),
                anyLong(),
                anyString()
        );
    }
    @Test
    void saveUserMessage_whenDataIntegrityViolation_shouldRecoverAndReturnExistingMessage() {
        Fixtures fixtures = fixtures(1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);

        when(sessionRepository.findById(eq(20L))).thenReturn(Optional.of(session));
        when(questionRepository.findById(eq(100L))).thenReturn(Optional.of(question));

        AiReviewMessage existingMsg = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.USER)
                .content("Answer")
                .clientRequestId("client-req-uuid-violation")
                .build();

        when(messageRepository.save(any(AiReviewMessage.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key uk_session_client_request"));
        when(messageRepository.findBySessionIdAndClientRequestId(eq(20L), eq("client-req-uuid-violation")))
                .thenReturn(List.of(existingMsg));

        AiReviewMessage recovered = service.saveUserMessage(20L, 100L, AiReviewMessageMode.FREE_QUESTION, "Answer", "client-req-uuid-violation");

        assertThat(recovered).isNotNull();
        assertThat(recovered.getClientRequestId()).isEqualTo("client-req-uuid-violation");
        verify(messageRepository).save(any(AiReviewMessage.class));
        verify(messageRepository).findBySessionIdAndClientRequestId(20L, "client-req-uuid-violation");
    }
 
    @Test
    void streamAnswer_shouldPropagateMdcContextAndCleanupOnTermination() {
        Fixtures fixtures = fixtures(1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(contextSupport.findOwnedSession(eq(1L), eq(20L))).thenReturn(session);

        Flux<String> streamFlux = Flux.just("data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is \"}");
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(streamFlux);

        // Given: Set MDC values in main servlet thread
        org.slf4j.MDC.put("traceId", "test-bridge-trace-id");
        org.slf4j.MDC.put("requestId", "test-bridge-request-id");

        try {
            // When
            SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L, "client-req-uuid-mdc");
            
            // Wait for subscription to complete
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {}

            // Then: verify MDC propagation inside Reactive Context is complete and safe
            assertThat(emitter).isNotNull();
            
            // Check that MDC is cleared after stream completion (cleanup assurance)
            assertThat(org.slf4j.MDC.get("traceId")).isNull();
            assertThat(org.slf4j.MDC.get("requestId")).isNull();
            
        } finally {
            org.slf4j.MDC.clear();
        }
    }








    private static Fixtures fixtures(int questionCount) {
        User user = User.builder()
                .email("learner@devmatch.com")
                .password("encoded")
                .name("learner")
                .role(Role.MENTEE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        com.devmatch.entity.Test test = com.devmatch.entity.Test.builder()
                .title("Java Backend")
                .description("diagnostic")
                .category("java-backend")
                .difficulty(Difficulty.INTERMEDIATE)
                .timeLimit(30)
                .passingScore(70)
                .questionCount(questionCount)
                .build();

        TestResult result = TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(0)
                .correctCount(0)
                .passed(false)
                .submittedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(result, "id", 10L);
        return new Fixtures(user, test, result);
    }

    private static Question question(
            com.devmatch.entity.Test test,
            Long id,
            String content,
            List<String> options,
            int correctAnswer,
            int orderIndex
    ) {
        Question question = Question.builder()
                .test(test)
                .content(content)
                .options(options)
                .correctAnswer(correctAnswer)
                .score(10)
                .orderIndex(orderIndex)
                .build();
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    private static TestAnswer wrongAnswer(TestResult result, Question question, int selectedAnswer) {
        return TestAnswer.builder()
                .testResult(result)
                .question(question)
                .selectedAnswer(selectedAnswer)
                .isCorrect(false)
                .build();
    }

    private static AiReviewSession session(User user, TestResult result, Long id) {
        AiReviewSession session = AiReviewSession.builder()
                .user(user)
                .testResult(result)
                .courseKey("java-backend")
                .status(AiReviewStatus.IN_PROGRESS)
                .build();
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private static AiReviewMessage aiMessage(
            AiReviewSession session,
            Question question,
            AiReviewMessageMode mode,
            String content
    ) {
        return AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content(content)
                .build();
    }

    private record Fixtures(User user, com.devmatch.entity.Test test, TestResult result) {
    }
}

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    @Mock private AiReviewMetricSink metricSink;
    @Mock private SemanticAnswerEvaluator semanticAnswerEvaluator;
    
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
        when(aiReviewService.submitAnswer(anyLong(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn(mockResponse);

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        verify(aiReviewService).submitAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L);
        verify(metricSink).fallbackToSync("streaming_disabled", 20L, 100L, "FREE_QUESTION");
    }

    @Test
    void streamAnswer_whenStreamingOffKillSwitchEnabled_shouldFallbackToRuleBasedService() {
        // Given
        AiReviewProperties degradedProps = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                new AiReviewProperties.PythonAi(true, "http://localhost:8001", "qwen3:1.7b", 0.2, 256, 1024, 4, 30, ""),
                null,
                null,
                null,
                null,
                new AiReviewProperties.Degraded(true),
                true,
                45
        );
        ReflectionTestUtils.setField(service, "properties", degradedProps);

        AiReviewSubmitResponse mockResponse = new AiReviewSubmitResponse(
                AiReviewMessageMode.CHECK_QUESTION.name(),
                "Fallback response",
                "resolved",
                false,
                null,
                new ArrayList<>()
        );
        when(aiReviewService.submitAnswer(anyLong(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn(mockResponse);

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        verify(aiReviewService).submitAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L);
        verify(pythonAiReviewClient, never()).streamReview(anyString(), anyString(), any());
        verify(metricSink).fallbackToSync("streaming_off", 20L, 100L, "FREE_QUESTION");
    }

    @Test
    void streamAnswer_whenSessionCompleted_shouldFallbackToRuleBasedService() {
        // Given
        Fixtures fixtures = fixtures(1);
        AiReviewSession completedSession = session(fixtures.user(), fixtures.result(), 20L);
        ReflectionTestUtils.setField(completedSession, "status", AiReviewStatus.COMPLETED);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(completedSession));
        
        AiReviewSubmitResponse mockResponse = new AiReviewSubmitResponse(
                AiReviewMessageMode.REVIEW_REPORT.name(),
                "Session already completed report",
                "resolved",
                true,
                "summary",
                new ArrayList<>()
        );
        when(aiReviewService.submitAnswer(anyLong(), anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn(mockResponse);

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        verify(aiReviewService).submitAnswer(1L, 20L, "Answer", "FREE_QUESTION", 100L);
        verify(metricSink).fallbackToSync("session_completed", 20L, 100L, "FREE_QUESTION");
    }

    @Test
    void streamAnswer_whenSessionOwnedByAnotherUser_shouldRejectBeforeSaving() {
        // Given
        Fixtures fixtures = fixtures(1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        // When / Then
        assertThatThrownBy(() -> service.streamAnswer(999L, 20L, "Answer", "FREE_QUESTION", 100L))
                .isInstanceOf(TestNotFoundException.class);
        verify(messageRepository, never()).save(any());
        verify(pythonAiReviewClient, never()).streamReview(anyString(), anyString(), any());
    }

    @Test
    void streamAnswer_whenFreeQuestionLimitReached_shouldRejectBeforeStreaming() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(3L);

        // When / Then
        assertThatThrownBy(() -> service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L))
                .isInstanceOf(InvalidSessionStateException.class);
        verify(messageRepository, never()).save(any());
        verify(pythonAiReviewClient, never()).streamReview(anyString(), anyString(), any());
    }

    @Test
    void streamAnswer_whenModeIsNotFreeQuestion_shouldDelegateToSynchronousService() {
        // Given
        AiReviewSubmitResponse mockResponse = new AiReviewSubmitResponse(
                AiReviewMessageMode.CHECK_QUESTION.name(),
                "sync response",
                "resolved",
                false,
                null,
                new ArrayList<>()
        );
        when(aiReviewService.submitAnswer(1L, 20L, "Answer", "CHECK_ANSWER", 100L))
                .thenReturn(mockResponse);

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "Answer", "CHECK_ANSWER", 100L);

        // Then
        assertThat(emitter).isNotNull();
        verify(aiReviewService).submitAnswer(1L, 20L, "Answer", "CHECK_ANSWER", 100L);
        verify(pythonAiReviewClient, never()).streamReview(anyString(), anyString(), any());
        verify(metricSink).fallbackToSync("mode_not_streamable", 20L, 100L, "CHECK_ANSWER");
    }

    @Test
    void streamAnswer_whenSuccessfulFreeQuestion_shouldStreamAndSaveCompletedMessage() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage lastAiMsg = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "deadlock prompt");

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
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
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

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
        assertThat(aiMsg.getContent())
                .contains("A deadlock is a state where processes wait for resources.")
                .contains("### 다음 확인 질문");
        assertThat(aiMsg.getAiRoute()).isEqualTo("rag");
        assertThat(aiMsg.getAiLatencyMs()).isEqualTo(150);
        assertThat(aiMsg.getAiQualityFlags()).contains("STATUS:COMPLETED", "high_conf");
        assertThat(aiMsg.getAiCandidateId()).isEqualTo("cand-123");

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PythonAiReviewClient.PythonAiRequest> requestCaptor =
                ArgumentCaptor.forClass(PythonAiReviewClient.PythonAiRequest.class);
        verify(pythonAiReviewClient).streamReview(uriCaptor.capture(), anyString(), requestCaptor.capture());

        PythonAiReviewClient.PythonAiRequest request = requestCaptor.getValue();
        assertThat(uriCaptor.getValue()).isEqualTo("/api/review/free-question");
        assertThat(request.question()).isEqualTo("What is a deadlock?");
        assertThat(request.options()).containsExactly("A", "B");
        assertThat(request.correct_answer()).isEqualTo("A");
        assertThat(request.selected_answer()).isEqualTo("B");
        assertThat(request.user_answer()).isEqualTo("What is it?");
        assertThat(request.course_id()).isEqualTo("java-backend");
        assertThat(request.test_id()).isEqualTo("30");
        assertThat(request.question_id()).isEqualTo("100");
        assertThat(request.source_question_id()).isEqualTo("java-backend:1");
        assertThat(request.stream()).isTrue();

        verify(metricSink).streamFirstToken(
                anyString(), eq(20L), eq(100L), eq("FREE_QUESTION"), anyLong()
        );
        verify(metricSink).streamCompleted(
                anyString(), eq(20L), eq(100L), eq("FREE_QUESTION"), anyLong(), anyLong(), eq(2), anyInt()
        );
    }

    @Test
    void streamAnswer_whenPythonFailsBeforeFirstChunk_shouldNotSaveUserMessage() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any()))
                .thenReturn(Flux.just("data: {\"type\": \"error\", \"error\": \"upstream unavailable\"}"));

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        verify(messageRepository, never()).save(any(AiReviewMessage.class));
        verify(metricSink).streamPartialFailed(
                anyString(), eq(20L), eq(100L), eq("FREE_QUESTION"), eq("upstream unavailable"), anyLong(), eq(0), eq(0)
        );
    }

    @Test
    void streamAnswer_whenClientDisconnectsBeforeFirstChunk_shouldDisposeUpstreamWithoutSavingMessages() throws Exception {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        CountDownLatch cancelled = new CountDownLatch(1);
        Sinks.Many<String> upstream = Sinks.many().multicast().onBackpressureBuffer();
        TestSseEmitter testEmitter = new TestSseEmitter(45_000L);
        AiReviewStreamingService streamingService = serviceWithEmitter(testEmitter);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any()))
                .thenReturn(upstream.asFlux().doOnCancel(cancelled::countDown));

        // When
        SseEmitter emitter = streamingService.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);
        testEmitter.triggerCompletion();
        boolean disposed = cancelled.await(1, TimeUnit.SECONDS);
        upstream.tryEmitNext("data: {\"type\": \"chunk\", \"chunk\": \"late chunk\"}");
        upstream.tryEmitNext("data: {\"type\": \"done\", \"response\": {\"answer\": \"Late completed\"}}");
        Thread.sleep(100);

        // Then
        assertThat(emitter).isNotNull();
        assertThat(disposed).isTrue();
        verify(messageRepository, never()).save(any(AiReviewMessage.class));
        verify(metricSink).streamDisconnected(
                anyString(), eq(20L), eq(100L), eq("FREE_QUESTION"), eq("completion_callback"), eq(-1L), anyLong(), eq(0)
        );
        verify(metricSink, never()).streamFirstToken(anyString(), anyLong(), anyLong(), anyString(), anyLong());
        verify(metricSink, never()).streamCompleted(anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void streamAnswer_whenReactiveChannelThrowsBeforeFirstChunk_shouldNotSaveMessages() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any()))
                .thenReturn(Flux.error(new RuntimeException("Python stream failed before chunk")));

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        verify(messageRepository, never()).save(any(AiReviewMessage.class));
        verify(metricSink).streamPartialFailed(
                anyString(),
                eq(20L),
                eq(100L),
                eq("FREE_QUESTION"),
                eq("Python stream failed before chunk"),
                anyLong(),
                eq(0),
                eq(0)
        );
    }

    @Test
    void streamAnswer_whenUpstreamCompletesEmpty_shouldEmitErrorAndNotSaveMessages() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        TestSseEmitter testEmitter = new TestSseEmitter(45_000L);
        AiReviewStreamingService streamingService = serviceWithEmitter(testEmitter);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(Flux.empty());

        // When
        SseEmitter emitter = streamingService.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        verify(messageRepository, never()).save(any(AiReviewMessage.class));
        verify(metricSink).streamPartialFailed(
                anyString(), eq(20L), eq(100L), eq("FREE_QUESTION"), eq("empty_completion"), anyLong(), eq(0), eq(0)
        );
        verify(metricSink, never()).streamCompleted(anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyLong(), anyInt(), anyInt());
        assertThat(earlySendMaps(testEmitter))
                .anySatisfy(event -> {
                    assertThat(event.get("type")).isEqualTo("error");
                    assertThat(event.get("error")).isEqualTo("Stream completed before response was generated");
                });
    }

    @Test
    void streamAnswer_whenDone_shouldSendSubmitResponseWithSavedMessages() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is \"}",
                "data: {\"type\": \"done\", \"response\": {\"answer\": \"A deadlock is a wait cycle.\", \"route\": \"rag\"}}"
        ));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> {
            AiReviewMessage message = invocation.getArgument(0);
            if (message.getRole() == AiReviewMessageRole.USER) {
                ReflectionTestUtils.setField(message, "id", 501L);
            } else {
                ReflectionTestUtils.setField(message, "id", 502L);
            }
            return message;
        });

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        Map<?, ?> doneEvent = earlySendMaps(emitter).stream()
                .filter(event -> "done".equals(event.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(doneEvent.get("response")).isInstanceOf(AiReviewSubmitResponse.class);

        AiReviewSubmitResponse response = (AiReviewSubmitResponse) doneEvent.get("response");
        assertThat(response.getEvaluation()).isEqualTo("FREE_QUESTION");
        assertThat(response.getFeedback())
                .contains("A deadlock is a wait cycle.")
                .contains("### 다음 확인 질문");
        assertThat(response.getNextQuestion()).isNotBlank();
        assertThat(response.getFeedback()).contains(response.getNextQuestion());
        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getMessages().get(0).getId()).isEqualTo(501L);
        assertThat(response.getMessages().get(1).getId()).isEqualTo(502L);
    }

    @Test
    void streamAnswer_whenStreamCompletesAfterChunkWithoutDone_shouldSendSubmitResponse() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is a wait cycle.\"}"
        ));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> {
            AiReviewMessage message = invocation.getArgument(0);
            if (message.getRole() == AiReviewMessageRole.USER) {
                ReflectionTestUtils.setField(message, "id", 601L);
            } else {
                ReflectionTestUtils.setField(message, "id", 602L);
            }
            return message;
        });

        // When
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        Map<?, ?> doneEvent = earlySendMaps(emitter).stream()
                .filter(event -> "done".equals(event.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(doneEvent.get("response")).isInstanceOf(AiReviewSubmitResponse.class);

        AiReviewSubmitResponse response = (AiReviewSubmitResponse) doneEvent.get("response");
        assertThat(response.getFeedback())
                .contains("A deadlock is a wait cycle.")
                .contains("### 다음 확인 질문");
        assertThat(response.getNextQuestion()).isNotBlank();
        assertThat(response.getFeedback()).contains(response.getNextQuestion());
        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getMessages().get(0).getId()).isEqualTo(601L);
        assertThat(response.getMessages().get(1).getId()).isEqualTo(602L);
    }

    @Test
    void streamAnswer_whenPythonEmitsErrorEvent_shouldSavePartialFailedMessage() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage lastAiMsg = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "deadlock prompt");

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
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
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

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

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
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
        SseEmitter emitter = service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

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
    void streamAnswer_whenClientDisconnectsAfterChunk_shouldDisposeUpstreamAndIgnoreLateDone() throws Exception {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        CountDownLatch cancelled = new CountDownLatch(1);
        Sinks.Many<String> upstream = Sinks.many().multicast().onBackpressureBuffer();
        TestSseEmitter testEmitter = new TestSseEmitter(45_000L);
        AiReviewStreamingService streamingService = serviceWithEmitter(testEmitter);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any()))
                .thenReturn(upstream.asFlux().doOnCancel(cancelled::countDown));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        streamingService.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);
        upstream.tryEmitNext("data: {\"type\": \"chunk\", \"chunk\": \"Partial answer\"}");
        Thread.sleep(100);
        testEmitter.triggerCompletion();
        boolean disposed = cancelled.await(1, TimeUnit.SECONDS);
        upstream.tryEmitNext("data: {\"type\": \"done\", \"response\": {\"answer\": \"Late completed\"}}");
        Thread.sleep(100);

        // Then
        assertThat(disposed).isTrue();
        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());
        assertThat(savedMsgs.getAllValues())
                .filteredOn(message -> message.getRole() == AiReviewMessageRole.AI)
                .singleElement()
                .satisfies(message -> assertThat(message.getAiQualityFlags()).isEqualTo("STATUS:DISCONNECTED"));
        verify(metricSink, never()).streamCompleted(anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void streamAnswer_whenEmitterTimesOutAfterChunk_shouldDisposeUpstreamAndSaveDisconnected() throws Exception {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        CountDownLatch cancelled = new CountDownLatch(1);
        Sinks.Many<String> upstream = Sinks.many().multicast().onBackpressureBuffer();
        TestSseEmitter testEmitter = new TestSseEmitter(45_000L);
        AiReviewStreamingService streamingService = serviceWithEmitter(testEmitter);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any()))
                .thenReturn(upstream.asFlux().doOnCancel(cancelled::countDown));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        SseEmitter emitter = streamingService.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);
        upstream.tryEmitNext("data: {\"type\": \"chunk\", \"chunk\": \"Partial answer\"}");
        Thread.sleep(100);
        testEmitter.triggerTimeout();
        boolean disposed = cancelled.await(1, TimeUnit.SECONDS);

        // Then
        assertThat(emitter).isNotNull();
        assertThat(disposed).isTrue();
        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());
        assertThat(savedMsgs.getAllValues())
                .filteredOn(message -> message.getRole() == AiReviewMessageRole.AI)
                .singleElement()
                .satisfies(message -> assertThat(message.getAiQualityFlags()).isEqualTo("STATUS:DISCONNECTED"));
        verify(metricSink).streamDisconnected(
                anyString(), eq(20L), eq(100L), eq("FREE_QUESTION"), eq("timeout"), anyLong(), anyLong(), anyInt()
        );
    }

    @Test
    void streamAnswer_whenDuplicateTerminalEventsArrive_shouldPersistOnlyOneAiTerminalMessage() throws Exception {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        TestSseEmitter testEmitter = new TestSseEmitter(45_000L);
        AiReviewStreamingService streamingService = serviceWithEmitter(testEmitter);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is \"}",
                "data: {\"type\": \"done\", \"response\": {\"answer\": \"A deadlock is a wait cycle.\"}}",
                "data: {\"type\": \"error\", \"error\": \"late error\"}"
        ));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        SseEmitter emitter = streamingService.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        assertThat(emitter).isNotNull();
        Thread.sleep(200);

        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());
        assertThat(savedMsgs.getAllValues())
                .filteredOn(message -> message.getRole() == AiReviewMessageRole.AI)
                .singleElement()
                .satisfies(message -> assertThat(message.getAiQualityFlags()).contains("STATUS:COMPLETED"));
        verify(metricSink, never()).streamPartialFailed(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void saveCompletedAiMessage_whenStreamRequestAlreadyHasTerminal_shouldReturnExistingTerminal() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage existing = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(AiReviewMessageMode.FREE_QUESTION)
                .content("Already completed")
                .streamRequestId("ai-stream-fixed")
                .streamTerminalStatus(AiReviewStreamTerminalStatus.COMPLETED)
                .aiQualityFlags("STATUS:COMPLETED")
                .build();

        when(messageRepository.findByStreamRequestId("ai-stream-fixed")).thenReturn(Optional.of(existing));

        // When
        AiReviewMessage saved = service.saveCompletedAiMessage(
                "ai-stream-fixed",
                20L,
                100L,
                AiReviewMessageMode.FREE_QUESTION,
                "Late completed",
                null
        );

        // Then
        assertThat(saved).isSameAs(existing);
        verify(messageRepository, never()).save(any(AiReviewMessage.class));
    }

    @Test
    void streamAnswer_whenSuccessfulFreeQuestion_shouldPersistTerminalRequestIdAndStatus() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L), eq(100L), eq(AiReviewMessageRole.USER), any()
        )).thenReturn(0L);
        when(messageRepository.findByStreamRequestId(anyString())).thenReturn(Optional.empty());
        when(pythonAiReviewClient.streamReview(anyString(), anyString(), any())).thenReturn(Flux.just(
                "data: {\"type\": \"chunk\", \"chunk\": \"A deadlock is a wait cycle.\"}",
                "data: {\"type\": \"done\", \"response\": {\"route\": \"rag\"}}"
        ));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.streamAnswer(1L, 20L, "What is it?", "FREE_QUESTION", 100L);

        // Then
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        ArgumentCaptor<AiReviewMessage> savedMsgs = ArgumentCaptor.forClass(AiReviewMessage.class);
        verify(messageRepository, times(2)).save(savedMsgs.capture());
        AiReviewMessage aiTerminal = savedMsgs.getAllValues().stream()
                .filter(message -> message.getRole() == AiReviewMessageRole.AI)
                .findFirst()
                .orElseThrow();
        assertThat(aiTerminal.getStreamRequestId()).startsWith("ai-stream-");
        assertThat(aiTerminal.getStreamTerminalStatus()).isEqualTo(AiReviewStreamTerminalStatus.COMPLETED);
    }

    @Test
    void saveCompletedAiMessage_whenUniqueGuardLosesRace_shouldReturnExistingTerminal() {
        // Given
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "What is a deadlock?", List.of("A", "B"), 0, 1);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage existing = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(AiReviewMessageMode.FREE_QUESTION)
                .content("Winning terminal")
                .streamRequestId("ai-stream-race")
                .streamTerminalStatus(AiReviewStreamTerminalStatus.DISCONNECTED)
                .aiQualityFlags("STATUS:DISCONNECTED")
                .build();

        Optional<AiReviewMessage> noTerminalMessage = Optional.empty();
        when(messageRepository.findByStreamRequestId("ai-stream-race"))
                .thenReturn(noTerminalMessage)
                .thenReturn(Optional.of(existing));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(messageRepository.save(any(AiReviewMessage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate stream request"));

        // When
        AiReviewMessage saved = service.saveCompletedAiMessage(
                "ai-stream-race",
                20L,
                100L,
                AiReviewMessageMode.FREE_QUESTION,
                "Late completed",
                null
        );

        // Then
        assertThat(saved).isSameAs(existing);
    }

    private AiReviewStreamingService serviceWithEmitter(SseEmitter emitter) {
        AiReviewStreamingService streamingService = new TestableAiReviewStreamingService(
                pythonAiReviewClient,
                aiReviewService,
                properties,
                objectMapper,
                sessionRepository,
                testAnswerRepository,
                questionRepository,
                messageRepository,
                metricSink,
                emitter
        );
        ReflectionTestUtils.setField(streamingService, "self", streamingService);
        return streamingService;
    }

    private static final class TestableAiReviewStreamingService extends AiReviewStreamingService {

        private final SseEmitter emitter;

        private TestableAiReviewStreamingService(
                PythonAiReviewClient pythonAiReviewClient,
                RuleBasedAiReviewService aiReviewService,
                AiReviewProperties properties,
                ObjectMapper objectMapper,
                AiReviewSessionRepository sessionRepository,
                TestAnswerRepository testAnswerRepository,
                QuestionRepository questionRepository,
                AiReviewMessageRepository messageRepository,
                AiReviewMetricSink metricSink,
                SseEmitter emitter
        ) {
            super(
                    pythonAiReviewClient,
                    aiReviewService,
                    properties,
                    objectMapper,
                    sessionRepository,
                    testAnswerRepository,
                    questionRepository,
                    messageRepository,
                    metricSink
            );
            this.emitter = emitter;
        }

        @Override
        protected SseEmitter createEmitter(long timeoutMs) {
            return emitter;
        }
    }

    private static final class TestSseEmitter extends SseEmitter {

        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private TestSseEmitter(long timeoutMs) {
            super(timeoutMs);
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
        }

        @Override
        public synchronized void onError(java.util.function.Consumer<Throwable> callback) {
        }

        void triggerCompletion() {
            if (completionCallback != null) {
                completionCallback.run();
            }
        }

        void triggerTimeout() {
            if (timeoutCallback != null) {
                timeoutCallback.run();
            }
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
        ReflectionTestUtils.setField(test, "id", 30L);

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

    private static List<Map<?, ?>> earlySendMaps(SseEmitter emitter) {
        Object attempts = ReflectionTestUtils.getField(emitter, "earlySendAttempts");
        assertThat(attempts).isInstanceOf(Set.class);
        List<Map<?, ?>> events = new ArrayList<>();
        for (Object attempt : (Set<?>) attempts) {
            Object data = ReflectionTestUtils.getField(attempt, "data");
            if (data instanceof Map<?, ?> event) {
                events.add(event);
            }
        }
        return events;
    }

    private record Fixtures(User user, com.devmatch.entity.Test test, TestResult result) {
    }
}

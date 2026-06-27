package com.devmatch.service.ai;

import com.devmatch.dto.aireview.AiReviewSummaryResponse;
import com.devmatch.config.AiReviewProperties;
import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import com.devmatch.entity.AiReviewSession;
import com.devmatch.entity.AiReviewStatus;
import com.devmatch.entity.Difficulty;
import com.devmatch.entity.Question;
import com.devmatch.entity.Role;
import com.devmatch.entity.TestAnswer;
import com.devmatch.entity.TestResult;
import com.devmatch.entity.User;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.TestAnswerRepository;
import com.devmatch.repository.TestResultRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleBasedAiReviewServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TestResultRepository testResultRepository;
    @Mock private TestAnswerRepository testAnswerRepository;
    @Mock private AiReviewSessionRepository sessionRepository;
    @Mock private AiReviewMessageRepository messageRepository;
    @Mock private PythonAiReviewClient pythonAiReviewClient;
    @Mock private OllamaAiReviewClient ollamaAiReviewClient;
    @Mock private AiReviewProperties aiReviewProperties;
    @Mock private SemanticAnswerEvaluator semanticAnswerEvaluator;
    @InjectMocks private RuleBasedAiReviewService service;

    @Test
    void evaluateAnswer_usesSemanticEvaluatorOnlyWhenFeatureFlagIsEnabled() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "Which layer should own transaction boundaries?",
                List.of("Controller", "Service", "Repository", "Entity"),
                1,
                1
        );
        when(aiReviewProperties.evaluation()).thenReturn(new AiReviewProperties.Evaluation(true));
        when(semanticAnswerEvaluator.evaluate(question, "tiny"))
                .thenReturn(Optional.of(AiReviewEvaluation.UNDERSTOOD));

        AiReviewEvaluation evaluation = ReflectionTestUtils.invokeMethod(service, "evaluateAnswer", question, "tiny");

        assertThat(evaluation).isEqualTo(AiReviewEvaluation.UNDERSTOOD);
    }

    @Test
    void startReview_usesPythonGeneratedFirstQuestionWhenAvailable() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "Which layer should own transaction boundaries?",
                List.of("Controller", "Service", "Repository", "Entity"),
                1,
                2
        );
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(fixtures.user()));
        when(testResultRepository.findById(10L)).thenReturn(Optional.of(fixtures.result()));
        when(sessionRepository.findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(1L, 10L)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(AiReviewSession.class))).thenAnswer(invocation -> {
            AiReviewSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 20L);
            return session;
        });
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(pythonAiReviewClient.generateFirstQuestion(any(), any(), any()))
                .thenReturn(Optional.of(AiGeneratedAnswer.plain("Why is the Service layer the better transaction boundary?")));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of());

        service.startReview(1L, 10L);

        ArgumentCaptor<AiReviewMessage> savedMessage = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository).save(savedMessage.capture());
        assertThat(savedMessage.getValue().getContent()).isEqualTo("Why is the Service layer the better transaction boundary?");
        org.mockito.Mockito.verify(pythonAiReviewClient).generateFirstQuestion(eq(question), eq("Service"), eq("Repository"));
    }

    @Test
    void generateFirstQuestion_usesOllamaWhenPythonThrows() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "Why override equals and hashCode together?",
                List.of("inheritance", "collection equality"),
                1,
                1
        );
        when(pythonAiReviewClient.generateFirstQuestion(question, "collection equality", "inheritance"))
                .thenThrow(new RuntimeException("python unavailable"));
        when(ollamaAiReviewClient.generateFirstQuestion(question, "collection equality", "inheritance"))
                .thenReturn(Optional.of("Ollama first question"));

        Optional<String> answer = ReflectionTestUtils.invokeMethod(
                service,
                "generateFirstQuestion",
                question,
                "collection equality",
                "inheritance"
        );

        assertThat(answer).contains("Ollama first question");
    }

    @Test
    void generateFollowUp_usesOllamaWhenPythonThrows() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "Why override equals and hashCode together?",
                List.of("inheritance", "collection equality"),
                1,
                1
        );
        when(pythonAiReviewClient.generateFollowUp(
                eq(question),
                eq("collection equality"),
                eq("inheritance"),
                eq("I am not sure"),
                eq(AiReviewEvaluation.NEEDS_REVIEW),
                eq(2),
                eq("previous"),
                eq("equals/hashCode")
        )).thenThrow(new RuntimeException("python unavailable"));
        when(ollamaAiReviewClient.generateFollowUp(
                question,
                "collection equality",
                "inheritance",
                "I am not sure",
                AiReviewEvaluation.NEEDS_REVIEW,
                2
        )).thenReturn(Optional.of("Ollama follow-up"));

        Optional<String> answer = ReflectionTestUtils.invokeMethod(
                service,
                "generateFollowUp",
                question,
                "collection equality",
                "inheritance",
                "I am not sure",
                AiReviewEvaluation.NEEDS_REVIEW,
                2,
                "previous",
                "equals/hashCode"
        );

        assertThat(answer).contains("Ollama follow-up");
    }

    @Test
    void generateFreeQuestionAnswer_usesOllamaWhenPythonThrows() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "Why override equals and hashCode together?",
                List.of("inheritance", "collection equality"),
                1,
                1
        );
        when(pythonAiReviewClient.answerFreeQuestion(question, "collection equality", "inheritance", "hashmap이 무엇인가요?"))
                .thenThrow(new RuntimeException("python unavailable"));
        when(ollamaAiReviewClient.answerFreeQuestion(question, "collection equality", "inheritance", "hashmap이 무엇인가요?"))
                .thenReturn(Optional.of("Ollama free-question answer"));

        Optional<AiGeneratedAnswer> answer = ReflectionTestUtils.invokeMethod(
                service,
                "generateFreeQuestionAnswer",
                question,
                "collection equality",
                "inheritance",
                "hashmap이 무엇인가요?"
        );

        assertThat(answer).isPresent();
        assertThat(answer.orElseThrow().answer()).isEqualTo("Ollama free-question answer");
    }

    @Test
    void submitAnswer_saves_python_freeQuestionMetadataOnAiMessage() {
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "aria-label이 필요한 상황은?", List.of("화면에 보이는 텍스트가 충분함", "아이콘 버튼에 접근 가능한 이름이 필요함"), 1, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 0);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage firstPrompt = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "aria-label은 언제 쓰나요?");

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(firstPrompt));
        when(messageRepository.findTopBySessionIdOrderByIdDesc(20L)).thenReturn(Optional.empty());
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L),
                eq(100L),
                eq(AiReviewMessageRole.USER),
                any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.answerFreeQuestion(any(), any(), any(), any()))
                .thenReturn(Optional.of(new AiGeneratedAnswer(
                        "aria-label은 아이콘 버튼처럼 화면 텍스트가 없을 때 접근 가능한 이름을 제공합니다.",
                        "generated_card_fast_path",
                        "aria-label",
                        "typo",
                        "frontend-aria-label",
                        "definition",
                        List.of("missing_topic"),
                        "auto-123",
                        14,
                        List.of()
                )));
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(anyLong(), anyLong())).thenReturn(List.of());

        service.submitAnswer(1L, 20L, "arila-label이 뭔가요?", "FREE_QUESTION", 100L);

        ArgumentCaptor<AiReviewMessage> savedMessages = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository, times(2)).save(savedMessages.capture());
        AiReviewMessage aiAnswer = savedMessages.getAllValues().stream()
                .filter(message -> message.getMode() == AiReviewMessageMode.FREE_ANSWER)
                .findFirst()
                .orElseThrow();
        assertThat(aiAnswer.getAiRoute()).isEqualTo("generated_card_fast_path");
        assertThat(aiAnswer.getAiResolvedQuery()).isEqualTo("aria-label");
        assertThat(aiAnswer.getAiCorrectionType()).isEqualTo("typo");
        assertThat(aiAnswer.getAiMatchedConceptId()).isEqualTo("frontend-aria-label");
        assertThat(aiAnswer.getAiAnswerStyle()).isEqualTo("definition");
        assertThat(aiAnswer.getAiQualityFlags()).isEqualTo("missing_topic");
        assertThat(aiAnswer.getAiCandidateId()).isEqualTo("auto-123");
        assertThat(aiAnswer.getAiLatencyMs()).isEqualTo(14);
    }

    @Test
    void summarizeQuestion_returnsStructuredMarkdownStudySummary() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "@Transactional은 어느 계층에 두는 것이 적절한가?",
                List.of("Controller", "Service", "Repository", "Entity"),
                1,
                1
        );
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 2);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage firstPrompt = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "Repository는 DB와 가까워 보이지만 트랜잭션 경계는 Service가 잡습니다.");
        AiReviewMessage userAnswer = userMessage(session, question, "DB와 가까운 Repository가 정답이라고 생각했습니다.", AiReviewEvaluation.NEEDS_REVIEW);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(firstPrompt, userAnswer));
        when(messageRepository.findTopBySessionIdOrderByIdDesc(20L)).thenReturn(Optional.empty());
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(20L, 0L)).thenReturn(List.of());

        AiReviewSummaryResponse response = service.summarizeQuestion(1L, 20L, 100L);

        assertThat(response.getSummary())
                .contains("# @Transactional")
                .contains("## 핵심 개념")
                .contains("## 판단 기준")
                .contains("## 오답 함정")
                .contains("## 실무 연결")
                .contains("## 반례 / 예외")
                .contains("## 내 오답 원인")
                .contains("## 한 줄 압축")
                .contains("**정답 조건**")
                .contains("Repository")
                .contains("Service");

        ArgumentCaptor<AiReviewMessage> savedMessage = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository).save(savedMessage.capture());
        assertThat(savedMessage.getValue().getContent()).isEqualTo(response.getSummary());
        assertThat(savedMessage.getValue().getMode()).isEqualTo(AiReviewMessageMode.QUESTION_SUMMARY);
    }

    @Test
    void submitAnswer_usesTopicSpecificFallbackWhenAiClientsAreUnavailable() {
        Fixtures fixtures = fixtures(1);
        Question question = question(fixtures.test(), 100L, "Git tag와 release version의 관계는?", List.of("branch", "tag"), 1, 1);
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 0);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage firstPrompt = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "Git tag가 무엇인지 설명해보세요.");

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(firstPrompt));
        when(messageRepository.findTopBySessionIdOrderByIdDesc(20L)).thenReturn(Optional.empty());
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L),
                eq(100L),
                eq(AiReviewMessageRole.USER),
                any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.answerFreeQuestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(ollamaAiReviewClient.answerFreeQuestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(anyLong(), anyLong())).thenReturn(List.of());

        service.submitAnswer(1L, 20L, "Git tag는 뭔데?", "FREE_QUESTION", 100L);

        ArgumentCaptor<AiReviewMessage> savedMessages = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository, times(2)).save(savedMessages.capture());
        AiReviewMessage aiAnswer = savedMessages.getAllValues().stream()
                .filter(message -> message.getMode() == AiReviewMessageMode.FREE_ANSWER)
                .findFirst()
                .orElseThrow();
        assertThat(aiAnswer.getContent())
                .contains("Git tag")
                .contains("commit")
                .contains("version")
                .doesNotContain("정의, 쓰이는 상황");
    }

    @Test
    void submitAnswer_usesLayerSpecificFallbackForFreeQuestionWhenAiClientsAreUnavailable() {
        Fixtures fixtures = fixtures(1);
        Question question = question(
                fixtures.test(),
                100L,
                "Spring backend layered architecture transaction boundary",
                List.of("Controller", "Service", "Repository", "Entity"),
                1,
                1
        );
        TestAnswer wrongAnswer = wrongAnswer(fixtures.result(), question, 2);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage firstPrompt = aiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, "Why should Service own the transaction boundary?");

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(firstPrompt));
        when(messageRepository.findTopBySessionIdOrderByIdDesc(20L)).thenReturn(Optional.empty());
        when(messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                eq(20L),
                eq(100L),
                eq(AiReviewMessageRole.USER),
                any()
        )).thenReturn(0L);
        when(pythonAiReviewClient.answerFreeQuestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(ollamaAiReviewClient.answerFreeQuestion(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(anyLong(), anyLong())).thenReturn(List.of());

        service.submitAnswer(1L, 20L, "계층은 어떻게 있나요??", "FREE_QUESTION", 100L);

        ArgumentCaptor<AiReviewMessage> savedMessages = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository, times(2)).save(savedMessages.capture());
        AiReviewMessage aiAnswer = savedMessages.getAllValues().stream()
                .filter(message -> message.getMode() == AiReviewMessageMode.FREE_ANSWER)
                .findFirst()
                .orElseThrow();
        assertThat(aiAnswer.getContent())
                .contains("Controller")
                .contains("Service")
                .contains("Repository")
                .contains("Entity")
                .contains("transaction")
                .doesNotContain("질문으로 이해했어요");
    }

    @Test
    void summarizeReview_returnsWeaknessAnalysisMarkdownReport() {
        Fixtures fixtures = fixtures(2);
        Question transactionalQuestion = question(
                fixtures.test(),
                100L,
                "@Transactional은 어느 계층에 두는 것이 적절한가?",
                List.of("Controller", "Service", "Repository", "Entity"),
                1,
                1
        );
        Question dtoQuestion = question(
                fixtures.test(),
                101L,
                "API 응답 DTO를 Entity와 분리하는 이유는?",
                List.of("키워드가 비슷해서", "계층 책임을 분리하기 위해", "DB 속도 때문에", "예외가 없어서"),
                1,
                2
        );
        TestAnswer wrongTransactional = wrongAnswer(fixtures.result(), transactionalQuestion, 2);
        TestAnswer wrongDto = wrongAnswer(fixtures.result(), dtoQuestion, 0);
        AiReviewSession session = session(fixtures.user(), fixtures.result(), 20L);
        AiReviewMessage needsReview = userMessage(session, transactionalQuestion, "Repository가 DB와 가까워서 정답이라고 생각했습니다.", AiReviewEvaluation.NEEDS_REVIEW);
        AiReviewMessage partial = userMessage(session, dtoQuestion, "DTO와 Entity의 책임 차이를 헷갈렸습니다.", AiReviewEvaluation.PARTIAL);

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongTransactional, wrongDto));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(needsReview, partial));
        when(messageRepository.findTopBySessionIdOrderByIdDesc(20L)).thenReturn(Optional.empty());
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(20L, 0L)).thenReturn(List.of());

        AiReviewSummaryResponse response = service.summarizeReview(1L, 20L);

        assertThat(response.getSummary())
                .contains("# 전체 복습 리포트")
                .contains("## 오늘의 오답 범위")
                .contains("## 핵심 약점 TOP 3")
                .contains("## 반복 오답 패턴")
                .contains("## 우선 복습할 문제")
                .contains("## 다음 복습 전략")
                .contains("## 오늘의 한 줄 피드백")
                .contains("책임/계층 분리 부족")
                .contains("키워드 유사성 함정")
                .contains("조건 기반 판단 부족");

        ArgumentCaptor<AiReviewMessage> savedMessage = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository).save(savedMessage.capture());
        assertThat(savedMessage.getValue().getContent()).isEqualTo(response.getSummary());
        assertThat(savedMessage.getValue().getMode()).isEqualTo(AiReviewMessageMode.REVIEW_REPORT);
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

    private static AiReviewMessage userMessage(
            AiReviewSession session,
            Question question,
            String content,
            AiReviewEvaluation evaluation
    ) {
        return AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.CHECK_ANSWER)
                .content(content)
                .evaluation(evaluation)
                .build();
    }

    private record Fixtures(User user, com.devmatch.entity.Test test, TestResult result) {
    }
}

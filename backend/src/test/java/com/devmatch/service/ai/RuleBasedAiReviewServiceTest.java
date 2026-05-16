package com.devmatch.service.ai;

import com.devmatch.dto.aireview.AiReviewSummaryResponse;
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
    @InjectMocks private RuleBasedAiReviewService service;

    @Test
    void summarizeQuestion_returnsConceptConflictNoteFormat() {
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
                .questionCount(1)
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

        Question question = Question.builder()
                .test(test)
                .content("@Transactional은 어느 계층에 적용하는 것이 가장 적절한가?")
                .options(List.of("Controller", "Service", "Repository", "Entity"))
                .correctAnswer(1)
                .score(10)
                .orderIndex(1)
                .build();
        ReflectionTestUtils.setField(question, "id", 100L);

        TestAnswer wrongAnswer = TestAnswer.builder()
                .testResult(result)
                .question(question)
                .selectedAnswer(2)
                .isCorrect(false)
                .build();

        AiReviewSession session = AiReviewSession.builder()
                .user(user)
                .testResult(result)
                .courseKey("java-backend")
                .status(AiReviewStatus.IN_PROGRESS)
                .build();
        ReflectionTestUtils.setField(session, "id", 20L);

        AiReviewMessage firstPrompt = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(AiReviewMessageMode.CHECK_QUESTION)
                .content("Repository도 DB를 다루는데, 왜 트랜잭션 경계는 Service에 두는지 설명해보세요.")
                .build();
        AiReviewMessage userAnswer = AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.CHECK_ANSWER)
                .content("DB를 다루니까 Repository에 두는 것 같습니다.")
                .evaluation(AiReviewEvaluation.NEEDS_REVIEW)
                .build();

        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(testAnswerRepository.findByTestResultId(10L)).thenReturn(List.of(wrongAnswer));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(firstPrompt, userAnswer));
        when(messageRepository.findTopBySessionIdOrderByIdDesc(20L)).thenReturn(Optional.empty());
        when(messageRepository.save(any(AiReviewMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(20L, 0L)).thenReturn(List.of());

        AiReviewSummaryResponse response = service.summarizeQuestion(1L, 20L, 100L);

        assertThat(response.getSummary())
                .contains("@Transactional 위치")
                .contains("문제 핵심")
                .contains("내가 틀린 이유")
                .contains("정답 기준")
                .contains("구조 연결")
                .contains("왜 이 보기가 함정인지")
                .contains("실무 연결")
                .contains("정답 조건 먼저 말하기")
                .contains("트랜잭션은 업무 단위니까 Service");

        ArgumentCaptor<AiReviewMessage> savedMessage = ArgumentCaptor.forClass(AiReviewMessage.class);
        org.mockito.Mockito.verify(messageRepository).save(savedMessage.capture());
        assertThat(savedMessage.getValue().getContent()).isEqualTo(response.getSummary());
        assertThat(savedMessage.getValue().getMode()).isEqualTo(AiReviewMessageMode.QUESTION_SUMMARY);
    }
}

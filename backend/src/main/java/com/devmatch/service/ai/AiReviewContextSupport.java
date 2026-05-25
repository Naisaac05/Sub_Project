package com.devmatch.service.ai;

import com.devmatch.entity.*;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.TestAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AiReviewContextSupport {

    private final AiReviewSessionRepository sessionRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final AiReviewMessageRepository messageRepository;

    public AiReviewSession findOwnedSession(Long userId, Long sessionId) {
        AiReviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new TestNotFoundException("AI 복습 세션을 찾을 수 없습니다."));
        if (!Objects.equals(session.getUser().getId(), userId)) {
            throw new TestNotFoundException("AI 복습 세션을 찾을 수 없습니다.");
        }
        return session;
    }

    public List<TestAnswer> wrongAnswers(Long testResultId) {
        return testAnswerRepository.findByTestResultId(testResultId).stream()
                .filter(answer -> !Boolean.TRUE.equals(answer.getIsCorrect()))
                .sorted(Comparator.comparing(answer -> answer.getQuestion().getOrderIndex()))
                .toList();
    }

    public Question resolveCurrentQuestion(
            List<TestAnswer> wrongAnswers,
            AiReviewMessage lastAiMessage,
            Long questionId,
            AiReviewMessageMode mode
    ) {
        if (questionId != null && mode != AiReviewMessageMode.NEXT_QUESTION) {
            return wrongAnswers.stream()
                    .map(TestAnswer::getQuestion)
                    .filter(question -> Objects.equals(question.getId(), questionId))
                    .findFirst()
                    .orElseThrow(() -> new TestNotFoundException("선택한 복습 문제를 찾을 수 없습니다."));
        }
        return lastAiMessage.getQuestion();
    }

    public long countAiResponses(Long sessionId, Long questionId, Collection<AiReviewMessageMode> limitModes) {
        return messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                sessionId,
                questionId,
                AiReviewMessageRole.AI,
                limitModes
        );
    }

    public long countFreeQuestions(Long sessionId, Long questionId) {
        return messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                sessionId,
                questionId,
                AiReviewMessageRole.USER,
                List.of(AiReviewMessageMode.FREE_QUESTION)
        );
    }

    public String optionAt(Question question, int index) {
        if (index < 0 || question.getOptions() == null || index >= question.getOptions().size()) {
            return "미응답";
        }
        return question.getOptions().get(index);
    }
}

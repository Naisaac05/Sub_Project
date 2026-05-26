package com.devmatch.service.ai;

import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import com.devmatch.entity.AiReviewSession;
import com.devmatch.entity.Question;
import com.devmatch.entity.TestAnswer;
import com.devmatch.exception.TestNotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;

final class AiReviewContextSupport {

    private AiReviewContextSupport() {
    }

    static AiReviewSession requireOwnedSession(Optional<AiReviewSession> session, Long userId) {
        AiReviewSession resolved = session
                .orElseThrow(() -> new TestNotFoundException("AI 복습 세션을 찾을 수 없습니다."));
        Long ownerId = resolved.getUser() == null ? null : resolved.getUser().getId();
        if (!Objects.equals(ownerId, userId)) {
            throw new TestNotFoundException("AI 복습 세션을 찾을 수 없습니다.");
        }
        return resolved;
    }

    static List<TestAnswer> wrongAnswers(List<TestAnswer> answers) {
        return answers.stream()
                .filter(answer -> !Boolean.TRUE.equals(answer.getIsCorrect()))
                .sorted(Comparator.comparing(answer -> answer.getQuestion().getOrderIndex()))
                .toList();
    }

    static Optional<AiReviewMessage> latestQuestionMessage(List<AiReviewMessage> messages) {
        ListIterator<AiReviewMessage> iterator = messages.listIterator(messages.size());
        while (iterator.hasPrevious()) {
            AiReviewMessage message = iterator.previous();
            if (message.getRole() != AiReviewMessageRole.AI || message.getQuestion() == null) {
                continue;
            }
            if (message.getMode() == AiReviewMessageMode.QUESTION_SUMMARY
                    || message.getMode() == AiReviewMessageMode.REVIEW_REPORT) {
                continue;
            }
            return Optional.of(message);
        }
        return Optional.empty();
    }

    static Question questionById(List<TestAnswer> wrongAnswers, Long questionId) {
        return wrongAnswers.stream()
                .map(TestAnswer::getQuestion)
                .filter(question -> Objects.equals(question.getId(), questionId))
                .findFirst()
                .orElseThrow(() -> new TestNotFoundException("선택한 복습 문제를 찾을 수 없습니다."));
    }

    static String optionAt(Question question, int index) {
        if (index < 0 || question.getOptions() == null || index >= question.getOptions().size()) {
            return "미응답";
        }
        return question.getOptions().get(index);
    }
}

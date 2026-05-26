package com.devmatch.service.ai;

import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import com.devmatch.entity.AiReviewSession;
import com.devmatch.entity.Question;
import com.devmatch.entity.TestAnswer;
import com.devmatch.entity.User;
import com.devmatch.exception.TestNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiReviewContextSupportTest {

    @Test
    void latestQuestionMessage_ignoresSummariesAndReports() {
        Question firstQuestion = question(100L, 1);
        Question secondQuestion = question(200L, 2);
        AiReviewMessage firstPrompt = aiMessage(firstQuestion, AiReviewMessageMode.CHECK_QUESTION);
        AiReviewMessage summary = aiMessage(firstQuestion, AiReviewMessageMode.QUESTION_SUMMARY);
        AiReviewMessage secondPrompt = aiMessage(secondQuestion, AiReviewMessageMode.EXPLANATION);
        AiReviewMessage report = aiMessage(null, AiReviewMessageMode.REVIEW_REPORT);

        Optional<AiReviewMessage> resolved = AiReviewContextSupport.latestQuestionMessage(
                List.of(firstPrompt, summary, secondPrompt, report)
        );

        assertThat(resolved).containsSame(secondPrompt);
    }

    @Test
    void wrongAnswers_sortsByQuestionOrderAndFiltersCorrectAnswers() {
        Question firstQuestion = question(100L, 1);
        Question secondQuestion = question(200L, 2);
        TestAnswer correct = answer(question(300L, 3), true);

        List<TestAnswer> resolved = AiReviewContextSupport.wrongAnswers(
                List.of(answer(secondQuestion, false), correct, answer(firstQuestion, false))
        );

        assertThat(resolved).extracting(answer -> answer.getQuestion().getId())
                .containsExactly(100L, 200L);
    }

    @Test
    void requireOwnedSession_rejectsMissingOrDifferentOwner() {
        AiReviewSession session = session(1L);

        assertThat(AiReviewContextSupport.requireOwnedSession(Optional.of(session), 1L))
                .isSameAs(session);
        assertThatThrownBy(() -> AiReviewContextSupport.requireOwnedSession(Optional.of(session), 2L))
                .isInstanceOf(TestNotFoundException.class);
        assertThatThrownBy(() -> AiReviewContextSupport.requireOwnedSession(Optional.empty(), 1L))
                .isInstanceOf(TestNotFoundException.class);
    }

    @Test
    void optionAt_returnsFallbackForInvalidIndex() {
        Question question = question(100L, 1);
        ReflectionTestUtils.setField(question, "options", List.of("A", "B"));

        assertThat(AiReviewContextSupport.optionAt(question, 1)).isEqualTo("B");
        assertThat(AiReviewContextSupport.optionAt(question, -1)).isEqualTo("미응답");
        assertThat(AiReviewContextSupport.optionAt(question, 2)).isEqualTo("미응답");
    }

    private static Question question(Long id, int orderIndex) {
        Question question = Question.builder()
                .content("question")
                .options(List.of("A", "B"))
                .correctAnswer(0)
                .score(10)
                .orderIndex(orderIndex)
                .build();
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    private static TestAnswer answer(Question question, boolean correct) {
        return TestAnswer.builder()
                .question(question)
                .isCorrect(correct)
                .build();
    }

    private static AiReviewMessage aiMessage(Question question, AiReviewMessageMode mode) {
        return AiReviewMessage.builder()
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content("content")
                .build();
    }

    private static AiReviewSession session(Long userId) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);
        return AiReviewSession.builder()
                .user(user)
                .build();
    }
}

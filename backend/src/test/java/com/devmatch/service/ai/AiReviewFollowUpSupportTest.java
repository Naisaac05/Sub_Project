package com.devmatch.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewFollowUpSupportTest {

    @Test
    void offTopicFreeQuestionDoesNotAppendLearningFollowUp() {
        AiGeneratedAnswer answer = new AiGeneratedAnswer(
                "이 화면은 현재 틀린 문제를 복습하는 공간이라 해당 질문에는 답변하지 않을게요.",
                "off_topic_redirect",
                "점심 뭐 먹을까?",
                null,
                null,
                "off_topic",
                List.of("off_topic"),
                null,
                0,
                List.of()
        );

        String followUp = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(null, "점심 뭐 먹을까?", answer);
        String appended = AiReviewFollowUpSupport.appendFollowUp(answer.answer(), followUp);

        assertThat(followUp).isBlank();
        assertThat(appended).isEqualTo(answer.answer());
    }

    @Test
    void outOfCourseRedirectDoesNotAppendLearningFollowUp() {
        AiGeneratedAnswer answer = new AiGeneratedAnswer(
                "이 질문은 현재 코스 복습 범위 밖의 기술 주제라 여기서는 답변하지 않을게요.",
                "out_of_course_redirect",
                "@Transactional이 뭐야?",
                null,
                "spring-transactional",
                "out_of_course",
                List.of("out_of_course"),
                null,
                0,
                List.of()
        );

        String followUp = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(null, "@Transactional이 뭐야?", answer);
        String appended = AiReviewFollowUpSupport.appendFollowUp(answer.answer(), followUp);

        assertThat(followUp).isBlank();
        assertThat(appended).isEqualTo(answer.answer());
    }

    @Test
    void definitionFollowUpDoesNotUseRepeatedOneSentenceTemplate() {
        String followUp = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(
                null,
                "useEffect가 뭔가요?",
                "definition",
                "useEffect",
                "frontend-useeffect"
        );

        assertThat(followUp).contains("useEffect");
        assertThat(followUp).doesNotContain("핵심을 한 문장으로 다시 설명");
    }

    @Test
    void followUpPromptVariesByTopic() {
        String first = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(
                null,
                "useEffect가 뭔가요?",
                "definition",
                "useEffect",
                "frontend-useeffect"
        );
        String second = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(
                null,
                "CSS가 뭔가요?",
                "definition",
                "CSS",
                null
        );

        assertThat(first).isNotEqualTo(second);
    }
}

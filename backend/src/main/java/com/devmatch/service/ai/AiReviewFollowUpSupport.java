package com.devmatch.service.ai;

import com.devmatch.entity.Question;

final class AiReviewFollowUpSupport {

    private static final String FOLLOW_UP_HEADING = "### 다음 확인 질문";

    private AiReviewFollowUpSupport() {
    }

    static String buildFreeQuestionFollowUp(Question question, String userQuestion, AiGeneratedAnswer answer) {
        return buildFreeQuestionFollowUp(
                question,
                userQuestion,
                answer.answerStyle(),
                answer.resolvedQuery(),
                answer.matchedConceptId()
        );
    }

    static String buildFreeQuestionFollowUp(
            Question question,
            String userQuestion,
            String answerStyle,
            String resolvedQuery,
            String matchedConceptId
    ) {
        String topic = followUpTopic(question, userQuestion, resolvedQuery, matchedConceptId);
        String style = answerStyle == null ? "" : answerStyle;
        if ("practical".equals(style)) {
            return topic + "를 지금 문제에 적용한다면 어떤 기준으로 판단하면 좋을까요?";
        }
        if ("comparison".equals(style)) {
            return topic + "에서 정답과 헷갈리는 선택지를 가르는 차이는 무엇일까요?";
        }
        return topic + "의 핵심을 한 문장으로 다시 설명하면 어떻게 말할 수 있을까요?";
    }

    static String appendFollowUp(String answer, String followUpQuestion) {
        String cleanAnswer = answer == null ? "" : answer.strip();
        String cleanFollowUp = followUpQuestion == null ? "" : followUpQuestion.strip();
        if (cleanFollowUp.isBlank() || cleanAnswer.contains(FOLLOW_UP_HEADING)) {
            return cleanAnswer;
        }
        return cleanAnswer + "\n\n" + FOLLOW_UP_HEADING + "\n" + cleanFollowUp;
    }

    static String extractFollowUp(String content) {
        if (content == null) {
            return null;
        }
        int headingIndex = content.indexOf(FOLLOW_UP_HEADING);
        if (headingIndex < 0) {
            return null;
        }
        String followUp = content.substring(headingIndex + FOLLOW_UP_HEADING.length()).strip();
        return followUp.isBlank() ? null : followUp;
    }

    private static String followUpTopic(
            Question question,
            String userQuestion,
            String resolvedQuery,
            String matchedConceptId
    ) {
        if (resolvedQuery != null && !resolvedQuery.isBlank()) {
            return resolvedQuery.strip();
        }
        if (matchedConceptId != null && !matchedConceptId.isBlank()) {
            return matchedConceptId.strip();
        }
        if (userQuestion != null && !userQuestion.isBlank()) {
            return shorten(userQuestion);
        }
        return question == null ? "이 개념" : shorten(question.getContent());
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "이 개념";
        }
        String compact = value.replaceAll("\\s+", " ").strip();
        return compact.length() <= 80 ? compact : compact.substring(0, 80) + "...";
    }
}

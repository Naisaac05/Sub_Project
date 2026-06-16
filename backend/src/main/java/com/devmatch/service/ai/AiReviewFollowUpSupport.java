package com.devmatch.service.ai;

import com.devmatch.entity.Question;

final class AiReviewFollowUpSupport {

    private static final String FOLLOW_UP_HEADING = "### 다음 확인 질문";

    private AiReviewFollowUpSupport() {
    }

    static String buildFreeQuestionFollowUp(Question question, String userQuestion, AiGeneratedAnswer answer) {
        if (shouldSkipFollowUp(answer)) {
            return "";
        }
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
        return definitionFollowUp(topic);
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

    private static String definitionFollowUp(String topic) {
        return switch (Math.floorMod(topic.hashCode(), 3)) {
            case 0 -> topic + "가 실제 코드에서 드러나는 신호를 하나 고르면 무엇일까요?";
            case 1 -> topic + "가 필요한 상황과 그렇지 않은 상황을 어떻게 구분할 수 있을까요?";
            default -> "현재 문제의 보기 중 " + topic + "와 직접 연결되는 단서는 무엇일까요?";
        };
    }

    private static boolean shouldSkipFollowUp(AiGeneratedAnswer answer) {
        if (answer == null) {
            return false;
        }
        if ("off_topic_redirect".equals(answer.route())
                || "out_of_course_redirect".equals(answer.route())
                || "off_topic".equals(answer.answerStyle())
                || "out_of_course".equals(answer.answerStyle())) {
            return true;
        }
        return answer.qualityFlags() != null
                && (answer.qualityFlags().contains("off_topic")
                || answer.qualityFlags().contains("out_of_course"));
    }
}

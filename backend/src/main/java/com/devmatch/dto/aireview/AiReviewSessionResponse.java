package com.devmatch.dto.aireview;

import com.devmatch.entity.AiReviewSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiReviewSessionResponse {

    private Long sessionId;
    private Long testResultId;
    private String courseKey;
    private String status;
    private String summary;
    private String weaknessTags;
    private List<WrongQuestionResponse> wrongQuestions;
    private List<AiReviewMessageResponse> messages;

    public static AiReviewSessionResponse of(
            AiReviewSession session,
            List<WrongQuestionResponse> wrongQuestions,
            List<AiReviewMessageResponse> messages
    ) {
        return new AiReviewSessionResponse(
                session.getId(),
                session.getTestResult().getId(),
                session.getCourseKey(),
                session.getStatus().name(),
                session.getSummary(),
                session.getWeaknessTags(),
                wrongQuestions,
                messages
        );
    }
}

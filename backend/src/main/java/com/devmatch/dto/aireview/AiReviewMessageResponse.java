package com.devmatch.dto.aireview;

import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AiReviewMessageResponse {

    private Long id;
    private Long questionId;
    private String role;
    private String mode;
    private String content;
    private String evaluation;
    private LocalDateTime createdAt;

    public static AiReviewMessageResponse from(AiReviewMessage message) {
        AiReviewMessageRole role = message.getRole();
        AiReviewMessageMode mode = message.getMode();
        AiReviewEvaluation evaluation = message.getEvaluation();
        return new AiReviewMessageResponse(
                message.getId(),
                message.getQuestion() == null ? null : message.getQuestion().getId(),
                role.name(),
                mode == null ? null : mode.name(),
                message.getContent(),
                evaluation == null ? null : evaluation.name(),
                message.getCreatedAt()
        );
    }
}

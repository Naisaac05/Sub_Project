package com.devmatch.dto.aireview;

import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
    private String aiRoute;
    private String aiResolvedQuery;
    private String aiCorrectionType;
    private String aiMatchedConceptId;
    private String aiAnswerStyle;
    private List<String> aiQualityFlags;
    private String aiCandidateId;
    private Integer aiLatencyMs;

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
                message.getCreatedAt(),
                message.getAiRoute(),
                message.getAiResolvedQuery(),
                message.getAiCorrectionType(),
                message.getAiMatchedConceptId(),
                message.getAiAnswerStyle(),
                parseQualityFlags(message.getAiQualityFlags()),
                message.getAiCandidateId(),
                message.getAiLatencyMs()
        );
    }

    private static List<String> parseQualityFlags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(flag -> !flag.isBlank())
                .toList();
    }
}

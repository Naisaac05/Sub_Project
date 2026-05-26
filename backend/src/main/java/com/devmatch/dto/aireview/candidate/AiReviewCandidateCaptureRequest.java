package com.devmatch.dto.aireview.candidate;

import jakarta.validation.constraints.NotBlank;

public record AiReviewCandidateCaptureRequest(
        @NotBlank String candidateId,
        @NotBlank String term,
        String category,
        String definitionDraft,
        String sourceQuestion,
        String resolvedQuery,
        String route,
        Double confidenceScore,
        String needsReviewReason
) {
}

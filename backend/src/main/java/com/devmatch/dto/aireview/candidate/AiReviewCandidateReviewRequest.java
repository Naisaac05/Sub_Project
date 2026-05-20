package com.devmatch.dto.aireview.candidate;

import jakarta.validation.constraints.NotBlank;

public record AiReviewCandidateReviewRequest(
        String candidateId,
        @NotBlank String term,
        String category,
        @NotBlank String action,
        String definition,
        String rejectedReason,
        String reviewer
) {
    public AiReviewCandidateReviewRequest(
            String term,
            String category,
            String action,
            String definition,
            String rejectedReason,
            String reviewer
    ) {
        this(null, term, category, action, definition, rejectedReason, reviewer);
    }
}

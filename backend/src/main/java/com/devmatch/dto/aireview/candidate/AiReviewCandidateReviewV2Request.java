package com.devmatch.dto.aireview.candidate;

import com.devmatch.entity.AiReviewCandidateReviewAction;
import jakarta.validation.constraints.NotNull;

public record AiReviewCandidateReviewV2Request(
        @NotNull AiReviewCandidateReviewAction action,
        String definition,
        String reviewerEditedAnswer,
        String rejectedReason,
        Long mergedIntoId,
        String reviewer,
        Integer retentionDays
) {
}

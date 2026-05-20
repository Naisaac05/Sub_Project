package com.devmatch.dto.aireview.candidate;

import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;

import java.time.LocalDateTime;

public record AiReviewCandidateV2Response(
        Long id,
        String externalCandidateId,
        String term,
        String category,
        AiReviewCandidateSource source,
        AiReviewCandidateStatus status,
        String definition,
        String definitionDraft,
        String reviewerEditedAnswer,
        String rejectedReason,
        Long mergedIntoId,
        String sourceQuestion,
        String resolvedQuery,
        String route,
        Double confidenceScore,
        String needsReviewReason,
        String reviewer,
        LocalDateTime reviewedAt,
        LocalDateTime retentionUntil,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.devmatch.dto.aireview.candidate;

import java.util.List;
import java.util.Map;

public record AiReviewCandidateResponse(
        String candidateId,
        String term,
        String category,
        String source,
        List<String> aliases,
        String definition,
        String definitionDraft,
        String definitionStatus,
        boolean approved,
        String humanReviewStatus,
        String criticRiskLevel,
        String criticRecommendation,
        Map<String, Object> criticFeedback,
        String duplicateStatus,
        List<String> duplicateConceptIds,
        String duplicateReason,
        List<String> sourceQuestionIds,
        String sourceQuestion,
        String resolvedQuery,
        String route,
        Double confidenceScore,
        String needsReviewReason,
        String createdAt,
        String rejectedReason,
        String reviewedAt,
        String reviewer
) {
}

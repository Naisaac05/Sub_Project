package com.devmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_review_candidates", indexes = {
        @Index(name = "idx_ai_review_candidate_status", columnList = "status"),
        @Index(name = "idx_ai_review_candidate_external_id", columnList = "external_candidate_id"),
        @Index(name = "idx_ai_review_candidate_term_category", columnList = "term, category")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiReviewCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_candidate_id", length = 120)
    private String externalCandidateId;

    @Column(nullable = false, length = 200)
    private String term;

    @Column(nullable = false, length = 120)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiReviewCandidateSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiReviewCandidateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_phase", length = 30)
    private AiReviewCandidateWorkflowPhase workflowPhase;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @Column(columnDefinition = "TEXT")
    private String definitionDraft;

    @Column(columnDefinition = "TEXT")
    private String reviewerEditedAnswer;

    @Column(length = 500)
    private String rejectedReason;

    private Long mergedIntoId;

    @Column(length = 1000)
    private String sourceQuestion;

    @Column(length = 500)
    private String resolvedQuery;

    @Column(length = 80)
    private String route;

    private Double confidenceScore;

    @Column(length = 120)
    private String needsReviewReason;

    @Column(length = 80)
    private String reviewer;

    private LocalDateTime reviewedAt;

    private LocalDateTime retentionUntil;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void approve(String definition, String reviewer, LocalDateTime reviewedAt, LocalDateTime retentionUntil) {
        this.status = AiReviewCandidateStatus.APPROVED;
        this.workflowPhase = AiReviewCandidateWorkflowPhase.APPROVED;
        this.definition = definition;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.retentionUntil = retentionUntil;
        this.rejectedReason = null;
        this.mergedIntoId = null;
    }

    public void editAndApprove(String editedAnswer, String reviewer, LocalDateTime reviewedAt, LocalDateTime retentionUntil) {
        this.reviewerEditedAnswer = editedAnswer;
        approve(editedAnswer, reviewer, reviewedAt, retentionUntil);
    }

    public void reject(String rejectedReason, String reviewer, LocalDateTime reviewedAt, LocalDateTime retentionUntil) {
        this.status = AiReviewCandidateStatus.REJECTED;
        this.workflowPhase = AiReviewCandidateWorkflowPhase.REJECTED;
        this.rejectedReason = rejectedReason;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.retentionUntil = retentionUntil;
    }

    public void merge(Long mergedIntoId, String reason, String reviewer, LocalDateTime reviewedAt, LocalDateTime retentionUntil) {
        this.status = AiReviewCandidateStatus.MERGED;
        this.workflowPhase = AiReviewCandidateWorkflowPhase.MERGED;
        this.mergedIntoId = mergedIntoId;
        this.rejectedReason = reason;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.retentionUntil = retentionUntil;
    }

    public boolean fillDraftIfBlank(String definitionDraft, String route, Double confidenceScore, String needsReviewReason) {
        if (definitionDraft == null || definitionDraft.isBlank() || this.definitionDraft != null && !this.definitionDraft.isBlank()) {
            return false;
        }
        this.definitionDraft = definitionDraft;
        this.workflowPhase = AiReviewCandidateWorkflowPhase.DRAFTED;
        this.route = route;
        this.confidenceScore = confidenceScore;
        this.needsReviewReason = needsReviewReason;
        return true;
    }

    public void startReview(String reviewer, LocalDateTime reviewedAt, LocalDateTime retentionUntil) {
        this.workflowPhase = AiReviewCandidateWorkflowPhase.HUMAN_REVIEW;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.retentionUntil = retentionUntil;
    }
}

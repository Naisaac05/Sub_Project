package com.devmatch.service;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateResponse;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewV2Request;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateV2Response;
import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateAudit;
import com.devmatch.entity.AiReviewCandidateReviewAction;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.devmatch.repository.AiReviewCandidateAuditRepository;
import com.devmatch.repository.AiReviewCandidateRepository;
import com.devmatch.service.ai.AiReviewMetricSink;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiReviewCandidateApprovalV2Service {

    private static final Logger log = LoggerFactory.getLogger(AiReviewCandidateApprovalV2Service.class);
    private static final int DEFAULT_RETENTION_DAYS = 365;

    private final AiReviewCandidateRepository candidateRepository;
    private final AiReviewCandidateAuditRepository auditRepository;
    private final AiReviewCandidateAdminService jsonlService;
    private final AiReviewMetricSink metricSink;
    private final AiReviewKnowledgeReindexer knowledgeReindexer;

    @Transactional(readOnly = true)
    public List<AiReviewCandidateV2Response> listCandidates() {
        logCandidateBacklog("list");
        return candidateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AiReviewCandidateV2Response reviewCandidate(Long id, AiReviewCandidateReviewV2Request request) {
        AiReviewCandidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No matching AI review candidate: " + id));

        AiReviewCandidateStatus previousStatus = candidate.getStatus();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retentionUntil = now.plusDays(retentionDays(request));
        String reviewer = blankToDefault(request.reviewer(), "admin-ui");

        if (request.action() == AiReviewCandidateReviewAction.APPROVE) {
            candidate.approve(requiredDefinition(candidate, request.definition()), reviewer, now, retentionUntil);
        } else if (request.action() == AiReviewCandidateReviewAction.EDIT_AND_APPROVE) {
            candidate.editAndApprove(requiredText(request.reviewerEditedAnswer(), "EDIT_AND_APPROVE requires reviewerEditedAnswer"), reviewer, now, retentionUntil);
        } else if (request.action() == AiReviewCandidateReviewAction.REJECT) {
            candidate.reject(blankToDefault(request.rejectedReason(), "No reason provided"), reviewer, now, retentionUntil);
        } else if (request.action() == AiReviewCandidateReviewAction.MERGE) {
            Long mergedIntoId = request.mergedIntoId();
            if (mergedIntoId == null || !candidateRepository.existsById(mergedIntoId)) {
                throw new IllegalArgumentException("MERGE requires an existing mergedIntoId");
            }
            candidate.merge(mergedIntoId, blankToDefault(request.rejectedReason(), "Merged into another candidate"), reviewer, now, retentionUntil);
        } else {
            throw new IllegalArgumentException("Unsupported candidate review action: " + request.action());
        }

        AiReviewCandidate saved = candidateRepository.save(candidate);
        auditRepository.save(AiReviewCandidateAudit.builder()
                .candidate(saved)
                .previousStatus(previousStatus)
                .nextStatus(saved.getStatus())
                .action(request.action())
                .reviewer(reviewer)
                .reviewerEditedAnswer(request.reviewerEditedAnswer())
                .reason(request.rejectedReason())
                .build());
        if (saved.getStatus() == AiReviewCandidateStatus.APPROVED) {
            knowledgeReindexer.reindexChanged(saved);
        }
        logCandidateBacklog("review_" + request.action().name().toLowerCase());
        return toResponse(saved);
    }

    @Transactional
    public int importJsonlCandidates() {
        int imported = 0;
        for (AiReviewCandidateResponse row : jsonlService.listCandidates()) {
            if (!isImportable(row)) {
                continue;
            }
            if (isDuplicate(row)) {
                updateExistingDraftIfBlank(row);
                continue;
            }
            candidateRepository.save(fromJsonl(row));
            imported++;
        }
        logCandidateBacklog("import_jsonl");
        return imported;
    }

    private boolean isImportable(AiReviewCandidateResponse row) {
        if (isBlank(row.term())) {
            return false;
        }
        boolean autoCandidate = "ai-review-auto-candidate".equalsIgnoreCase(row.source())
                || "auto-candidates".equalsIgnoreCase(row.source());
        if (!autoCandidate) {
            return true;
        }
        boolean hasReviewContext = !isBlank(row.sourceQuestion()) || !isBlank(row.resolvedQuery());
        return hasReviewContext && !"unknown".equalsIgnoreCase(row.term());
    }

    private void logCandidateBacklog(String operation) {
        long pending = candidateRepository.countByStatus(AiReviewCandidateStatus.PENDING);
        metricSink.candidateBacklog(operation, pending);
        log.info("ai_review.candidate_backlog operation={} pending={}", operation, pending);
    }

    private boolean isDuplicate(AiReviewCandidateResponse row) {
        if (!isBlank(row.candidateId())) {
            return candidateRepository.existsByExternalCandidateId(row.candidateId());
        }
        return candidateRepository.existsByTermIgnoreCaseAndCategoryIgnoreCase(row.term(), row.category());
    }

    private void updateExistingDraftIfBlank(AiReviewCandidateResponse row) {
        if (isBlank(row.candidateId())) {
            return;
        }
        candidateRepository.findByExternalCandidateId(row.candidateId())
                .filter(candidate -> candidate.fillDraftIfBlank(
                        row.definitionDraft(),
                        row.route(),
                        row.confidenceScore(),
                        row.needsReviewReason()
                ))
                .ifPresent(candidateRepository::save);
    }

    private AiReviewCandidate fromJsonl(AiReviewCandidateResponse row) {
        return AiReviewCandidate.builder()
                .externalCandidateId(blankToNull(row.candidateId()))
                .term(blankToDefault(row.term(), "unknown"))
                .category(blankToDefault(row.category(), "uncategorized"))
                .source(sourceFrom(row.source()))
                .status(row.approved() ? AiReviewCandidateStatus.APPROVED : AiReviewCandidateStatus.PENDING)
                .definition(row.definition())
                .definitionDraft(row.definitionDraft())
                .rejectedReason(row.rejectedReason())
                .sourceQuestion(row.sourceQuestion())
                .resolvedQuery(row.resolvedQuery())
                .route(row.route())
                .confidenceScore(row.confidenceScore())
                .needsReviewReason(row.needsReviewReason())
                .reviewer(row.reviewer())
                .build();
    }

    private AiReviewCandidateV2Response toResponse(AiReviewCandidate candidate) {
        return new AiReviewCandidateV2Response(
                candidate.getId(),
                candidate.getExternalCandidateId(),
                candidate.getTerm(),
                candidate.getCategory(),
                candidate.getSource(),
                candidate.getStatus(),
                candidate.getDefinition(),
                candidate.getDefinitionDraft(),
                candidate.getReviewerEditedAnswer(),
                candidate.getRejectedReason(),
                candidate.getMergedIntoId(),
                candidate.getSourceQuestion(),
                candidate.getResolvedQuery(),
                candidate.getRoute(),
                candidate.getConfidenceScore(),
                candidate.getNeedsReviewReason(),
                candidate.getReviewer(),
                candidate.getReviewedAt(),
                candidate.getRetentionUntil(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt()
        );
    }

    private static int retentionDays(AiReviewCandidateReviewV2Request request) {
        return request.retentionDays() == null || request.retentionDays() <= 0
                ? DEFAULT_RETENTION_DAYS
                : request.retentionDays();
    }

    private static String requiredDefinition(AiReviewCandidate candidate, String requestedDefinition) {
        String definition = blankToDefault(requestedDefinition, candidate.getDefinitionDraft());
        return requiredText(definition, "APPROVE requires definition or definitionDraft");
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static AiReviewCandidateSource sourceFrom(String source) {
        if ("ai-review-auto-candidate".equalsIgnoreCase(source) || "auto-candidates".equalsIgnoreCase(source)) {
            return AiReviewCandidateSource.AUTO;
        }
        if ("course-concepts".equalsIgnoreCase(source)) {
            return AiReviewCandidateSource.COURSE;
        }
        return AiReviewCandidateSource.MANUAL;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

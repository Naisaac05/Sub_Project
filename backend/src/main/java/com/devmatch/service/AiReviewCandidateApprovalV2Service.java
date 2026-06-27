package com.devmatch.service;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateResponse;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateCaptureRequest;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewV2Request;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateV2Response;
import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateAudit;
import com.devmatch.entity.AiReviewCandidateReviewAction;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.devmatch.entity.AiReviewCandidateWorkflowPhase;
import com.devmatch.repository.AiReviewCandidateAuditRepository;
import com.devmatch.repository.AiReviewCandidateRepository;
import com.devmatch.service.ai.AiReviewMetricSink;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class AiReviewCandidateApprovalV2Service {

    private static final Logger log = LoggerFactory.getLogger(AiReviewCandidateApprovalV2Service.class);
    private static final int DEFAULT_RETENTION_DAYS = 365;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AiReviewCandidateRepository candidateRepository;
    private final AiReviewCandidateAuditRepository auditRepository;
    private final AiReviewCandidateAdminService jsonlService;
    private final AiReviewMetricSink metricSink;
    private final AiReviewKnowledgeReindexer knowledgeReindexer;
    private final ObjectMapper objectMapper;
    private final Path conceptsV2Root;

    @Autowired
    public AiReviewCandidateApprovalV2Service(
            AiReviewCandidateRepository candidateRepository,
            AiReviewCandidateAuditRepository auditRepository,
            AiReviewCandidateAdminService jsonlService,
            AiReviewMetricSink metricSink,
            AiReviewKnowledgeReindexer knowledgeReindexer,
            @Value("${app.ai-review.concepts-v2-path:../ai/app/knowledge/concepts_v2}") String conceptsV2Root
    ) {
        this(candidateRepository, auditRepository, jsonlService, metricSink, knowledgeReindexer, new ObjectMapper(), Path.of(conceptsV2Root));
    }

    AiReviewCandidateApprovalV2Service(
            AiReviewCandidateRepository candidateRepository,
            AiReviewCandidateAuditRepository auditRepository,
            AiReviewCandidateAdminService jsonlService,
            AiReviewMetricSink metricSink,
            AiReviewKnowledgeReindexer knowledgeReindexer
    ) {
        this(candidateRepository, auditRepository, jsonlService, metricSink, knowledgeReindexer, new ObjectMapper(), Path.of("../ai/app/knowledge/concepts_v2"));
    }

    AiReviewCandidateApprovalV2Service(
            AiReviewCandidateRepository candidateRepository,
            AiReviewCandidateAuditRepository auditRepository,
            AiReviewCandidateAdminService jsonlService,
            AiReviewMetricSink metricSink,
            AiReviewKnowledgeReindexer knowledgeReindexer,
            ObjectMapper objectMapper,
            Path conceptsV2Root
    ) {
        this.candidateRepository = candidateRepository;
        this.auditRepository = auditRepository;
        this.jsonlService = jsonlService;
        this.metricSink = metricSink;
        this.knowledgeReindexer = knowledgeReindexer;
        this.objectMapper = objectMapper;
        this.conceptsV2Root = conceptsV2Root;
    }

    @Transactional(readOnly = true)
    public List<AiReviewCandidateV2Response> listCandidates() {
        logCandidateBacklog("list");
        List<AiReviewCandidateV2Response> responses = new ArrayList<>(candidateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList());
        responses.addAll(listApprovedConceptCards(responses));
        return responses;
    }

    @Transactional
    public AiReviewCandidateV2Response captureCandidate(AiReviewCandidateCaptureRequest request) {
        if (candidateRepository.existsByExternalCandidateId(request.candidateId())) {
            AiReviewCandidate existing = candidateRepository.findByExternalCandidateId(request.candidateId())
                    .orElseThrow(() -> new IllegalStateException("Candidate duplicate guard was inconsistent: " + request.candidateId()));
            if (existing.fillDraftIfBlank(
                    request.definitionDraft(),
                    request.route(),
                    request.confidenceScore(),
                    request.needsReviewReason()
            )) {
                candidateRepository.save(existing);
                auditCapture(existing, "duplicate_draft_updated");
            }
            logCandidateBacklog("capture_duplicate");
            return toResponse(existing);
        }

        AiReviewCandidate saved = candidateRepository.save(AiReviewCandidate.builder()
                .externalCandidateId(blankToNull(request.candidateId()))
                .term(blankToDefault(request.term(), "unknown"))
                .category(blankToDefault(request.category(), "auto-review"))
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.PENDING)
                .workflowPhase(phaseForDraft(request.definitionDraft()))
                .definition("")
                .definitionDraft(request.definitionDraft())
                .sourceQuestion(request.sourceQuestion())
                .resolvedQuery(request.resolvedQuery())
                .route(request.route())
                .confidenceScore(request.confidenceScore())
                .needsReviewReason(request.needsReviewReason())
                .build());
        auditCapture(saved, "captured");
        logCandidateBacklog("capture");
        return toResponse(saved);
    }

    @Transactional
    public AiReviewCandidateV2Response reviewCandidate(Long id, AiReviewCandidateReviewV2Request request) {
        AiReviewCandidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No matching AI review candidate: " + id));

        AiReviewCandidateStatus previousStatus = candidate.getStatus();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retentionUntil = now.plusDays(retentionDays(request));
        String reviewer = blankToDefault(request.reviewer(), "admin-ui");

        if (request.action() == AiReviewCandidateReviewAction.START_REVIEW) {
            candidate.startReview(reviewer, now, retentionUntil);
        } else if (request.action() == AiReviewCandidateReviewAction.APPROVE) {
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

        if (candidate.getStatus() == AiReviewCandidateStatus.APPROVED) {
            try {
                knowledgeReindexer.reindexChanged(candidate);
                String cardId = LoggingAiReviewKnowledgeReindexer.cardId(candidate);
                candidate.markPublished(
                        cardId,
                        "ai/app/knowledge/concepts_v2/" + LoggingAiReviewKnowledgeReindexer.categorySlug(candidate) + "/" + cardId + ".json"
                );
            } catch (RuntimeException ex) {
                candidate.markPublishFailed(ex.getMessage(), reviewer, now, retentionUntil);
            }
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

    private void auditCapture(AiReviewCandidate candidate, String reason) {
        auditRepository.save(AiReviewCandidateAudit.builder()
                .candidate(candidate)
                .previousStatus(AiReviewCandidateStatus.PENDING)
                .nextStatus(AiReviewCandidateStatus.PENDING)
                .action(AiReviewCandidateReviewAction.CAPTURE)
                .reviewer("python-runtime")
                .reviewerEditedAnswer(candidate.getDefinitionDraft())
                .reason(reason)
                .build());
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
                .workflowPhase(row.approved() ? AiReviewCandidateWorkflowPhase.APPROVED : phaseForDraft(row.definitionDraft()))
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
                workflowPhaseOf(candidate),
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
                candidate.getPublishError(),
                candidate.getPublishedCardId(),
                candidate.getPublishedCardPath(),
                candidate.getReviewer(),
                candidate.getReviewedAt(),
                candidate.getRetentionUntil(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt()
        );
    }

    private List<AiReviewCandidateV2Response> listApprovedConceptCards(List<AiReviewCandidateV2Response> existing) {
        if (!Files.isDirectory(conceptsV2Root)) {
            return List.of();
        }
        Set<String> existingCardIds = new HashSet<>();
        for (AiReviewCandidateV2Response response : existing) {
            if (response.status() == AiReviewCandidateStatus.APPROVED
                    && response.workflowPhase() == AiReviewCandidateWorkflowPhase.APPROVED
                    && !isBlank(response.publishedCardId())) {
                existingCardIds.add(response.publishedCardId());
            }
        }

        try (Stream<Path> paths = Files.walk(conceptsV2Root)) {
            List<Path> jsonPaths = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> conceptsV2Root.relativize(path).toString()))
                    .toList();
            List<AiReviewCandidateV2Response> responses = new ArrayList<>();
            long syntheticId = -1L;
            for (Path path : jsonPaths) {
                AiReviewCandidateV2Response response = approvedCardResponse(path, syntheticId);
                if (response == null) {
                    continue;
                }
                if (existingCardIds.add(response.publishedCardId())) {
                    responses.add(response);
                    syntheticId--;
                }
            }
            return responses;
        } catch (IOException ex) {
            log.warn("Failed to list approved concepts_v2 cards for AI review candidates", ex);
            return List.of();
        }
    }

    private AiReviewCandidateV2Response approvedCardResponse(Path path, long id) {
        try {
            Map<String, Object> card = objectMapper.readValue(path.toFile(), MAP_TYPE);
            Map<String, Object> review = objectMap(card.get("review"));
            if (!"approved".equalsIgnoreCase(stringValue(review.get("card_status")))) {
                return null;
            }
            String cardId = requiredString(card.get("card_id"));
            String category = blankToDefault(stringValue(card.get("category")), "uncategorized");
            String term = blankToDefault(stringValue(card.get("term")), cardId);
            String definition = conceptDefinition(card);
            String reviewer = blankToDefault(stringValue(review.get("reviewer")), "concepts_v2");
            LocalDateTime reviewedAt = parseDateTime(stringValue(review.get("approved_at")));
            String publishedPath = "ai/app/knowledge/concepts_v2/" + conceptsV2Root.relativize(path).toString().replace('\\', '/');

            return new AiReviewCandidateV2Response(
                    id,
                    cardId,
                    term,
                    category,
                    AiReviewCandidateSource.MANUAL,
                    AiReviewCandidateStatus.APPROVED,
                    AiReviewCandidateWorkflowPhase.APPROVED,
                    definition,
                    "",
                    null,
                    null,
                    null,
                    displaySourceQuestion(card),
                    term,
                    "concepts_v2",
                    null,
                    "approved_concepts_v2_card",
                    null,
                    cardId,
                    publishedPath,
                    reviewer,
                    reviewedAt,
                    null,
                    reviewedAt,
                    reviewedAt
            );
        } catch (RuntimeException | IOException ex) {
            log.warn("Skipping unreadable approved concepts_v2 card: {}", path, ex);
            return null;
        }
    }

    private static String conceptDefinition(Map<String, Object> card) {
        Map<String, Object> payloads = objectMap(card.get("payloads"));
        Map<String, Object> definition = objectMap(payloads.get("CONCEPT_DEFINITION"));
        return blankToDefault(stringValue(definition.get("content")), "");
    }

    private static String displaySourceQuestion(Map<String, Object> card) {
        String readableSourceQuestion = blankToDefault(
                stringValue(card.get("source_question")),
                stringValue(card.get("source_question_text"))
        );
        if (!isBlank(readableSourceQuestion)) {
            return readableSourceQuestion;
        }
        Object values = card.get("source_question_ids");
        if (values instanceof List<?> list && !list.isEmpty()) {
            return stringValue(list.get(0));
        }
        return "";
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private static String requiredString(Object value) {
        String result = stringValue(value);
        if (result.isBlank()) {
            throw new IllegalArgumentException("Required card field is blank");
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDateTime parseDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private static int retentionDays(AiReviewCandidateReviewV2Request request) {
        return request.retentionDays() == null || request.retentionDays() <= 0
                ? DEFAULT_RETENTION_DAYS
                : request.retentionDays();
    }

    private static AiReviewCandidateWorkflowPhase phaseForDraft(String definitionDraft) {
        return definitionDraft == null || definitionDraft.isBlank()
                ? AiReviewCandidateWorkflowPhase.CAPTURED
                : AiReviewCandidateWorkflowPhase.DRAFTED;
    }

    private static AiReviewCandidateWorkflowPhase workflowPhaseOf(AiReviewCandidate candidate) {
        if (candidate.getWorkflowPhase() != null) {
            return candidate.getWorkflowPhase();
        }
        if (candidate.getStatus() == AiReviewCandidateStatus.APPROVED) {
            return AiReviewCandidateWorkflowPhase.APPROVED;
        }
        if (candidate.getStatus() == AiReviewCandidateStatus.REJECTED) {
            return AiReviewCandidateWorkflowPhase.REJECTED;
        }
        if (candidate.getStatus() == AiReviewCandidateStatus.MERGED) {
            return AiReviewCandidateWorkflowPhase.MERGED;
        }
        return phaseForDraft(candidate.getDefinitionDraft());
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

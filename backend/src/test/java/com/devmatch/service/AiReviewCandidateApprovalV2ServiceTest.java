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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewCandidateApprovalV2ServiceTest {

    private final AiReviewCandidateRepository candidateRepository = mock(AiReviewCandidateRepository.class);
    private final AiReviewCandidateAuditRepository auditRepository = mock(AiReviewCandidateAuditRepository.class);
    private final AiReviewCandidateAdminService jsonlService = mock(AiReviewCandidateAdminService.class);
    private final AiReviewMetricSink metricSink = mock(AiReviewMetricSink.class);
    private final AiReviewKnowledgeReindexer knowledgeReindexer = mock(AiReviewKnowledgeReindexer.class);
    private final AiReviewCandidateApprovalV2Service service =
            new AiReviewCandidateApprovalV2Service(candidateRepository, auditRepository, jsonlService, metricSink, knowledgeReindexer);

    @TempDir
    Path tempDir;

    @Test
    void listCandidates_includesApprovedConceptsV2Cards() {
        when(candidateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<AiReviewCandidateV2Response> responses = service.listCandidates();

        List<AiReviewCandidateV2Response> approvedCards = responses.stream()
                .filter(response -> response.workflowPhase() == AiReviewCandidateWorkflowPhase.APPROVED)
                .filter(response -> response.status() == AiReviewCandidateStatus.APPROVED)
                .filter(response -> response.publishedCardId() != null)
                .toList();
        assertThat(approvedCards).hasSize(85);
        assertThat(approvedCards)
                .extracting(AiReviewCandidateV2Response::publishedCardId)
                .contains("algorithm-2", "spring-aop", "python-asyncio");
    }

    @Test
    void listCandidates_usesReadableSourceQuestionForApprovedConceptsV2Cards() throws Exception {
        Path frontendDir = Files.createDirectories(tempDir.resolve("frontend"));
        Files.writeString(frontendDir.resolve("frontend-conditional-rendering.json"), """
                {
                  "card_id": "frontend-conditional-rendering",
                  "category": "frontend",
                  "term": "conditional-rendering",
                  "source_question": "React에서 조건부 렌더링에 사용할 수 없는 방법은?",
                  "source_question_ids": ["frontend:67"],
                  "payloads": {
                    "CONCEPT_DEFINITION": {
                      "content": "React 조건부 렌더링은 조건에 따라 JSX를 선택하는 방식이다."
                    }
                  },
                  "review": {
                    "card_status": "approved",
                    "approved_at": "2026-06-13T06:51:47.613334Z",
                    "reviewer": "codex-assisted-quality-review"
                  }
                }
                """);
        AiReviewCandidateApprovalV2Service tempService = new AiReviewCandidateApprovalV2Service(
                candidateRepository,
                auditRepository,
                jsonlService,
                metricSink,
                knowledgeReindexer,
                new ObjectMapper(),
                tempDir
        );
        when(candidateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<AiReviewCandidateV2Response> responses = tempService.listCandidates();

        assertThat(responses).singleElement()
                .extracting(AiReviewCandidateV2Response::sourceQuestion)
                .isEqualTo("React에서 조건부 렌더링에 사용할 수 없는 방법은?");
    }

    @Test
    void listCandidates_keepsPublishedCardWhenDbExternalIdMatchesCardId() {
        AiReviewCandidate candidate = AiReviewCandidate.builder()
                .id(999L)
                .externalCandidateId("auto-review-dto")
                .term("DTO")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.APPROVED)
                .workflowPhase(AiReviewCandidateWorkflowPhase.APPROVED)
                .definition("DB approved candidate")
                .definitionDraft("")
                .build();
        when(candidateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(candidate));

        List<AiReviewCandidateV2Response> responses = service.listCandidates();

        assertThat(responses)
                .filteredOn(response -> "auto-review-dto".equals(response.publishedCardId()))
                .hasSize(1);
        assertThat(responses)
                .filteredOn(response -> "auto-review-dto".equals(response.externalCandidateId()))
                .hasSize(2);
    }

    @Test
    void listCandidates_keepsPublishedCardWhenDbDuplicateIsNotApproved() {
        AiReviewCandidate candidate = AiReviewCandidate.builder()
                .id(998L)
                .externalCandidateId("auto-rejected-websocket")
                .term("WebSocket")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.REJECTED)
                .workflowPhase(AiReviewCandidateWorkflowPhase.REJECTED)
                .definition("")
                .definitionDraft("")
                .publishedCardId("auto-review-websocket")
                .build();
        when(candidateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(candidate));

        List<AiReviewCandidateV2Response> responses = service.listCandidates();

        assertThat(responses)
                .filteredOn(response -> "auto-review-websocket".equals(response.publishedCardId()))
                .hasSize(2);
        assertThat(responses)
                .filteredOn(response -> "auto-review-websocket".equals(response.publishedCardId()))
                .filteredOn(response -> response.workflowPhase() == AiReviewCandidateWorkflowPhase.APPROVED)
                .hasSize(1);
    }

    @Test
    void captureCandidate_savesPendingAutoCandidate() {
        when(candidateRepository.existsByExternalCandidateId("auto-rest-api")).thenReturn(false);
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.captureCandidate(new AiReviewCandidateCaptureRequest(
                "auto-rest-api",
                "REST API",
                "auto-review",
                "REST API는 URL과 HTTP 메서드로 자원을 다루는 API 설계 방식입니다.",
                "REST API가 뭐야?",
                "REST API",
                "static_fast_path",
                0.8,
                "static_answer_unapproved"
        ));

        assertThat(response.externalCandidateId()).isEqualTo("auto-rest-api");
        assertThat(response.term()).isEqualTo("REST API");
        assertThat(response.source()).isEqualTo(AiReviewCandidateSource.AUTO);
        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.PENDING);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.DRAFTED);
        assertThat(response.definitionDraft()).contains("HTTP");
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
        verify(metricSink).candidateBacklog("capture", 0L);
    }

    @Test
    void captureCandidate_withoutDraftStartsCapturedPhase() {
        when(candidateRepository.existsByExternalCandidateId("auto-empty")).thenReturn(false);
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.captureCandidate(new AiReviewCandidateCaptureRequest(
                "auto-empty",
                "unknown topic",
                "auto-review",
                "",
                "unknown topic?",
                "unknown topic",
                "fallback_template",
                0.4,
                "fallback_used"
        ));

        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.PENDING);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.CAPTURED);
    }

    @Test
    void captureCandidate_updatesBlankDraftForDuplicateExternalCandidate() {
        AiReviewCandidate existing = AiReviewCandidate.builder()
                .externalCandidateId("auto-rest-api")
                .term("REST API")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.PENDING)
                .definition("")
                .definitionDraft("")
                .build();
        when(candidateRepository.existsByExternalCandidateId("auto-rest-api")).thenReturn(true);
        when(candidateRepository.findByExternalCandidateId("auto-rest-api")).thenReturn(Optional.of(existing));

        var response = service.captureCandidate(new AiReviewCandidateCaptureRequest(
                "auto-rest-api",
                "REST API",
                "auto-review",
                "REST API draft",
                "REST API가 뭐야?",
                "REST API",
                "generation",
                0.5,
                "no_match"
        ));

        assertThat(response.definitionDraft()).isEqualTo("REST API draft");
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.DRAFTED);
        assertThat(existing.getRoute()).isEqualTo("generation");
        assertThat(existing.getNeedsReviewReason()).isEqualTo("no_match");
        verify(candidateRepository).save(existing);
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
        verify(candidateRepository, never()).save(argThat(candidate -> candidate != existing));
    }

    @Test
    void startReview_movesPendingCandidateToHumanReviewAndRecordsReviewer() {
        AiReviewCandidate candidate = candidate("auto-human-review", "pagination");
        when(candidateRepository.findById(4L)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reviewCandidate(
                4L,
                new AiReviewCandidateReviewV2Request(
                        AiReviewCandidateReviewAction.START_REVIEW,
                        null,
                        null,
                        "Needs evidence check",
                        null,
                        "reviewer-1",
                        7
                )
        );

        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.PENDING);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.HUMAN_REVIEW);
        assertThat(response.reviewer()).isEqualTo("reviewer-1");
        assertThat(response.reviewedAt()).isNotNull();
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
        verify(knowledgeReindexer, never()).reindexChanged(any(AiReviewCandidate.class));
    }

    @Test
    void editAndApprove_savesReviewerEditedAnswerAndAudit() {
        AiReviewCandidate candidate = candidate("auto-123", "pagination");
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reviewCandidate(
                1L,
                new AiReviewCandidateReviewV2Request(
                        AiReviewCandidateReviewAction.EDIT_AND_APPROVE,
                        "Pagination is the UI/API pattern for splitting large result sets.",
                        "Pagination approved by reviewer.",
                        null,
                        null,
                        "admin-ui",
                        90
                )
        );

        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.APPROVED);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.APPROVED);
        assertThat(response.definition()).isEqualTo("Pagination approved by reviewer.");
        assertThat(response.reviewerEditedAnswer()).isEqualTo("Pagination approved by reviewer.");
        assertThat(response.retentionUntil()).isNotNull();
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
        verify(knowledgeReindexer).reindexChanged(candidate);
    }

    @Test
    void editAndApprove_keepsCandidatePendingWhenV2PublishFails() {
        AiReviewCandidate candidate = candidate("auto-publish-fail", "pagination");
        when(candidateRepository.findById(10L)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("v2 card collision")).when(knowledgeReindexer).reindexChanged(candidate);

        var response = service.reviewCandidate(
                10L,
                new AiReviewCandidateReviewV2Request(
                        AiReviewCandidateReviewAction.EDIT_AND_APPROVE,
                        null,
                        "검토된 페이지네이션 정의",
                        null,
                        null,
                        "admin-ui",
                        90
                )
        );

        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.PENDING);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.PUBLISH_FAILED);
        assertThat(response.publishError()).contains("collision");
        assertThat(response.publishedCardId()).isNull();
    }

    @Test
    void reject_recordsRejectedReasonAndAudit() {
        AiReviewCandidate candidate = candidate("auto-456", "circuit breaker");
        when(candidateRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reviewCandidate(
                2L,
                new AiReviewCandidateReviewV2Request(
                        AiReviewCandidateReviewAction.REJECT,
                        null,
                        null,
                        "Too vague",
                        null,
                        "admin-ui",
                        30
                )
        );

        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.REJECTED);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.REJECTED);
        assertThat(response.rejectedReason()).isEqualTo("Too vague");
        assertThat(response.retentionUntil()).isNotNull();
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
        verify(knowledgeReindexer, never()).reindexChanged(any(AiReviewCandidate.class));
    }

    @Test
    void merge_marksCandidateMergedIntoTarget() {
        AiReviewCandidate candidate = candidate("auto-789", "controller advice");
        when(candidateRepository.findById(3L)).thenReturn(Optional.of(candidate));
        when(candidateRepository.existsById(99L)).thenReturn(true);
        when(candidateRepository.save(any(AiReviewCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reviewCandidate(
                3L,
                new AiReviewCandidateReviewV2Request(
                        AiReviewCandidateReviewAction.MERGE,
                        null,
                        null,
                        "Duplicate",
                        99L,
                        "admin-ui",
                        365
                )
        );

        assertThat(response.status()).isEqualTo(AiReviewCandidateStatus.MERGED);
        assertThat(response.workflowPhase()).isEqualTo(AiReviewCandidateWorkflowPhase.MERGED);
        assertThat(response.mergedIntoId()).isEqualTo(99L);
        assertThat(response.rejectedReason()).isEqualTo("Duplicate");
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
    }

    @Test
    void importJsonl_deduplicatesByExternalCandidateId() {
        when(jsonlService.listCandidates()).thenReturn(List.of(
                new AiReviewCandidateResponse(
                        "auto-123",
                        "pagination",
                        "auto-review",
                        "ai-review-auto-candidate",
                        List.of("pagination"),
                        "",
                        "draft",
                        "needs_draft",
                        false,
                        "",
                        "",
                        "",
                        null,
                        "",
                        List.of(),
                        "",
                        List.of(),
                        "pagination?",
                        "pagination",
                        "fallback_template",
                        0.42,
                        "fallback_used",
                        "2026-05-19T00:00:00Z",
                        "",
                        "",
                        ""
                )
        ));
        when(candidateRepository.existsByExternalCandidateId("auto-123")).thenReturn(true);

        int imported = service.importJsonlCandidates();

        assertThat(imported).isZero();
    }

    @Test
    void importJsonl_updatesBlankDraftOnExistingExternalCandidate() {
        AiReviewCandidate existing = AiReviewCandidate.builder()
                .externalCandidateId("auto-recyclerview")
                .term("RecyclerView")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.PENDING)
                .definition("")
                .definitionDraft("")
                .build();
        when(jsonlService.listCandidates()).thenReturn(List.of(
                new AiReviewCandidateResponse(
                        "auto-recyclerview",
                        "RecyclerView",
                        "auto-review",
                        "ai-review-auto-candidate",
                        List.of("RecyclerView"),
                        "",
                        "RecyclerView는 목록 형태의 데이터를 효율적으로 표시하는 Android 위젯입니다.",
                        "drafted",
                        false,
                        "",
                        "",
                        "",
                        null,
                        "",
                        List.of(),
                        "",
                        List.of(),
                        "RecyclerView가 뭔가요?",
                        "RecyclerView가 뭔가요?",
                        "generation",
                        0.8,
                        "no_match",
                        "2026-05-20T00:00:00Z",
                        "",
                        "",
                        ""
                )
        ));
        when(candidateRepository.existsByExternalCandidateId("auto-recyclerview")).thenReturn(true);
        when(candidateRepository.findByExternalCandidateId("auto-recyclerview")).thenReturn(Optional.of(existing));

        int imported = service.importJsonlCandidates();

        assertThat(imported).isZero();
        assertThat(existing.getDefinitionDraft()).isEqualTo("RecyclerView는 목록 형태의 데이터를 효율적으로 표시하는 Android 위젯입니다.");
        verify(candidateRepository).save(existing);
    }

    @Test
    void importJsonl_skipsUnknownAutoCandidateWithoutReviewContext() {
        when(jsonlService.listCandidates()).thenReturn(List.of(
                new AiReviewCandidateResponse(
                        "auto-da39a3ee5e6b",
                        "unknown",
                        "auto-review",
                        "ai-review-auto-candidate",
                        List.of("unknown"),
                        "",
                        "",
                        "needs_draft",
                        false,
                        "",
                        "",
                        "",
                        null,
                        "",
                        List.of(),
                        "",
                        List.of(),
                        "",
                        "",
                        "fallback_template",
                        0.79,
                        "fallback_used",
                        "2026-05-19T00:00:00Z",
                        "",
                        "",
                        ""
                )
        ));

        int imported = service.importJsonlCandidates();

        assertThat(imported).isZero();
        verify(candidateRepository, never()).save(any(AiReviewCandidate.class));
    }

    private static AiReviewCandidate candidate(String externalId, String term) {
        return AiReviewCandidate.builder()
                .externalCandidateId(externalId)
                .term(term)
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.PENDING)
                .definition("")
                .definitionDraft("draft")
                .build();
    }
}

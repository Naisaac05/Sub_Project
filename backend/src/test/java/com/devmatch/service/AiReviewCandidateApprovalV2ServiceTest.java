package com.devmatch.service;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateResponse;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewV2Request;
import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateAudit;
import com.devmatch.entity.AiReviewCandidateReviewAction;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.devmatch.repository.AiReviewCandidateAuditRepository;
import com.devmatch.repository.AiReviewCandidateRepository;
import com.devmatch.service.ai.AiReviewMetricSink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertThat(response.definition()).isEqualTo("Pagination approved by reviewer.");
        assertThat(response.reviewerEditedAnswer()).isEqualTo("Pagination approved by reviewer.");
        assertThat(response.retentionUntil()).isNotNull();
        verify(auditRepository).save(any(AiReviewCandidateAudit.class));
        verify(knowledgeReindexer).reindexChanged(candidate);
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

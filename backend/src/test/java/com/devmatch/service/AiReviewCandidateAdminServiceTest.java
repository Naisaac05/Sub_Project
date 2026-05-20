package com.devmatch.service;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewRequest;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewCandidateAdminServiceTest {

    @TempDir
    Path tempDir;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void list_reads_jsonl_candidates() throws Exception {
        Path candidates = tempDir.resolve("course_concepts.jsonl");
        Files.writeString(candidates, """
                {"term":"FastAPI","category":"python-backend","approved":false,"definition_draft":"FastAPI draft","critic_risk_level":"low"}
                {"term":"API","category":"distributed-lock","approved":false,"definition_draft":"API draft","critic_risk_level":"high"}
                """);
        var service = new AiReviewCandidateAdminService(objectMapper, candidates);

        List<AiReviewCandidateResponse> result = service.listCandidates();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).term()).isEqualTo("FastAPI");
        assertThat(result.get(0).criticRiskLevel()).isEqualTo("low");
    }

    @Test
    void review_approves_candidate_and_persists_jsonl() throws Exception {
        Path candidates = tempDir.resolve("course_concepts.jsonl");
        Files.writeString(candidates, """
                {"term":"FastAPI","category":"python-backend","approved":false,"definition":"","definition_draft":"FastAPI draft","definition_status":"critic_reviewed"}
                """);
        var service = new AiReviewCandidateAdminService(objectMapper, candidates);

        AiReviewCandidateResponse response = service.reviewCandidate(
                new AiReviewCandidateReviewRequest(
                        "FastAPI",
                        "python-backend",
                        "approve",
                        "FastAPI approved definition",
                        "",
                        "admin-ui"
                )
        );

        assertThat(response.approved()).isTrue();
        assertThat(response.definition()).isEqualTo("FastAPI approved definition");
        assertThat(response.humanReviewStatus()).isEqualTo("approved");
        String saved = Files.readString(candidates);
        assertThat(saved).contains("\"approved\":true");
        assertThat(saved).contains("\"definition\":\"FastAPI approved definition\"");
    }

    @Test
    void list_merges_course_and_auto_candidate_queues_with_source_metadata() throws Exception {
        Path courseCandidates = tempDir.resolve("course_concepts.jsonl");
        Path autoCandidates = tempDir.resolve("auto_candidates.jsonl");
        Files.writeString(courseCandidates, """
                {"term":"FastAPI","category":"python-backend","approved":false,"definition_draft":"FastAPI draft","critic_risk_level":"low"}
                """);
        Files.writeString(autoCandidates, """
                {"candidate_id":"auto-123","term":"pagination","category":"auto-review","approved":false,"definition":"","definition_status":"needs_draft","source":"ai-review-auto-candidate","source_question":"pagination이 뭐야?","resolved_query":"pagination","route":"fallback_template","confidence_score":0.42,"needs_review_reason":"fallback_used","created_at":"2026-05-19T00:00:00Z"}
                """);
        var service = new AiReviewCandidateAdminService(objectMapper, courseCandidates, autoCandidates);

        List<AiReviewCandidateResponse> result = service.listCandidates();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).term()).isEqualTo("FastAPI");
        assertThat(result.get(0).source()).isEqualTo("course-concepts");
        assertThat(result.get(1).candidateId()).isEqualTo("auto-123");
        assertThat(result.get(1).source()).isEqualTo("ai-review-auto-candidate");
        assertThat(result.get(1).sourceQuestion()).isEqualTo("pagination이 뭐야?");
        assertThat(result.get(1).needsReviewReason()).isEqualTo("fallback_used");
        assertThat(result.get(1).confidenceScore()).isEqualTo(0.42);
    }

    @Test
    void review_updates_auto_candidate_queue_by_candidate_id() throws Exception {
        Path courseCandidates = tempDir.resolve("course_concepts.jsonl");
        Path autoCandidates = tempDir.resolve("auto_candidates.jsonl");
        Files.writeString(courseCandidates, """
                {"term":"pagination","category":"python-backend","approved":false,"definition_draft":"Course draft"}
                """);
        Files.writeString(autoCandidates, """
                {"candidate_id":"auto-123","term":"pagination","category":"auto-review","approved":false,"definition":"","definition_status":"needs_draft","source":"ai-review-auto-candidate","source_question":"pagination이 뭐야?","resolved_query":"pagination","route":"fallback_template","confidence_score":0.42,"needs_review_reason":"fallback_used","created_at":"2026-05-19T00:00:00Z"}
                """);
        var service = new AiReviewCandidateAdminService(objectMapper, courseCandidates, autoCandidates);

        AiReviewCandidateResponse response = service.reviewCandidate(
                new AiReviewCandidateReviewRequest(
                        "auto-123",
                        "pagination",
                        "auto-review",
                        "approve",
                        "Pagination approved definition",
                        "",
                        "admin-ui"
                )
        );

        assertThat(response.candidateId()).isEqualTo("auto-123");
        assertThat(response.approved()).isTrue();
        assertThat(Files.readString(autoCandidates)).contains("\"definition\":\"Pagination approved definition\"");
        assertThat(Files.readString(courseCandidates)).doesNotContain("Pagination approved definition");
    }
}

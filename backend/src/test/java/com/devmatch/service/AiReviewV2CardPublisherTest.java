package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiReviewV2CardPublisherTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @TempDir
    Path tempDir;

    @Test
    void approvedCandidatePublishesMinimalV2CardOnly() throws Exception {
        Path root = tempDir.resolve("concepts_v2");
        LoggingAiReviewKnowledgeReindexer publisher =
                new LoggingAiReviewKnowledgeReindexer(root, tempDir.resolve("unused-manifest.json"));

        publisher.reindexChanged(approvedCandidate());

        Path cardPath = root.resolve("auto-review/auto-review-pagination.json");
        assertThat(cardPath).exists();
        Map<String, Object> card = OBJECT_MAPPER.readValue(cardPath.toFile(), MAP_TYPE);
        assertThat(card).containsEntry("card_id", "auto-review-pagination");
        assertThat(card).containsEntry("category", "auto-review");
        assertThat(card).containsEntry("term", "pagination");
        assertThat(card.get("source_question_ids")).isEqualTo(List.of("auto:auto-123"));
        Map<?, ?> payloads = (Map<?, ?>) card.get("payloads");
        assertThat(payloads).hasSize(1);
        assertThat(payloads.containsKey("CONCEPT_DEFINITION")).isTrue();
        Map<?, ?> review = (Map<?, ?>) card.get("review");
        assertThat(review.get("card_status")).isEqualTo("approved");
        assertThat(review.get("payload_status")).isEqualTo(Map.of("CONCEPT_DEFINITION", "approved"));
        assertThat(tempDir.resolve("unused-manifest.json")).doesNotExist();
    }

    @Test
    void differentSourceCollisionDoesNotOverwriteExistingCard() throws Exception {
        Path root = tempDir.resolve("concepts_v2");
        Path cardPath = root.resolve("auto-review/auto-review-pagination.json");
        Files.createDirectories(cardPath.getParent());
        String existing = """
                {"card_id":"auto-review-pagination","source_question_ids":["auto:someone-else"]}
                """;
        Files.writeString(cardPath, existing);
        LoggingAiReviewKnowledgeReindexer publisher =
                new LoggingAiReviewKnowledgeReindexer(root, tempDir.resolve("unused-manifest.json"));

        assertThatThrownBy(() -> publisher.reindexChanged(approvedCandidate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collision");
        assertThat(Files.readString(cardPath)).isEqualTo(existing);
    }

    private static AiReviewCandidate approvedCandidate() {
        AiReviewCandidate candidate = AiReviewCandidate.builder()
                .externalCandidateId("auto-123")
                .term("pagination")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.PENDING)
                .definitionDraft("draft")
                .sourceQuestion("pagination이 무엇인가요?")
                .resolvedQuery("pagination")
                .build();
        candidate.approve(
                "Pagination은 큰 결과 집합을 일정한 크기의 페이지로 나누는 방식이다.",
                "admin-ui",
                LocalDateTime.of(2026, 6, 15, 12, 0),
                LocalDateTime.of(2027, 6, 15, 12, 0)
        );
        return candidate;
    }
}

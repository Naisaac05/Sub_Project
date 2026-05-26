package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingAiReviewKnowledgeReindexerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReindexerContextConfig.class)
            .withPropertyValues(
                    "app.ai-review.generated-concepts-path=../ai/app/knowledge/concepts/generated",
                    "app.ai-review.index-manifest-path=../ai/app/vectorstore/index_manifest.json",
                    "app.ai-review.chroma-reindex-enabled=false"
            );

    @Test
    void reindexChanged_writesGeneratedConceptCardForApprovedCandidate() throws Exception {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        LoggingAiReviewKnowledgeReindexer reindexer =
                new LoggingAiReviewKnowledgeReindexer(conceptRoot, manifestPath);
        AiReviewCandidate candidate = approvedCandidate();

        reindexer.reindexChanged(candidate);

        Path card = conceptRoot.resolve("auto-review-pagination.md");
        assertThat(card).exists();
        String text = Files.readString(card);
        assertThat(text).contains("id: auto-review-pagination");
        assertThat(text).contains("category: auto-review");
        assertThat(text).contains("version: admin-approved-candidate");
        assertThat(text).contains("# pagination");
        assertThat(text).contains("## 핵심 설명");
        assertThat(text).contains("Pagination splits a large result set into stable pages.");
        assertThat(text).contains("원 질문: pagination이 뭐야?");
        assertThat(text).contains("해석된 질문: pagination");
        assertThat(text).contains("승인자: admin-ui");
        assertThat(text).contains("source:auto-123");
    }

    @Test
    void springContext_canInstantiateReindexerBeanWithValueConstructor() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(LoggingAiReviewKnowledgeReindexer.class));
    }

    @Test
    void reindexChanged_updatesManifestWithGeneratedCardEntryAndPreservesExistingEntries() throws Exception {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, """
                {
                  "version": 1,
                  "entries": {
                    "spring-n-plus-one": {
                      "concept_id": "spring-n-plus-one",
                      "path": "app/knowledge/concepts/spring/n-plus-one.md",
                      "content_hash": "old-content",
                      "metadata_hash": "old-metadata"
                    }
                  }
                }
                """);
        LoggingAiReviewKnowledgeReindexer reindexer =
                new LoggingAiReviewKnowledgeReindexer(conceptRoot, manifestPath);

        reindexer.reindexChanged(approvedCandidate());

        String manifest = Files.readString(manifestPath);
        assertThat(manifest).contains("\"spring-n-plus-one\"");
        assertThat(manifest).contains("\"auto-review-pagination\"");
        assertThat(manifest).contains("\"concept_id\" : \"auto-review-pagination\"");
        assertThat(manifest).contains("\"content_hash\"");
        assertThat(manifest).contains("\"metadata_hash\"");
    }

    @Test
    void reindexChanged_writesImmutableManifestFieldsAndSnapshotsPreviousManifest() throws Exception {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, """
                {
                  "version": 1,
                  "entries": {
                    "spring-n-plus-one": {
                      "concept_id": "spring-n-plus-one",
                      "path": "app/knowledge/concepts/spring/n-plus-one.md",
                      "content_hash": "old-content",
                      "metadata_hash": "old-metadata"
                    }
                  }
                }
                """);
        LoggingAiReviewKnowledgeReindexer reindexer =
                new LoggingAiReviewKnowledgeReindexer(conceptRoot, manifestPath);

        reindexer.reindexChanged(approvedCandidate());

        Map<String, Object> manifest = OBJECT_MAPPER.readValue(manifestPath.toFile(), MAP_TYPE);
        assertThat(manifest).containsEntry("schema_version", 2);
        assertThat((String) manifest.get("manifest_hash")).isNotBlank();
        assertThat((String) manifest.get("knowledge_index_version")).startsWith("ki-");
        assertThat(manifest.get("cache_namespace_version")).isEqualTo(manifest.get("knowledge_index_version"));
        assertThat(manifest).containsKey("previous_versions");

        Path snapshotDir = manifestPath.getParent().resolve("manifests");
        assertThat(snapshotDir).isDirectoryContaining(path -> path.getFileName().toString().endsWith(".json"));
    }

    @Test
    void reindexChanged_skipsRejectedCandidate() {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        LoggingAiReviewKnowledgeReindexer reindexer =
                new LoggingAiReviewKnowledgeReindexer(conceptRoot, manifestPath);
        AiReviewCandidate candidate = AiReviewCandidate.builder()
                .externalCandidateId("auto-456")
                .term("circuit breaker")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.REJECTED)
                .definition("Rejected definition")
                .build();

        reindexer.reindexChanged(candidate);

        assertThat(conceptRoot).doesNotExist();
        assertThat(manifestPath).doesNotExist();
    }

    @Test
    void reindexChanged_runsChromaReindexForApprovedCandidate() throws Exception {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        List<List<String>> commands = new ArrayList<>();
        LoggingAiReviewKnowledgeReindexer reindexer = new LoggingAiReviewKnowledgeReindexer(
                conceptRoot,
                manifestPath,
                true,
                "python",
                Path.of("scripts/reindex_knowledge.py"),
                Path.of("../ai"),
                Path.of("app/vectorstore/chroma"),
                "devmatch_concepts",
                "BAAI/bge-m3",
                (command, workingDirectory) -> commands.add(command)
        );

        reindexer.reindexChanged(approvedCandidate());

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0)).containsExactly(
                "python",
                "scripts/reindex_knowledge.py",
                "--chroma",
                "--concept-id",
                "auto-review-pagination",
                "--chroma-path",
                "app/vectorstore/chroma",
                "--collection",
                "devmatch_concepts",
                "--embedding-model",
                "BAAI/bge-m3"
        );
    }

    @Test
    void reindexChanged_throwsWhenChromaReindexFails() {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        LoggingAiReviewKnowledgeReindexer reindexer = new LoggingAiReviewKnowledgeReindexer(
                conceptRoot,
                manifestPath,
                true,
                "python",
                Path.of("scripts/reindex_knowledge.py"),
                Path.of("../ai"),
                Path.of("app/vectorstore/chroma"),
                "devmatch_concepts",
                "BAAI/bge-m3",
                (command, workingDirectory) -> {
                    throw new IllegalStateException("Chroma reindex command failed with exit code 1");
                }
        );

        assertThatThrownBy(() -> reindexer.reindexChanged(approvedCandidate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chroma reindex command failed");
    }

    private static AiReviewCandidate approvedCandidate() {
        AiReviewCandidate candidate = AiReviewCandidate.builder()
                .externalCandidateId("auto-123")
                .term("pagination")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.PENDING)
                .definitionDraft("draft")
                .sourceQuestion("pagination이 뭐야?")
                .resolvedQuery("pagination")
                .build();
        candidate.approve(
                "Pagination splits a large result set into stable pages.",
                "admin-ui",
                LocalDateTime.of(2026, 5, 20, 12, 0),
                LocalDateTime.of(2027, 5, 20, 12, 0)
        );
        return candidate;
    }

    @Import(LoggingAiReviewKnowledgeReindexer.class)
    static class ReindexerContextConfig {
    }
}

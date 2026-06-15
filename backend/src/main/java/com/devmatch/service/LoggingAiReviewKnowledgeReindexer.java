package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class LoggingAiReviewKnowledgeReindexer implements AiReviewKnowledgeReindexer {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path conceptsV2Root;

    @Autowired
    public LoggingAiReviewKnowledgeReindexer(
            @Value("${app.ai-review.concepts-v2-path:../ai/app/knowledge/concepts_v2}") String conceptsV2Root,
            @Value("${app.ai-review.index-manifest-path:../ai/app/vectorstore/index_manifest.json}") String ignoredManifestPath,
            @Value("${app.ai-review.chroma-reindex-enabled:false}") boolean ignoredChromaEnabled,
            @Value("${app.ai-review.chroma-reindex-command:python}") String ignoredCommand,
            @Value("${app.ai-review.chroma-reindex-script:scripts/reindex_knowledge.py}") String ignoredScript,
            @Value("${app.ai-review.chroma-reindex-working-directory:../ai}") String ignoredWorkingDirectory,
            @Value("${app.ai-review.chroma-path:app/vectorstore/chroma}") String ignoredChromaPath,
            @Value("${app.ai-review.chroma-collection:devmatch_concepts}") String ignoredCollection,
            @Value("${app.ai-review.embedding-model:BAAI/bge-m3}") String ignoredEmbeddingModel
    ) {
        this(new ObjectMapper(), Path.of(conceptsV2Root));
    }

    LoggingAiReviewKnowledgeReindexer(Path conceptsV2Root, Path ignoredManifestPath) {
        this(new ObjectMapper(), conceptsV2Root);
    }

    LoggingAiReviewKnowledgeReindexer(
            Path conceptsV2Root,
            Path ignoredManifestPath,
            boolean ignoredChromaEnabled,
            String ignoredCommand,
            Path ignoredScript,
            Path ignoredWorkingDirectory,
            Path ignoredChromaPath,
            String ignoredCollection,
            String ignoredEmbeddingModel,
            ChromaReindexCommandRunner ignoredRunner
    ) {
        this(new ObjectMapper(), conceptsV2Root);
    }

    LoggingAiReviewKnowledgeReindexer(
            ObjectMapper objectMapper,
            Path conceptsV2Root,
            Path ignoredManifestPath,
            boolean ignoredChromaEnabled,
            String ignoredCommand,
            Path ignoredScript,
            Path ignoredWorkingDirectory,
            Path ignoredChromaPath,
            String ignoredCollection,
            String ignoredEmbeddingModel,
            ChromaReindexCommandRunner ignoredRunner
    ) {
        this(objectMapper, conceptsV2Root);
    }

    private LoggingAiReviewKnowledgeReindexer(ObjectMapper objectMapper, Path conceptsV2Root) {
        this.objectMapper = objectMapper;
        this.conceptsV2Root = conceptsV2Root;
    }

    @Override
    public void reindexChanged(AiReviewCandidate candidate) {
        if (candidate.getStatus() != AiReviewCandidateStatus.APPROVED) {
            return;
        }
        String definition = blankToDefault(candidate.getDefinition(), candidate.getReviewerEditedAnswer());
        if (definition.isBlank()) {
            throw new IllegalStateException("Approved candidate requires a definition");
        }

        String cardId = cardId(candidate);
        String category = categorySlug(candidate);
        Path target = conceptsV2Root.resolve(category).resolve(cardId + ".json");
        Path staging = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            assertNoCollision(target, candidate);
            Files.createDirectories(target.getParent());
            Map<String, Object> card = renderCard(candidate, cardId, category, definition);
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(card);
            objectMapper.readValue(json, MAP_TYPE);
            Files.write(staging, json);
            atomicMove(staging, target);
        } catch (IOException ex) {
            deleteQuietly(staging);
            throw new IllegalStateException("Failed to publish approved candidate as v2 card", ex);
        } catch (RuntimeException ex) {
            deleteQuietly(staging);
            throw ex;
        }
    }

    private void assertNoCollision(Path target, AiReviewCandidate candidate) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        Map<String, Object> existing = objectMapper.readValue(target.toFile(), MAP_TYPE);
        Object sources = existing.get("source_question_ids");
        String expected = sourceQuestionId(candidate);
        if (!(sources instanceof List<?> list) || !list.contains(expected)) {
            throw new IllegalStateException("v2 card collision: " + target);
        }
    }

    private Map<String, Object> renderCard(
            AiReviewCandidate candidate,
            String cardId,
            String category,
            String definition
    ) {
        String term = blankToDefault(candidate.getTerm(), cardId);
        String now = Instant.now().atOffset(ZoneOffset.UTC).toString();
        String sourceQuestion = blankToDefault(candidate.getSourceQuestion(), "");
        String resolvedQuery = blankToDefault(candidate.getResolvedQuery(), term);
        String embeddingText = String.join(" ", List.of(term, sourceQuestion, resolvedQuery)).trim();

        LinkedHashMap<String, Object> card = new LinkedHashMap<>();
        card.put("card_id", cardId);
        card.put("category", category);
        card.put("term", term);
        card.put("aliases", List.of(term));
        card.put("source_question_ids", List.of(sourceQuestionId(candidate)));
        card.put("retrieval", Map.of(
                "embedding_text", embeddingText,
                "embedding_hash", "",
                "boost_keywords", List.of(term),
                "intent_types", List.of("CONCEPT_DEFINITION")
        ));
        card.put("payloads", Map.of(
                "CONCEPT_DEFINITION", Map.of("content", definition, "examples", List.of())
        ));
        card.put("review", Map.of(
                "card_status", "approved",
                "payload_status", Map.of("CONCEPT_DEFINITION", "approved"),
                "approved_at", now,
                "reviewer", blankToDefault(candidate.getReviewer(), "admin-ui"),
                "rejected_reason", ""
        ));
        card.put("related_card_ids", List.of());
        card.put("tags", List.of());
        card.put("created_at", now);
        card.put("updated_at", now);
        return card;
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    static String cardId(AiReviewCandidate candidate) {
        return categorySlug(candidate)
                + "-"
                + slug(blankToDefault(candidate.getTerm(), "concept"));
    }

    static String categorySlug(AiReviewCandidate candidate) {
        return slug(blankToDefault(candidate.getCategory(), "auto-review"));
    }

    private static String sourceQuestionId(AiReviewCandidate candidate) {
        return "auto:" + blankToDefault(candidate.getExternalCandidateId(), cardId(candidate));
    }

    private static String slug(String value) {
        String result = NON_SLUG.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        return result.replaceAll("^-|-$", "").replaceAll("-+", "-");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    interface ChromaReindexCommandRunner {
        void run(List<String> command, Path workingDirectory);
    }
}

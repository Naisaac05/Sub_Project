package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class LoggingAiReviewKnowledgeReindexer implements AiReviewKnowledgeReindexer {

    private static final Logger log = LoggerFactory.getLogger(LoggingAiReviewKnowledgeReindexer.class);
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path generatedConceptRoot;
    private final Path manifestPath;
    private final boolean chromaReindexEnabled;
    private final String chromaReindexCommand;
    private final Path chromaReindexScript;
    private final Path chromaReindexWorkingDirectory;
    private final Path chromaPersistPath;
    private final String chromaCollection;
    private final String chromaEmbeddingModel;
    private final ChromaReindexCommandRunner chromaReindexCommandRunner;

    @Autowired
    public LoggingAiReviewKnowledgeReindexer(
            @Value("${app.ai-review.generated-concepts-path:../ai/app/knowledge/concepts/generated}") String generatedConceptRoot,
            @Value("${app.ai-review.index-manifest-path:../ai/app/vectorstore/index_manifest.json}") String manifestPath,
            @Value("${app.ai-review.chroma-reindex-enabled:true}") boolean chromaReindexEnabled,
            @Value("${app.ai-review.chroma-reindex-command:python}") String chromaReindexCommand,
            @Value("${app.ai-review.chroma-reindex-script:scripts/reindex_knowledge.py}") String chromaReindexScript,
            @Value("${app.ai-review.chroma-reindex-working-directory:../ai}") String chromaReindexWorkingDirectory,
            @Value("${app.ai-review.chroma-path:app/vectorstore/chroma}") String chromaPersistPath,
            @Value("${app.ai-review.chroma-collection:devmatch_concepts}") String chromaCollection,
            @Value("${app.ai-review.embedding-model:BAAI/bge-m3}") String chromaEmbeddingModel
    ) {
        this(
                new ObjectMapper(),
                Path.of(generatedConceptRoot),
                Path.of(manifestPath),
                chromaReindexEnabled,
                chromaReindexCommand,
                Path.of(chromaReindexScript),
                Path.of(chromaReindexWorkingDirectory),
                Path.of(chromaPersistPath),
                chromaCollection,
                chromaEmbeddingModel,
                LoggingAiReviewKnowledgeReindexer::runProcess
        );
    }

    LoggingAiReviewKnowledgeReindexer(Path generatedConceptRoot, Path manifestPath) {
        this(
                new ObjectMapper(),
                generatedConceptRoot,
                manifestPath,
                false,
                "python",
                Path.of("scripts/reindex_knowledge.py"),
                Path.of("../ai"),
                Path.of("app/vectorstore/chroma"),
                "devmatch_concepts",
                "BAAI/bge-m3",
                (command, workingDirectory) -> {
                }
        );
    }

    LoggingAiReviewKnowledgeReindexer(
            Path generatedConceptRoot,
            Path manifestPath,
            boolean chromaReindexEnabled,
            String chromaReindexCommand,
            Path chromaReindexScript,
            Path chromaReindexWorkingDirectory,
            Path chromaPersistPath,
            String chromaCollection,
            String chromaEmbeddingModel,
            ChromaReindexCommandRunner chromaReindexCommandRunner
    ) {
        this(
                new ObjectMapper(),
                generatedConceptRoot,
                manifestPath,
                chromaReindexEnabled,
                chromaReindexCommand,
                chromaReindexScript,
                chromaReindexWorkingDirectory,
                chromaPersistPath,
                chromaCollection,
                chromaEmbeddingModel,
                chromaReindexCommandRunner
        );
    }

    LoggingAiReviewKnowledgeReindexer(
            ObjectMapper objectMapper,
            Path generatedConceptRoot,
            Path manifestPath,
            boolean chromaReindexEnabled,
            String chromaReindexCommand,
            Path chromaReindexScript,
            Path chromaReindexWorkingDirectory,
            Path chromaPersistPath,
            String chromaCollection,
            String chromaEmbeddingModel,
            ChromaReindexCommandRunner chromaReindexCommandRunner
    ) {
        this.objectMapper = objectMapper;
        this.generatedConceptRoot = generatedConceptRoot;
        this.manifestPath = manifestPath;
        this.chromaReindexEnabled = chromaReindexEnabled;
        this.chromaReindexCommand = chromaReindexCommand;
        this.chromaReindexScript = chromaReindexScript;
        this.chromaReindexWorkingDirectory = chromaReindexWorkingDirectory;
        this.chromaPersistPath = chromaPersistPath;
        this.chromaCollection = chromaCollection;
        this.chromaEmbeddingModel = chromaEmbeddingModel;
        this.chromaReindexCommandRunner = chromaReindexCommandRunner;
    }

    @Override
    public void reindexChanged(AiReviewCandidate candidate) {
        if (candidate.getStatus() != AiReviewCandidateStatus.APPROVED) {
            return;
        }
        String definition = blankToDefault(candidate.getDefinition(), candidate.getReviewerEditedAnswer());
        if (definition.isBlank()) {
            return;
        }

        String conceptId = conceptId(candidate);
        Path cardPath = generatedConceptRoot.resolve(conceptId + ".md");
        try {
            Files.createDirectories(generatedConceptRoot);
            Files.writeString(cardPath, renderCard(candidate, conceptId, definition), StandardCharsets.UTF_8);
            updateManifest(cardPath, conceptId);
            runChromaReindex(conceptId);
            log.info(
                    "ai_review.reindex.generated_card candidateId={} externalCandidateId={} conceptId={} path={}",
                    candidate.getId(),
                    candidate.getExternalCandidateId(),
                    conceptId,
                    cardPath
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to sync approved AI review candidate to knowledge card", ex);
        }
    }

    private void runChromaReindex(String conceptId) {
        if (!chromaReindexEnabled) {
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(chromaReindexCommand);
        command.add(toCliPath(chromaReindexScript));
        command.add("--chroma");
        command.add("--concept-id");
        command.add(conceptId);
        command.add("--chroma-path");
        command.add(toCliPath(chromaPersistPath));
        command.add("--collection");
        command.add(chromaCollection);
        command.add("--embedding-model");
        command.add(chromaEmbeddingModel);

        chromaReindexCommandRunner.run(command, chromaReindexWorkingDirectory);
    }

    private static String toCliPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String renderCard(AiReviewCandidate candidate, String conceptId, String definition) {
        String term = blankToDefault(candidate.getTerm(), conceptId);
        String category = blankToDefault(candidate.getCategory(), "auto-review");
        String source = blankToDefault(candidate.getExternalCandidateId(), "manual-" + conceptId);
        String sourceQuestion = blankToDefault(candidate.getSourceQuestion(), "-");
        String resolvedQuery = blankToDefault(candidate.getResolvedQuery(), term);
        String reviewer = blankToDefault(candidate.getReviewer(), "admin-ui");

        return """
                ---
                id: %s
                category: %s
                difficulty: intermediate
                version: admin-approved-candidate
                last_updated: %s
                ---

                # %s

                ## 핵심 설명
                %s

                ## 사용 맥락
                - 원 질문: %s
                - 해석된 질문: %s
                - 승인자: %s

                ## 주의할 점
                - 승인된 후보 답변을 우선 사용하되, 더 구체적인 문제 맥락이 있으면 RAG 생성 답변에서 함께 고려한다.

                ## 검색 키워드
                - %s
                - %s
                - source:%s
                """.formatted(
                conceptId,
                category,
                LocalDate.now(),
                term,
                definition,
                sourceQuestion,
                resolvedQuery,
                reviewer,
                term,
                category,
                source
        );
    }

    private void updateManifest(Path cardPath, String conceptId) throws IOException {
        LinkedHashMap<String, Object> manifest = readManifest();
        Map<String, String> previousVersion = snapshotPreviousManifest(manifest);
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) manifest.computeIfAbsent("entries", ignored -> new LinkedHashMap<>());
        String content = Files.readString(cardPath, StandardCharsets.UTF_8);
        entries.put(conceptId, Map.of(
                "concept_id", conceptId,
                "path", cardPath.normalize().toString().replace('\\', '/'),
                "content_hash", sha256(content),
                "metadata_hash", sha256(conceptId)
        ));
        String manifestHash = sha256(canonicalJson(entries));
        String knowledgeIndexVersion = "ki-" + manifestHash.substring(0, 12);
        List<Object> previousVersions = previousVersionsOf(manifest);
        if (previousVersion != null) {
            previousVersions.add(previousVersion);
        }

        manifest.remove("version");
        manifest.put("schema_version", 2);
        manifest.put("knowledge_index_version", knowledgeIndexVersion);
        manifest.put("manifest_hash", manifestHash);
        manifest.put("cache_namespace_version", knowledgeIndexVersion);
        manifest.put("created_at", Instant.now().toString());
        manifest.put("previous_versions", previousVersions);
        manifest.put("entries", entries);
        Files.createDirectories(manifestPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
    }

    private Map<String, String> snapshotPreviousManifest(LinkedHashMap<String, Object> manifest) throws IOException {
        if (!Files.exists(manifestPath) || !Files.isRegularFile(manifestPath)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) manifest.getOrDefault("entries", Map.of());
        String previousHash = String.valueOf(
                manifest.getOrDefault("manifest_hash", sha256(canonicalJson(entries)))
        );
        String previousVersion = String.valueOf(
                manifest.getOrDefault("knowledge_index_version", "ki-" + previousHash.substring(0, 12))
        );
        Path snapshotDir = manifestPath.getParent().resolve("manifests");
        Path snapshotPath = snapshotDir.resolve(previousVersion + ".json");
        Files.createDirectories(snapshotDir);
        Files.copy(manifestPath, snapshotPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return Map.of(
                "knowledge_index_version", previousVersion,
                "manifest_hash", previousHash,
                "path", snapshotPath.toString().replace('\\', '/')
        );
    }

    private static List<Object> previousVersionsOf(Map<String, Object> manifest) {
        Object value = manifest.get("previous_versions");
        if (!(value instanceof List<?> existing)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(existing);
    }

    private String canonicalJson(Object value) throws IOException {
        return objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(value);
    }

    private LinkedHashMap<String, Object> readManifest() {
        if (!Files.exists(manifestPath)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(manifestPath.toFile(), MAP_TYPE);
        } catch (IOException ex) {
            return new LinkedHashMap<>();
        }
    }

    private static String conceptId(AiReviewCandidate candidate) {
        String category = slug(blankToDefault(candidate.getCategory(), "auto-review"));
        String term = slug(blankToDefault(candidate.getTerm(), ""));
        if (term.isBlank()) {
            term = "concept-" + sha256(blankToDefault(candidate.getExternalCandidateId(), "unknown")).substring(0, 8);
        }
        return category + "-" + term;
    }

    private static String slug(String value) {
        String slug = NON_SLUG.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-|-$", "");
        return slug.replaceAll("-+", "-");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                builder.append(String.format("%02x", valueByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void runProcess(List<String> command, Path workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Chroma reindex command failed with exit code " + exitCode + ": " + output
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start Chroma reindex command", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chroma reindex command was interrupted", ex);
        }
    }

    @FunctionalInterface
    interface ChromaReindexCommandRunner {
        void run(List<String> command, Path workingDirectory);
    }
}

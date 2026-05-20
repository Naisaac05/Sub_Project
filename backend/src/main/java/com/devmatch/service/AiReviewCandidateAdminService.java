package com.devmatch.service;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateResponse;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiReviewCandidateAdminService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path candidatePath;
    private final Path autoCandidatePath;

    @Autowired
    public AiReviewCandidateAdminService(
            ObjectMapper objectMapper,
            @Value("${app.ai-review.candidates-path:../ai/app/knowledge/candidates/course_concepts.jsonl}") String candidatePath,
            @Value("${app.ai-review.auto-candidates-path:../ai/app/knowledge/candidates/auto_candidates.jsonl}") String autoCandidatePath
    ) {
        this(objectMapper, Path.of(candidatePath), toOptionalPath(autoCandidatePath));
    }

    AiReviewCandidateAdminService(ObjectMapper objectMapper, Path candidatePath) {
        this(objectMapper, candidatePath, null);
    }

    AiReviewCandidateAdminService(ObjectMapper objectMapper, Path candidatePath, Path autoCandidatePath) {
        this.objectMapper = objectMapper;
        this.candidatePath = candidatePath;
        this.autoCandidatePath = autoCandidatePath;
    }

    public List<AiReviewCandidateResponse> listCandidates() {
        List<CandidateQueue> queues = readQueues();
        return queues.stream()
                .flatMap(queue -> queue.candidates().stream()
                        .map(candidate -> toResponse(candidate, queue.defaultSource())))
                .toList();
    }

    public AiReviewCandidateResponse reviewCandidate(AiReviewCandidateReviewRequest request) {
        for (CandidateQueue queue : readQueues()) {
            LinkedHashMap<String, Object> reviewed = null;
            int changed = 0;

            for (LinkedHashMap<String, Object> candidate : queue.candidates()) {
                if (!matches(candidate, request)) {
                    continue;
                }
                applyReview(candidate, request);
                reviewed = candidate;
                changed++;
            }

            if (changed > 0 && reviewed != null) {
                writeCandidates(queue.path(), queue.candidates());
                return toResponse(reviewed, queue.defaultSource());
            }
        }

        throw new IllegalArgumentException("No matching AI review candidate: " + request.term());
    }

    private void applyReview(LinkedHashMap<String, Object> candidate, AiReviewCandidateReviewRequest request) {
        String action = request.action().toLowerCase();
        String timestamp = OffsetDateTime.now().toString();
        candidate.put("reviewed_at", timestamp);
        candidate.put("reviewer", blankToDefault(request.reviewer(), "admin-ui"));

        if ("approve".equals(action)) {
            String definition = blankToDefault(request.definition(), asString(candidate.get("definition_draft")));
            if (definition.isBlank()) {
                throw new IllegalArgumentException("Approving a candidate requires definition or definitionDraft");
            }
            candidate.put("definition", definition);
            candidate.put("approved", true);
            candidate.put("human_review_status", "approved");
            candidate.put("definition_status", "human_approved");
            candidate.putIfAbsent("rejected_reason", "");
            return;
        }

        if ("reject".equals(action)) {
            candidate.put("approved", false);
            candidate.put("human_review_status", "rejected");
            candidate.put("definition_status", "human_rejected");
            candidate.put("rejected_reason", blankToDefault(request.rejectedReason(), "No reason provided"));
            return;
        }

        if ("hold".equals(action)) {
            candidate.put("approved", false);
            candidate.put("human_review_status", "hold");
            candidate.put("definition_status", "human_hold");
            candidate.put("rejected_reason", blankToDefault(request.rejectedReason(), ""));
            return;
        }

        throw new IllegalArgumentException("Unsupported candidate review action: " + request.action());
    }

    private List<CandidateQueue> readQueues() {
        List<CandidateQueue> queues = new ArrayList<>();
        queues.add(new CandidateQueue(candidatePath, "course-concepts", readCandidates(candidatePath)));
        if (autoCandidatePath != null) {
            queues.add(new CandidateQueue(autoCandidatePath, "auto-candidates", readCandidates(autoCandidatePath)));
        }
        return queues;
    }

    private List<LinkedHashMap<String, Object>> readCandidates(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    rows.add(objectMapper.readValue(line, MAP_TYPE));
                }
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read AI review candidates", ex);
        }
    }

    private void writeCandidates(Path path, List<LinkedHashMap<String, Object>> candidates) {
        try {
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                lines.add(objectMapper.writeValueAsString(candidate));
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write AI review candidates", ex);
        }
    }

    private boolean matches(Map<String, Object> candidate, AiReviewCandidateReviewRequest request) {
        if (!isBlank(request.candidateId())) {
            return asString(candidate.get("candidate_id")).equalsIgnoreCase(request.candidateId());
        }
        if (!asString(candidate.get("term")).equalsIgnoreCase(request.term())) {
            return false;
        }
        return request.category() == null
                || request.category().isBlank()
                || asString(candidate.get("category")).equalsIgnoreCase(request.category());
    }

    private AiReviewCandidateResponse toResponse(Map<String, Object> candidate, String defaultSource) {
        return new AiReviewCandidateResponse(
                asString(candidate.get("candidate_id")),
                asString(candidate.get("term")),
                asString(candidate.get("category")),
                blankToDefault(asString(candidate.get("source")), defaultSource),
                asStringList(candidate.get("aliases")),
                asString(candidate.get("definition")),
                asString(candidate.get("definition_draft")),
                asString(candidate.get("definition_status")),
                Boolean.TRUE.equals(candidate.get("approved")),
                asString(candidate.get("human_review_status")),
                asString(candidate.get("critic_risk_level")),
                asString(candidate.get("critic_recommendation")),
                asMap(candidate.get("critic_feedback")),
                asString(candidate.get("duplicate_status")),
                asStringList(candidate.get("duplicate_concept_ids")),
                asString(candidate.get("duplicate_reason")),
                asStringList(candidate.get("source_question_ids")),
                asString(candidate.get("source_question")),
                asString(candidate.get("resolved_query")),
                asString(candidate.get("route")),
                asDouble(candidate.get("confidence_score")),
                asString(candidate.get("needs_review_reason")),
                asString(candidate.get("created_at")),
                asString(candidate.get("rejected_reason")),
                asString(candidate.get("reviewed_at")),
                asString(candidate.get("reviewer"))
        );
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Path toOptionalPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value);
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private record CandidateQueue(
            Path path,
            String defaultSource,
            List<LinkedHashMap<String, Object>> candidates
    ) {
    }
}

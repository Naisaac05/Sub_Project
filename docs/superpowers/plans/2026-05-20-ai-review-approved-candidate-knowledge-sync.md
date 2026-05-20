# AI Review Approved Candidate Knowledge Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make admin-approved AI review candidates immediately available to Python RAG by writing generated concept cards and updating the knowledge manifest.

**Architecture:** Keep `AiReviewKnowledgeReindexer` as the approval hook. Replace the logging-only implementation with a filesystem implementation that renders a deterministic markdown concept card and updates `ai/app/vectorstore/index_manifest.json`; then make the Python lightweight generated-card path understand the new `핵심 설명` section title.

**Tech Stack:** Spring Boot 3, Java 17, JUnit 5, AssertJ, Python unittest, existing markdown concept-card parser.

---

## File Structure

- Modify `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`
  - Convert from log-only hook to file writer.
  - Keep the class name to avoid bean churn; its behavior becomes real sync plus logging.

- Create `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`
  - Unit-test markdown rendering, skip behavior, and manifest update using temporary directories.

- Modify `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`
  - Add negative verification that reject does not call the reindexer.

- Modify `ai/app/workflow/lightweight_answers.py`
  - Let generated-card answers read `핵심 설명` first, then fall back to the legacy mojibake section title.

- Create `ai/tests/test_generated_card_fast_path.py`
  - Verify `resolve_lightweight_answer()` can return a generated card with `핵심 설명`.

---

### Task 1: Backend Writer Test

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`
- Modify: none

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`:

```java
package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAiReviewKnowledgeReindexerTest {

    @TempDir
    Path tempDir;

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest
```

Expected: FAIL because `LoggingAiReviewKnowledgeReindexer` does not have the test constructor and does not write files.

---

### Task 2: Backend Writer Implementation

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`
- Test: `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`

- [ ] **Step 1: Implement minimal filesystem writer**

Replace `LoggingAiReviewKnowledgeReindexer.java` with:

```java
package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class LoggingAiReviewKnowledgeReindexer implements AiReviewKnowledgeReindexer {

    private static final Logger log = LoggerFactory.getLogger(LoggingAiReviewKnowledgeReindexer.class);
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final Path generatedConceptRoot;
    private final Path manifestPath;

    public LoggingAiReviewKnowledgeReindexer(
            @Value("${app.ai-review.generated-concepts-path:../ai/app/knowledge/concepts/generated}") String generatedConceptRoot,
            @Value("${app.ai-review.index-manifest-path:../ai/app/vectorstore/index_manifest.json}") String manifestPath
    ) {
        this(Path.of(generatedConceptRoot), Path.of(manifestPath));
    }

    LoggingAiReviewKnowledgeReindexer(Path generatedConceptRoot, Path manifestPath) {
        this.generatedConceptRoot = generatedConceptRoot;
        this.manifestPath = manifestPath;
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
        Files.createDirectories(manifestPath.getParent());
        String relativePath = cardPath.normalize().toString().replace('\\', '/');
        String content = Files.readString(cardPath, StandardCharsets.UTF_8);
        String entry = """
                {
                  "version": 1,
                  "entries": {
                    "%s": {
                      "concept_id": "%s",
                      "path": "%s",
                      "content_hash": "%s",
                      "metadata_hash": "%s"
                    }
                  }
                }
                """.formatted(conceptId, conceptId, relativePath, sha256(content), sha256(conceptId));
        Files.writeString(manifestPath, entry, StandardCharsets.UTF_8);
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
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run:

```bash
cd backend
.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest
```

Expected: PASS.

---

### Task 3: Manifest and Skip Behavior Tests

**Files:**
- Modify: `backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java`
- Modify: `backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java`

- [ ] **Step 1: Add failing tests**

Append these tests to `LoggingAiReviewKnowledgeReindexerTest`:

```java
    @Test
    void reindexChanged_updatesManifestWithGeneratedCardEntry() throws Exception {
        Path conceptRoot = tempDir.resolve("concepts/generated");
        Path manifestPath = tempDir.resolve("vectorstore/index_manifest.json");
        LoggingAiReviewKnowledgeReindexer reindexer =
                new LoggingAiReviewKnowledgeReindexer(conceptRoot, manifestPath);

        reindexer.reindexChanged(approvedCandidate());

        String manifest = Files.readString(manifestPath);
        assertThat(manifest).contains("\"auto-review-pagination\"");
        assertThat(manifest).contains("\"concept_id\": \"auto-review-pagination\"");
        assertThat(manifest).contains("\"content_hash\"");
        assertThat(manifest).contains("\"metadata_hash\"");
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
```

- [ ] **Step 2: Run tests**

Run:

```bash
cd backend
.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest
```

Expected: PASS if Task 2 already covers manifest and skip behavior; otherwise FAIL and fix the implementation to pass.

---

### Task 4: Approval Service Negative Interaction

**Files:**
- Modify: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`

- [ ] **Step 1: Add or update reject assertion**

In `reject_recordsRejectedReasonAndAudit`, add:

```java
        verify(knowledgeReindexer, never()).reindexChanged(any(AiReviewCandidate.class));
```

- [ ] **Step 2: Run service tests**

Run:

```bash
cd backend
.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest
```

Expected: PASS.

---

### Task 5: Python Generated Card Fast Path

**Files:**
- Modify: `ai/app/workflow/lightweight_answers.py`
- Create: `ai/tests/test_generated_card_fast_path.py`

- [ ] **Step 1: Write failing Python test**

Create `ai/tests/test_generated_card_fast_path.py`:

```python
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from app.rag.documents import load_concept_cards
from app.workflow.intent import FreeQuestionIntent
from app.workflow.lightweight_answers import resolve_lightweight_answer


class GeneratedCardFastPathTest(unittest.TestCase):
    def test_generated_card_uses_korean_core_section(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            card = root / "auto-review-pagination.md"
            card.write_text(
                """---
id: auto-review-pagination
category: auto-review
difficulty: intermediate
version: admin-approved-candidate
last_updated: 2026-05-20
---

# pagination

## 핵심 설명
Pagination approved by admin.

## 검색 키워드
- pagination
""",
                encoding="utf-8",
            )
            cards = load_concept_cards(root)
            intent = FreeQuestionIntent("concept_definition", "latest_question_only", "pagination")

            with patch("app.workflow.lightweight_answers._concept_cards_by_id", return_value={cards[0].concept_id: cards[0]}):
                answer = resolve_lightweight_answer(
                    "pagination이 뭐야?",
                    intent,
                    matched_concept_id="auto-review-pagination",
                )

        self.assertIsNotNone(answer)
        self.assertEqual(answer.answer, "Pagination approved by admin.")
        self.assertEqual(answer.route, "generated_card_fast_path")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd ai
python -m unittest tests.test_generated_card_fast_path -v
```

Expected: FAIL because `_concept_card_answer_for()` only reads the legacy section title.

- [ ] **Step 3: Implement minimal Python change**

Change `_concept_card_answer_for()` in `ai/app/workflow/lightweight_answers.py` to:

```python
def _concept_card_answer_for(concept_id: str | None) -> str | None:
    if not concept_id:
        return None

    card = _concept_cards_by_id().get(concept_id)
    if not card:
        return None
    return (
        card.sections.get("핵심 설명")
        or card.sections.get("?ë“­ë–– ?ã…»ì±¸")
        or None
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd ai
python -m unittest tests.test_generated_card_fast_path -v
```

Expected: PASS.

---

### Task 6: Final Verification

**Files:**
- No new edits unless a verification failure identifies a defect.

- [ ] **Step 1: Run backend targeted tests**

Run:

```bash
cd backend
.\gradlew.bat test --tests com.devmatch.service.LoggingAiReviewKnowledgeReindexerTest --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest --tests com.devmatch.config.CorsConfigTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Python targeted tests**

Run:

```bash
cd ai
python -m unittest tests.test_generated_card_fast_path tests.test_promotion_workflow -v
```

Expected: all tests pass.

- [ ] **Step 3: Run frontend build only if frontend files changed**

If no frontend files changed, skip this step and state it was skipped because the implementation is backend/Python only.

- [ ] **Step 4: Check git diff**

Run:

```bash
git diff -- backend/src/main/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexer.java backend/src/test/java/com/devmatch/service/LoggingAiReviewKnowledgeReindexerTest.java backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java ai/app/workflow/lightweight_answers.py ai/tests/test_generated_card_fast_path.py docs/superpowers/plans/2026-05-20-ai-review-approved-candidate-knowledge-sync.md
```

Expected: diff only contains the planned changes.

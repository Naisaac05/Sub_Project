---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰에서 자동 생성된 답변의 메타데이터 컴파일 실패 오류 원인 분석 및 해결 기록"

---

# AI review generated answer metadata compile failure

- Date: 2026-05-19
- Area: backend
- Severity: medium

## Symptoms

Running the focused backend AI review tests failed at compile time:

- `RuleBasedAiReviewService.java:375` and `RuleBasedAiReviewService.java:407` tried to assign `Optional<AiGeneratedAnswer>` to `Optional<String>`.
- `RuleBasedAiReviewService.java:611` referenced `StudySummary`, but that helper type was missing.

After restoring compilation, `RuleBasedAiReviewServiceTest.submitAnswer_saves_python_freeQuestionMetadataOnAiMessage` showed that Python free-question metadata such as `aiRoute`, `aiCandidateId`, and `aiLatencyMs` was no longer being saved.

## Root Cause

The Python AI client API had evolved from plain `String` answers to `AiGeneratedAnswer`, but parts of `RuleBasedAiReviewService` still used the older string-only flow. Mapping Python responses to `.answer()` fixed compilation in one path but dropped metadata before persisting the `AiReviewMessage`.

The study summary flow also used a builder-style `StudySummary` helper that was no longer defined in the service file, leaving a compile-time dangling reference.

## Fix

- Updated free-question generation to keep `AiGeneratedAnswer` until the AI message is persisted: `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java`.
- Added an overloaded `saveAiMessage(..., AiGeneratedAnswer)` that stores route, resolved query, correction type, concept id, answer style, quality flags, candidate id, and latency.
- Restored `StudySummary` as a small private record and changed its call site to constructor-style creation.
- Used Unicode escapes for summary section labels so Java source encoding does not corrupt Korean labels during compilation.

## Prevention / Notes

- When `PythonAiReviewClient` response types change, service-layer tests should assert both answer content and metadata persistence.
- Keep summary label strings either ASCII or Unicode-escaped in Java files that already contain mojibake-prone content.
- Verification run: `./gradlew.bat test --tests com.devmatch.config.AiReviewPropertiesTest --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest`.

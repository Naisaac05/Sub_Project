# AI Review Course Scope Gate Design

## Goal

AI review free-question answers must stay inside the current course/test review scope.

The already implemented A policy blocks obvious non-learning questions such as meals or phone recommendations. This B policy adds a course-aware gate:

- Current course questions can be answered.
- Current problem questions can be answered.
- Current course card misses can use Ollama and create review candidates.
- Technical questions from another course are blocked.
- Off-topic questions remain blocked.

## Current Context

Backend `Question` links to `Test`, and `Test.category` represents the course/test category. `AiReviewSession` also stores `courseKey`. Python `AiGenerateRequest` currently receives the question text, options, correct answer, selected answer, and user question, but not explicit `course_id`, `test_id`, `question_id`, or source question id.

RAG v2 cards store `source_question_ids` such as `frontend:4`, `java:11`, and `algorithm:2`. The prefix can be used as a course key. Current v2 approved fast path searches all approved cards and does not filter by current course.

## Decision

Use a deterministic course scope gate before v2 fast path and before Ollama generation.

Add explicit request metadata:

- `course_id`: normalized course key, preferably `Test.category` or `AiReviewSession.courseKey`
- `test_id`: backend test id
- `question_id`: backend question id
- `source_question_id`: derived stable id when available, for example `frontend:4`

The source id derivation should be conservative:

- Prefer explicit value if backend can provide it.
- Otherwise derive from `course_id` plus `Question.orderIndex`, for example `frontend:4`.
- If course id is unavailable, scope gate enters `scope_unknown` and keeps current behavior except for A policy off-topic blocking.

## Scope Categories

`OFF_TOPIC`

Everyday/life advice or unrelated non-technical requests. Already handled by A policy.

`CURRENT_PROBLEM`

The question refers to the current wrong answer, selected option, correct option, or asks why the shown problem is wrong/right. Answer using current problem context.

`COURSE_CARD_HIT`

The learner asks about a concept whose approved v2 card belongs to the current course. Answer via v2 approved payload.

`COURSE_CARD_MISS`

The learner asks a technical question that appears to belong to the current course, but no approved card answers it. Allow Ollama fallback and candidate capture.

`OUT_OF_COURSE_TECH`

The learner asks a technical question that matches an approved card or strong course prefix outside the current course. Do not answer and do not create a candidate.

`SCOPE_UNKNOWN`

The request lacks enough metadata to determine current course. Keep current v2/Ollama behavior, but emit a quality flag so the missing metadata is visible.

## Course Matching Rules

Normalize course ids to the existing concepts_v2 prefixes:

- `java`, `java-backend`, `JAVA-BASIC` -> `java`
- `spring`, `spring-backend` -> `spring`
- `frontend`, `react`, `nextjs` -> `frontend`
- `python`, `python-backend` -> `python`
- `algorithm`, `coding-test` -> `algorithm`

For a card, allowed courses come from:

- `card.category`
- each `source_question_ids` prefix before `:`

A card belongs to the current course if any normalized course id matches.

## Runtime Flow

1. Backend sends course/test/question metadata in `PythonAiRequest`.
2. Python `retrieve_context_node` keeps A policy intent classification.
3. Before v2 approved fast path, compute course scope.
4. If `OUT_OF_COURSE_TECH`, return a template redirect:

   > 이 질문은 현재 코스 복습 범위 밖의 기술 주제라 여기서는 답변하지 않을게요. 현재 문제의 개념, 정답 근거, 오답 이유를 질문해 주세요.

5. If current course is known, v2 fast path only searches approved cards whose category/source ids match the current course.
6. If no current-course card hits but the question is still technical and not out-of-course, allow Ollama fallback and candidate capture.
7. Candidate capture is blocked for `OFF_TOPIC`, `OUT_OF_COURSE_TECH`, and `SCOPE_UNKNOWN` unless explicitly changed later.

## Backend Follow-Up Policy

No learning follow-up is appended for:

- `off_topic_redirect`
- `out_of_course_redirect`

Normal educational follow-up remains for current course answers.

## Observability

Python response metadata should expose:

- `route`: `out_of_course_redirect`, `v2_approved_fast_path`, `generation`, etc.
- `quality_flags`: include one of `off_topic`, `out_of_course`, `scope_unknown`, `course_scope_applied`
- `matched_concept_id`: when a card match caused an out-of-course block
- `resolved_query`: learner query or resolved concept

## Tests

Python:

- frontend scope + `useEffect가 뭐야?` -> v2/current course path allowed
- frontend scope + `@Transactional이 뭐야?` -> `out_of_course_redirect`, generator not called
- frontend scope + unknown frontend concept -> generator allowed, candidate may be captured
- missing course metadata -> no out-of-course block, `scope_unknown` flag
- off-topic meal/phone questions remain `off_topic_redirect`

Backend:

- `PythonAiRequest` includes course/test/question metadata for free-question.
- `out_of_course_redirect` answer does not append `다음 확인 질문`.

## Non-Goals

- Do not modify concepts_v2 payloads.
- Do not approve or reject cards.
- Do not introduce an LLM judge for scope classification.
- Do not block all card misses; only out-of-course technical questions are blocked.

## Rollout

Implement behind deterministic code paths without new external services. If metadata is missing, behavior degrades to current A policy plus a `scope_unknown` flag instead of breaking answers.

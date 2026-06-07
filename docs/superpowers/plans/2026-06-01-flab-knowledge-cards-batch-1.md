# F-Lab 면접 지식카드 + 골든셋 (배치 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** F-Lab Java 기술면접 자료집의 핵심 문항 3개를 검증된 지식카드(Tier0)로 추가하고, 각 문항이 올바른 카드로 검색·분류되는지 골든 평가셋으로 회귀 보호한다.

**Architecture:** 설계 문서 [2026-06-01-ai-review-fast-accurate-architecture-design.md](../specs/2026-06-01-ai-review-fast-accurate-architecture-design.md) §10(F-Lab 콘텐츠 채택)의 첫 실행. 코드는 건드리지 않고 **콘텐츠만 추가**(카드 `.md` + 골든 `.jsonl`)하므로 미커밋 WIP와 충돌하지 않는다. 각 카드는 "골든 행을 먼저 넣어 실패를 확인(red) → 카드를 추가해 통과(green)"하는 TDD 루프로 진행한다.

**Tech Stack:** Python, Markdown 지식카드(`ai/app/knowledge/concepts/`), JSONL 골든셋(`ai/evals/golden_dataset.jsonl`), 린트·평가·재색인 스크립트(`ai/scripts/`).

---

## 사전 지식 (엔지니어가 알아야 할 것)

- **카드 형식**(린트 강제, `ai/scripts/lint_knowledge_cards.py`):
  - frontmatter 필수 키: `id`, `category`, `difficulty`, `version`, `last_updated`(YYYY-MM-DD).
  - 제목 `# <title>`.
  - 필수 섹션 4개: `## 핵심 설명`, `## 대표 해결`, `## 흔한 오해`, `## 평가 키워드`.
  - `평가 키워드`는 `-` 불릿 **2개 이상**.
  - `id`(=concept_id)는 전역 유일.
- **카드 위치**: `ai/app/knowledge/concepts/**/*.md` 는 `load_concept_cards()`가 재귀로 자동 발견한다([documents.py:27](../../../ai/app/rag/documents.py)). 새 파일을 두면 잡힌다.
- **골든 행 형식**(1줄 1 JSON, `ai/evals/golden_dataset.jsonl`):
  ```json
  {"id":"<유일>","question":"<질문>","expected_concepts":["<concept-id>"],"expected_intent":"concept_definition","expected_rag_policy":"latest_question_only"}
  ```
  비교형 질문은 `"expected_sub_intent":"comparison"`, 그 외 기술어가 섞인 질문은 `"related"`를 추가할 수 있다.
- **명령은 모두 `ai/` 디렉터리에서 실행**한다. 파이썬은 `ai/.venv/Scripts/python.exe`(없으면 `python`).
- **검증 3종**:
  - 린트: `python scripts/lint_knowledge_cards.py` → `Knowledge card lint passed`.
  - 재색인(파일시스템 manifest): `python scripts/reindex_knowledge.py`.
  - 골든 평가(결정론, Ollama 호출 없음): `python scripts/evaluate_lightweight_rag.py`.

> ⚠️ `concept_id`(카드 frontmatter `id`)와 골든 행의 `expected_concepts` 값은 **글자까지 정확히 일치**해야 한다. 불일치는 평가 실패의 가장 흔한 원인이다.

---

## Task 1: hashCode 카드 (F-Lab 질문 1)

F-Lab 질문 1은 equals/hashCode의 역할과 차이를 묻고, "hashCode는 메모리 주소를 리턴한다"를 **잘못된 답변 케이스**로 명시한다. equals 카드([concepts/java/equals.md](../../../ai/app/knowledge/concepts/java/equals.md))는 이미 있으므로 **hashCode 카드**를 추가한다.

**Files:**
- Modify: `ai/evals/golden_dataset.jsonl` (행 추가)
- Create: `ai/app/knowledge/concepts/java/hashcode.md`

- [ ] **Step 1: 골든 행 먼저 추가 (red)**

`ai/evals/golden_dataset.jsonl` **맨 끝에** 아래 3줄을 추가한다(각 줄은 한 줄짜리 JSON, 마지막에 빈 줄 없이):

```json
{"id":"java-hashcode-001","question":"hashCode는 뭐야?","expected_concepts":["java-hashcode"],"expected_intent":"concept_definition","expected_rag_policy":"latest_question_only"}
{"id":"java-hashcode-002","question":"hashCode가 뭐하는 메서드야?","expected_concepts":["java-hashcode"],"expected_intent":"concept_definition","expected_rag_policy":"latest_question_only"}
{"id":"java-hashcode-003","question":"equals랑 hashCode 차이가 뭐야?","expected_concepts":["java-hashcode"],"expected_intent":"concept_definition","expected_sub_intent":"comparison","expected_rag_policy":"latest_question_only"}
```

- [ ] **Step 2: 평가 실행 → 실패 확인 (red)**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: 새 `java-hashcode-*` 행에서 **검색 실패**(expected_concept `java-hashcode`가 아직 코퍼스에 없어 검색되지 않음). 종료 코드 ≠ 0 또는 실패 항목 보고.

- [ ] **Step 3: hashCode 카드 추가 (green 준비)**

Create `ai/app/knowledge/concepts/java/hashcode.md`:

```markdown
---
id: java-hashcode
category: java
difficulty: beginner
version: java17
last_updated: 2026-06-01
---

# hashCode

## 핵심 설명
hashCode는 객체를 HashMap·HashSet 같은 해시 기반 컬렉션에서 빠르게 분류하고 탐색하기 위해 정수 해시값을 반환하는 메서드다.

## 대표 해결
- equals를 재정의하면 hashCode도 반드시 함께 재정의한다. equals로 같다고 판단되는 두 객체는 같은 hashCode를 가져야 한다.
- 핵심 필드를 기준으로 `Objects.hash(field1, field2)`를 사용해 구현한다.

## 흔한 오해
- hashCode가 객체의 메모리 주소를 그대로 리턴한다고 오해하기 쉽다. 실제 값은 JVM 구현에 따라 다르고, 재정의하면 메모리 주소와 무관한 값을 반환한다.
- hashCode가 같으면 두 객체가 반드시 equals true라고 오해하기 쉽다. 해시 충돌로 값이 같아도 equals는 다를 수 있다.

## 평가 키워드
- 해시값
- equals-hashCode 계약
- HashMap
- 해시 충돌
```

- [ ] **Step 4: 재색인**

Run: `python scripts/reindex_knowledge.py`
Expected: 새 카드를 포함해 manifest 갱신(에러 없이 종료).

- [ ] **Step 5: 평가 실행 → 통과 확인 (green)**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: `java-hashcode-001/002/003` 모두 통과(검색=`java-hashcode`, intent=concept_definition). 기존 행 회귀 없음.

- [ ] **Step 6: 린트**

Run: `python scripts/lint_knowledge_cards.py`
Expected: `Knowledge card lint passed`.

- [ ] **Step 7: 커밋**

```bash
git add ai/app/knowledge/concepts/java/hashcode.md ai/evals/golden_dataset.jsonl ai/app/vectorstore/index_manifest.json
git commit -m "feat(ai-knowledge): hashCode 지식카드 + 골든셋 (F-Lab Q1)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: ArrayList 내부 구현 카드 (F-Lab 질문 4)

F-Lab 질문 4는 "ArrayList는 내부적으로 어떻게 구현되어 있나"와 꼬리질문 "크기가 차면 어떻게 무한히 데이터를 받나"를 묻는다.

**Files:**
- Modify: `ai/evals/golden_dataset.jsonl`
- Create: `ai/app/knowledge/concepts/java/arraylist.md`

- [ ] **Step 1: 골든 행 먼저 추가 (red)**

`ai/evals/golden_dataset.jsonl` 맨 끝에 3줄 추가:

```json
{"id":"java-arraylist-001","question":"ArrayList는 뭐야?","expected_concepts":["java-arraylist"],"expected_intent":"concept_definition","expected_rag_policy":"latest_question_only"}
{"id":"java-arraylist-002","question":"ArrayList는 내부적으로 어떻게 구현돼 있어?","expected_concepts":["java-arraylist"],"expected_intent":"concept_definition","expected_sub_intent":"related","expected_rag_policy":"latest_question_only"}
{"id":"java-arraylist-003","question":"ArrayList는 크기가 차면 어떻게 더 받아?","expected_concepts":["java-arraylist"],"expected_intent":"concept_definition","expected_sub_intent":"related","expected_rag_policy":"latest_question_only"}
```

- [ ] **Step 2: 평가 실행 → 실패 확인 (red)**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: `java-arraylist-*` 검색 실패(카드 없음).

- [ ] **Step 3: ArrayList 카드 추가**

Create `ai/app/knowledge/concepts/java/arraylist.md`:

```markdown
---
id: java-arraylist
category: java
difficulty: beginner
version: java17
last_updated: 2026-06-01
---

# ArrayList

## 핵심 설명
ArrayList는 내부적으로 배열(Object[])에 요소를 저장하는 List 구현체로, 인덱스 기반 임의 접근이 O(1)로 빠르다.

## 대표 해결
- 내부 배열이 가득 차면 더 큰 배열을 새로 만들어 기존 요소를 복사하며 자동으로 확장한다(보통 1.5배).
- 끝에 추가(add)는 분할상환 O(1)이지만, 중간 삽입·삭제는 뒤 요소 이동 때문에 O(n)이다.

## 흔한 오해
- ArrayList가 LinkedList처럼 노드를 포인터로 연결한 구조라고 오해하기 쉽다. 실제는 연속된 배열이다.
- 크기가 무제한이라 비용 없이 늘어난다고 오해하기 쉽다. 실제로는 배열 복사·확장 비용이 발생한다.

## 평가 키워드
- 동적 배열
- 자동 확장
- 인덱스 접근
- 분할상환 O(1)
```

- [ ] **Step 4: 재색인**

Run: `python scripts/reindex_knowledge.py`
Expected: 에러 없이 종료.

- [ ] **Step 5: 평가 실행 → 통과 확인 (green)**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: `java-arraylist-001/002/003` 통과, 기존 행 회귀 없음.

- [ ] **Step 6: 린트**

Run: `python scripts/lint_knowledge_cards.py`
Expected: `Knowledge card lint passed`.

- [ ] **Step 7: 커밋**

```bash
git add ai/app/knowledge/concepts/java/arraylist.md ai/evals/golden_dataset.jsonl ai/app/vectorstore/index_manifest.json
git commit -m "feat(ai-knowledge): ArrayList 내부구현 지식카드 + 골든셋 (F-Lab Q4)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Blocking / Non-Blocking IO 카드 (F-Lab 질문 8)

F-Lab 질문 8은 Blocking IO vs Non-Blocking IO 차이와, 꼬리질문(스레드가 멈추는 동안 CPU, Tomcat이 스레드를 많이 띄우는 이유)을 묻는다.

**Files:**
- Modify: `ai/evals/golden_dataset.jsonl`
- Create: `ai/app/knowledge/concepts/cs/blocking-io.md`

- [ ] **Step 1: 골든 행 먼저 추가 (red)**

`ai/evals/golden_dataset.jsonl` 맨 끝에 3줄 추가:

```json
{"id":"cs-blocking-io-001","question":"Blocking IO와 Non-Blocking IO 차이가 뭐야?","expected_concepts":["cs-blocking-io"],"expected_intent":"concept_definition","expected_sub_intent":"comparison","expected_rag_policy":"latest_question_only"}
{"id":"cs-blocking-io-002","question":"Blocking IO가 뭐야?","expected_concepts":["cs-blocking-io"],"expected_intent":"concept_definition","expected_rag_policy":"latest_question_only"}
{"id":"cs-blocking-io-003","question":"논블로킹 IO는 뭐야?","expected_concepts":["cs-blocking-io"],"expected_intent":"concept_definition","expected_rag_policy":"latest_question_only"}
```

- [ ] **Step 2: 평가 실행 → 실패 확인 (red)**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: `cs-blocking-io-*` 검색 실패(카드 없음).

- [ ] **Step 3: Blocking IO 카드 추가**

`ai/app/knowledge/concepts/cs/` 디렉터리가 없으면 함께 생성된다. Create `ai/app/knowledge/concepts/cs/blocking-io.md`:

```markdown
---
id: cs-blocking-io
category: cs
difficulty: intermediate
version: general
last_updated: 2026-06-01
---

# Blocking IO와 Non-Blocking IO

## 핵심 설명
Blocking IO는 입출력이 끝날 때까지 호출한 스레드가 멈춰서(블록) 기다리는 방식이고, Non-Blocking IO는 기다리지 않고 즉시 반환해 그 스레드가 다른 일을 할 수 있는 방식이다.

## 대표 해결
- Blocking은 요청 하나당 스레드 하나 모델(예: Tomcat 기본)에 적합하고 코드가 단순하다.
- Non-Blocking은 적은 수의 스레드로 많은 연결을 처리(예: Netty, Spring WebFlux)해 CPU 활용률을 높인다.

## 흔한 오해
- Blocking으로 기다리는 동안 스레드가 CPU를 계속 쓴다고 오해하기 쉽다. 실제로는 대기(waiting) 상태라 CPU를 쓰지 않고 놀린다. 그래서 Tomcat은 동시 요청만큼 스레드를 많이 띄운다.
- Non-Blocking이 항상 더 빠르다고 오해하기 쉽다. 구현 복잡도가 높고, CPU 바운드 작업에는 이점이 작다.

## 평가 키워드
- 블로킹 대기
- 논블로킹
- 요청당 스레드
- CPU 활용률
```

- [ ] **Step 4: 재색인**

Run: `python scripts/reindex_knowledge.py`
Expected: 에러 없이 종료.

- [ ] **Step 5: 평가 실행 → 통과 확인 (green)**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: `cs-blocking-io-001/002/003` 통과, 기존 행 회귀 없음.

- [ ] **Step 6: 린트**

Run: `python scripts/lint_knowledge_cards.py`
Expected: `Knowledge card lint passed`.

- [ ] **Step 7: 커밋**

```bash
git add ai/app/knowledge/concepts/cs/blocking-io.md ai/evals/golden_dataset.jsonl ai/app/vectorstore/index_manifest.json
git commit -m "feat(ai-knowledge): Blocking/Non-Blocking IO 지식카드 + 골든셋 (F-Lab Q8)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 전체 회귀 검증 + 단위 테스트

3개 카드가 추가된 뒤 전체가 깨지지 않았는지 확인한다.

**Files:**
- (검증 전용, 새 파일 없음)

- [ ] **Step 1: 전체 골든 평가**

Run: `python scripts/evaluate_lightweight_rag.py`
Expected: 새 9행 포함 전부 통과(또는 기존과 동일한 메트릭, 신규 회귀 0).

- [ ] **Step 2: 카드 린트 단위 테스트**

Run: `python -m pytest tests/test_knowledge_lint.py -v`
Expected: PASS (새 카드가 린트 규칙을 위반하지 않음).

- [ ] **Step 3: 카드 로딩/인덱스 단위 테스트**

Run: `python -m pytest tests/test_rag_documents.py tests/test_index_manifest.py -v`
Expected: PASS.

- [ ] **Step 4: (선택) 실제 답변 스모크 — Ollama 실행 중일 때만**

Run: `python scripts/evaluate_lightweight_rag.py --real`
Expected: 새 카드 질문이 실제로 카드 기반 근거로 답변됨(수동 확인). Ollama 미실행 시 이 단계는 건너뛴다.

- [ ] **Step 5: 최종 확인 메모**

세 커밋(`git log --oneline -3`)이 카드 3개를 각각 담고 있고, 작업 트리에 의도치 않은 변경이 없는지(`git status`) 확인한다. AI 코드 파일(`client.py` 등 WIP)은 **건드리지 않았어야** 한다.

---

## Self-Review 결과 (작성자 점검)

- **스펙 커버리지**: 설계 §10(F-Lab 콘텐츠 채택), §14 step 3(카드+골든) 구현. 나머지 단계(pipeline 통합·모델 교체·route_tier·비동기 grounding·TOKEN_ALIASES)는 별도 계획서로 분리(이 계획 범위 밖).
- **플레이스홀더 없음**: 모든 카드 본문·골든 행·명령을 실제 값으로 기재.
- **타입/식별자 일관성**: 카드 `id` ↔ 골든 `expected_concepts` 일치 확인 — `java-hashcode`, `java-arraylist`, `cs-blocking-io`.
- **주의**: intent 분류는 규칙 기반이라, 골든 질문 문구가 의도 규칙에 맞게 작성됨("뭐야"=정의, "차이"=비교, 기술어+장문=related). 평가에서 intent 불일치가 나면 질문 문구나 `expected_sub_intent`를 조정.

## 비고 — 다음 계획서 후보

1. pipeline.py 통합(3중 중복 제거) + 스트리밍 ON — 설계 §14 step 1.
2. 모델 교체(`qwen3:1.7b`→`exaone3.5:2.4b`) + 후처리(strip_markdown·ends_with_question 신규) — WIP(`client.py`) 정리 포함, §14 step 2.
3. F-Lab 카드 배치 2(나머지 Top 20).

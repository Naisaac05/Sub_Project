---
type: architecture
category: rag
status: active
updated: 2026-06-18
description: "Local RAG + LangGraph 아키텍처 검토 의견 및 피드백 정리"

---

# AI Review Local RAG + LangGraph Architecture — 검토 의견

검토일: 2026-05-16
대상 문서: `ai-review-local-rag-langgraph-architecture.md` (Codex 작성)
검토 기준: 현 코드베이스([ai/app/main.py](../ai/app/main.py), [service.py](../ai/app/service.py), [schemas.py](../ai/app/schemas.py), [requirements.txt](../ai/requirements.txt), [AiReviewSubmitResponse.java](../backend/src/main/java/com/devmatch/dto/aireview/AiReviewSubmitResponse.java))와의 정합성, 실무 디테일, 학생 노트북 환경 현실성

---

## 1. 우선 수정해야 할 정합성/정확성 문제

### 1-1. 모델 표기 — 현재 코드와 불일치

- 현재 [schemas.py:12](../ai/app/schemas.py:12) 기본값은 `qwen2.5:1.5b`. 문서는 `qwen3:1.7b` / `qwen2.5:3b-instruct` 권장.
- "기존 코드는 qwen2.5:1.5b로 시작해 qwen3:1.7b로 마이그레이션한다"는 **현재→목표 전이 한 줄**을 넣을 것.
- 안 그러면 문서만 보고 들어온 사람이 모델 다운로드 단계에서 혼란.

### 1-2. 응답 시간 목표 ↔ 예시 DTO 모순

- 목표: "평균 8s, p95 12s 이하" (line 12)
- 예시 DTO: `"latency_ms": 12840` (line 442) — **이미 평균 목표 초과**
- 예시값을 4000~6000ms로 바꾸거나, 목표 자체를 재조정.
- CPU 노트북 + qwen3:1.7b + bge-m3 + reranker + hybrid retrieval + LangGraph 다단계까지 합치면 8s 평균은 매우 빡빡. **GPU 가정인지 명시 필요**.

### 1-3. Confidence score 공식 — 정규화/가중치 누락

```
confidence_score = retrieval_score + rule_match_score + answer_validation_score + model_self_check_score
```

- 단순 덧셈인데 임계값은 `0.80` 식 [0,1] 스케일 (line 408~412). 각 항목 스케일이 다르면 의미가 안 맞음.
- 권장 형태:

```
confidence_score =
  0.4 * retrieval_score      # [0,1]
+ 0.2 * rule_match_score     # [0,1]
+ 0.3 * answer_validation    # [0,1]
+ 0.1 * model_self_check     # [0,1]
```

### 1-4. Reranker 실행 위치 미정의

- `bge-reranker-large` / `flashrank`는 Ollama에서 못 돌림.
- 별도 서빙 방식(HuggingFaceCrossEncoder를 FastAPI 프로세스 내에서 로드 / sentence-transformers / TEI 컨테이너)을 적어야 함.
- 노트북 RAM 예산에 직접 영향 (아래 2-1 참고).

### 1-5. 후보 status enum과 action 정합성

- status: `PENDING / APPROVED / REJECTED / MERGED` (line 519)
- action: `APPROVED / EDIT_AND_APPROVE / MERGED / REJECTED` (line 529)
- `EDIT_AND_APPROVE` 액션의 최종 status가 무엇인지(APPROVED인지 별도 enum 추가인지) 불명확.
- 통일안: `APPROVED` 상태 + `reviewer_edited_answer` 컬럼으로 분리.

### 1-6. Spring Boot DTO 확장 시 마이그레이션 전략 없음

- 현재 [AiReviewSubmitResponse.java](../backend/src/main/java/com/devmatch/dto/aireview/AiReviewSubmitResponse.java)는 `evaluation/feedback/nextQuestion/completed/summary/messages` 6개 필드뿐.
- 문서가 요구하는 `confidence_score, model_used, fallback_used, retrieved_concept_ids, candidate_id`가 추가되면 프론트도 영향.
- **기존 필드를 유지하면서 nullable로 추가**한다는 호환성 한 줄 필요.

---

## 2. 빠진 실무 디테일

### 2-1. 노트북 RAM 예산

- bge-m3 ≈ 560MB
- bge-reranker-large ≈ 1.3GB
- qwen3:1.7b ≈ 1GB
- qwen3:4b-q4 ≈ 2.5GB
- Chroma + FastAPI + OS

총 **최소 6~8GB 여유 필요**. 학생 노트북 환경이면 MVP에서 reranker를 빼거나 `flashrank`(훨씬 작음)만 사용 권장.

### 2-2. BM25 한국어 토크나이저

- LangChain `BM25Retriever` 기본은 whitespace split. 한국어 형태소(예: "지연로딩" vs "지연 로딩")에서 점수가 망가짐.
- **`kiwipiepy` 또는 `konlpy` 토크나이저 주입** 권고를 RAG 검색 전략 섹션에 한 줄 추가.

### 2-3. Concept card 청킹 가이드 과대

- 권장 `chunk_size: 500~800 tokens`. 그런데 예시 concept card(line 139~167)는 200 토큰 안팎.
- **MarkdownHeaderTextSplitter로 섹션 단위 분할 후 추가 split 없음**을 명시.
- 청크 사이즈가 카드보다 크면 유의미한 분할이 일어나지 않음.

### 2-4. Golden set의 초기 규모/유지 정책

- 예시 1개만 있고 초기 몇 개로 시작, 분기당 얼마나 추가, 누가 관리하는지 빠짐.
- 권장: **MVP 30~50문항, concept당 2~3문항, 회귀 깨질 때마다 깨진 케이스 추가**.

### 2-5. Evaluation 단계 순서 — 너무 뒤로 밀려 있음

- 구현 순서 8.5단계(line 856)에서 evaluate_rag.py 도입.
- 실제로는 **4단계 LangGraph 기본 workflow 직후에 minimal evaluator**가 있어야 retrieval/모델/프롬프트 바꿀 때마다 회귀를 잡음.
- 원칙: "회귀 잡는 안전망 먼저"가 RAG 운영의 기본.

### 2-6. PII 패턴 구체화

- "개인정보 패턴 필터링"만 있고 어떤 패턴인지 미정.
- 한국 휴대폰 정규식, 주민번호, 이메일, 카드번호 패턴을 명시하면 구현 가능.

### 2-7. Ollama keep_alive 권장값

- "keep_alive 길게" (line 704)만 있음.
- 구체값: **small model `keep_alive=-1` (영구), fallback model `keep_alive=30m`**.
- 그래야 cold start 6~10초가 안 발생.

### 2-8. Vector store 마이그레이션 경로

- 문서: "Chroma → FAISS → PGVector" (line 285~297).
- 실무: **Chroma → PGVector 직행**이 일반적.
- FAISS는 더 raw하고 persistence를 직접 구현해야 해서 Chroma에서 다운그레이드할 이유가 적음.
- FAISS는 "메모리 내 빠른 검색 실험용"으로 별도 위치에 두는 게 자연스러움.

---

## 3. 작은 보완점

### 3-1. Mermaid 다이어그램 (line 56~75)

- `Save candidate log → Human approval`이 동기 흐름처럼 보임.
- `Human approval`은 점선/비동기 표기로 떨어뜨리면 의미가 정확해짐.

### 3-2. 한국어 검증 기준

- "한국어 여부 검사"만 있음.
- 메소드명/클래스명(`equals()`, `Stream API`) 같은 영문은 정상이므로 **"한글 비율 N% 이상"** 임계값으로 명시.

### 3-3. 이력서 표현 (line 877~884)

- "human-in-the-loop 승인 흐름을 상태 그래프로 설계"는 면접에서 "LangGraph `interrupt` 썼냐"는 질문 유도.
- 실제 설계는 candidate DB + 별도 API 승인이므로, **"LangGraph의 candidate state로 승인 게이트 분리"**같이 정확히 표현하는 게 안전.

### 3-4. prompts/prompt_versions.yml 스키마 미정의

- 한 줄 예시(prompt 파일명, version, created_at, owner)라도 추가하면 구현이 매끄러움.

### 3-5. Approval UI 추천이 Streamlit

- 백엔드/프론트와 별도 인증/배포가 늘어남.
- 처음에는 **Spring Boot Admin endpoint + 간단 페이지 한 장**이 일관성 측면에서 더 좋음.

---

## 4. 그대로 좋은 부분

- LangChain/LangGraph 역할 분리(line 45~52) — 깔끔.
- AI 답변 자동 학습 금지 + 사람 승인 후 promote 원칙(line 169~175) — 학생 포트폴리오에 강력한 어필 포인트.
- Knowledge card 형식 + lint 스크립트 도입 — RAG 운영의 핵심을 잘 잡음.
- Incremental indexing + hash manifest(line 752~789) — 운영 디테일까지 잡힌 인상적인 부분.

---

## 5. 요약 — 우선순위별

| 우선순위 | 항목 | 이유 |
|---------|------|------|
| **필수** | 1-2 응답 시간 목표와 예시 latency 모순 | 검토자/면접관이 가장 먼저 짚는 부분 |
| **필수** | 1-3 confidence score 공식 정규화 | 임계값이 의미를 잃음 |
| **필수** | 1-5 candidate status enum 정합성 | 구현 단계에서 막힘 |
| 권장 | 1-1 / 1-4 / 1-6 | 정합성/명확성 |
| 권장 | 2-1 RAM 예산 / 2-5 evaluator 조기 도입 | 실제 실행 가능성 확보 |
| 보완 | 2-2 ~ 2-8 | 운영 품질 |
| 선택 | 3-1 ~ 3-5 | 문서 품질 |

---

## 6. 다음 단계 권장

1. 위 1번 섹션 6개 결함을 superpowers `brainstorming` 스킬로 먼저 정리
2. 정리된 결정 사항을 반영한 phase plan을 `writing-plans` 스킬로 작성
3. phase 1~3 실행해 보고 흐름이 무거우면 조정 (`executing-plans` + `verification-before-completion` + TDD)

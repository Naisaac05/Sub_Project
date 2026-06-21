---
type: analysis
category: model_selection
status: active
updated: 2026-06-18
description: "현재 시스템의 AI 모델 구조, 평가 결과 및 최종 선택 이유 분석"
---

# 🧠 AI 모델 선택 분석

&nbsp;

## 현재 모델 구성

📌 **ExaOne 2.4B 단일 모델 + 템플릿 Fallback 구조입니다. Qwen은 사용하지 않습니다.**

&nbsp;

| 역할 | 모델 / 방식 | 코드 위치 |
|---|---|---|
| **메인 생성** | `exaone3.5:2.4b` | `ai/app/ollama/client.py` → `DEFAULT_MODEL` |
| **Fallback 생성** | `exaone3.5:2.4b` (동일) | `ai/app/ollama/client.py` → `FALLBACK_MODEL` |
| **최종 Fallback** | **템플릿 매크로** (모른다고 안내) | `ai/app/validation/text.py` → `korean_fallback()` |
| **임베딩 (검색)** | `bge-m3` | 다국어/한국어 지원 |
| **의도 분류** | `bge-m3` 임베딩 분류기 (99.6%) | `ai/app/workflow/embedding_intent.py` |
| **Reranker** | `flashrank` | FastAPI 인-프로세스 |

&nbsp;

> ⚠️ `backend/…/application.yml`에 `OLLAMA_MODEL: qwen3:4b-q4_K_M`이 기본값으로 남아있지만, 실제 Python AI 서비스에서 Qwen을 호출하는 경로는 없습니다.

&nbsp;

* **LangChain RAG + LangGraph (현재 운영)** — RAG 카드 검색 → 의도 파악 → 컨텍스트 주입 생성 → 검증 파이프라인을 LangGraph StateGraph로 제어합니다.
* **SSE 스트리밍** — FastAPI → Spring Boot → 프론트엔드까지 실시간 토큰 출력을 지원합니다.
* **Fallback 흐름** — ExaOne 생성 실패 or Validation 미통과 → ExaOne 재시도 → 최종 실패 시 **템플릿 매크로** ("승인된 지식 카드가 아직 부족해서…") 반환

&nbsp;

---

&nbsp;

## ExaOne vs Qwen — 왜 ExaOne을 선택했는가

📌 **Qwen3의 치명적인 한국어 누수(영어·중국어 혼입)가 결정적 탈락 사유였습니다.**

&nbsp;

### 답변 생성 품질 비교 (A/B 실험, 5개 시나리오 × 4조건)

> 출처: `experiments/prompt_v2_comparison/REPORT.md` — 인간 채점(1~5점)

| 항목 | ExaOne 3.5 (2.4B) | Qwen3 (4B) | 선택 |
|---|---|---|---|
| **자연스러움 (no_rag v2)** | **4.2 / 5.0** ⭐ | 3.8 / 5.0 | **ExaOne** |
| **진단 적합성 (no_rag v2)** | **4.0 / 5.0** ⭐ | 4.0 / 5.0 | 동등 |
| **자연스러움 (rag v1)** | **4.0 / 5.0** | 2.2 / 5.0 ⚠️ | **ExaOne** |
| **한국어 누수 (20케이스)** | **0건 (0%)** ✅ | 5건 (25%) ❌ | **ExaOne** |
| **v2 Timeout 발생 (5건 중)** | **0건 (0%)** ✅ | 2건 (40%) ❌ | **ExaOne** |

&nbsp;

### 속도 비교 (CPU only, 1샘플당 평균)

> 출처: `experiments/prompt_v2_comparison/REPORT.md`

| 조건 | ExaOne 3.5 (2.4B) | Qwen3 (4B) | 선택 |
|---|---|---|---|
| **RAG 포함 (현재 운영)** | **22초** ⭐ | 28초 | **ExaOne (21% 빠름)** |
| **RAG 없이** | 14초 | 17초 | ExaOne (18% 빠름) |

&nbsp;

### 자원 효율성 비교

| 항목 | ExaOne 3.5 (2.4B) | Qwen3 (4B) | 선택 |
|---|---|---|---|
| **모델 메모리** | **약 1.6 GB** | 약 2.5 GB | **ExaOne (36% 절약)** |

&nbsp;

### 🟢 ExaOne 채택 이유 요약

* **한국어 누수 0건** — Qwen3는 20케이스 중 5건(25%)에서 영어·중국어가 섞였지만, ExaOne은 단 한 건도 없었습니다. 교육용 시스템에서 이런 누수는 사용자 신뢰를 즉시 무너뜨립니다.
* **속도도 더 빠름** — 같은 CPU 환경에서 ExaOne(14초)이 Qwen(17초)보다 **18% 빠르고**, 메모리도 36% 적게 씁니다.
* **금지 표현 완벽 차단 100.0%** — 12개 전체 질문에서 금지 표현이 한 번도 포함되지 않았습니다.

&nbsp;

---

&nbsp;

## RAG의 핵심 가치 — 왜 RAG를 쓰는가

📌 **RAG 카드가 매칭되면, 환각 없이 근거 있는 정확한 답변을 생성합니다. 이것이 소형 모델의 한계를 극복하는 핵심 전략입니다.**

&nbsp;

> 출처: `evals/exaone_live_e2e/REPORT.md`

### RAG 검색 성공 케이스 — 실제 효과

RAG 카드가 존재하는 주제에 대해 ExaOne은 **품질 플래그 없이 깨끗하고 정확한 답변**을 생성했습니다:

| 질문 | 검색된 카드 | 품질 플래그 | 답변 품질 |
|---|---|---|---|
| Spring N+1 문제 | `spring-n-plus-one`, `spring-fetch-join` | **없음** ✅ | 지연 로딩 원인 + 해결법 정확히 설명 |
| ControllerAdvice 사용법 | `java-backend-controlleradvice` | **없음** ✅ | 키워드 통과, 예시 코드 포함 |
| aria-label 필요 이유 | `frontend-aria-label` | **없음** ✅ | 스크린 리더 용도 정확히 설명 |

&nbsp;

### RAG 없이 생성한 경우 — 환각 위험

같은 질문을 RAG 카드 없이 LLM만으로 생성하면, 빠르지만 근거가 부족한 답변이 나옵니다:

| 질문 | 품질 플래그 | 문제점 |
|---|---|---|
| Spring N+1 문제 | `evidence_missing, hallucination_suspected, contradiction_suspected` | 부정확한 설명 (서브쿼리 원인 잘못 지목) |
| ControllerAdvice | `evidence_missing, hallucination_suspected` | 근거 없는 답변 |
| Docker 이미지/컨테이너 | `evidence_missing, hallucination_suspected` | 환각 의심 |

&nbsp;

### RAG vs no_rag 비교 요약

| 항목 | RAG (현재 운영) | no_rag (LLM 직접 생성) |
|---|---|---|
| **카드 매칭 시 답변 정확도** | **높음** (품질 플래그 없음) ✅ | 환각·근거 부족 빈번 ⚠️ |
| **금지 표현 미포함률** | **100.0%** | **100.0%** |
| **속도 (A/B 실험 평균)** | 22초 | 14초 |
| **RAG 검색 성공률** | 66.7% (카드 커버리지에 따라 ↑) | — |

&nbsp;

---

&nbsp;

## 현재 과제 & 개선 방향

📌 **RAG 카드가 있는 주제는 정확하게 답변합니다. 카드가 없는 주제만 Fallback됩니다.**

&nbsp;

### 🔴 현재 Trade-off

* **카드 미비 영역의 Fallback** — 12개 질문 중 7개가 템플릿으로 빠졌지만, 이는 React, Docker, HTTP 401/403 등 **아직 Knowledge Card가 없는 주제**에서만 발생합니다. 카드가 있는 Spring, JPA, aria-label 등은 **모두 정상 생성에 성공**했습니다.
* **카드 커버리지 = 시스템 품질** — 현재 구조에서 답변 품질은 모델 성능이 아니라 **Knowledge Card 보유량에 비례**합니다. 카드를 늘리면 Fallback율과 정확도가 동시에 개선됩니다.

&nbsp;

### 개선 우선순위

| 우선순위 | 과제 | 기대 효과 |
|---|---|---|
| **1순위** | Knowledge Card 커버리지 확대 (React, Docker, HTTP 등) | Fallback율 ↓ + 키워드 통과율 ↑ 동시 개선 |
| **2순위** | Validation 임계값 튜닝 (현재 0.60 / 0.80) | 불필요한 재시도 감소 → E2E 지연 대폭 단축 |
| **3순위** | v2.1 프롬프트 도입 ("모르겠어요" 분기 수정 + 메타라벨 금지) | 자연스러움 4.2 → 4.5+ 기대 |

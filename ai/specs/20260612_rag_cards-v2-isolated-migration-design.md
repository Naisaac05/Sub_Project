---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "기존 시스템 영향 최소화를 위한 RAG Cards v2 격리 마이그레이션 설계서"

---

# RAG 카드 v2 격리 마이그레이션 설계

## 목표

기존 프로덕션 카드 저장소를 변경하거나 활성화하지 않고 `backend/data/devmatch-data-only.sql`에서 개념 중심의 RAG 카드 초안을 생성합니다.

## 격리 및 안전성

- 마이그레이션 출력 루트는 `ai/app/knowledge/concepts_v2`로 고정합니다.
- 런타임 기본값은 `ai/app/knowledge/concepts`로 유지하며 `ACTIVE_CARD_STORE`를 추가하거나 변경하지 않습니다.
- 마이그레이션 명령은 드라이런만 지원하며 `--write`는 거부합니다.
- 기존 카드 파일을 삭제하거나 수정하거나 덮어쓰지 않습니다.
- 백업은 `ai/app/knowledge/concepts_backup` 아래에 읽기 전용 스냅숏으로 생성합니다.

## 카드 생성

- `questions.content`, `questions.options`, `questions.correct_answer`, `questions.test_id`를 파싱합니다.
- 내용이 E2E 실패를 직접 설명하는 질문은 건너뜁니다.
- `CONCEPT_DEFINITION`, `ANSWER_REASON`, `WRONG_ANSWER_REASON`만 생성합니다.
- 모든 카드와 생성 페이로드의 상태는 `draft`입니다.
- 정규화된 용어 일치, 별칭 겹침, 임베딩 유사도를 사용하되 같은 카테고리 안에서만 병합합니다.
- `source_question_ids`는 추적 메타데이터로만 유지합니다.
- 용어, 최대 3개 별칭, 카테고리, 3~7개의 부스트 키워드로 `embedding_text`를 구성합니다.

## 검색 및 평가

- Exact Match Hit@K는 데이터 누출 기준선으로만 보고합니다.
- LOO는 후보 50개를 검색하고 원본 카드를 제거한 뒤 후보 가용성, 평균 점수, 동일 카테고리 비율을 보고합니다.
- v2 카드 초안은 명시적으로 주입한 카드 로더로 평가하며 프로덕션 런타임 로더에는 절대 포함하지 않습니다.

## 검증

스키마 규칙, v2 경로 격리, 초안 상태, 페이로드 정책, 카드 ID 규칙, 검색 텍스트 제한, 드라이런 동작, 워크플로 의도 정책, `git diff --check`를 검증합니다.


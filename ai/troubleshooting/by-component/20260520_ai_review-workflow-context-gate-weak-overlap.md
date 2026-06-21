---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "RAG 기반 AI review 워크플로 컨텍스트 게이트가 본문 우연 겹침을 통과시킴 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review 워크플로 컨텍스트 게이트가 본문 우연 겹침을 통과시킴

- 발생 일시: 2026-05-20
- 영역: ai (Python RAG 워크플로)
- 심각도: medium

## 증상

카드 밖 질문인데 무관한 개념 카드가 RAG 근거(context)로 주입되는 사례 발견.
예: `"REST API와 GraphQL 차이"` 질문에 `java-backend-controlleradvice` 카드가
워크플로 컨텍스트로 채택됨. 작은 로컬 모델은 잘못된 근거를 그대로 믿고 틀린 답을
확신 있게 낼 위험이 있음.

## 원인

`retrieve_context_node`의 게이트 `MIN_WORKFLOW_CONTEXT_SCORE = 2.0`이 너무 관대했음.
`score_card`([ai/app/rag/retriever.py:501](ai/app/rag/retriever.py)) 점수 구조상:

- 본문(searchable_text) 겹침 토큰 1개당 +1.0
- 제목 토큰 +1.5, 평가 키워드 토큰 +2.0
- 강신호(concept_id/제목/키워드) 매칭 시 `_has_exact_phrase_match` +4.0

따라서 **강신호 토큰이 하나라도 맞으면 점수는 최소 5.0**이고, 2.0~5.0 구간은
전부 "강신호 없는 본문 우연 겹침"이다. `"REST API..."`의 `api`, `rest` 두 토큰이
ControllerAdvice 카드 *본문*(REST API 설명 문맥)에만 겹쳐 정확히 2.0 → 게이트 통과.
제목/키워드/강매칭은 0이었음.

## 해결 방법

게이트를 강신호 1개의 최소 점수인 5.0으로 상향.
[ai/app/workflow/nodes.py:18-21](ai/app/workflow/nodes.py) — `MIN_WORKFLOW_CONTEXT_SCORE = 5.0`
로 변경하고 근거(왜 5.0인지)를 주석으로 남김.

검증:
- `python scripts/evaluate_lightweight_rag.py` golden 50건 — retrieval_hit_rate/intent
  등 모든 지표 변경 전과 동일(인도메인 무손실). 단 golden 평가는 게이트를 적용하지
  않으므로 retrieval_hit_rate 자체는 본 변경에 영향받지 않음.
- 임시 프로브: 인도메인 3건 모두 PASS(7~22점) 유지, 아웃오브도메인 8건의 오매칭
  (`REST→ControllerAdvice` 2.0)이 drop으로 전환됨.

## 재발 방지 / 메모

- 같은 계열 선행 기록: [2026-05-16 RAG generic token false positive](2026-05-16-ai-review-rag-generic-token-false-positive.md).
  이번 건은 retriever 점수 자체가 아니라 **워크플로 채택 게이트** 경계에서 재발한 변종.
- 5.0은 현재 `score_card` 가중치(강매칭 +4, 겹침 +1)에 묶인 값. 가중치를 바꾸면
  이 임계값도 재검토 필요.
- 본문 토큰이 6개 이상 우연히 겹치면 5.0을 넘을 수 있으므로 게이트만으로 완벽히
  막히지는 않음. 근본 해결은 한국어 토크나이저 개선 + 카드 커버리지 확대.
- 답변 텍스트 실제 정확도는 여전히 미측정(평가 스크립트가 Ollama 대신 결정론적
  가짜 생성기 사용). 후속으로 실측 모드 필요.
- 평가/프로브 실행 시 워크플로의 `candidate_save_node`가 기본 큐
  `ai/app/knowledge/candidates/auto_candidates.jsonl`에 추가 기록함 — git 추적 파일이
  오염되니, 평가 전 `AI_REVIEW_AUTO_CANDIDATES_PATH`를 임시 경로로 지정하는 게 좋음.

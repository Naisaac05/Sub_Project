# 승인된 useEffect 카드 Fast Path 미스 및 기술 식별자 답변 잘림

- 발생 일시: 2026-06-15
- 영역: AI workflow / RAG card
- 심각도: high

## 증상

승인된 `frontend-useeffect` 카드가 있는데도 `useEffect가 뭐야?` 질문에 약 38초가 걸렸고, 생성된 답변이 `ReactDOM.`에서 끝나 문장이 잘린 것처럼 표시됐다.

## 원인

명확한 기술 개념 정의 질문도 먼저 로컬 embedding intent 분류를 실행했다. 분류에 약 10초가 걸린 뒤 `unknown`으로 판정되어 v2 Fast Path가 `unsupported_intent`로 실패했고 Ollama 생성으로 넘어갔다. 또한 문장 압축기가 기술 식별자 내부의 마침표도 문장 끝으로 계산하여 `ReactDOM.render`를 `ReactDOM.`에서 잘랐다. 승인된 useEffect 정의 payload도 깨진 인코딩과 일반 템플릿 문장으로 구성되어 있었다.

## 해결 방법

- 영문 기술 토큰을 포함한 명확한 정의 질문은 embedding 호출 전에 규칙 기반 정의 intent로 분류한다 (`ai/app/workflow/embedding_intent.py`).
- 영숫자 사이의 마침표는 문장 끝으로 계산하지 않는다 (`ai/app/validation/text.py`).
- `frontend-useeffect`의 승인된 `CONCEPT_DEFINITION` payload를 정상적인 한국어 설명으로 교정했다 (`ai/app/knowledge/concepts_v2/frontend/frontend-useeffect.json`).
- 회귀 테스트를 추가했다 (`ai/tests/test_embedding_intent.py`, `ai/tests/test_service_helpers.py`).

## 재발 방지 / 메모

대표 질문 `useEffect가 뭐야?`의 동기 경로는 약 0.32초, 스트리밍 경로는 약 0.24초에 `v2_approved_fast_path`로 완료되는 것을 확인했다. 검색 필드는 변경하지 않았다.

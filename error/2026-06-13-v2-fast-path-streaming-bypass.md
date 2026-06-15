# v2 approved Fast Path was bypassed by streaming workflow

- 발생 일시: 2026-06-13
- 영역: AI / RAG v2 Fast Path
- 심각도: high

## 증상

`java-equals` 카드가 approved 상태이고 검색에도 적중하지만 실제 화면의 `/messages/stream` 질문은 약 38초 동안 Ollama 생성 흐름을 사용한 뒤 승인 카드가 부족하다는 fallback 문구를 반환했다.

## 원인

비스트리밍 `generate_answer_node`에는 v2 approved Fast Path 판정이 연결되어 있었지만 `run_review_workflow_stream`에는 해당 판정이 없었다. 또한 master flag 기본값이 비활성화였고 comparison intent가 approved `CONCEPT_DEFINITION` payload에 연결되지 않았다.

관련 파일:
- `ai/app/workflow/runner.py:283`
- `ai/app/workflow/v2_approved_fast_path.py:50`
- `ai/app/workflow/v2_approved_fast_path.py:116`
- `frontend/src/lib/ai-review.ts:51`

## 해결 방법

스트리밍 워크플로 시작부에 v2 approved Fast Path 판정을 추가했다. Shadow Mode에서는 판정 metadata만 기록하고 기존 스트리밍을 유지하며, Serve Mode의 approved hit에서만 payload를 즉시 SSE로 반환한다. comparison intent는 approved `CONCEPT_DEFINITION` payload로 연결하고, 설정 파일의 master flag를 활성화했다.

## 재발 방지 / 메모

Fast Path 기능은 비스트리밍과 스트리밍 경로에 동일한 정책 테스트가 있어야 한다. 실제 설정은 계속 `SHADOW_MODE=true`이며 Serve Mode 동작은 테스트 주입으로만 검증한다.

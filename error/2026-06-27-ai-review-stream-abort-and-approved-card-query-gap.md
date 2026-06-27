# AI review 정상 스트림 강제 취소와 approved 카드 한국어 조사 검색 누락

- 발생 일시: 2026-06-27
- 영역: frontend / backend / AI RAG
- 심각도: high

## 증상

AI 답변은 화면과 DB에 표시되지만 Chrome 콘솔에 `ERR_INCOMPLETE_CHUNKED_ENCODING`과 `TypeError: network error`가 남았다. 또한 현재 approved v2 카드 85장을 원문 질문 형태로 전수 검사했을 때 `반응형이란 무엇인가요?`, `DTO 개념이란 무엇인가요?`가 각각 retrieval miss와 anchor miss로 Fast Path를 타지 못했다.

## 원인

프론트는 SSE `done` 이벤트를 받자마자 공통 cleanup을 호출했다. 이 cleanup이 `ReadableStreamDefaultReader.cancel()`과 `AbortController.abort()`를 실행해서 Spring이 응답을 정상 종료하기 전에 브라우저가 chunked 응답을 강제로 끊었다. 그래서 답변 적용은 성공해도 네트워크 콘솔에는 불완전 종료 오류가 기록됐다: `frontend/src/app/tests/results/[id]/review/page.tsx:509`.

RAG 정규식 토크나이저는 한국어 조사가 붙은 `반응형이란`, `개념이란`을 카드 term인 `반응형`, `개념`과 다른 토큰으로 취급했다. Fast Path의 마지막 anchor 검사도 별도 토큰 규칙을 사용해 검색과 판정이 불일치했다: `ai/app/rag/retriever.py:551`, `ai/app/workflow/v2_approved_fast_path.py:211`.

Spring SSE 기본 제한 45초도 로컬 Ollama 최대 요청 시간보다 짧아 느린 환경에서 서버가 먼저 연결을 종료할 여지가 있었다.

## 해결 방법

정상 완료 전용 `finishSuccessfulStream()`을 추가해 reader lock만 해제하고 cancel/abort는 하지 않도록 했다. `done` 수신 후에는 서버 EOF까지 읽으며, 화면 이탈이나 실제 오류에서만 기존 강제 cleanup을 사용한다: `frontend/src/lib/ai-review-stream.ts:4`, `frontend/src/app/tests/results/[id]/review/page.tsx:255`, `frontend/src/app/tests/results/[id]/review/page.tsx:559`.

Spring SSE 기본 제한을 120초로 올렸다: `backend/src/main/resources/application.yml:81`.

한국어 질문 토큰에서 자주 쓰는 조사를 원래 위치에서 제거하고 Fast Path anchor도 같은 `tokenize_query()`를 사용하도록 통일했다: `ai/app/rag/retriever.py:43`, `ai/app/rag/retriever.py:551`, `ai/app/workflow/v2_approved_fast_path.py:211`.

approved 카드 전체를 동적으로 순회해 질문이 해당 카드와 승인 답변으로 이어지는지 검사하는 회귀 테스트를 추가했다: `ai/tests/test_approved_card_catalog.py:14`.

## 재발 방지 / 메모

- SSE의 애플리케이션 `done` 이벤트와 HTTP 응답 EOF를 구분한다. 정상 완료 경로에서 `abort()`나 `cancel()`을 호출하지 않는다.
- AI provider 최대 대기 시간은 Spring SSE 제한보다 짧게 유지한다. 로컬 기본값은 SSE 120초다.
- approved 카드 수는 고정값으로 테스트하지 않고 현재 저장소의 approved 카드 전체를 순회한다. 2026-06-27 기준 85장이다.
- 한국어 검색 회귀 테스트에는 조사 결합 질문을 포함한다.

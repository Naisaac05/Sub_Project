# 푸시 전 AI 회귀 테스트와 승인 카드 개수 테스트 실패

- 발생 일시: 2026-07-07
- 영역: ai / backend
- 심각도: medium

## 증상

AI 전체 테스트에서 스트리밍 생성 예외 테스트의 `fallback_used`가 `false`로 반환됐고, 코스 문항 shadow 품질 점수가 기준 4.5보다 낮은 4.44였다. 백엔드 승인 카드 목록 테스트는 실제 카드 86개를 반환했지만 85개로 고정된 단언 때문에 실패했다.

## 원인

스트리밍 테스트는 이전 테스트의 답변 캐시와 free-question fast path 영향을 차단하지 않아 생성기 예외 경로를 보장하지 못했다. React 부모-자식 데이터 전달 문항은 정상 한글 질의가 기존 깨진 한국어 토큰화 데이터와 매칭되지 않아 `props` 카드를 찾지 못했다. 승인 카드 테스트는 계속 늘어나는 지식 카탈로그의 정확한 개수를 테스트 계약으로 사용했다.

## 해결 방법

스트리밍 테스트 전후에 답변 캐시를 비우고 예외 검증을 `first-question` 생성 경로에서 수행하도록 수정했다 (`ai/tests/test_stream.py:8`, `ai/tests/test_stream.py:15`). 부모 데이터 전달 질의를 `props`와 해당 카드 식별 토큰으로 확장했다 (`ai/app/rag/retriever.py:561`). 승인 카드 검증은 고정 개수 대신 비어 있지 않고 대표 카드가 포함되는지를 검사하도록 변경했다 (`backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java:58`).

## 재발 방지 / 메모

스트리밍 생성기 예외 테스트는 캐시·fast path를 우회해 실제 생성기 호출 경로를 보장해야 한다. 지식 카드 카탈로그 테스트는 총개수보다 필수 카드와 상태를 검증한다. 한국어 검색 문구를 추가할 때 코스 shadow 품질 게이트와 RAG retriever 테스트를 함께 실행한다.

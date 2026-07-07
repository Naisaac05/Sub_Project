# React props 카드 미매칭과 free-question 답변 잘림

- 발생 일시: 2026-07-07
- 영역: ai
- 심각도: medium

## 증상

`React props가 뭐야?` 질문이 승인 카드가 있는데도 `fallback_template`로 처리됐다. 카드가 없는 CQRS 질문은 Ollama가 정상 생성했지만 free-question 토큰 상한에 도달해 답변이 문장 중간에서 끝났다.

## 원인

`React`와 `props`가 함께 들어간 질의를 `frontend:63` 카드의 검색 토큰으로 확장하는 규칙이 없어 여러 React 카드가 비슷한 점수를 받았고 margin gate가 적중을 거부했다. free-question 생성 토큰은 요청값과 관계없이 최대 128로 제한되어 비교·장단점 설명에 부족했다.

## 해결 방법

`React + props` 질의를 해당 카드 식별 토큰으로 확장했다 (`ai/app/rag/retriever.py:24`). free-question 최대 생성 토큰을 256으로 늘렸다 (`ai/app/workflow/nodes.py:594`). 실제 질문을 고정한 카드 적중 테스트와 256 토큰 예산 테스트를 추가했다 (`ai/tests/test_v2_approved_fast_path.py:82`, `ai/tests/test_workflow_runner.py:1439`).

## 재발 방지 / 메모

승인 카드의 짧은 자연어 별칭을 추가할 때 실제 사용자 문구로 margin gate까지 검증한다. 생성 토큰 예산을 변경할 때는 단순 숫자뿐 아니라 비교·장단점 질문이 완결된 문장으로 끝나는지도 실제 모델 호출로 확인한다.

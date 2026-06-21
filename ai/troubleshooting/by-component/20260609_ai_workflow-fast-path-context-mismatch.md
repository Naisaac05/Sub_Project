---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI workflow fast path context mismatch 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI workflow fast path context mismatch

- 발생 일시: 2026-06-09
- 영역: ai
- 심각도: medium

## 증상
자유 질문 workflow 검증 중 일부 질문이 의도한 생성 경로를 타지 않았다.

- `응답 JSON의 의미` 질문은 단순 JSON 정적 답변으로 빠져 generator가 호출되지 않을 수 있었다.
- `equals가 뭐야?` / `equals를 짧게 설명해줘` 질문은 검색 상위의 `hashCode` 생성 카드가 선택되어 `generated_card_fast_path`로 빠지고, equals 답변 대신 hashCode 답변을 반환할 수 있었다.

관련 회귀 테스트:

- `ai/tests/test_workflow_runner.py:702`
- `ai/tests/test_workflow_runner.py:791`
- `ai/tests/test_workflow_runner.py:1230`

## 원인
`generated_card_fast_path`가 검색 점수와 카드 승인 상태만 보고 카드의 제목·주제가 실제 learner query와 강하게 일치하는지 확인하지 않았다.

이 때문에 `equals` 질문에서 `hashCode` 카드가 더 높은 lexical 점수로 선택되면, lightweight answer가 그 카드를 그대로 답변으로 사용했다.

또한 JSON contextual 질문은 정적 fast path 대신 generation/RAG generation으로 처리되어야 하는데, 검증 과정에서 route 기대값이 현재 검색 카드 기반 생성 경로와 어긋나 있었다.

관련 코드:

- `ai/app/workflow/lightweight_answers.py:237`
- `ai/app/workflow/lightweight_answers.py:266`
- `ai/app/workflow/lightweight_answers.py:286`
- `ai/app/workflow/lightweight_answers.py:362`

## 해결 방법
생성 카드 fast path를 사용할 때 `concept_title_matches_query()`로 learner query/topic과 카드 title이 강하게 맞는지 확인하도록 제한했다.

- `hashCode` 카드가 `equals` 질문에 fast path로 사용되지 않게 했다.
- `@ControllerAdvice`처럼 typo 보정 및 주제 일치가 명확한 생성 카드는 fast path를 유지했다.
- contextual JSON 테스트는 정적 fast path 회피가 핵심이므로 현재 올바른 `rag_generation` 경로를 기대하도록 정리했다.

수정·검증 파일:

- `ai/app/workflow/lightweight_answers.py:266`
- `ai/app/workflow/lightweight_answers.py:286`
- `ai/tests/test_workflow_runner.py:702`
- `ai/tests/test_workflow_runner.py:791`
- `ai/tests/test_workflow_runner.py:1230`

## 재발 방지 / 메모
생성 카드 fast path는 "검색됨"만으로 충분하지 않다. 검색 점수는 관련 개념 간 alias나 설명 단어가 겹칠 때 오염될 수 있으므로, fast path에는 제목·topic 수준의 강한 일치 조건을 유지해야 한다.

RAG context를 일반 생성에 제공하는 것은 허용하되, lightweight generated-card 답변으로 바로 반환하는 경우에는 더 엄격한 검증이 필요하다.

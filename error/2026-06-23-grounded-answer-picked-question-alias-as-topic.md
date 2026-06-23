# Grounded answer가 질문형 alias를 topic 표시명으로 사용함

- 발생 일시: 2026-06-23
- 영역: ai
- 심각도: medium

## 증상

`python-asyncio` 카드의 근거 추출 답변이 `asyncio에서 await 키워드의 역할은?는 ...`처럼 질문형 alias를 topic 표시명으로 사용했다. 카드 첫 문장을 `asyncio는 ...`으로 보강한 뒤에도 표시명 선택 로직이 긴 alias를 우선해 답변 문장이 어색해졌다.

## 원인

`ai/app/workflow/grounded_fallback.py:119`의 `_display_topic`이 공백이 있고 영문이 포함된 alias를 우선 선택했다. `python-asyncio` 카드에는 `asyncio에서 await 키워드의 역할은?` 같은 질문형 alias가 있어, canonical term인 `asyncio`보다 이 alias가 먼저 선택됐다. 또한 근거 첫 문장이 이미 `asyncio는`으로 시작해도 `build_grounded_answer_from_evidence`가 다시 `asyncio는`을 붙여 중복 prefix가 생길 수 있었다.

## 해결 방법

`ai/app/workflow/grounded_fallback.py`에서 표시명 선택 규칙을 조정했다.

- 단일 canonical term이 alias에 그대로 있으면 term을 우선 사용한다.
- 물음표가 있거나 3단어를 넘는 alias는 표시명 후보에서 제외한다.
- 근거 첫 문장이 이미 topic으로 시작하면 topic prefix를 중복으로 붙이지 않는다.

회귀 테스트는 `ai/tests/test_grounded_fallback.py`의 `test_builds_grounded_answer_with_concise_card_term_when_alias_is_question`에 추가했다.

## 재발 방지 / 메모

alias는 검색 회수율을 높이기 위한 필드라 질문형 문구가 들어갈 수 있다. 사용자-facing 답변의 topic 표시명에는 검색 alias를 그대로 쓰지 말고, 짧고 안정적인 canonical term 또는 짧은 표시용 alias만 사용해야 한다.

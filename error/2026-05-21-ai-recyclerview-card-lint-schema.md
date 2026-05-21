# AI RecyclerView generated card failed knowledge lint schema

- 발생 일시: 2026-05-21
- 영역: ai / knowledge-card
- 심각도: low

## 증상

`python -m unittest discover -s ai\tests -v` 실행 시 `test_valid_bundled_cards_pass_lint`가 실패했다. `ai/app/knowledge/concepts/generated/auto-review-recyclerview.md`에 필수 섹션 `대표 해결`, `흔한 오해`, `평가 키워드`가 없고, `평가 키워드` bullet item이 2개 미만이라는 lint 오류가 발생했다.

## 원인

생성된 승인 카드가 예전 카드 형식(`핵심 설명`, `사용 맥락`, `주의할 점`, `검색 키워드`)만 갖고 있었다. 현재 lint 기준은 `ai/scripts/lint_knowledge_cards.py:13`의 `REQUIRED_SECTIONS = ("핵심 설명", "대표 해결", "흔한 오해", "평가 키워드")`를 모든 bundled card에 요구한다.

## 해결 방법

`ai/app/knowledge/concepts/generated/auto-review-recyclerview.md`에 `대표 해결`, `흔한 오해`, `평가 키워드` 섹션을 추가하고, 평가 키워드에 `RecyclerView`, `ViewHolder`, `Adapter`, `목록 렌더링`을 넣어 lint 요구사항을 만족시켰다.

## 재발 방지 / 메모

새 generated concept card를 승인하거나 repository에 포함할 때는 전체 테스트 전에 `python -m unittest ai.tests.test_knowledge_lint -v` 또는 knowledge card lint를 먼저 실행한다. 카드 생성/승인 경로가 새 섹션 스키마를 자동으로 채우는지도 별도 점검 대상이다.

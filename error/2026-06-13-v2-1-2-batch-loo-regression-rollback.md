# v2.1.2 배치 LOO 회귀 롤백

- 발생 일시: 2026-06-13
- 영역: ai
- 심각도: medium

## 증상
상위 10개 카드 배치 중 6개 카드의 payload, aliases, boost keywords를 패치한 뒤 Exact Hit@1/3/5는 유지됐지만 LOO 평균 점수가 5.29452에서 5.26712로 약 0.517% 하락했다.

## 원인
`ai/app/knowledge/concepts_v2/python/python-range.json:3` 등 후보 카드의 aliases와 boost keywords를 의미 중심으로 정리하면서 대체 후보의 lexical 점수가 달라졌다. 또한 `java-jvm`, `spring-jpa` 카드의 일부 payload 문장이 중복 점수 0.3을 초과했다.

## 해결 방법
중단 기준인 LOO 0.5% 하락을 초과했으므로 `ai/app/knowledge/concepts_v2_backups/card-quality-v2-1-2-batch-20260613` 백업에서 후보 10개 전체를 복원했다. 최종 검색 지표가 기준선과 동일한지 재검증한다.

## 재발 방지 / 메모
다음 배치는 aliases와 boost keywords를 한꺼번에 변경하지 말고 payload 전용 패치부터 평가한다. 검색 필드 변경이 필요하면 카드별 LOO 영향도를 측정해 회귀가 없는 변경만 별도 승인한다.

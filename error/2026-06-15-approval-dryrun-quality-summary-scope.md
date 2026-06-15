# 승인 전 dry-run 품질 통과 집계 범위 오류

- 발생 일시: 2026-06-15
- 영역: AI validation
- 심각도: medium

## 증상
첫 승인 전 dry-run 보고서가 준비 초안의 `fake_example_score` 실패 10건을 승인 판단에는 반영하면서도 `quality_passed_count=20`으로 표시했다.

## 원인
`quality_passed_count`가 payload 본문 validator 결과만 집계하고, 실행 검증과 준비 manifest의 예시 품질 지표를 포함한 통합 품질 게이트를 집계하지 않았다.

## 해결 방법
통합 품질 실패 사유를 카드 결과의 `quality_reasons`로 분리하고, `quality_passed_count`는 해당 사유가 없는 카드만 세도록 수정했다. 기존 본문 validator 통과 수는 `payload_validator_passed_count`로 별도 보고한다.

관련 파일: `ai/app/scripts/dryrun_factchecked_next20_approval.py:66`, `ai/app/scripts/dryrun_factchecked_next20_approval.py:191`, `ai/tests/test_dryrun_factchecked_next20_approval.py:49`

## 재발 방지·메모
승인 보고서의 통과 수는 단일 validator가 아니라 승인에 사용되는 모든 품질 게이트를 기준으로 집계한다. 개별 validator 통과 수가 필요하면 이름을 명확히 분리한다.

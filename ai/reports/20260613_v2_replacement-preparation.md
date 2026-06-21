---
type: report
category: inference
status: active
updated: 2026-06-18
description: "v2 검색 강화 및 v1 교체 준비 보고서 관련 주요 기능 및 가이드라인"

---

# v2 검색 강화 및 v1 교체 준비 보고서

## 판정

**NOT_READY**

검색 성능과 Fast Path 목표는 충족했지만 approved 카드가 55개로 교체 기준 80개에 미달한다.
`SHADOW_MODE=true`를 유지했으며 v1 concepts, commit, push는 건드리지 않았다.

## 신규 승인

- 신규 approved 카드: 50개
- 전체 approved 카드: 55개
- 신규 approved 운영 payload: 150개
- 기존 approved 5개 payload_status 변경: 0건
- lazy payload 승인: 0건

신규 승인 카드:

`algorithm`, `algorithm-2`, `algorithm-3`, `algorithm-4`, `algorithm-5`,
`algorithm-6`, `algorithm-7`, `algorithm-linked`, `algorithm-queue`, `algorithm-stack`,
`frontend`, `frontend-2`, `frontend-button`, `frontend-conditional-rendering`,
`frontend-functional-component`, `frontend-hook`, `frontend-jsx-expression`,
`frontend-react-project-tool`, `frontend-useeffect`, `frontend-usestate`,
`java-access-modifier`, `java-array-length`, `java-arraylist`, `java-extends-keyword`,
`java-final-keyword`, `java-int-variable`, `java-loop-control`, `java-main`, `java-primitive`,
`python`, `python-decorator`, `python-dictionary`, `python-fstring`,
`python-function-definition`, `python-immutable`, `python-multiline-string`,
`python-negative-indexing`, `python-none`, `python-range`, `python-with-statement`,
`spring-applicationyml`, `spring-autowired`, `spring-boot`, `spring-controller`,
`spring-dependency-injection`, `spring-embedded-tomcat`, `spring-ioc`,
`spring-requestmapping`, `spring-service`, `spring-spring-bean-scope`.

승인 전 백업:

- `ai/app/knowledge/concepts_v2_backups/reviewed-approval-20260613-160000`
- `ai/app/knowledge/concepts_v2_backups/reviewed-priority-approval-20260613-161500`

## 최신 Shadow 결과

동일한 코스 questions 50개를 평가했다.

| 지표 | v1 | v2 |
|---|---:|---:|
| Top1 관련성 | 78% | 96% |
| Top3 관련성 | 80% | 96% |
| Fast Path hit | 78% | 96% |
| fallback | 22% | 4% |
| 자동 응답 품질 | 2.56/5 | 4.84/5 |

실제 approved Fast Path resolver 검증:

- 관련 Fast Path: 96%
- irrelevant hit: 0건
- fallback: 4% (`anchor_miss` 2건)
- 평균 latency: 7.76ms
- p95 latency: 14.49ms

상세 결과:

- `ai/reports/course_question_v1_v2_comparison_2026-06-13.json`
- `ai/reports/course_question_actual_fast_path_shadow_2026-06-13.json`
- `ai/logs/course_shadow_test_2026-06-13.log`

## 검색 개선

- approved immutable allowlist를 55개로 확대했다.
- 일반 토큰 중복만으로 발생하는 Fast Path 오검색을 막기 위해 specific anchor gate를 추가했다.
- immutable approved 카드 로딩을 캐시해 평균 resolver latency를 84ms 수준에서 7.76ms로 줄였다.
- anchor gate 적용 후 irrelevant hit는 0건이다.

## 정리한 파일

제거:

- `ai/app/scripts/enrich_draft_cards_korean.py`
- `ai/tests/test_enrich_draft_cards_korean.py`
- 완료된 dry-run 임시 보고서 6개

유지:

- 2주 이상 된 백업은 존재하지 않아 백업 삭제를 수행하지 않았다.
- runner/retriever의 v1 fallback 경로는 rollback 안전망이므로 제거하지 않았다.

## 검증

- migration validate-only: lint errors 0
- knowledge-card lint: 통과
- focused tests: 64 passed 후 기대값 수정
- v2 Fast Path tests: 16 passed
- workflow regression: 64 passed, 기존 v1 corpus missing 6건 실패
- `SHADOW_MODE=true` 유지

## 교체 달성률

- Top1 관련성 ≥75%: 충족
- Fast Path ≥75%: 충족
- fallback ≤25%: 충족
- 응답 품질 v1 이상: 충족
- approved 카드 ≥80개: **미충족, 55/80**

권고 시점은 추가 25개 이상을 동일 검토 기준으로 승인하고, 실제 Shadow 트래픽에서 관련 Fast Path와 latency를 재검증한 이후다.

## 남은 위험

1. 신규 카드 payload는 문항 중심 설명이라 개념 일반화 품질에 사람 검토가 추가로 필요하다.
2. 실제 Fast Path의 두 fallback 문항은 기존 protected approved 카드의 한국어 retrieval 필드가 약하다.
3. approved allowlist 캐시는 카드 변경 후 프로세스 재시작 또는 cache clear가 필요하다.
4. 기존 v1 corpus missing 6건으로 workflow 전체 통과 상태는 아니다.

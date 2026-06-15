# concepts_v2 questions 기반 보강 결과

## 안전 상태

- 판정: **NOT_READY**
- `SHADOW_MODE`: `true` 유지
- approved 카드 변경: 0건
- v1 concepts 변경: 0건
- commit / push: 수행하지 않음

## 백업

- 전체 백업: `ai/app/knowledge/concepts_v2_backups/course-question-enrichment-20260613-152946`
- approved manifest: `ai/app/knowledge/concepts_v2_backups/course-question-enrichment-20260613-152946/approved-sha256-manifest.json`
- approved SHA-256 불일치: 0건

## 보강 결과

- 전체 카드: 142개
- questions 문항과 매핑된 draft 카드: 137개
- 보강한 draft 카드: 137개
- unmapped draft 카드: 0개
- broad term 위험: 0개
- 구조 품질 감사 후보: 142개
- 실제 신규 승인 추천 draft 카드: 137개

구조 품질 감사 후보는 broad term, 중복 term, weak retrieval 필드 같은 정적 위험을 통과했다는 의미다.
코스 질문 retrieval 성능이 아직 v1보다 낮으므로 실제 일괄 승인을 권장하지 않는다.
전체 후보 목록은 `ai/reports/course_question_quality_audit_2026-06-13.json`에 기록했다.

## Shadow 및 v1/v2 비교

동일한 코스별 questions 문항 50개를 사용했다. 각 코스에서 10개씩 선택했다.

| 지표 | v1 | v2 |
|---|---:|---:|
| Top1 관련성 | 78% | 50% |
| Top3 관련성 | 80% | 58% |
| Fast Path hit rate | 78% | 0% |
| fallback rate | 22% | 100% |
| 평균 retrieval latency | 23.89ms | 16.77ms |
| 자동 응답 품질 점수 | 2.56/5 | 3.00/5 |

v2 fallback 원인은 Top1 miss 25건, payload draft 25건이다. draft payload를 Fast Path hit로 계산하지 않았다.
상세 결과와 Top3는 `ai/reports/course_question_v1_v2_comparison_2026-06-13.json`,
Shadow 로그는 `ai/logs/course_shadow_test_2026-06-13.log`에 기록했다.

## 대표 카드

- `java-primitive`: Java 기본 자료형 문항, 정답 `String`, 오답별 이유와 Java 실행 예시 보강
- `spring-aop`: `@Around` 어드바이스의 실행 전후 제어 설명과 실무 적용 보강
- `frontend-jsx-expression`: JSX 표현식의 `{ }` 사용 이유와 오답 분석 보강
- `python-decorator`: 함수 수정 없이 기능을 추가하는 데코레이터 설명 보강
- `algorithm-breadth-first-search`: BFS의 큐 사용 이유와 비교 설명 보강

## 검증

- migration v2 validate-only: lint errors 0
- knowledge-card lint: 통과
- 관련 테스트: 33 passed
- approved manifest: 5개 모두 일치
- 카드 파일 수: 142개 유지

## 남은 작업 및 위험

1. v2 Top1 관련성이 50%로 v1보다 낮아 retrieval ranking 추가 보정이 필요하다.
2. 137개 draft payload는 승인되지 않아 실제 Fast Path에서 사용할 수 없다.
3. 범용 문장에 `은(는)`, `String가`처럼 한국어 조사가 어색한 사례가 있어 사람 검토가 필요하다.
4. 자동 품질 감사 142개 통과는 정적 구조 기준이며 실제 교육적 정확성 승인을 대체하지 않는다.
5. 코스별 대표 질의와 paraphrase를 추가해 v2 Top1 관련성이 v1 이상인지 재검증해야 한다.

현재 상태에서 v1 교체 또는 `SHADOW_MODE=False` 전환을 권장하지 않는다.

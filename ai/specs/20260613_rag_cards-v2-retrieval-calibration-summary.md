---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "RAG Cards v2 검색 정확도 향상을 위한 보정(Calibration) 모델 명세서"

---

# RAG 카드 v2 검색 보정

## 결정

**READY**

이 결정은 검색 순위 준비 상태에만 적용됩니다. 카드나 페이로드를 승인하지 않았으며 v2도 활성화하지 않았습니다.

## 근본 원인

어휘 점수 계산기가 제목이나 개념 토큰을 하나라도 공유하면 정확한 구문으로 간주해 같은 고정 가중치를 추가했습니다. 그 결과 다음 문제가 발생했습니다.

- `extends` 질의에서 `java-extends-keyword`와 `java-extends`의 점수가 같았습니다.
- `with` 질의에서 `python-with-statement`와 `python-with`의 점수가 같았습니다.
- 두 동점의 순위가 파일 순서로 결정됐습니다.
- Spring CacheEvict 카드에 영어 캐시 별칭이 없어 `Spring cache` 영문 질의로 검색할 수 없었습니다.

## 보정

- 토큰 중첩 기반 정확 일치 가중치를 순서가 있는 구문 일치로 교체했습니다.
- 용어 구문, 별칭 구문, 부스트 키워드 구문에 서로 다른 순위 가중치를 적용했습니다.
- 기존 `spring-spring-question-59` 카드에 Spring cache, cache eviction, cache evict 검색 필드를 추가했습니다.
- 임계값이나 질문별 순위 예외는 추가하지 않았습니다.

카드 한 개의 검색 필드를 보정했고, 일반 어휘 순위 규칙은 모든 카드에 맞게 보정했습니다.

## 대상별 상위 3개 결과

| Query | Rank 1 | Score | Rank 2 | Score | Rank 3 | Score |
| --- | --- | ---: | --- | ---: | --- | ---: |
| `Spring cache` | `spring-spring-question-59` | 7.5 | `spring-spring-bean-scope` | 4.5 | `spring-aop` | 3.0 |
| `extends` | `java-extends` | 8.5 | `java-extends-keyword` | 4.5 | - | - |
| `with` | `python-with` | 8.5 | `python-with-statement` | 4.5 | - | - |

대조 질의는 기존처럼 1위를 유지했습니다.

- `React key` → `frontend-react-key`, score 11.0
- `Java equals` → `java-equals`, score 9.5

## 섀도 비교

| 지표 | 이전 | 이후 |
| --- | ---: | ---: |
| 빠른 경로 성공률 | 80.0% | 100.0% |
| 폴백 비율 | 20.0% | 0.0% |
| 예상 Ollama 호출 감소율 | 80.0% | 100.0% |
| 평균 검색 지연 시간 | 3.52 ms | 7.44 ms |
| 대상 오검색 수 | 3 | 0 |
| 오검색 제거율 | - | 100.0% |

보정 후 1위 점수 분포는 다음과 같습니다.

- 최솟값: 7.5
- P25: 9.5
- 중앙값: 11.0
- P75: 11.0
- P90 / 최댓값: 14.5

## 안전성

- 카드 142개와 생성 페이로드는 모두 `draft` 상태를 유지합니다.
- 카드를 추가하거나 생성하지 않았습니다.
- `ACTIVE_CARD_STORE`를 변경하지 않았습니다.
- v1 개념을 수정하지 않았습니다.
- 커밋이나 푸시를 수행하지 않았습니다.

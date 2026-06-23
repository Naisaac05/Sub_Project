---
type: spec
category: rag
status: active
updated: 2026-06-21
description: "RAG v2 카드 82장 승인 이후 전환 기준 재판정"
---

# RAG v2 전환 준비도 재판정

## 결론

**NOT_READY**

### 2026-06-22 재검증

- 50문항 평균 응답 품질: **4.52/5**, 기준 통과
- 합성 Shadow: Top1 100%, Fast Path 90%, fallback 10%, 오검색 0건, 라우팅 기준 통과
- 실제 Ollama fallback: 필수 표본 **0/2 통과**, 품질 기준 미달
- 실제 운영 트래픽: 미검증

따라서 카드 payload 품질과 합성 Shadow 라우팅은 개선됐지만 실제 fallback 생성 품질이 확보되지 않아 `NOT_READY`를 유지한다.

### 2026-06-22 근거 기반 fallback 안전화

- approved 카드의 강한 단일 근거가 있을 때만 Ollama fallback을 호출한다.
- 실제 라이브 평가에서 근거 있음 1회 호출, 근거 없음 0회 호출을 확인했다.
- 실제 생성 답변은 `missing_topic` 품질 게이트에서 차단됐고 모델 원문 대신 안전 응답을 반환했다.
- 근거 없는 질의도 모델을 호출하지 않고 동일한 안전 응답을 반환했다.
- 사용자 노출 안전 게이트는 통과했지만 생성 품질 자체는 개선되지 않아 `NOT_READY`를 유지한다.

승인 카드 수와 검색 정확도 기준은 충족했지만 평균 응답 품질과 운영 Shadow 검증 기준을 아직 충족하지 못했다. v1 제거와 `SHADOW_MODE=false` 전환은 진행하지 않는다.

## 검증 결과

| 기준 | 목표 | 결과 | 판정 |
|---|---:|---:|---|
| approved 카드 | 80장 이상 | 82장 | 통과 |
| 기존 approved 체크섬 불일치 | 0건 | 0건 | 통과 |
| 50문항 Top1 관련성 | 95% 이상 | 100% | 통과 |
| Fast Path | 90% 이상 | 100% | 통과 |
| fallback | 10% 이하 | 0% | 통과 |
| 관련 없는 Top1 | 0건 | 0건 | 통과 |
| 평균 응답 품질 | 4.5/5 이상 | 4.52/5 | 통과 |
| knowledge lint | 오류 0건 | 오류 0건 | 통과 |
| 합성 Shadow 트래픽 | 기준 충족 | Top1 100%, Fast Path 90%, fallback 10% | 통과 |
| 실제 Ollama fallback | 필수 표본 통과 | 0/2 | **미달** |
| 운영 Shadow 트래픽 | 검증 완료 | 미검증 | **미달** |
| 재시작·롤백 복원 | 검증 완료 | 미검증 | **미달** |

## 이번 배치

- 후보 20장 중 실행 검증을 통과한 19장을 승인했다.
- `spring-circuit`는 Resilience4j 실행 의존성이 없어 draft로 보류했다.
- approved는 63장에서 82장으로, draft는 81장에서 62장으로 변경됐다.
- Java 문자열 비교와 React 리스트 key 질의는 Lexical 및 BM25 Top1으로 고정했다.
- 기존 approved 63장의 파일 SHA-256은 모두 유지됐다.

## Shadow E2E 해석

`evaluate_v2_approved_ollama_e2e.py`는 approved 카드의 검색·라우팅·Fast Path를 50문항으로 검증했다. 이번 표본은 fallback이 0건이어서 Ollama 호출은 발생하지 않았다. 따라서 이 결과는 Shadow 라우팅 E2E 증거이며, 실제 fallback 생성 품질 검증을 대체하지 않는다.

## 다음 전환 조건

1. 실제 fallback 표본에서 Ollama 생성 품질과 실패 복구 기준을 통과한다.
2. `SHADOW_MODE=true` 운영 트래픽에서 합성 Shadow와 동일한 기준을 확인한다.
3. 캐시 무효화, 프로세스 재시작, 백업 복원 리허설을 통과한다.

## 근거

- `ai/reports/rag_card_expansion_baseline_2026-06-21.json`
- `ai/reports/rag_card_expansion_post_approval_2026-06-21.json`
- `ai/reports/rag_card_next20_approval_dryrun_2026-06-21.json`
- `ai/reports/rag_card_expansion_retrieval_2026-06-21.json`
- `ai/reports/v2_approved_ollama_e2e_2026-06-21.json`
- `ai/reports/v2_approved_ollama_e2e_2026-06-22.json`
- `ai/reports/live_ollama_fallback_2026-06-22.json`
- `ai/reports/synthetic_shadow_traffic_2026-06-22.json`
- `ai/reports/grounded_fallback_live_2026-06-22.json`

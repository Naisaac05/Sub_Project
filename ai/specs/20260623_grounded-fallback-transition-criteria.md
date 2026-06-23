---
type: spec
category: rag
status: active
updated: 2026-06-23
description: "근거 기반 fallback 운영 Shadow 성공 기준과 전환 판정 기준"
---

# 근거 기반 fallback 전환 기준

## 목적

근거 기반 fallback은 승인된 RAG 카드가 있을 때만 모델을 호출하고, 모델 출력이 품질 게이트를 통과하지 못하면 승인 근거에서 추출한 답변으로 복구한다. 승인 근거가 없으면 모델을 호출하지 않고 안전 응답을 반환한다.

이 문서는 운영 Shadow 검증에서 어떤 결과를 성공으로 볼지와 serve 전환을 언제 막을지를 고정한다.

## 성공 기준

- `expected_route=grounded_fallback_generation` 케이스는 최종 route가 반드시 `grounded_fallback_generation`이어야 한다.
- `expected_route=grounded_fallback_safe_response` 케이스는 최종 route가 반드시 `grounded_fallback_safe_response`이어야 한다.
- 승인 근거 케이스는 실제 모델 호출이 1회 이상 발생해야 한다.
- 승인 근거가 없는 케이스는 모델 호출이 0회여야 한다.
- `generation`, `rag_generation`, `fallback_template` 같은 비검증 route가 최종 응답으로 노출되면 실패다.
- 승인 근거 generation 성공률은 95% 이상이어야 한다.

## Shadow 전환 판정

`shadow_readiness=READY` 조건:

- 전체 case gate 통과
- unsafe route 0건
- 승인 근거 케이스의 실제 모델 호출 확인
- 근거 없음 케이스의 모델 호출 0회 확인
- 승인 근거 generation 성공률 95% 이상

## Serve 전환 판정

`serve_readiness=READY` 조건은 Shadow 전환 조건에 더해 실제 운영 트래픽 Shadow 검증이 필요하다. 로컬 합성/확장 케이스만 통과한 상태에서는 `production_shadow_not_validated`를 blocker로 남기고 `serve_readiness=NOT_READY`로 판정한다.

## 2026-06-23 재판정

- 운영 Shadow 확장 케이스: 8개
- 승인 근거 generation 케이스: 6개
- 근거 없음 안전 응답 케이스: 2개
- route 결과: `grounded_fallback_generation` 6건, `grounded_fallback_safe_response` 2건
- unsafe route: 0건
- 승인 근거 generation 성공률: 100%
- 근거 없음 모델 호출: 0회

판정:

- `shadow_readiness=READY`
- `serve_readiness=NOT_READY`
- serve blocker: `production_shadow_not_validated`

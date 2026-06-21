---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "v1 → v2 교체 준비 기준 상세 요구사항 및 기능 동작 명세서"

---

# v1 → v2 교체 준비 기준

## 교체 승인 기준

다음 조건을 모두 만족할 때만 v1 전체 교체를 검토한다.

- 코스 questions 50개 이상 Shadow 평가에서 v2 Top1 관련성 75% 이상
- v2 Fast Path hit rate 75% 이상
- v2 approved 카드 80개 이상
- v2 자동 응답 품질 점수가 v1 이상
- v2 fallback rate 25% 이하
- migration lint와 knowledge-card lint 오류 0건
- approved 카드 SHA-256 manifest 불일치 0건
- workflow regression이 기존 기준보다 악화되지 않음
- `SHADOW_MODE=true` 상태에서 최소 1회 운영 트래픽 검증 완료

## 현재 달성 상태

- Top1 관련성: 96% — 충족
- Fast Path hit rate: 96% — 충족
- approved 카드: 55개 — 미충족
- 자동 응답 품질: v2 4.84/5, v1 2.56/5 — 충족
- fallback rate: 4% — 충족
- Shadow mode: `true` 유지

현재 판정은 **NOT_READY**다. approved 카드 80개 기준이 충족되지 않았다.

## Rollback

1. `SHADOW_MODE=true`를 유지하거나 복원한다.
2. v2 Fast Path master flag를 비활성화한다.
3. 기본 v1 retriever와 Ollama fallback 경로를 계속 사용한다.
4. 신규 승인 48개는 `ai/app/knowledge/concepts_v2_backups/reviewed-approval-20260613-160000`에서 복원한다.
5. 기존 approved 5개는 `ai/app/knowledge/concepts_v2_backups/course-question-enrichment-20260613-152946/approved-sha256-manifest.json`으로 무결성을 확인한다.
6. v2 교체 전후 동일한 코스 질문 세트로 Shadow 평가를 재실행한다.

v1 fallback 코드와 저장소는 rollback 검증이 완료될 때까지 제거하지 않는다.

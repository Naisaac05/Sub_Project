---
type: report
category: rag
status: complete
updated: 2026-06-23
description: "근거 기반 fallback 운영 Shadow 확장 검증과 전환 판정"
---

# 근거 기반 fallback 전환 판정 리포트

## 실행 결과

- 실행 리포트: `ai/reports/grounded_fallback_live_2026-06-23.json`
- 케이스 수: 8
- 통과: 8
- 실패: 0
- route 분포:
  - `grounded_fallback_generation`: 6
  - `grounded_fallback_safe_response`: 2
- unsafe route: 0
- 승인 근거 generation 성공률: 100%
- 근거 없음 케이스 모델 호출: 0회
- 모델 다운로드: 없음

## 케이스 구성

승인 근거 generation:

- React key 리스트 사용 주의점
- React useState 상태 변경 주의점
- Java equals 비교 로직
- Java ArrayList 실무 특징
- Spring Valid 입력 검증
- Python asyncio 비동기 작업

근거 없음 안전 응답:

- Java CopyOnWriteArrayList
- Next.js Server Action 보안 주의점

## 전환 판정

- Shadow 확대: `READY`
- Serve 전환: `NOT_READY`

Serve 전환 blocker:

- `production_shadow_not_validated`

## 해석

근거 기반 fallback은 로컬 운영 Shadow 확장 케이스에서 기대 route를 모두 만족했다. 승인 근거가 있는 경우에는 모델 호출 후 최종 응답이 검증된 `grounded_fallback_generation`으로 귀결됐고, 승인 근거가 없는 경우에는 모델 호출 없이 안전 응답으로 닫혔다.

다만 이번 검증은 로컬 확장 케이스 기반이다. 실제 운영 트래픽 Shadow에서 사용자 질문 분포, 지연 시간, cache 영향, 누락 카드 비율을 확인하지 않았으므로 serve 전환은 보류한다.

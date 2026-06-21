---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "LangGraph 기반 AI 리뷰 프로덕션 클로즈아웃 상세 절차 설계서"

---

# AI 리뷰 프로덕션 마무리 설계

## 목표

AI 리뷰 프로덕션 마무리 항목을 완료합니다. 관리자 UI를 후보 승인 v2에 연결하고, DB·운영 요구사항을 문서화하며, LangGraph 의존성 동작을 검증합니다. 또한 서비스 토큰 배포를 구성하고 집중 빌드를 실행하며 로그·메트릭 수집 방식을 정의합니다.

## 접근 방식

관리자 UI는 DB 기반 v2 API를 우선 사용합니다. 명시적인 `import-jsonl` 작업으로 JSONL을 계속 사용할 수 있게 해 v1 폴백 경로를 제거하지 않고 기존 큐를 마이그레이션합니다. 운영 항목은 이 작업 공간 외부의 환경 설정과 로그 인프라에 의존하므로 배포 체크리스트에 기록합니다.

## UI 동작

후보 페이지는 v2 수명 주기 상태(`PENDING`, `APPROVED`, `REJECTED`, `MERGED`)를 표시하고 다음 작업을 지원합니다.

- `reviewerEditedAnswer`를 사용한 수정 후 승인
- `rejectedReason`을 사용한 거부
- ID를 지정해 다른 후보로 병합
- JSONL 행을 v2 DB로 가져오기

## 운영

체크리스트는 Hibernate 스키마 검토, 필수 환경 변수, LangGraph 선택적 의존성 검증, 서비스 토큰 배포, 전체 빌드 명령을 다룹니다. 폴백, 검색 실패, 후보 수집, 적체량을 확인하는 로그 수집 쿼리도 포함합니다.

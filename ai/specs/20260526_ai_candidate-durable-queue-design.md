---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "안정적인 데이터 처리를 위한 AI 후보 내구성 대기열(Durable Queue) 설계서"

---

# AI 후보 영속 큐 설계

## 목표

런타임 AI 리뷰 후보 수집을 암묵적인 JSONL 기록 방식에서 기존 Spring DB 기반 후보 큐로 전환합니다. JSONL은 명시적으로 선택하는 개발용 폴백으로만 유지합니다.

## 아키텍처

Spring은 이미 `AiReviewCandidate`, `AiReviewCandidateAudit`, V2 승인 워크플로를 통해 영속적인 후보 상태를 관리합니다. Python 자동 후보 페이로드를 받아 `source=AUTO`, `status=PENDING`으로 저장하는 내부 수집 API를 추가합니다. Python 후보 수집은 이 HTTP 싱크를 우선 사용하며, `AI_REVIEW_CANDIDATE_SINK=jsonl`을 명시적으로 설정한 경우에만 JSONL에 추가합니다.

## 데이터 흐름

1. Python 워크플로가 현재 JSONL에 기록하는 것과 같은 자동 후보 페이로드를 만듭니다.
2. `AI_REVIEW_CANDIDATE_SINK=http`이면 `candidate_save_node()`가 페이로드를 Spring으로 전송합니다.
3. Spring은 `external_candidate_id`로 중복을 제거하고 가능한 경우 빈 초안을 갱신하며 적체량 지표를 기록합니다.
4. 후보 승인은 기존 V2 관리자 API를 통해 계속 진행합니다.

## 오류 처리

후보 수집은 답변 경로 밖에서 처리합니다. HTTP 수집 실패, 중복 충돌, JSONL 기록 실패, 수집 비활성화가 발생해도 `candidate_capture_failed` 또는 `candidate_capture_disabled` 품질 플래그와 함께 정상 답변 응답을 반환합니다.

## 테스트

Spring 수집 서비스·컨트롤러 동작과 Python 싱크 선택은 TDD로 구현합니다. 회귀 테스트는 기본값에서 JSONL을 사용하지 않는지, 명시적 JSONL 모드가 개발 환경에서 계속 동작하는지, 중복 DB 수집이 멱등인지, 수집 실패가 답변 생성을 중단하지 않는지 검증해야 합니다.

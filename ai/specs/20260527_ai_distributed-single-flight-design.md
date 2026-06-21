---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "중복 요청 최소화를 위한 분산 Single-Flight 패턴 상세 명세서"

---

# AI 분산 Single-Flight 설계

## 목표

기존 워크플로 single-flight 경로에 선택적 Redis 요청 키 잠금을 추가해 여러 Python 워커 프로세스에서 발생하는 중복 LLM 생성을 줄입니다.

## 범위

다음 P3 분산 Single-Flight 항목을 완료합니다.

- Redis 요청 키 잠금
- 타임아웃 및 오래된 잠금 해제 정책
- 스트리밍 참여 가능성 검토

## 접근 방식

기존 프로세스 내부 `run_single_flight()` API를 유지하고 `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT=true`와 `AI_REVIEW_ANSWER_CACHE_BACKEND=redis`로 명시적으로 활성화한 경우에만 Redis 기반 조정 계층을 추가합니다.

잠금 키는 답변 캐시 키와 같은 캐시 네임스페이스를 사용하므로 불변 인덱스 캐시 네임스페이스 변경 시 분산 잠금 트래픽도 격리됩니다.

## 데이터 흐름

1. 로컬 프로세스 수준 single-flight가 한 프로세스 내부의 스레드 중복을 계속 제거합니다.
2. 로컬 리더가 임의의 소유자 토큰과 함께 `singleflight:<request-key>`에 Redis `SET NX EX`를 시도합니다.
3. 잠금 획득에 성공하면 프로듀서를 실행하고 소유자 토큰을 확인해 잠금을 해제합니다.
4. 잠금 획득에 실패하면 제한된 시간 동안 Redis 답변 캐시를 폴링합니다.
5. 답변이 나타나면 기존 프로듀서가 실행돼 정상 캐시 적중 워크플로 상태를 반환합니다.
6. 타임아웃 전에 답변이 나타나지 않으면 잠금이 오래됐거나 느리다고 판단하고 로컬에서 프로듀서를 실행합니다.

## 타임아웃

- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_LOCK_TTL_SECONDS`: default 60 seconds.
- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_WAIT_TIMEOUT_SECONDS`: default 30 seconds.
- `AI_REVIEW_DISTRIBUTED_SINGLE_FLIGHT_POLL_INTERVAL_MS`: default 100 milliseconds.

0 이하이거나 유효하지 않은 값은 기본값으로 대체합니다.

## 스트리밍 참여

이 단계에서는 완전한 스트리밍 참여를 구현하지 않습니다. 현재 설계는 비스트리밍 워크플로 생성을 조정하며, 완료 후 팔로워가 답변 캐시를 통해 참여하게 합니다. 스트리밍 참여에는 청크 팬아웃이나 요청별 스트림 버퍼가 필요하므로 별도로 처리해야 합니다.

## 실패 정책

Redis 실패는 치명적 오류로 처리하지 않습니다. 잠금 획득, 대기, 해제에 실패하면 워크플로는 기존 로컬 single-flight 동작으로 폴백합니다.

소유자 토큰을 확인한 해제 방식은 한 워커가 다른 워커가 갱신했거나 새로 획득한 잠금을 삭제하지 못하게 합니다.

## 테스트

결정론적 가짜 Redis 테스트를 사용합니다.

- 팔로워가 원격 답변을 기다린 뒤 중복 생성 없이 캐시 결과를 반환하는지 확인합니다.
- 오래됐거나 느린 잠금의 대기 타임아웃이 로컬 생성으로 폴백하는지 확인합니다.
- 소유자 토큰 기반 해제가 다른 소유자의 잠금을 삭제하지 않는지 확인합니다.

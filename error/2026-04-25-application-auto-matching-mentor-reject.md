# 멘티 신청서 자동 매칭 흐름에 멘토 승인/거절 API가 남아 있던 문제

- 발생 날짜: 2026-04-25
- 범위: backend / DB
- 심각도: medium

## 증상

멘티 신청서는 결제 후 자동 매칭되어야 하는데, 백엔드에 멘토가 배정된 신청서를 조회하고 승인/거절하는 API가 남아 있었다. 이 상태에서는 신청서가 `PENDING_MENTOR_APPROVAL` 단계로 이동할 수 있어, "멘토는 멘티를 거절할 수 없다"는 자동 매칭 정책과 맞지 않았다.

## 원인

과거 수동 승인 흐름의 코드가 남아 있었다. `ApplicationService`에서 결제 완료 후 `assignNextAvailableMentor`가 신청서를 멘토 승인 대기 상태로 만들고, `/api/applications/my-assignments`, `/approve`, `/reject` 엔드포인트가 그 상태를 전제로 동작했다.

## 해결 방법

결제 완료 시 바로 멘토를 선택하고 `MatchingStatus.ACCEPTED` 매칭을 생성하도록 변경했다. 외부에서 멘토가 신청서를 승인/거절하거나 본인 배정 신청서를 조회하는 API는 제거했다.

- `backend/src/main/java/com/devmatch/service/ApplicationService.java:66` - 멘토 거절 이력을 제외하지 않고 현재 멘토 목록에서 부하가 가장 낮은 멘토를 선택해 즉시 매칭 생성
- `backend/src/main/java/com/devmatch/service/ApplicationService.java:101` - 결제 확인 후 `createAutoMatching`을 호출해 자동 매칭 완료
- `backend/src/main/java/com/devmatch/controller/ApplicationController.java:22` - 신청서 제출/결제 확인 API만 남기고 멘토 승인/거절 API 제거
- `backend/src/main/java/com/devmatch/entity/Application.java:158` - 자동 매칭 완료 시 담당 멘토와 `ACCEPTED` 상태를 함께 저장

검증:

```text
cd backend
GRADLE_USER_HOME=../.gradle-user-home ./gradlew compileJava --no-daemon
BUILD SUCCESSFUL
```

## 재발 방지·메모

신청서 자동 매칭 정책에서는 멘토 승인/거절 엔드포인트를 다시 노출하지 않는다. `PENDING_MENTOR_APPROVAL` 값과 일부 엔티티 메서드는 과거 데이터 호환 가능성이 있어 이번 수정에서는 제거하지 않았지만, 새 결제/매칭 흐름에서는 사용하지 않아야 한다.

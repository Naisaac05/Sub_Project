# AdminAuditLog.target_type 컬럼 길이 부족 — MENTOR_CHANGE_REQUEST 삽입 시 500

- 발생 일시: 2026-04-25
- 영역: backend / DB
- 심각도: high

## 증상

관리자가 멘토 교체 신청을 반려(`POST /api/admin/mentor-change-requests/{id}/reject`)하거나
승인(`/approve`)하면 HTTP 500 으로 응답. 로그에는 다음 오류가 남음:

```
SQL Error: 1406, SQLState: 22001
Data truncation: Data too long for column 'target_type' at row 1
```

통합 테스트(`AdminMentorChangeRequestControllerIT`) 실행 시 동일하게 재현.

## 원인

`AdminAuditLog` 엔티티의 `target_type` 컬럼이 `length = 20`으로 선언되어 있는데,
`AdminMentorChangeRequestService`가 감사 로그를 기록할 때 전달하는 문자열
`"MENTOR_CHANGE_REQUEST"` 는 **21자**로 1자 초과.

관련 파일:
- `backend/src/main/java/com/devmatch/entity/AdminAuditLog.java:33` — `@Column(name = "target_type", nullable = false, length = 20)`
- `backend/src/main/java/com/devmatch/service/AdminMentorChangeRequestService.java:83` — `"MENTOR_CHANGE_REQUEST"` 전달

## 해결 방법

`AdminAuditLog.java:33` 의 `length = 20` 을 `length = 30` 으로 변경.
Hibernate `ddl-auto: update` 가 실행 시 자동으로 `ALTER TABLE` 을 적용.

변경 내용:
```java
// before
@Column(name = "target_type", nullable = false, length = 20)

// after
@Column(name = "target_type", nullable = false, length = 30)
```

## 재발 방지 / 메모

- 현재 사용 중인 target_type 값들: `"POST"`, `"COMMENT"`, `"PAYMENT"`, `"USER"`, `"MENTOR_PROFILE"`, `"MENTOR_CHANGE_REQUEST"` (21자) — 가장 긴 것이 21자.
- length = 30 으로 설정하면 향후 새 target_type 추가에도 여유 있음.
- `@Size` 또는 단위 테스트에서 targetType 길이를 검증하는 장치가 없어 컬럼 길이 위반이 런타임에야 드러남. 새 target_type 문자열을 추가할 때는 `AdminAuditLog.java` 의 컬럼 길이와 비교해 확인할 것.

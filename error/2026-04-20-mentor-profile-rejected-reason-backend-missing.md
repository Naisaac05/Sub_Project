# 멘토 프로필 반려 사유(`rejectedReason`) 백엔드 갭 — 관리자 엔드포인트 부재

- 발생일: 2026-04-20 (진단) / 2026-04-21 (해결)
- 영역: backend (+ frontend 계약 불일치)
- 심각도: medium

## 증상

Phase G 시나리오 3 (REJECTED → 재신청) 테스트 중:

1. DB 에서 `mentor_profiles.status='REJECTED'` 로 강제 전환 후 `/mentor/status` 접속 → 빨간 REJECTED 카드는 정상 노출.
2. 카드에서 "재신청하기" 버튼 → `/mentor/apply` 로 이동. 이전 값 프리필은 정상.
3. 그러나 페이지 상단에 있어야 하는 **"이전 신청 반려됨 — 사유: …"** 빨간 배너가 표시되지 않음.

프론트 조건식:
```tsx
// frontend/src/app/mentor/apply/page.tsx:336
{rejectedReason && (
  <div className="rounded-lg border border-red-200 bg-red-50 ...">
    이전 신청 반려됨 — 사유: {rejectedReason}
  </div>
)}
```

`rejectedReason` 이 `null` 이라 조건이 거짓 → 배너가 렌더되지 않는다.

## 원인

**최초 진단(오진)**: `MentorProfile` 엔티티에 `rejectedReason` 컬럼 자체가 없다고 기록했으나, 재검토 결과 오진이었다.

**실제 원인**: 저장소 자체에는 사유를 적을 자리가 있다. 다만 **사유를 써넣을 관리자 진입점 자체가 없었다**.

- `MentorProfileHistory` 엔티티에는 이미 `rejectedReason`, `reviewedAt`, `reviewedBy` 필드가 있고, `markApproved(Long)` / `markRejected(Long, String)` 메서드도 구현되어 있다. ([MentorProfileHistory.java:74-97](backend/src/main/java/com/devmatch/entity/MentorProfileHistory.java:74))
- `MentorProfileResponse` DTO 에도 `rejectedReason` 필드가 있고, `MentorService.getMyMentorProfile()` 이 REJECTED 상태면 히스토리에서 최신 사유를 읽어와 DTO 에 채운다. ([MentorService.java:110-122](backend/src/main/java/com/devmatch/service/MentorService.java:110))
- 문제는 `MentorController` 가 `/apply` (POST), `/me` (GET) 두 개만 노출하고 있어서, **관리자가 신청을 승인/반려하며 사유를 기록할 엔드포인트 자체가 없었다**는 점. ([MentorController.java](backend/src/main/java/com/devmatch/controller/MentorController.java))
- 테스트 중에는 DB 에서 직접 `UPDATE mentor_profiles SET status='REJECTED' WHERE id=6;` 로 강제 전환했기 때문에 히스토리 측 `rejected_reason` 은 여전히 `NULL` 이었고, 결국 프론트 배너가 비어 있었다.

즉 계약 갭이 아니라 **플로우 갭**이었다: 데이터 모델과 조회 API 는 완비되어 있었지만, 사유를 기록할 쓰기 진입점이 없었다.

## 해결 방법

`claude/mentor-admin-endpoints` 브랜치에서 관리자 엔드포인트를 신설했다. 엔티티 변경은 불필요 — DTO/서비스/컨트롤러 계층만 손댔다.

### 1. 신규 예외

- [MentorProfileNotFoundException.java](backend/src/main/java/com/devmatch/exception/MentorProfileNotFoundException.java) — 404
- [InvalidMentorReviewStateException.java](backend/src/main/java/com/devmatch/exception/InvalidMentorReviewStateException.java) — 409 (이미 심사된 건을 다시 심사하려 할 때)

`GlobalExceptionHandler` 에 두 예외 매핑 추가. ([GlobalExceptionHandler.java:51-66](backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java:51))

### 2. 반려 DTO

[RejectRequest.java](backend/src/main/java/com/devmatch/dto/admin/RejectRequest.java) — `reason` 필드, `@NotBlank + @Size(min=10, max=500)` 검증.

### 3. 서비스 메서드

[MentorService.java](backend/src/main/java/com/devmatch/service/MentorService.java) 에 세 메서드 추가:

- `findAllForAdmin(MentorStatus statusFilter)` — 관리자 목록 조회. status 없으면 전체, 있으면 필터.
- `approve(profileId, adminUserId)` — profile.status → APPROVED, 최신 history 행에 `markApproved(adminUserId)`.
- `reject(profileId, adminUserId, reason)` — profile.status → REJECTED, 최신 history 행에 `markRejected(adminUserId, reason)`.

공통 가드: profile 미존재 → `MentorProfileNotFoundException` / `status != PENDING` → `InvalidMentorReviewStateException`.

### 4. 관리자 컨트롤러

[AdminMentorController.java](backend/src/main/java/com/devmatch/controller/AdminMentorController.java) — `/api/admin/mentor` 하위 3개 엔드포인트.

- `GET /api/admin/mentor?status=PENDING` — 목록
- `POST /api/admin/mentor/{profileId}/approve` — 승인
- `POST /api/admin/mentor/{profileId}/reject` (body: `{ reason }`) — 반려

`/api/admin/**` 경로는 [SecurityConfig.java:49](backend/src/main/java/com/devmatch/config/SecurityConfig.java:49) 에서 `hasRole("ADMIN")` 으로 이미 보호돼 있어서 별도 어노테이션 불필요.

## 재발 방지 / 메모

- **최초 진단이 틀렸던 이유**: 엔티티 이름(`MentorProfile`)만 보고 결론을 냈고, 히스토리 테이블은 확인하지 않았다. 다음에 "컬럼이 없다" 고 주장하기 전에 관련 엔티티/히스토리/Audit 테이블까지 훑어야 한다.
- **마이그레이션 주의**: 엔티티는 변경하지 않았으므로 DB 스키마 변경은 없다. `mentor_profile_history` 테이블에만 데이터가 쌓인다.
- **프론트 관리자 페이지 연결은 별도 브랜치에서**. 현재는 백엔드만 준비된 상태 — 관리자 UI 에서 이 엔드포인트들을 호출하는 페이지(`/admin/mentor`)가 추가로 필요하다.
- 재신청 시 `rejectedReason` 초기화는 불필요 — 재신청은 히스토리에 새 행을 추가하고 프로필 상태를 PENDING 으로 덮으므로, `getMyMentorProfile` 이 "status=REJECTED 일 때만" 사유를 읽어오기 때문에 자동으로 새 플로우를 타게 된다.
- 관련 프론트 참조:
  - [frontend/src/app/mentor/apply/page.tsx:336](frontend/src/app/mentor/apply/page.tsx:336) — 배너 조건부 렌더
  - [frontend/src/lib/mentor.ts](frontend/src/lib/mentor.ts) — `MentorProfileResponse` 타입 정의

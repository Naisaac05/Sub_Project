---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "멘토 교체 신청·심사 설계 (Design Spec) 상세 요구사항 및 기능 동작 명세서"

---

# 멘토 교체 신청·심사 설계 (Design Spec)

> 작성일: 2026-04-25
> 관련 도메인: 매칭(Matching), 관리자 콘솔
> 재사용: `MentorSwapService.swap()`, `AdminAuditLogService`, `admin/posts` 프론트 패턴

---

## 1. 배경

기존에는 관리자가 `/admin/users/[id]` 의 "멘토 교체" 액션으로 임의 시점에 멘토를 바꿀 수 있었지만, 멘티가 직접 교체를 요청할 채널이 없었다. 본 스펙은 멘티가 사유와 함께 교체를 신청 → 관리자가 심사하여 승인(다른 멘토로 교체) 또는 반려하는 워크플로우를 추가한다.

승인 시 실제 교체 메커니즘은 이미 구현된 [`MentorSwapService.swap()`](../../../backend/src/main/java/com/devmatch/service/MentorSwapService.java) 을 그대로 호출하며, 본 스펙은 그 위에 "신청 → 심사" 단계만 얹는다.

## 2. 스코프

### In scope
- 신규 엔티티 `MentorChangeRequest` + 상태 enum + 마이그레이션
- 멘티 API 4개 (제출 / 활성 신청 조회 / 상세 / 취소)
- 관리자 API 5개 (목록 / 상세 / 후보 멘토 조회 / 승인 / 반려)
- 관리자 프론트 페이지 2개 (`/admin/mentor-change-requests` 목록·상세)
- 감사 로그 액션 2종 추가 (`MENTOR_CHANGE_APPROVE`, `MENTOR_CHANGE_REJECT`)
- 백엔드 단위·통합 테스트

### Out of scope
| 항목 | 이유 |
|---|---|
| 멘티 화면(매칭 내역의 "멘토 교체" 버튼) | 별도 담당자 작업. 본 스펙은 호출 가능한 API 까지만 제공 |
| 자동 추천 멘토(점수 기반) | YAGNI. 후보 목록은 카테고리·키워드 검색만 |
| 알림(이메일/푸시) | Phase III 이관 (관리자 콘솔 Common 결정과 동일) |
| 결제·세션 정산 | `MentorSwapService` 가 손대지 않음. 그대로 유지 |
| 신청 이력 통계/대시보드 | YAGNI |

## 3. 결정 사항 요약

| 항목 | 결정 | 대안 / 탈락 이유 |
|---|---|---|
| 데이터 모델 | 신규 `MentorChangeRequest` 엔티티 | `SessionChangeRequest` 확장은 컬럼·검증 혼재 → 가독성 저하 |
| 승인 메커니즘 | 기존 `MentorSwapService.swap()` 호출 | 새로 만들 이유 없음. 단일 책임 |
| 새 멘토 결정 주체 | 관리자가 후보에서 선택 | 멘티 희망 멘토 입력은 운영 통제력 약화 |
| PENDING 동시 보유 | 멘티당 1건 | 큐 단순화·중복 신청 방지 |
| 신청 가능 조건 | 활성 매칭(ACCEPTED/TRIAL) 필수 | PENDING/없음 상태에서 교체 신청은 의미 모호 |
| 멘티의 신청 취소 | PENDING 한정 허용 → CANCELLED | 운영 부담 없이 멘티 융통성 확보 |
| 반려 사유 | 필수 + 멘티에게 노출 | 학생 포트폴리오 시연용 흐름 완결성 |
| 후보 멘토 필터 | 현재 매칭 카테고리 + APPROVED + 현재 멘토 제외 | 전체 표시는 운영 비용↑, 추천 점수 정렬은 YAGNI |
| 부분 유니크 제약 | 애플리케이션 단 검증(MySQL 부분 인덱스 미지원) | 트리거는 과함. 단위 테스트로 보장 |
| 감사 로그 | `MENTOR_CHANGE_APPROVE` + `MENTOR_CHANGE_REJECT` 신규 + 기존 `USER_MENTOR_SWAP` 도 자동 기록(swap() 내부) | swap() 시그니처 변경은 회귀 위험 |

## 4. 데이터 모델

### 4.1 테이블 `mentor_change_requests`

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | BIGINT PK AI | N | |
| `mentee_id` | BIGINT | N | `users.id` (멘티) |
| `current_matching_id` | BIGINT | N | 신청 시점의 활성 매칭 ID 스냅샷 |
| `current_mentor_id` | BIGINT | N | 신청 시점의 멘토 `users.id` 스냅샷 |
| `reason` | VARCHAR(500) | N | 멘티가 입력한 교체 사유 (1~500자) |
| `status` | VARCHAR(20) | N | `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` |
| `decided_by_admin_id` | BIGINT | Y | 승인·반려한 관리자 `users.id` |
| `new_mentor_id` | BIGINT | Y | 승인 시 채워짐. 어떤 멘토로 교체했는지 |
| `reject_reason` | VARCHAR(500) | Y | 반려 시 필수 |
| `created_at` | DATETIME(6) | N | |
| `responded_at` | DATETIME(6) | Y | 승인/반려/취소 시각 |

인덱스
- `idx_mentor_change_status_created (status, created_at)` — 관리자 PENDING 큐 페이지네이션
- `idx_mentor_change_mentee_status (mentee_id, status)` — PENDING 중복 검증/멘티 본인 조회

스키마 반영
- dev: JPA `ddl-auto: update` 가 엔티티로부터 자동 생성
- prod: `ddl-auto: validate` 이므로 `backend/data/devmatch-schema.sql` 와 `backend/src/main/resources/seed-lms.sql` 의 `session_change_requests` 인근에 동일한 `CREATE TABLE mentor_change_requests` DDL 을 추가
- 별도 Flyway/Liquibase 도입은 본 스펙 범위 밖

### 4.2 신규 enum

`com.devmatch.entity.MentorChangeRequestStatus`
```
PENDING, APPROVED, REJECTED, CANCELLED
```

`com.devmatch.entity.AdminActionType` 에 추가:
```
MENTOR_CHANGE_APPROVE,
MENTOR_CHANGE_REJECT
```

### 4.3 엔티티 메서드 (상태 전이)

`MentorChangeRequest` 는 다음 도메인 메서드만 노출 (외부에서 status 직접 변경 불가):

```java
public void approve(Long adminId, Long newMentorUserId) {
    requirePending();
    this.status = APPROVED;
    this.decidedByAdminId = adminId;
    this.newMentorId = newMentorUserId;
    this.respondedAt = LocalDateTime.now();
}

public void reject(Long adminId, String rejectReason) {
    requirePending();
    if (rejectReason == null || rejectReason.isBlank()) throw new IllegalArgumentException(...);
    this.status = REJECTED;
    this.decidedByAdminId = adminId;
    this.rejectReason = rejectReason;
    this.respondedAt = LocalDateTime.now();
}

public void cancel() {
    requirePending();
    this.status = CANCELLED;
    this.respondedAt = LocalDateTime.now();
}

private void requirePending() {
    if (this.status != PENDING) throw new IllegalStateException(...);
}
```

## 5. API

### 5.1 멘티용 (`ROLE_MENTEE`, 본인 소유 검증)

| Method | Path | Body | 응답(2xx) | 주요 에러 |
|---|---|---|---|---|
| POST | `/api/mentee/mentor-change-requests` | `{reason: string}` | `201 {id, status, createdAt}` | 400 `VALIDATION` (사유 길이), 409 `DUPLICATE_PENDING`, 400 `NO_ACTIVE_MATCHING` |
| GET | `/api/mentee/mentor-change-requests/latest` | — | `200 {id, status, reason, rejectReason?, newMentorId?, createdAt, respondedAt?} \| null` | — |
| GET | `/api/mentee/mentor-change-requests/{id}` | — | `200 <상세>` | 403 `NOT_OWNER`, 404 |
| DELETE | `/api/mentee/mentor-change-requests/{id}` | — | `204` | 403 `NOT_OWNER`, 409 `NOT_PENDING`, 404 |

`latest` 엔드포인트는 멘티 화면(타 담당자 작업) 의 버튼 라벨/결과 모달용. 상태와 무관하게 가장 최근 1건을 반환:
- `null` → "멘토 교체 신청" 버튼만
- `PENDING` → "멘토 교체 심사중" (버튼 비활성)
- `APPROVED` / `REJECTED` / `CANCELLED` → 다시 "멘토 교체 신청" 버튼 노출 + 결과(반려 사유 등)를 모달/툴팁으로 노출
  - 결정 4(B1=멘티가 반려 사유 노출)을 만족하기 위해 `rejectReason` 도 응답에 포함

### 5.2 관리자용 (`ROLE_ADMIN`)

| Method | Path | Query/Body | 설명 |
|---|---|---|---|
| Method | Path | Query/Body | 응답(2xx) | 주요 에러 |
|---|---|---|---|---|
| GET | `/api/admin/mentor-change-requests` | `?status=PENDING&page=0&size=20&keyword=` | `200 Page<요약 DTO>` | — |
| GET | `/api/admin/mentor-change-requests/{id}` | — | `200 <상세 DTO>` | 404 |
| GET | `/api/admin/mentor-change-requests/{id}/candidate-mentors` | `?keyword=&page=0&size=20` | `200 Page<후보 DTO>` | 404 |
| POST | `/api/admin/mentor-change-requests/{id}/approve` | `{newMentorUserId: long}` | `200 <상세 DTO>` (APPROVED 상태) | 404, 409 `NOT_PENDING`, 400 `INVALID_MENTOR` (swap 검증 실패: 동일 멘토/미승인/자기자신/활성 매칭 없음) |
| POST | `/api/admin/mentor-change-requests/{id}/reject` | `{rejectReason: string}` | `200 <상세 DTO>` (REJECTED 상태) | 404, 409 `NOT_PENDING`, 400 `VALIDATION` |

권한 매핑은 기존 `Admin*Controller` 패턴(`@PreAuthorize` 또는 `SecurityConfig` 룰)을 그대로 따른다.

## 6. 서비스 동작 명세

### 6.1 `MentorChangeRequestService` (멘티용)

```
submit(menteeUserId, reason):
  1. 사유 길이 검증 (1~500)
  2. 활성 매칭 조회 (PENDING/ACCEPTED/TRIAL 중 가장 최근)
     - ACCEPTED/TRIAL 만 허용. 그 외 → NoActiveMatchingException
  3. PENDING 중복 검증: existsByMenteeIdAndStatus(menteeUserId, PENDING)
     - true → DuplicatePendingMentorChangeRequestException (409)
  4. MentorChangeRequest 저장 (current_matching_id/current_mentor_id 스냅샷)
  5. 응답 DTO 반환

cancel(menteeUserId, requestId):
  1. 조회 → 없으면 404
  2. mentee_id == menteeUserId 검증 → 아니면 ForbiddenOperationException (403)
  3. request.cancel() 호출 (PENDING 검증은 엔티티가 수행)
```

### 6.2 `AdminMentorChangeRequestService` (관리자용)

```
approve(adminId, requestId, newMentorUserId):
  @Transactional
  1. request 조회 → 404 / PENDING 검증 (엔티티 메서드가 수행)
  2. mentorSwapService.swap(adminId, request.menteeId, newMentorUserId, request.reason)
     - swap() 가 활성 매칭/멘토 검증 + USER_MENTOR_SWAP 감사 로그 기록
     - swap() 내부에서 활성 매칭이 없거나 동일 멘토면 ForbiddenOperationException → 그대로 전파
  3. request.approve(adminId, newMentorUserId)
  4. adminAuditLogService.record(adminId, MENTOR_CHANGE_APPROVE, "MENTOR_CHANGE_REQUEST", requestId,
        request.reason, Map.of("newMentorUserId", newMentorUserId, "oldMentorUserId", request.currentMentorId))

reject(adminId, requestId, rejectReason):
  @Transactional
  1. request 조회 → 404 / PENDING 검증
  2. request.reject(adminId, rejectReason) — 사유 빈값 검증은 엔티티가 수행
  3. adminAuditLogService.record(adminId, MENTOR_CHANGE_REJECT, "MENTOR_CHANGE_REQUEST", requestId,
        rejectReason, Map.of("menteeReason", request.reason))

listCandidateMentors(requestId, keyword, pageable):
  1. request 조회
  2. matchingRepo 에서 request.currentMatchingId 의 카테고리 조회
  3. mentorProfileRepo.findApprovedByCategoryAndKeyword(category, keyword, pageable) — 신규 쿼리 메서드
  4. request.currentMentorId 와 동일 user 는 응답 시 필터링 (또는 쿼리에서 NOT IN)
```

### 6.3 동시성/엣지 케이스

- 신청 시점 활성 매칭과 승인 시점 활성 매칭이 다를 수 있음 (멘티가 다른 경로로 매칭이 변경되거나 종료됨). `swap()` 자체가 활성 매칭 재조회 후 검증하므로 **승인 트랜잭션이 안전하게 실패**한다 → 신청은 PENDING 으로 남고, 관리자가 반려 처리 가능.
- 동시에 두 관리자가 같은 requestId 를 승인 시도 → 두 번째 트랜잭션은 `requirePending()` 에서 `IllegalStateException` 으로 실패. 트랜잭션 격리가 READ_COMMITTED 라도 두 번째는 첫 번째 커밋 이후 다시 읽고 실패. 추가 비관락 불필요.
- 멘티 취소와 관리자 승인 경합: 동일 — 두 번째 시도가 PENDING 아님으로 실패.

## 7. 관리자 프론트엔드

### 7.1 라우트 / 파일

| 경로 | 파일 |
|---|---|
| 목록 | `frontend/src/app/admin/mentor-change-requests/page.tsx` |
| 상세 | `frontend/src/app/admin/mentor-change-requests/[id]/page.tsx` |
| API 클라이언트 | `frontend/src/lib/admin/mentor-change-requests.ts` |

신규 컴포넌트는 가능한 한 분리하지 않고 페이지 안에서 관리(파일 비대해지면 그때 추출). 기존 공용 컴포넌트(`AdminListHeader`, `AdminTabs`, `Pagination`)는 재사용.

### 7.2 목록 페이지

- 헤더: "멘토 교체 신청 관리"
- 탭: PENDING / APPROVED / REJECTED / CANCELLED / 전체 (기본 PENDING)
- 검색: 멘티 이름/이메일 키워드
- 테이블 컬럼: 신청일 / 멘티(이름·이메일) / 현재 멘토 / 사유 미리보기(40자, 말줄임) / 상태 뱃지 / 처리일
- 행 클릭 → 상세

### 7.3 상세 페이지 — PENDING

좌측 패널 (정보):
- 신청 정보: 신청일, 멘티(이름·이메일), 현재 매칭 카테고리, 현재 멘토, 사유 전문

우측 패널 — 두 카드:

**(A) 승인 카드**
- 후보 멘토 검색창 + 테이블(이름, 분야, 평점/리뷰수, 진행중 멘티수)
- 라디오로 1명 선택
- "교체 승인" 버튼 → 확인 모달 ("이 멘토로 교체합니다. 되돌릴 수 없습니다.") → POST approve
- 성공: 토스트 + 목록으로 라우팅
- 409 `MatchingNoLongerActive` (혹은 `swap` 의 ForbiddenOperationException): 알림 토스트 "신청 이후 매칭 상태가 변경됨. 반려 처리해 주세요"

**(B) 반려 카드**
- 반려 사유 textarea (1~500자, 카운터)
- "반려" 버튼 → 확인 모달 → POST reject
- 성공: 토스트 + 목록으로 라우팅

### 7.4 상세 페이지 — PENDING 아님 (읽기 전용)

- 결과 패널: 상태에 따라 표시
  - APPROVED: 새 멘토 정보 + 처리한 관리자 + 처리일
  - REJECTED: 반려 사유 + 처리한 관리자 + 처리일
  - CANCELLED: 취소 시각 (관리자 정보 없음)

## 8. 감사 로그 호출 규약

| 액션 | target_type | target_id | reason | metadata |
|---|---|---|---|---|
| `USER_MENTOR_SWAP` (자동, swap() 내부) | `USER` | menteeUserId | 멘티 신청 사유 | `{oldMatchingId, oldMentorUserId, newMentorUserId}` |
| `MENTOR_CHANGE_APPROVE` (신청 서비스) | `MENTOR_CHANGE_REQUEST` | requestId | 멘티 신청 사유 | `{newMentorUserId, oldMentorUserId}` |
| `MENTOR_CHANGE_REJECT` (신청 서비스) | `MENTOR_CHANGE_REQUEST` | requestId | 반려 사유 | `{menteeReason}` |

(취소는 관리자 행위가 아니므로 감사 로그 미기록)

## 9. 테스트

### 9.1 백엔드 단위 테스트
- `MentorChangeRequestTest`: 상태 전이 (`approve/reject/cancel` 정상 + PENDING 아님 시 예외 + 반려 사유 빈값 시 예외)
- `MentorChangeRequestServiceTest`: 신청 (정상 / 사유 길이 검증 / 활성 매칭 없음 / PENDING 중복) / 취소 (정상 / 비-소유자 / PENDING 아님)
- `AdminMentorChangeRequestServiceTest`: 승인 (정상 → swap 호출 검증 / PENDING 아님 / swap 실패 전파) / 반려 (정상 / PENDING 아님 / 사유 빈값) / 후보 멘토 필터(카테고리·현재 멘토 제외)

### 9.2 백엔드 통합 테스트 (`@SpringBootTest`)
- 멘티 컨트롤러: 권한(타 멘티 신청 접근 시 403), happy path 1건
- 관리자 컨트롤러: 비-관리자 401/403, 승인 happy path 1건 (실제 매칭 SWAPPED 검증), 반려 happy path 1건

### 9.3 프론트
자동화 생략, 수동 시나리오 체크리스트를 PR 본문에 첨부:
- 목록 탭별 필터 정상
- PENDING 상세에서 후보 멘토 검색·선택·승인 → 토스트 + 목록 복귀 + 상태 APPROVED
- PENDING 상세에서 반려 사유 빈값 시 버튼 disabled
- 비-PENDING 상세에서 액션 카드 미노출

## 10. 결제·세션 연쇄 영향

- `MentorSwapService.swap()` 가 결제·세션을 건드리지 않으므로 본 스펙도 동일. 진행 중 결제/세션은 옛 매칭에 그대로 남고, 새 매칭은 빈 상태로 시작.
- 학생 포트폴리오 규모상 의도된 결정 (이미 `admin-users` 스펙의 Out of scope 와 동일).

## 11. 영향 받는 기존 파일

| 파일 | 변경 내용 |
|---|---|
| [`AdminActionType.java`](../../../backend/src/main/java/com/devmatch/entity/AdminActionType.java) | 액션 2개 추가 |
| [`MentorProfileRepository.java`](../../../backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java) | `findApprovedByCategoryAndKeyword` 추가 (필요 시) |
| `SecurityConfig` | 신규 엔드포인트 권한 매핑 |
| `frontend/src/app/admin/layout.tsx` 또는 사이드바 컴포넌트 | "멘토 교체 신청" 메뉴 항목 추가 |

`MentorSwapService.swap()` 시그니처는 변경하지 않는다 (회귀 위험 회피).

## 12. 산출물 요약

**백엔드**
- 엔티티 1, enum 1, 레포 1, 서비스 2, 컨트롤러 2, 신규 예외 1, 마이그레이션 SQL 1, AdminActionType 수정 1, 테스트 ~7

**프론트엔드**
- API 클라이언트 1, 페이지 2, 사이드바 메뉴 1줄 수정

**문서**
- 본 스펙 1
- error 폴더 기록(필요 시 구현 중 발생한 실 이슈만)

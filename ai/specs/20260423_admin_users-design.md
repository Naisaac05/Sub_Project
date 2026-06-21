---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "관리자 콘솔 Phase II — 회원 관리 (Feature 1) 설계 (Design Spec) 상세 요구사항 및 기능 동작 명세서"

---

# 관리자 콘솔 Phase II — 회원 관리 (Feature 1) 설계 (Design Spec)

> 작성일: 2026-04-23
> 대상 Phase: II / Feature 1
> Common 의존: [2026-04-22-admin-console-common-design.md](./2026-04-22-admin-console-common-design.md)
> 관련 mockup (pre-brainstorming 초안, 본 스펙으로 갱신됨): [docs/mockups/admin-users.md](../../mockups/admin-users.md)

---

## 1. 배경

Phase II Common 으로 감사 로그 인프라(`AdminAuditLog` + `AdminAuditLogService`) 와 관리자 사이드바 4개 메뉴 확장이 완료됐다. 본 스펙은 그 중 첫 번째 feature 인 **회원 관리** 의 상세 설계다.

### 핵심 변화 (pre-brainstorming 초안 대비)

1. **역할 변경 기능 제거** — 사용자 결정에 따라 ADMIN 은 MENTEE/MENTOR 와 독립적으로 관리. `/admin/users` 에서 역할 변경 액션은 제공하지 않음.
2. **관리자 계정 생성 기능 신설** — SUPER_ADMIN 만 가능. 별도 페이지 `/admin/admins`.
3. **상태 모델링 도입** — `UserStatus { ACTIVE, DEACTIVATED, DELETED }` 단일 enum.
4. **비밀번호 리셋 기능** — 이메일 인프라 없이 임시 비번 1회 표시 방식.
5. **멘토 교체 기능 추가** — 멘티의 매칭을 다른 멘토로 단순 교체 (결제·세션은 보존).

## 2. 스코프

### In scope

- `/admin/users` — 회원 목록 (검색·역할/상태 필터·페이지네이션)
- `/admin/users/[id]` — 회원 상세 + 액션 (비활성화/재활성화/삭제/비밀번호 리셋/멘토 교체)
- `/admin/admins` — SUPER_ADMIN 전용 ADMIN 계정 생성·목록 화면
- `/account/change-password` — `mustChangePassword=true` 사용자의 강제 비밀번호 변경
- `Role` enum 확장: `SUPER_ADMIN` 추가
- `User` 엔티티 확장: `status`, `jobTitle`, `mustChangePassword` 컬럼
- 모든 관리자 액션의 `AdminAuditLog` 기록 (Common 호출 규약 §4.4 준수)
- Spring Security 권한 매핑 변경 (SUPER_ADMIN 이 ROLE_ADMIN 도 보유)
- 상태 필터 전파 — `AuthService` 로그인 차단, 멘토 조회 쿼리 등

### Out of scope (명시적 제외)

| 항목 | 이유 |
|------|------|
| 이메일/푸시 알림 | Common 결정 — Phase III 이관 |
| 비밀번호 리셋 시 이메일 발송 | 위와 동일. 임시 비번 UI 1회 표시로 대체 |
| ADMIN 의 세부 권한 분할 (예: USER_MANAGER, PAYMENT_REVIEWER 등) | YAGNI — 학생 프로젝트엔 SUPER_ADMIN/ADMIN 2단계로 충분 |
| ADMIN/SUPER_ADMIN 의 비활성화·삭제 | DB 직접 조작 (`UPDATE users SET status='DELETED' WHERE …`) — UI 경로 없음 |
| 멘토 교체 시 결제 환불·차액 정산 자동화 | 결제 도메인 개입 시 스펙 비대화. 결제 관리(Feature 2) 에서 수동 처리 |
| 삭제된 회원의 PII 익명화 | 학생 프로젝트엔 GDPR 미적용. Phase III 운영 단계에서 검토 |
| 관리자 추가 → 기존 MENTEE/MENTOR 를 ADMIN 으로 promote | ADMIN 은 항상 신규 계정으로 생성 (사용자 결정) |

## 3. 결정 사항 요약

| 항목 | 결정 | 대안 / 탈락 이유 |
|------|------|-----------------|
| 회원 lifecycle 모델 | 단일 `UserStatus` enum | boolean 2개는 invariant 관리 부담 |
| 삭제된 회원의 연관 데이터 | 마스킹만 (PII 그대로 보존) | 익명화/cascade 정리는 학생 프로젝트엔 과함 |
| 비밀번호 리셋 방식 | 임시 비번 + UI 1회 표시 + `mustChangePassword=true` 플래그 | 이메일/리셋 토큰은 인프라 부담 |
| 관리자 계층 모델 | 2단계 SUPER_ADMIN/ADMIN | 권한 플래그 방식은 코드 분기 ↑ |
| 멘토 교체 | 단순 교체 (기존 매칭 CANCELLED + 신규 매칭 생성) | 결제 인계/환불 자동화는 분쟁 소지 |
| 자기 자신 보호 | 백엔드에서 `adminId == targetId` 검증 차단 | FE-only 차단은 우회 가능 |
| 마지막 SUPER_ADMIN 보호 | 시스템 SUPER_ADMIN 0명 방지 검증 | (락아웃 예방) |

## 4. 백엔드 설계

### 4.1 엔티티/Enum 변경

#### `Role` enum 확장
```java
package com.devmatch.entity;

public enum Role {
    MENTEE,
    MENTOR,
    ADMIN,
    SUPER_ADMIN
}
```

#### `UserStatus` enum 신규
```java
package com.devmatch.entity;

public enum UserStatus {
    ACTIVE,        // 정상 활성 사용자
    DEACTIVATED,   // 관리자가 일시 비활성화 (로그인·매칭 불가, 데이터 보존)
    DELETED        // 관리자가 영구 삭제 (UI 비가역, 표시 시 마스킹)
}
```

#### `User` 엔티티 추가 필드 + 메서드

기존 필드(id, email, password, name, role, provider, providerId, createdAt, updatedAt) 유지. 다음 추가:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
@Builder.Default
private UserStatus status = UserStatus.ACTIVE;

@Column(name = "job_title", length = 100)
private String jobTitle;   // ADMIN/SUPER_ADMIN 만 사용 (생성 시 입력). MENTEE/MENTOR 는 null.

@Column(name = "must_change_password", nullable = false)
@Builder.Default
private Boolean mustChangePassword = false;

// 신규 update 메서드
public void deactivate()                    { this.status = UserStatus.DEACTIVATED; }
public void reactivate()                    { this.status = UserStatus.ACTIVE; }
public void markDeleted()                   { this.status = UserStatus.DELETED; }
public void forcePasswordChange(String enc) { this.password = enc; this.mustChangePassword = true; }
public void clearMustChangePassword()       { this.mustChangePassword = false; }
```

기존 `updateRole(Role role)` 메서드는 **유지** (Common 스펙에서 사용 중) 하되 본 스펙의 새 엔드포인트에서는 호출하지 않음.

### 4.2 Spring Security 권한 매핑

`CustomUserDetails.getAuthorities()` 가 사용자 role 에 따라 다음을 반환:

| User.role | getAuthorities() 결과 |
|-----------|----------------------|
| MENTEE | `[ROLE_MENTEE]` |
| MENTOR | `[ROLE_MENTOR]` |
| ADMIN | `[ROLE_ADMIN]` |
| SUPER_ADMIN | `[ROLE_SUPER_ADMIN, ROLE_ADMIN]` |

이 매핑으로 SUPER_ADMIN 은 ROLE_ADMIN 가드도 통과하며, `/api/admin/admins/**` 만 추가 권한 가드.

`SecurityConfig.java` 룰 추가 (기존 `/api/admin/**` 규칙 위에 더 구체적인 룰):

```java
.requestMatchers("/api/admin/admins/**").hasRole("SUPER_ADMIN")
.requestMatchers("/api/admin/**").hasRole("ADMIN")  // 기존 유지
```

### 4.3 신규 컨트롤러 + 엔드포인트

#### `AdminUserController` (`/api/admin/users`)

| HTTP | 경로 | body | 권한 | 설명 |
|------|------|------|------|------|
| GET | `/api/admin/users?role=&status=&q=&page=&size=` | — | ADMIN | 페이지네이션 목록. 모든 role 포함 |
| GET | `/api/admin/users/{id}` | — | ADMIN | 상세 + 연관 활동 요약(결제 건수·게시글 수·멘토 프로필 링크) |
| POST | `/api/admin/users/{id}/deactivate` | `{reason: string}` (10~500자) | ADMIN | DEACTIVATED 전이. 대상이 ADMIN/SUPER_ADMIN 이면 400 |
| POST | `/api/admin/users/{id}/reactivate` | — | ADMIN | DEACTIVATED → ACTIVE |
| POST | `/api/admin/users/{id}/delete` | `{reason: string}` | ADMIN | DELETED 전이 (비가역). 대상 ADMIN/SUPER_ADMIN 차단 |
| POST | `/api/admin/users/{id}/reset-password` | — | ADMIN | 응답에 평문 임시 비번 1회 포함. 대상이 SUPER_ADMIN 이면 차단(MENTEE/MENTOR/ADMIN 만 허용) |
| POST | `/api/admin/users/{menteeId}/swap-mentor` | `{newMentorId: long, reason: string}` | ADMIN | 멘토 교체 (§4.5) |

#### `AdminAccountController` (`/api/admin/admins`)

| HTTP | 경로 | body | 권한 | 설명 |
|------|------|------|------|------|
| GET | `/api/admin/admins` | — | SUPER_ADMIN | ADMIN/SUPER_ADMIN 계정 목록 |
| POST | `/api/admin/admins` | `{email, name, jobTitle}` | SUPER_ADMIN | 신규 ADMIN 생성. 응답에 평문 임시 비번 1회 포함 |

### 4.4 가드 정책 (서비스 레이어)

다음은 모두 백엔드 서비스에서 검증, 위반 시 `ForbiddenOperationException` (HTTP 403) 또는 `IllegalArgumentException` (400):

| 가드 | 검증 위치 |
|------|----------|
| 자기 자신 비활성화/삭제 차단 (`adminId == targetId`) | `AdminUserService.deactivate/delete` |
| ADMIN/SUPER_ADMIN 대상 비활성화·삭제 차단 | 동일 |
| SUPER_ADMIN 대상 비밀번호 리셋 차단 (SUPER_ADMIN 끼리도 막음) | `AdminUserService.resetPassword` |
| DELETED 사용자에 대한 모든 액션 차단 (이미 비가역 상태) | 각 액션 메서드 진입 시 |
| 멘토 교체: 새 멘토가 APPROVED 상태가 아닌 경우 차단 | `MentorSwapService.swap` |
| 멘토 교체: 멘티의 활성 매칭이 없는 경우 차단 | 동일 |
| 마지막 SUPER_ADMIN 보호: SUPER_ADMIN 1명 남았을 때 그 계정의 강등/비활성화 차단 | `AdminAccountService` (해당 액션 자체는 이 스펙 범위 외이지만 검증은 hook 로 준비) |

### 4.5 상태 전파 (다른 도메인 영향)

| 위치 | 변경 |
|------|------|
| `AuthService.login` | `user.status != ACTIVE` 면 `AccountInactiveException` (신규) 발생 → 401 응답 |
| `AuthService.signup` | 변경 없음 (신규 가입은 항상 ACTIVE) |
| 기존 `/api/auth/me` 응답 | `mustChangePassword: boolean` 필드 추가 |
| `MentorService.findActiveByCourse`, `RecommendationService` 등 멘토 조회 | `mentorProfile.user.status = ACTIVE` 필터 추가 |
| `MentorService.findAllForAdmin` | 변경 없음 — 관리자 화면은 모든 status 표시 |
| 다른 사용자 정보가 노출되는 응답 DTO (예: `MentorProfileResponse`, `PostResponse`, `MatchingResponse` 등) | `displayName(user)` 유틸 적용 — `status==DELETED` 면 `"탈퇴한 회원"` |

### 4.6 멘토 교체 동작 상세

`POST /api/admin/users/{menteeId}/swap-mentor` body `{newMentorId, reason}`:

```
@Transactional
swap(adminId, menteeId, newMentorId, reason):
  1. 멘티 사용자 조회 — status==ACTIVE 인지 확인
  2. 새 멘토 프로필 조회 — status==APPROVED 인지 확인
  3. 멘티의 활성 매칭 조회 — status in (PENDING, CONFIRMED) 1건 있어야 함
  4. 기존 매칭.cancel(reason="관리자 교체: " + reason)
       (Matching 엔티티에 cancel 메서드 신규 추가 필요시)
  5. 새 Matching 엔티티 빌드 — mentee=mentee.user, mentor=새멘토.user, status=CONFIRMED
  6. matchingRepository.save(새매칭)
  7. auditLogService.record(adminId, USER_MENTOR_SWAP, "USER", menteeId, reason,
       Map.of("oldMatchingId", old.id, "oldMentorUserId", old.mentor.id,
              "newMentorUserId", newMentor.user.id))
  8. 응답: 새 MatchingResponse
```

**보존되는 데이터** (변경 없음):
- 기존 `MentoringSession` (기존 매칭에 묶인 채 히스토리로 남음)
- 기존 `LmsAssignment`, `Curriculum`, `LearningNote`, `AssignmentSubmission`
- 기존 `Payment` (matching_id 그대로)

신규 매칭은 빈 상태로 시작 — 새 멘토가 별도 세션 생성·과제 부여.

### 4.7 비밀번호 리셋 동작

`POST /api/admin/users/{id}/reset-password`:

```
@Transactional
resetPassword(adminId, targetUserId):
  1. target 조회 — DELETED 면 차단
  2. SUPER_ADMIN 대상은 차단
  3. 평문 임시 비번 생성: 12자 무작위 (대소문자+숫자+특수문자 1자 이상씩)
       — SecureRandom 사용
  4. encoded = passwordEncoder.encode(평문)
  5. target.forcePasswordChange(encoded)
  6. auditLogService.record(adminId, USER_PASSWORD_RESET, "USER", targetUserId,
       null, null)  // 사유 별도 입력 받지 않음
  7. 응답: { temporaryPassword: 평문, mustChangePassword: true, message: "..." }
       — 평문은 응답 1회만 포함, 로그·감사로그·DB 어디에도 평문 저장 X
```

`/api/auth/me` 응답에 `mustChangePassword` 필드 추가 → 프런트의 `useAuth()` 훅이 이 값을 보고 강제 리다이렉트.

`/account/change-password` (신규 엔드포인트):
- `POST /api/auth/change-password` body `{currentPassword, newPassword}`
- 검증: 현재 비번 일치, 새 비번 정책 (최소 8자 등 기존 가입 규칙 따름)
- 성공 시 `forcePasswordChange(newEncoded)` 후 `clearMustChangePassword()`

### 4.8 ADMIN 계정 생성 동작

`POST /api/admin/admins` body `{email, name, jobTitle}` (SUPER_ADMIN only):

```
@Transactional
createAdmin(superAdminId, email, name, jobTitle):
  1. 이메일 중복 체크
  2. 평문 임시 비번 생성 (위와 동일 로직)
  3. encoded = passwordEncoder.encode(평문)
  4. user = User.builder()
       .email(email).name(name).password(encoded)
       .role(Role.ADMIN).status(UserStatus.ACTIVE)
       .jobTitle(jobTitle).mustChangePassword(true).build()
  5. userRepository.save(user)
  6. auditLogService.record(superAdminId, ADMIN_CREATE, "USER", user.id,
       null, Map.of("email", email, "jobTitle", jobTitle))
  7. 응답: { user: AdminProfileResponse, temporaryPassword: 평문 }
```

### 4.9 `AdminActionType` enum 확장 (Common 스펙 갱신 필요)

신규 enum 값 (Common 스펙 §4.2 의 enum 에 추가):

```java
public enum AdminActionType {
    USER_ROLE_CHANGE,    // (Common 에 정의됨, 본 스펙에선 미사용)
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,
    MENTOR_APPROVE,
    MENTOR_REJECT,
    USER_DEACTIVATE,     // 신규
    USER_REACTIVATE,     // 신규
    USER_DELETE,         // 신규
    USER_PASSWORD_RESET, // 신규
    USER_MENTOR_SWAP,    // 신규
    ADMIN_CREATE         // 신규
}
```

본 스펙 머지 시 Common 스펙 문서(`2026-04-22-admin-console-common-design.md` §4.2) 도 함께 업데이트.

## 5. 프런트 설계

### 5.1 라우팅

| 경로 | 화면 | 권한 |
|------|------|------|
| `/admin/users` | 회원 목록 | ADMIN+ |
| `/admin/users/[id]` | 회원 상세 + 액션 | ADMIN+ |
| `/admin/admins` | ADMIN 계정 관리 | SUPER_ADMIN only (그 외 403) |
| `/account/change-password` | 강제 비밀번호 변경 | 로그인 + `mustChangePassword=true` |

### 5.2 사이드바 확장

`AdminSidebar.tsx` 의 `NAV_ITEMS` 타입에 `requireSuperAdmin?: boolean` 필드 추가, SUPER_ADMIN 전용 항목 1개 추가:

```tsx
{
  href: '/admin/admins',
  label: '관리자 계정',
  icon: ShieldCheck,
  match: (p) => p === '/admin/admins' || p.startsWith('/admin/admins/'),
  requireSuperAdmin: true,
}
```

렌더링 로직:
```tsx
const { user } = useAuth();
const isSuperAdmin = user?.role === 'SUPER_ADMIN';
NAV_ITEMS
  .filter(item => !item.requireSuperAdmin || isSuperAdmin)
  .map(...)
```

### 5.3 화면 구성 (Pencil 목업으로 별도 정의 — 플랜의 명시 Task)

본 스펙에서는 화면 구성 요건만 정의하고, 구체 레이아웃은 Pencil(.pen) 목업으로 별도 작성 후 사용자 승인을 받는다 ([CLAUDE.md](../../CLAUDE.md) memory: "프론트 구현 전 Pencil 목업 + shadcn 컴포넌트 매핑을 사용자에게 확인받음").

**`/admin/users` 요건**
- 페이지 헤더: 제목 "회원 관리" + 설명
- 필터: 역할 탭 (ALL/MENTEE/MENTOR/ADMIN), 상태 탭 (ALL/ACTIVE/DEACTIVATED/DELETED), 검색 input (이름·이메일, 300ms debounce)
- 테이블: 이름, 이메일, 역할 배지, 상태 배지, 가입일, 상세 버튼
- DELETED 행은 회색 톤 + "탈퇴한 회원" 표시
- 페이지네이션 (서버 사이드, size=20)

**`/admin/users/[id]` 요건**
- 헤더: 이름·이메일·역할/상태 배지
- 카드 A: 기본 정보 (이름, 이메일, 가입 경로, 가입일, 마지막 수정일)
- 카드 B: 연관 활동 요약 (결제 N건 / 게시글 N건 / 멘토 프로필 → 링크)
- 카드 C: 액션 영역 (대상 role 에 따라 활성화)
  - MENTEE/MENTOR 대상: 비활성화 / 재활성화 / 삭제 / 비밀번호 리셋
  - MENTEE 추가: "멘토 교체" 버튼 (활성 매칭 있을 때만)
  - ADMIN/SUPER_ADMIN 대상: 비밀번호 리셋만 (SUPER_ADMIN 은 그것도 비활성)
  - DELETED 대상: 모든 버튼 비활성 + "이미 삭제된 계정" 안내
- 액션 모달: 사유 입력(10~500자) + destructive 색상 + 확인

**멘토 교체 모달 요건**
- 현재 멘토 표시
- 새 멘토 선택 (드롭다운 or 검색): APPROVED 상태 멘토만 표시, 현재 멘토 제외
- 사유 입력 (10~500자)
- 경고: "기존 매칭은 취소되며 신규 매칭이 생성됩니다. 결제 환불은 별도 처리 필요."

**비밀번호 리셋 결과 모달**
- 임시 비밀번호 표시 (monospace) + 복사 버튼
- 안내: "이 비밀번호는 닫으면 다시 볼 수 없습니다. 사용자에게 안전하게 전달하세요."
- 닫기 버튼만 (취소 액션 없음 — 이미 서버에서 비번이 바뀐 상태)

**`/admin/admins` 요건**
- 페이지 헤더 + "관리자 추가" 버튼 (우측 상단)
- 테이블: 이름, 이메일, 회사직책, 역할 배지(ADMIN/SUPER_ADMIN), 상태 배지, 가입일
- "관리자 추가" 모달: 이메일·이름·회사직책 입력 → 제출 → 임시 비밀번호 결과 모달

**`/account/change-password` 요건**
- 진입 시 `useAuth()` 의 `mustChangePassword` 가 false 면 마이페이지로 리다이렉트
- 폼: 현재 비밀번호 / 새 비밀번호 / 새 비밀번호 확인
- 제출 → 성공 시 `mustChangePassword=false` 갱신 + 마이페이지로 이동

### 5.4 `useAuth()` 훅 변경

`useAuth()` 응답에 `mustChangePassword` 추가. 전역 레이아웃에서:
```tsx
useEffect(() => {
  if (user?.mustChangePassword && pathname !== '/account/change-password') {
    router.push('/account/change-password');
  }
}, [user, pathname]);
```

이 강제 리다이렉트는 모든 인증 상태 페이지에 적용.

### 5.5 공용 유틸

- `displayName(user: { name: string; status: UserStatus })`: `status==='DELETED'` 면 `"탈퇴한 회원"`
- 이 유틸은 백엔드(`displayName(User)` Java) 와 프런트(`displayName(user)` TS) 양쪽 모두 작성 — 응답 직렬화 시 백엔드가 마스킹할지, FE 가 받아서 마스킹할지는 일관성을 위해 **백엔드에서 `name` 필드를 이미 마스킹된 값으로 반환**하는 정책. FE 의 `displayName` 은 fallback 안전망.

### 5.6 페이지네이션·검색·DateRange — 공용 컴포넌트 추출

Phase II Common 결정 §3 에 따라, 본 스펙(첫 feature) 에서는 인라인 구현. 다음 feature 스펙(결제 관리) 작성 시점에 `components/admin/common/` 으로 추출.

이 스펙에서 인라인 작성할 것:
- `Pagination` (서버 사이드 페이지 번호 navigation)
- `DebouncedSearchInput`
- `Skeleton` 행 (로딩 상태)

## 6. 마이그레이션

### dev (`ddl-auto: update`)
Hibernate 가 자동 적용. 단:
- 기존 `users` 행은 새 컬럼이 NULL/default 값으로 채워짐 → 모든 기존 사용자가 `status='ACTIVE'`, `mustChangePassword=false` 로 정상 동작
- `seed-lms.sql` 에 SUPER_ADMIN 시드 1건 추가 (기존 admin 시드를 SUPER_ADMIN 으로 변경하거나, 신규 추가)

### prod (`ddl-auto: validate`) — 배포 체크리스트 추가

```sql
ALTER TABLE users
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN job_title VARCHAR(100),
  ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 ADMIN 한 명을 SUPER_ADMIN 으로 승급 (이메일은 환경에 맞게)
UPDATE users SET role='SUPER_ADMIN' WHERE email='<운영자 이메일>';
```

`ROADMAP.md` 배포 체크리스트에 항목 추가.

## 7. 테스트 전략

### 단위 테스트 (Mockito)

- `AdminUserServiceTest`
  - 비활성화/재활성화/삭제 정상 케이스 + 감사 로그 검증
  - 자기 자신 차단, ADMIN/SUPER_ADMIN 차단, DELETED 차단
  - 비밀번호 리셋: 평문 응답 + `mustChangePassword=true` 설정 + 감사 로그
- `AdminAccountServiceTest`
  - 신규 ADMIN 생성 정상 + 평문 비번 응답
  - 이메일 중복 거부
- `MentorSwapServiceTest`
  - 정상 교체: old=CANCELLED, new=CONFIRMED, 감사 로그
  - 새 멘토 미승인 차단, 활성 매칭 없음 차단

### 통합 테스트 (`@SpringBootTest` 또는 `@DataJpaTest` 부분)

- `AuthService.login` — DEACTIVATED/DELETED 사용자 차단
- `/api/admin/admins` 엔드포인트 — ADMIN(non-SUPER) 으로 호출 시 403

### 프런트 수동 스모크 (Pencil 승인 후 구현 단계)

1. ADMIN 으로 `/admin/users` 진입 → 목록 표시, 검색·필터 동작 확인
2. MENTEE 한 명 비활성화 → 그 계정으로 로그인 시도 → 차단 확인
3. 멘토 교체 → DB 에서 `matchings` 테이블 확인 (old=CANCELLED, new=CONFIRMED)
4. 비밀번호 리셋 → 임시 비번으로 로그인 → 강제 변경 페이지 표시 확인
5. SUPER_ADMIN 으로 `/admin/admins` → ADMIN 추가 → 새 계정으로 로그인 → 강제 변경 페이지

## 8. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| `User.status` 필터를 일부 쿼리에 빠뜨려 비활성 사용자가 멘토 추천에 노출 | 운영 데이터 신뢰 저하 | 검색 가능한 모든 User 조회 메서드를 PR 체크리스트로 점검. `MentorRepository`/`RecommendationService` 회귀 테스트 |
| 첫 SUPER_ADMIN 시드 누락 → 관리자 계정 추가 불가 | 운영 진입 불가 | 배포 체크리스트 명시 + dev 시드 강제 |
| 멘토 교체 후 결제 잔여 정산 분쟁 | 사용자 클레임 | 스펙·UI 모두 "결제 처리는 결제 관리에서 별도" 명시. 교체 모달에 경고 문구 |
| 평문 임시 비번이 관리자 화면·로그에 노출 | PII 유출 | 모달이 닫히면 응답 캐시도 즉시 폐기. 로깅 시 응답 body 직렬화 제외. Sentry 등 모니터링에도 마스킹 |
| `mustChangePassword` 강제 리다이렉트 무한 루프 | 사용자 차단 | `pathname === '/account/change-password'` 가드. 추가로 로그아웃 경로는 항상 허용 |
| 멘토 교체로 생성된 신규 매칭이 빈 상태 → 멘티 혼란 | UX | UI 가이드 문구: "신규 멘토와의 세션은 새로 등록됩니다" |

## 9. 구현 순서 (이 스펙)

1. **백엔드 도메인 변경**
   - `Role.SUPER_ADMIN` 추가
   - `UserStatus` enum 신규
   - `User` 컬럼·메서드 추가 + DB 마이그레이션 확인
   - `AdminActionType` enum 6개 값 추가 + Common 스펙 문서 갱신
   - `seed-lms.sql` 에 SUPER_ADMIN 시드
2. **`AuthService` 가드** (DEACTIVATED/DELETED 로그인 차단)
3. **`displayName` 유틸** + 응답 DTO 적용
4. **`SecurityConfig` 룰 추가** + `CustomUserDetails` authorities 매핑
5. **`AdminUserService`** + 컨트롤러 + 단위 테스트
6. **`AdminAccountService`** + 컨트롤러 + 단위 테스트
7. **`MentorSwapService`** + 단위 테스트 + 정책 검증
8. **`/api/auth/change-password`** + `mustChangePassword` 응답 필드
9. **🚧 Pencil 목업 작성 + 사용자 확인 (프런트 구현 전 필수 게이트)**
10. `/admin/users` 목록·상세 페이지
11. `/admin/users/[id]` 액션 모달들 (비활성화/삭제/비번리셋/멘토교체)
12. `/admin/admins` 목록·생성 모달
13. `/account/change-password` 페이지 + 강제 리다이렉트
14. 사이드바 SUPER_ADMIN 항목 추가
15. 회귀 테스트 + PR

## 10. 후속 스펙

이 스펙 머지 후:
- `2026-MM-DD-admin-payments-design.md` (결제 관리 + 환불) — `/admin/users/[id]` 의 "결제 N건" 링크가 이 스펙으로 이어짐
- `2026-MM-DD-admin-posts-design.md` (게시물 관리 + 강제 삭제)

공용 컴포넌트(Pagination, DebouncedSearchInput) 는 다음 스펙(결제) 시점에 본 스펙 인라인 구현에서 추출.

---

## 변경 이력

| 일자 | 내용 |
|------|------|
| 2026-04-23 | 최초 작성 (브레인스토밍 Q1~Q7 결정 반영) |

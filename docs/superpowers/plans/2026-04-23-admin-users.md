# 회원 관리 (Phase II Feature 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 회원(MENTEE/MENTOR) 의 상태(활성/비활성/삭제) 와 비밀번호를 관리하고, SUPER_ADMIN 이 ADMIN 계정을 추가하며, 멘티의 멘토를 교체할 수 있는 기능을 구현한다.

**Architecture:** `User` 엔티티에 `UserStatus`/`jobTitle`/`mustChangePassword` 컬럼을 추가하고, `Role` enum 에 `SUPER_ADMIN` 을 추가한다. 모든 관리자 액션은 Phase II Common 의 `AdminAuditLogService.record(...)` 를 통해 감사 로그를 남긴다. 백엔드를 먼저 완성한 뒤 Pencil 목업 사용자 승인을 게이트로 두고 프런트를 구현한다.

**Tech Stack:** Spring Boot 3 (JPA + Hibernate ddl-auto), JUnit 5 + Mockito, Next.js 14 (App Router), shadcn/ui, lucide-react, Pencil MCP (.pen 파일).

**Spec:** `docs/superpowers/specs/2026-04-23-admin-users-design.md`

---

## File Structure

### 신규 생성 (백엔드 — 19 파일)

| 경로 | 책임 |
|------|------|
| `backend/src/main/java/com/devmatch/entity/UserStatus.java` | 회원 상태 enum |
| `backend/src/main/java/com/devmatch/exception/AccountInactiveException.java` | 비활성/삭제된 계정 로그인 시도 예외 |
| `backend/src/main/java/com/devmatch/exception/ForbiddenOperationException.java` | 관리자 가드 위반 (자기 자신·SUPER_ADMIN 대상 등) |
| `backend/src/main/java/com/devmatch/exception/InvalidPasswordChangeException.java` | 강제 비번 변경 시 현재 비번 불일치 등 |
| `backend/src/main/java/com/devmatch/util/UserDisplay.java` | `displayName(User)` 마스킹 유틸 |
| `backend/src/main/java/com/devmatch/util/PasswordGenerator.java` | 임시 비밀번호 생성 유틸 |
| `backend/src/main/java/com/devmatch/dto/admin/AdminUserListResponse.java` | 목록 응답 (페이지) |
| `backend/src/main/java/com/devmatch/dto/admin/AdminUserDetailResponse.java` | 상세 응답 (연관 활동 요약 포함) |
| `backend/src/main/java/com/devmatch/dto/admin/UserActionRequest.java` | `{reason: string}` 공통 body |
| `backend/src/main/java/com/devmatch/dto/admin/MentorSwapRequest.java` | `{newMentorId, reason}` |
| `backend/src/main/java/com/devmatch/dto/admin/PasswordResetResponse.java` | `{temporaryPassword, mustChangePassword}` |
| `backend/src/main/java/com/devmatch/dto/admin/AdminCreateRequest.java` | `{email, name, jobTitle}` |
| `backend/src/main/java/com/devmatch/dto/admin/AdminCreateResponse.java` | `{user, temporaryPassword}` |
| `backend/src/main/java/com/devmatch/dto/auth/PasswordChangeRequest.java` | 강제 비번 변경 요청 |
| `backend/src/main/java/com/devmatch/service/AdminUserService.java` | list/get/deactivate/reactivate/delete/resetPassword |
| `backend/src/main/java/com/devmatch/service/AdminAccountService.java` | list admins/createAdmin |
| `backend/src/main/java/com/devmatch/service/MentorSwapService.java` | swap matching |
| `backend/src/main/java/com/devmatch/controller/AdminUserController.java` | `/api/admin/users/**` |
| `backend/src/main/java/com/devmatch/controller/AdminAccountController.java` | `/api/admin/admins/**` |

### 수정 (백엔드 — 8 파일)

| 경로 | 변경 |
|------|------|
| `backend/src/main/java/com/devmatch/entity/Role.java` | `SUPER_ADMIN` 추가 |
| `backend/src/main/java/com/devmatch/entity/User.java` | `status`/`jobTitle`/`mustChangePassword` 컬럼 + 메서드 |
| `backend/src/main/java/com/devmatch/entity/AdminActionType.java` | 6개 신규 enum 값 (Common 스펙 §4.2 도 함께 갱신) |
| `backend/src/main/java/com/devmatch/security/CustomUserDetails.java` | SUPER_ADMIN → 두 권한 반환 |
| `backend/src/main/java/com/devmatch/config/SecurityConfig.java` | `/api/admin/admins/**` SUPER_ADMIN 룰 추가 |
| `backend/src/main/java/com/devmatch/service/AuthService.java` | `login()` 에서 status 가드 |
| `backend/src/main/java/com/devmatch/dto/user/UserResponse.java` | `status`, `mustChangePassword` 필드 추가 |
| `backend/src/main/java/com/devmatch/controller/AuthController.java` | `POST /api/auth/change-password` 추가 |
| `backend/src/main/resources/seed-lms.sql` | SUPER_ADMIN 시드 1건 |
| `docs/superpowers/specs/2026-04-22-admin-console-common-design.md` | enum 6개 추가 반영 |
| `ROADMAP.md` | 배포 체크리스트 ALTER TABLE 추가 |

### 신규 (테스트 — 6 파일)

| 경로 | 책임 |
|------|------|
| `backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java` | 모든 액션 단위 테스트 |
| `backend/src/test/java/com/devmatch/service/AdminAccountServiceTest.java` | 관리자 생성 |
| `backend/src/test/java/com/devmatch/service/MentorSwapServiceTest.java` | 멘토 교체 |
| `backend/src/test/java/com/devmatch/service/AuthServiceLoginGuardTest.java` | DEACTIVATED/DELETED 로그인 차단 (단위) |
| `backend/src/test/java/com/devmatch/util/PasswordGeneratorTest.java` | 임시 비번 생성 정책 |
| `backend/src/test/java/com/devmatch/util/UserDisplayTest.java` | displayName 마스킹 |

### 신규 / 수정 (프런트 — 11 파일)

| 경로 | 변경 |
|------|------|
| `frontend/src/lib/types.ts` | `UserResponse` 타입에 `status`, `mustChangePassword` 추가 |
| `frontend/src/contexts/AuthContext.tsx` | `mustChangePassword` 강제 리다이렉트 |
| `frontend/src/components/admin/AdminSidebar.tsx` | SUPER_ADMIN 전용 메뉴 항목 |
| `frontend/src/lib/admin/users.ts` (신규) | API 클라이언트 함수들 |
| `frontend/src/lib/utils/displayName.ts` (신규) | FE displayName fallback |
| `frontend/src/components/admin/Pagination.tsx` (신규, 인라인 추출 대상) | 서버 페이지네이션 UI |
| `frontend/src/components/admin/DebouncedSearchInput.tsx` (신규) | 검색 입력 |
| `frontend/src/app/admin/users/page.tsx` (신규) | 목록 |
| `frontend/src/app/admin/users/[id]/page.tsx` (신규) | 상세 + 액션 |
| `frontend/src/app/admin/admins/page.tsx` (신규) | 관리자 계정 관리 |
| `frontend/src/app/account/change-password/page.tsx` (신규) | 강제 비번 변경 |

### 신규 (디자인)

| 경로 | 책임 |
|------|------|
| `docs/mockups/admin-users.pen` | Pencil 목업 — 5개 화면 + 모달 4개 |

---

## Task 1: 도메인 모델 확장 (`Role`, `UserStatus`, `User`)

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/Role.java`
- Create: `backend/src/main/java/com/devmatch/entity/UserStatus.java`
- Modify: `backend/src/main/java/com/devmatch/entity/User.java`

- [ ] **Step 1: `Role` enum 에 `SUPER_ADMIN` 추가**

`backend/src/main/java/com/devmatch/entity/Role.java`:

```java
package com.devmatch.entity;

public enum Role {
    MENTEE,
    MENTOR,
    ADMIN,
    SUPER_ADMIN
}
```

- [ ] **Step 2: `UserStatus` enum 신규**

`backend/src/main/java/com/devmatch/entity/UserStatus.java`:

```java
package com.devmatch.entity;

/**
 * 회원 lifecycle 상태.
 *
 * - ACTIVE: 정상 (기본값)
 * - DEACTIVATED: 관리자 비활성화 (로그인·매칭 불가, 데이터 보존, 재활성화 가능)
 * - DELETED: 관리자 영구 삭제 (UI 비가역. 표시 시 "탈퇴한 회원" 으로 마스킹)
 */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED,
    DELETED
}
```

- [ ] **Step 3: `User` 엔티티에 컬럼 + 메서드 추가**

`backend/src/main/java/com/devmatch/entity/User.java` 의 마지막 메서드(`updateRole`) 다음에 추가하고, 필드 선언부에는 다음 3개를 추가한다.

필드 선언부 (기존 `private String providerId;` 와 `@CreatedDate` 사이) 에 추가:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private Boolean mustChangePassword = false;
```

`updateRole(Role role)` 메서드 다음에 추가:

```java
    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }

    public void markDeleted() {
        this.status = UserStatus.DELETED;
    }

    public void forcePasswordChange(String encodedPassword) {
        this.password = encodedPassword;
        this.mustChangePassword = true;
    }

    public void clearMustChangePassword() {
        this.mustChangePassword = false;
    }

    public void updateJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
```

- [ ] **Step 4: 컴파일 확인**

Run:
```
cd backend && ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/entity/Role.java backend/src/main/java/com/devmatch/entity/UserStatus.java backend/src/main/java/com/devmatch/entity/User.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): Role.SUPER_ADMIN + UserStatus + User 컬럼 추가"
```

> 주: 이후 Task 들의 git 명령은 모두 `git -C "<worktree-abspath>"` 형식으로 실행 — `<worktree-abspath>` 는 `C:\Users\aucu2\Sub_Project\.claude\worktrees\festive-williamson-e52b71` (Phase II Common 인시던트 §재발 방지 참고).

---

## Task 2: `AdminActionType` enum 6개 값 추가 + Common 스펙 갱신

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/AdminActionType.java`
- Modify: `docs/superpowers/specs/2026-04-22-admin-console-common-design.md`

- [ ] **Step 1: enum 확장**

`backend/src/main/java/com/devmatch/entity/AdminActionType.java`:

```java
package com.devmatch.entity;

/**
 * 관리자 행위 유형. AdminAuditLog.actionType 에서 사용한다.
 *
 * 새 값 추가 시 반드시 스펙 문서
 * (docs/superpowers/specs/2026-04-22-admin-console-common-design.md) 업데이트.
 */
public enum AdminActionType {
    // Phase II Common
    USER_ROLE_CHANGE,
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,
    MENTOR_APPROVE,
    MENTOR_REJECT,

    // Phase II Feature 1 (회원 관리)
    USER_DEACTIVATE,
    USER_REACTIVATE,
    USER_DELETE,
    USER_PASSWORD_RESET,
    USER_MENTOR_SWAP,
    ADMIN_CREATE
}
```

- [ ] **Step 2: Common 스펙 enum 섹션 갱신**

`docs/superpowers/specs/2026-04-22-admin-console-common-design.md` 의 §4.2 enum 코드 블록을 다음으로 교체:

```java
public enum AdminActionType {
    // Phase II Common
    USER_ROLE_CHANGE,    // (Common 에서 선언, Feature 1 에서 미사용)
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,
    MENTOR_APPROVE,
    MENTOR_REJECT,

    // Phase II Feature 1 (회원 관리, 2026-04-23-admin-users-design.md)
    USER_DEACTIVATE,
    USER_REACTIVATE,
    USER_DELETE,
    USER_PASSWORD_RESET,
    USER_MENTOR_SWAP,
    ADMIN_CREATE
}
```

- [ ] **Step 3: 컴파일 + 커밋**

```
cd backend && ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/entity/AdminActionType.java docs/superpowers/specs/2026-04-22-admin-console-common-design.md
git -C "<worktree-abspath>" commit -m "feat(admin-audit): AdminActionType enum 에 회원 관리 6개 값 추가"
```

---

## Task 3: 임시 비밀번호 생성 유틸 (`PasswordGenerator`) - TDD

**Files:**
- Create: `backend/src/test/java/com/devmatch/util/PasswordGeneratorTest.java`
- Create: `backend/src/main/java/com/devmatch/util/PasswordGenerator.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/devmatch/util/PasswordGeneratorTest.java`:

```java
package com.devmatch.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generate_길이는_12자() {
        String pwd = generator.generate();
        assertThat(pwd).hasSize(12);
    }

    @RepeatedTest(20)
    void generate_각_문자종류를_최소_1자씩_포함() {
        String pwd = generator.generate();
        assertThat(pwd).matches(".*[A-Z].*");      // 대문자
        assertThat(pwd).matches(".*[a-z].*");      // 소문자
        assertThat(pwd).matches(".*\\d.*");        // 숫자
        assertThat(pwd).matches(".*[!@#$%^&*].*"); // 특수문자
    }

    @RepeatedTest(5)
    void generate_매번_다른_값을_반환() {
        String a = generator.generate();
        String b = generator.generate();
        assertThat(a).isNotEqualTo(b);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run:
```
cd backend && ./gradlew.bat test --tests "com.devmatch.util.PasswordGeneratorTest"
```
Expected: 컴파일 실패 (`PasswordGenerator` 없음).

- [ ] **Step 3: 최소 구현**

`backend/src/main/java/com/devmatch/util/PasswordGenerator.java`:

```java
package com.devmatch.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관리자 액션(비번 리셋, 관리자 생성) 시 1회용 임시 비밀번호 생성.
 *
 * 정책: 12자, 대소문자+숫자+특수문자 각각 최소 1자, SecureRandom 기반.
 */
@Component
public class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";   // I, O 제외 (가독성)
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";   // l, o 제외
    private static final String DIGIT = "23456789";                    // 0, 1 제외
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGIT));
        chars.add(pick(SPECIAL));
        for (int i = chars.size(); i < LENGTH; i++) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, random);

        StringBuilder sb = new StringBuilder(LENGTH);
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    private char pick(String pool) {
        return pool.charAt(random.nextInt(pool.length()));
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.util.PasswordGeneratorTest"
```
Expected: BUILD SUCCESSFUL, 22 tests (1 + 20 + 5 with @RepeatedTest counted) passed.

- [ ] **Step 5: 커밋**

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/util/PasswordGenerator.java backend/src/test/java/com/devmatch/util/PasswordGeneratorTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): PasswordGenerator 유틸 + 단위 테스트"
```

---

## Task 4: `displayName` 마스킹 유틸 - TDD

**Files:**
- Create: `backend/src/test/java/com/devmatch/util/UserDisplayTest.java`
- Create: `backend/src/main/java/com/devmatch/util/UserDisplay.java`

- [ ] **Step 1: 실패하는 테스트**

`backend/src/test/java/com/devmatch/util/UserDisplayTest.java`:

```java
package com.devmatch.util;

import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDisplayTest {

    @Test
    void displayName_ACTIVE_사용자_원래_이름_반환() {
        User u = User.builder().name("김멘티").status(UserStatus.ACTIVE).build();
        assertThat(UserDisplay.displayName(u)).isEqualTo("김멘티");
    }

    @Test
    void displayName_DEACTIVATED_사용자_원래_이름_반환() {
        User u = User.builder().name("이멘토").status(UserStatus.DEACTIVATED).build();
        assertThat(UserDisplay.displayName(u)).isEqualTo("이멘토");
    }

    @Test
    void displayName_DELETED_사용자_탈퇴한_회원으로_마스킹() {
        User u = User.builder().name("박삭제").status(UserStatus.DELETED).build();
        assertThat(UserDisplay.displayName(u)).isEqualTo("탈퇴한 회원");
    }

    @Test
    void displayName_null_사용자_탈퇴한_회원_반환() {
        assertThat(UserDisplay.displayName(null)).isEqualTo("탈퇴한 회원");
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.util.UserDisplayTest"
```
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

`backend/src/main/java/com/devmatch/util/UserDisplay.java`:

```java
package com.devmatch.util;

import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;

/**
 * 사용자 표시 이름 마스킹.
 * DELETED 상태 또는 null 사용자에 대해 "탈퇴한 회원" 으로 일관되게 표시.
 */
public final class UserDisplay {

    private static final String DELETED_LABEL = "탈퇴한 회원";

    private UserDisplay() { }

    public static String displayName(User user) {
        if (user == null || user.getStatus() == UserStatus.DELETED) {
            return DELETED_LABEL;
        }
        return user.getName();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인 + 커밋**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.util.UserDisplayTest"
```
Expected: BUILD SUCCESSFUL, 4/4 통과.

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/util/UserDisplay.java backend/src/test/java/com/devmatch/util/UserDisplayTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): UserDisplay.displayName 마스킹 유틸"
```

---

## Task 5: `AccountInactiveException` + `AuthService.login` 가드 - TDD

**Files:**
- Create: `backend/src/main/java/com/devmatch/exception/AccountInactiveException.java`
- Create: `backend/src/test/java/com/devmatch/service/AuthServiceLoginGuardTest.java`
- Modify: `backend/src/main/java/com/devmatch/service/AuthService.java`
- Modify: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 예외 클래스 작성**

`backend/src/main/java/com/devmatch/exception/AccountInactiveException.java`:

```java
package com.devmatch.exception;

public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: GlobalExceptionHandler 에 401 핸들러 추가**

`backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java` 의 기존 `@ExceptionHandler` 들과 같은 위치에 추가 (다른 핸들러를 보고 동일 패턴 따름):

```java
    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountInactive(AccountInactiveException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage()));
    }
```

> 만약 `ApiResponse.error` 시그니처가 다르거나 핸들러 패턴이 다르면 기존 `@ExceptionHandler(BadCredentialsException.class)` 등의 옆에 같은 스타일로 작성.

- [ ] **Step 3: 실패하는 테스트 작성 (단위)**

`backend/src/test/java/com/devmatch/service/AuthServiceLoginGuardTest.java`:

```java
package com.devmatch.service;

import com.devmatch.dto.auth.LoginRequest;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.AccountInactiveException;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginGuardTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    // AuthService 의 다른 의존성도 추가로 @Mock 선언 — 실제 시그니처를 확인 후 동일하게 작성:
    // @Mock private JwtTokenProvider jwtTokenProvider;
    // @Mock private RefreshSessionService refreshSessionService;
    // @Mock private 등등

    @InjectMocks private AuthService authService;

    private static User userWith(UserStatus status) {
        User u = User.builder()
                .email("u@test").password("encoded").name("U")
                .role(Role.MENTEE).status(status).build();
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private static LoginRequest req() {
        LoginRequest r = new LoginRequest();
        ReflectionTestUtils.setField(r, "email", "u@test");
        ReflectionTestUtils.setField(r, "password", "raw");
        return r;
    }

    @Test
    void login_DEACTIVATED_사용자_AccountInactiveException() {
        when(userRepository.findByEmail("u@test")).thenReturn(Optional.of(userWith(UserStatus.DEACTIVATED)));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req(), "ua", "ip"))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("비활성");
    }

    @Test
    void login_DELETED_사용자_AccountInactiveException() {
        when(userRepository.findByEmail("u@test")).thenReturn(Optional.of(userWith(UserStatus.DELETED)));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req(), "ua", "ip"))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("탈퇴");
    }
}
```

> 구현자 주의: `AuthService` 의 실제 의존성을 보고 모든 `@Mock` 필드를 추가. `lenient()` 가 필요하면 적용.

- [ ] **Step 4: 테스트 실행 → 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.AuthServiceLoginGuardTest"
```
Expected: 두 테스트 실패 (로그인 시도가 토큰 발급까지 가버려서 예외가 안 던져짐).

- [ ] **Step 5: `AuthService.login` 에 가드 추가**

`backend/src/main/java/com/devmatch/service/AuthService.java` 의 `login` 메서드에서 사용자를 조회한 직후 `passwordEncoder.matches(...)` 호출 직전에 다음 검증을 삽입:

```java
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new AccountInactiveException("비활성화된 계정입니다. 관리자에게 문의해 주세요.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AccountInactiveException("탈퇴한 계정입니다.");
        }
```

import 추가:
```java
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.AccountInactiveException;
```

- [ ] **Step 6: 테스트 통과 확인 + 회귀**

```
cd backend && ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL. 다른 AuthService 테스트(있다면)도 status=ACTIVE 기본값이라 회귀 없음.

- [ ] **Step 7: 커밋**

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/exception/AccountInactiveException.java backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java backend/src/main/java/com/devmatch/service/AuthService.java backend/src/test/java/com/devmatch/service/AuthServiceLoginGuardTest.java
git -C "<worktree-abspath>" commit -m "feat(auth): DEACTIVATED/DELETED 계정 로그인 차단"
```

---

## Task 6: `CustomUserDetails` 권한 매핑 + `SecurityConfig` admins 룰

**Files:**
- Modify: `backend/src/main/java/com/devmatch/security/CustomUserDetails.java`
- Modify: `backend/src/main/java/com/devmatch/config/SecurityConfig.java`

- [ ] **Step 1: `CustomUserDetails.getAuthorities()` 수정**

`backend/src/main/java/com/devmatch/security/CustomUserDetails.java` 의 `getAuthorities` 를 다음으로 교체:

```java
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == Role.SUPER_ADMIN) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
```

- [ ] **Step 2: `SecurityConfig` 에 SUPER_ADMIN 전용 룰 추가**

`backend/src/main/java/com/devmatch/config/SecurityConfig.java` 의 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 라인 **바로 위에** 다음 추가:

```java
                .requestMatchers("/api/admin/admins/**").hasRole("SUPER_ADMIN")
```

순서 중요: Spring Security 는 더 구체적인 룰을 먼저 매칭하므로 `/api/admin/admins/**` 가 `/api/admin/**` 위에 있어야 한다.

- [ ] **Step 3: 컴파일 + 전체 회귀 테스트**

```
cd backend && ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL. 기존 ADMIN 테스트 영향 없음 (SUPER_ADMIN 사용자가 없으므로).

- [ ] **Step 4: 커밋**

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/security/CustomUserDetails.java backend/src/main/java/com/devmatch/config/SecurityConfig.java
git -C "<worktree-abspath>" commit -m "feat(auth): SUPER_ADMIN 권한 매핑 + /api/admin/admins SUPER_ADMIN 가드"
```

---

## Task 7: `ForbiddenOperationException` + DTO 묶음 (사전 작업)

**Files:**
- Create: `backend/src/main/java/com/devmatch/exception/ForbiddenOperationException.java`
- Modify: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/UserActionRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/MentorSwapRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/PasswordResetResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/AdminCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/AdminCreateResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/AdminUserListResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/AdminUserDetailResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/auth/PasswordChangeRequest.java`

- [ ] **Step 1: 예외**

`backend/src/main/java/com/devmatch/exception/ForbiddenOperationException.java`:

```java
package com.devmatch.exception;

public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
```

핸들러 추가 (`GlobalExceptionHandler`):

```java
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenOperationException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }
```

- [ ] **Step 2: 공통 액션 요청 body**

`backend/src/main/java/com/devmatch/dto/admin/UserActionRequest.java`:

```java
package com.devmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UserActionRequest {

    @NotBlank(message = "사유는 필수입니다")
    @Size(min = 10, max = 500, message = "사유는 10~500자여야 합니다")
    private String reason;
}
```

- [ ] **Step 3: 멘토 교체 요청**

`backend/src/main/java/com/devmatch/dto/admin/MentorSwapRequest.java`:

```java
package com.devmatch.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class MentorSwapRequest {

    @NotNull(message = "새 멘토 사용자 ID 가 필요합니다")
    private Long newMentorId;

    @NotBlank(message = "사유는 필수입니다")
    @Size(min = 10, max = 500, message = "사유는 10~500자여야 합니다")
    private String reason;
}
```

> `newMentorId` 는 **MentorProfile.id 가 아닌 User.id** 로 설계(스펙 §4.6 의 metadata 키 `newMentorUserId` 와 일치). 구현 시 멘토 조회는 `mentorProfileRepository.findByUserId(...)`.

- [ ] **Step 4: 비밀번호 리셋 응답**

`backend/src/main/java/com/devmatch/dto/admin/PasswordResetResponse.java`:

```java
package com.devmatch.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordResetResponse {
    private String temporaryPassword;
    private boolean mustChangePassword;
}
```

- [ ] **Step 5: 관리자 생성 요청·응답**

`backend/src/main/java/com/devmatch/dto/admin/AdminCreateRequest.java`:

```java
package com.devmatch.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AdminCreateRequest {

    @NotBlank @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(max = 50)
    private String name;

    @NotBlank
    @Size(max = 100)
    private String jobTitle;
}
```

`backend/src/main/java/com/devmatch/dto/admin/AdminCreateResponse.java`:

```java
package com.devmatch.dto.admin;

import com.devmatch.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminCreateResponse {
    private UserResponse user;
    private String temporaryPassword;
}
```

- [ ] **Step 6: 회원 목록·상세 응답**

`backend/src/main/java/com/devmatch/dto/admin/AdminUserListResponse.java`:

```java
package com.devmatch.dto.admin;

import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserListResponse {
    private Long id;
    private String email;
    private String name;        // displayName 적용
    private String role;
    private String status;
    private String jobTitle;    // ADMIN/SUPER_ADMIN 만 값
    private LocalDateTime createdAt;

    public static AdminUserListResponse from(User user) {
        return new AdminUserListResponse(
                user.getId(),
                user.getEmail(),
                UserDisplay.displayName(user),
                user.getRole().name(),
                user.getStatus().name(),
                user.getJobTitle(),
                user.getCreatedAt()
        );
    }
}
```

`backend/src/main/java/com/devmatch/dto/admin/AdminUserDetailResponse.java`:

```java
package com.devmatch.dto.admin;

import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserDetailResponse {
    private Long id;
    private String email;
    private String name;
    private String role;
    private String status;
    private String jobTitle;
    private String provider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 연관 활동 요약
    private long paymentCount;
    private long postCount;
    private Long mentorProfileId;        // null 가능 (멘토 아니면)

    public static AdminUserDetailResponse from(User user, long paymentCount, long postCount,
                                               Long mentorProfileId) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                UserDisplay.displayName(user),
                user.getRole().name(),
                user.getStatus().name(),
                user.getJobTitle(),
                user.getProvider(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                paymentCount,
                postCount,
                mentorProfileId
        );
    }
}
```

- [ ] **Step 7: 강제 비번 변경 요청**

`backend/src/main/java/com/devmatch/dto/auth/PasswordChangeRequest.java`:

```java
package com.devmatch.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordChangeRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 100, message = "새 비밀번호는 8~100자")
    private String newPassword;
}
```

- [ ] **Step 8: 컴파일 + 커밋**

```
cd backend && ./gradlew.bat compileJava
```

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/exception/ForbiddenOperationException.java backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java backend/src/main/java/com/devmatch/dto/admin/ backend/src/main/java/com/devmatch/dto/auth/PasswordChangeRequest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): ForbiddenOperationException + 회원 관리 DTO 묶음"
```

---

## Task 8: `AdminUserService` 목록·상세 (TDD) + `AdminUserController` GET

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java`
- Create: `backend/src/main/java/com/devmatch/service/AdminUserService.java`
- Create: `backend/src/main/java/com/devmatch/controller/AdminUserController.java`
- Modify: `backend/src/main/java/com/devmatch/repository/UserRepository.java` — 페이지·필터 메서드 추가
- Modify: `backend/src/main/java/com/devmatch/repository/PaymentRepository.java` — `countByUserId(Long)` 가 없으면 추가
- Modify: `backend/src/main/java/com/devmatch/repository/PostRepository.java` — `countByAuthorId(Long)` 가 없으면 추가

- [ ] **Step 1: Repository 메서드 추가**

`UserRepository.java` 에 추가 (이미 있는 것은 재사용):
```java
    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);
    Page<User> findByRole(Role role, Pageable pageable);
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    Page<User> findByNameContainingOrEmailContaining(String name, String email, Pageable pageable);
    boolean existsByEmail(String email);  // 이미 있으면 생략
```

`PaymentRepository.java` 에 추가:
```java
    long countByUserId(Long userId);
```

`PostRepository.java` 에 추가 (Post.author 는 User 객체이므로 `author.id` 경로):
```java
    long countByAuthor_Id(Long userId);
```

> 구현자 주의: 파일을 열어 import 와 기존 메서드를 보고 같은 스타일로 추가. 검색·필터 조합은 Step 4 의 서비스 코드에서 사용 패턴 결정.

- [ ] **Step 2: 실패하는 테스트 작성**

`backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java`:

```java
package com.devmatch.service;

import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PostRepository postRepository;
    @Mock MentorProfileRepository mentorProfileRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.devmatch.util.PasswordGenerator passwordGenerator;
    @Mock com.devmatch.service.AdminAuditLogService auditLogService;

    @InjectMocks AdminUserService service;

    private User userOf(Long id, Role role, UserStatus status) {
        User u = User.builder().email("u@test").password("enc").name("U")
                .role(role).status(status).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void getDetail_연관_활동_카운트_포함() {
        User u = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(paymentRepository.countByUserId(7L)).thenReturn(3L);
        when(postRepository.countByAuthor_Id(7L)).thenReturn(12L);
        when(mentorProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());

        var detail = service.getDetail(7L);

        assertThat(detail.getId()).isEqualTo(7L);
        assertThat(detail.getPaymentCount()).isEqualTo(3L);
        assertThat(detail.getPostCount()).isEqualTo(12L);
        assertThat(detail.getMentorProfileId()).isNull();
    }

    @Test
    void getDetail_없는_사용자_예외() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDetail(99L))
                .isInstanceOf(com.devmatch.exception.ResourceNotFoundException.class);
    }
}
```

> 주: `ResourceNotFoundException` 또는 프로젝트의 기존 NotFound 패턴(`UserNotFoundException` 등) 을 사용. 작성자가 확인 후 맞는 클래스로 교체.

- [ ] **Step 3: 테스트 실행 → 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.AdminUserServiceTest"
```
Expected: 컴파일 실패 (`AdminUserService` 없음).

- [ ] **Step 4: `AdminUserService` 최소 구현 (목록·상세만)**

`backend/src/main/java/com/devmatch/service/AdminUserService.java`:

```java
package com.devmatch.service;

import com.devmatch.dto.admin.AdminUserDetailResponse;
import com.devmatch.dto.admin.AdminUserListResponse;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.ResourceNotFoundException;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PostRepository postRepository;
    private final MentorProfileRepository mentorProfileRepository;

    public Page<AdminUserListResponse> list(Role role, UserStatus status, String q, Pageable pageable) {
        Page<User> users;
        if (q != null && !q.isBlank()) {
            users = userRepository.findByNameContainingOrEmailContaining(q, q, pageable);
        } else if (role != null && status != null) {
            users = userRepository.findByRoleAndStatus(role, status, pageable);
        } else if (role != null) {
            users = userRepository.findByRole(role, pageable);
        } else if (status != null) {
            users = userRepository.findByStatus(status, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(AdminUserListResponse::from);
    }

    public AdminUserDetailResponse getDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다: " + userId));
        long paymentCount = paymentRepository.countByUserId(userId);
        long postCount = postRepository.countByAuthor_Id(userId);
        Long mentorProfileId = mentorProfileRepository.findByUserId(userId)
                .map(MentorProfile::getId).orElse(null);
        return AdminUserDetailResponse.from(user, paymentCount, postCount, mentorProfileId);
    }
}
```

> 만약 프로젝트에 `ResourceNotFoundException` 이 없으면 기존 NotFound 패턴을 따름 (예: `UserNotFoundException`).

- [ ] **Step 5: `AdminUserController` 최소 구현 (GET 만)**

`backend/src/main/java/com/devmatch/controller/AdminUserController.java`:

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.AdminUserDetailResponse;
import com.devmatch.dto.admin.AdminUserListResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.Role;
import com.devmatch.entity.UserStatus;
import com.devmatch.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users", description = "관리자 회원 관리 API")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 목록 (페이징)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserListResponse>>> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.list(role, status, q, pageable)));
    }

    @Operation(summary = "회원 상세")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getDetail(id)));
    }
}
```

- [ ] **Step 6: 테스트 통과 + 커밋**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.AdminUserServiceTest"
```
Expected: BUILD SUCCESSFUL, 2 tests passed.

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/service/AdminUserService.java backend/src/main/java/com/devmatch/controller/AdminUserController.java backend/src/main/java/com/devmatch/repository/ backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): AdminUserService 목록·상세 + Controller GET"
```

---

## Task 9: `AdminUserService` 상태 전이 (deactivate/reactivate/delete) + endpoints (TDD)

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/AdminUserService.java`
- Modify: `backend/src/main/java/com/devmatch/controller/AdminUserController.java`
- Modify: `backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`AdminUserServiceTest.java` 에 다음 추가:

```java
    @Test
    void deactivate_정상_케이스_상태_전이_및_감사로그() {
        User u = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        service.deactivate(1L, 7L, "스팸 행위");

        assertThat(u.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        org.mockito.Mockito.verify(auditLogService).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(com.devmatch.entity.AdminActionType.USER_DEACTIVATE),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("스팸 행위"),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void deactivate_본인_차단() {
        User u = userOf(1L, Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.deactivate(1L, 1L, "test reason"))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deactivate_ADMIN_대상_차단() {
        User u = userOf(7L, Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.deactivate(1L, 7L, "test reason"))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
    }

    @Test
    void deactivate_DELETED_대상_차단() {
        User u = userOf(7L, Role.MENTEE, UserStatus.DELETED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.deactivate(1L, 7L, "test reason"))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
    }

    @Test
    void reactivate_DEACTIVATED_사용자_ACTIVE_전이_감사로그() {
        User u = userOf(7L, Role.MENTEE, UserStatus.DEACTIVATED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        service.reactivate(1L, 7L);

        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
        org.mockito.Mockito.verify(auditLogService).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(com.devmatch.entity.AdminActionType.USER_REACTIVATE),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void delete_정상_케이스_DELETED_전이_감사로그() {
        User u = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        service.delete(1L, 7L, "탈퇴 요청 처리");

        assertThat(u.getStatus()).isEqualTo(UserStatus.DELETED);
        org.mockito.Mockito.verify(auditLogService).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(com.devmatch.entity.AdminActionType.USER_DELETE),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("탈퇴 요청 처리"),
                org.mockito.ArgumentMatchers.isNull());
    }
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.AdminUserServiceTest"
```
Expected: 새 테스트들 실패 (메서드 없음).

- [ ] **Step 3: 서비스에 메서드 추가**

`AdminUserService.java` 에 다음을 추가 (`@Transactional(readOnly = true)` 클래스 레벨이므로 수정 메서드는 `@Transactional` 명시):

import 추가:
```java
import com.devmatch.entity.AdminActionType;
import com.devmatch.exception.ForbiddenOperationException;
```

필드 추가:
```java
    private final AdminAuditLogService adminAuditLogService;
```

메서드 추가:
```java
    @Transactional
    public void deactivate(Long adminId, Long targetId, String reason) {
        User target = loadTarget(targetId);
        guardAgainstSelf(adminId, targetId);
        guardAgainstAdminTarget(target);
        guardAgainstDeleted(target);

        target.deactivate();
        adminAuditLogService.record(adminId, AdminActionType.USER_DEACTIVATE,
                "USER", targetId, reason, null);
    }

    @Transactional
    public void reactivate(Long adminId, Long targetId) {
        User target = loadTarget(targetId);
        guardAgainstAdminTarget(target);
        guardAgainstDeleted(target);

        target.reactivate();
        adminAuditLogService.record(adminId, AdminActionType.USER_REACTIVATE,
                "USER", targetId, null, null);
    }

    @Transactional
    public void delete(Long adminId, Long targetId, String reason) {
        User target = loadTarget(targetId);
        guardAgainstSelf(adminId, targetId);
        guardAgainstAdminTarget(target);
        guardAgainstDeleted(target);

        target.markDeleted();
        adminAuditLogService.record(adminId, AdminActionType.USER_DELETE,
                "USER", targetId, reason, null);
    }

    private User loadTarget(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다: " + id));
    }

    private void guardAgainstSelf(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new ForbiddenOperationException("관리자 본인 계정에는 이 작업을 수행할 수 없습니다.");
        }
    }

    private void guardAgainstAdminTarget(User target) {
        if (target.getRole() == Role.ADMIN || target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenOperationException("관리자 계정은 회원 관리 메뉴에서 변경할 수 없습니다.");
        }
    }

    private void guardAgainstDeleted(User target) {
        if (target.getStatus() == UserStatus.DELETED) {
            throw new ForbiddenOperationException("이미 삭제된 계정입니다.");
        }
    }
```

- [ ] **Step 4: 컨트롤러에 엔드포인트 추가**

`AdminUserController.java` 에 다음 추가:

import 추가:
```java
import com.devmatch.dto.admin.UserActionRequest;
import com.devmatch.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
```

엔드포인트 추가:
```java
    @Operation(summary = "회원 비활성화")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable Long id,
            @Valid @RequestBody UserActionRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminUserService.deactivate(admin.getUserId(), id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("회원이 비활성화되었습니다", null));
    }

    @Operation(summary = "회원 재활성화")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminUserService.reactivate(admin.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("회원이 재활성화되었습니다", null));
    }

    @Operation(summary = "회원 영구 삭제")
    @PostMapping("/{id}/delete")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @Valid @RequestBody UserActionRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminUserService.delete(admin.getUserId(), id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("회원이 삭제되었습니다", null));
    }
```

- [ ] **Step 5: 테스트 통과 + 커밋**

```
cd backend && ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/service/AdminUserService.java backend/src/main/java/com/devmatch/controller/AdminUserController.java backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): 회원 비활성화/재활성화/삭제 + 가드 + 감사로그"
```

---

## Task 10: `AdminUserService.resetPassword` + endpoint (TDD)

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/AdminUserService.java`
- Modify: `backend/src/main/java/com/devmatch/controller/AdminUserController.java`
- Modify: `backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`AdminUserServiceTest.java` 에 추가:

```java
    @Test
    void resetPassword_정상_평문_응답_및_플래그_설정() {
        User u = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(passwordGenerator.generate()).thenReturn("Tmp1!XyzAbc2");
        when(passwordEncoder.encode("Tmp1!XyzAbc2")).thenReturn("encoded-pwd");

        var resp = service.resetPassword(1L, 7L);

        assertThat(resp.getTemporaryPassword()).isEqualTo("Tmp1!XyzAbc2");
        assertThat(resp.isMustChangePassword()).isTrue();
        assertThat(u.getPassword()).isEqualTo("encoded-pwd");
        assertThat(u.getMustChangePassword()).isTrue();
        org.mockito.Mockito.verify(auditLogService).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(com.devmatch.entity.AdminActionType.USER_PASSWORD_RESET),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void resetPassword_SUPER_ADMIN_대상_차단() {
        User u = userOf(7L, Role.SUPER_ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.resetPassword(1L, 7L))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
    }

    @Test
    void resetPassword_DELETED_대상_차단() {
        User u = userOf(7L, Role.MENTEE, UserStatus.DELETED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.resetPassword(1L, 7L))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
    }

    @Test
    void resetPassword_ADMIN_대상은_허용() {
        User u = userOf(7L, Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(passwordGenerator.generate()).thenReturn("Tmp2!QwerXyz9");
        when(passwordEncoder.encode("Tmp2!QwerXyz9")).thenReturn("encoded2");

        var resp = service.resetPassword(1L, 7L);

        assertThat(resp.getTemporaryPassword()).isEqualTo("Tmp2!QwerXyz9");
    }
```

- [ ] **Step 2: 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.AdminUserServiceTest"
```

- [ ] **Step 3: 서비스 메서드 추가**

`AdminUserService.java` 에 추가 (필드 부분에 `passwordEncoder`, `passwordGenerator` 추가):

```java
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.devmatch.util.PasswordGenerator passwordGenerator;
```

메서드:
```java
    @Transactional
    public com.devmatch.dto.admin.PasswordResetResponse resetPassword(Long adminId, Long targetId) {
        User target = loadTarget(targetId);
        guardAgainstSuperAdminTarget(target);
        guardAgainstDeleted(target);

        String temp = passwordGenerator.generate();
        String encoded = passwordEncoder.encode(temp);
        target.forcePasswordChange(encoded);

        adminAuditLogService.record(adminId, AdminActionType.USER_PASSWORD_RESET,
                "USER", targetId, null, null);

        return new com.devmatch.dto.admin.PasswordResetResponse(temp, true);
    }

    private void guardAgainstSuperAdminTarget(User target) {
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenOperationException("SUPER_ADMIN 의 비밀번호는 이 메뉴에서 리셋할 수 없습니다.");
        }
    }
```

- [ ] **Step 4: 컨트롤러 엔드포인트 추가**

`AdminUserController.java` 에 추가:

```java
    @Operation(summary = "회원 비밀번호 리셋")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<com.devmatch.dto.admin.PasswordResetResponse>> resetPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails admin) {
        var resp = adminUserService.resetPassword(admin.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("임시 비밀번호가 발급되었습니다", resp));
    }
```

- [ ] **Step 5: 테스트 통과 + 커밋**

```
cd backend && ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/service/AdminUserService.java backend/src/main/java/com/devmatch/controller/AdminUserController.java backend/src/test/java/com/devmatch/service/AdminUserServiceTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): 비밀번호 리셋 (임시 비번 1회 응답)"
```

---

## Task 11: `MentorSwapService` + endpoint (TDD)

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/MentorSwapService.java`
- Create: `backend/src/test/java/com/devmatch/service/MentorSwapServiceTest.java`
- Modify: `backend/src/main/java/com/devmatch/controller/AdminUserController.java`
- Modify: `backend/src/main/java/com/devmatch/repository/MatchingRepository.java` (필요 시 메서드 추가)
- Possibly Modify: `backend/src/main/java/com/devmatch/entity/Matching.java` (cancel 메서드)

- [ ] **Step 1: `Matching` 도메인 확인**

먼저 `backend/src/main/java/com/devmatch/entity/Matching.java` 와 `backend/src/main/java/com/devmatch/entity/MatchingStatus.java` 를 읽어:
- `cancel(reason)` 또는 status 전이 메서드가 있는지 확인
- 없으면 entity 에 추가 필요. 있으면 재사용
- `MatchingStatus.CANCELLED` 가 enum 값으로 있는지 확인 (있을 가능성 높음)
- 활성 매칭 조회 메서드 (`findByMenteeIdAndStatusIn`) 가 `MatchingRepository` 에 있는지 확인

> 구현자 주의: 이 Step 은 **읽기만**. 발견한 내용을 다음 Step 의 코드에 반영.

- [ ] **Step 2: `MatchingRepository` 메서드 추가 (없으면)**

`MatchingRepository.java` 에 추가:
```java
    java.util.Optional<com.devmatch.entity.Matching> findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
            Long menteeId, java.util.Collection<MatchingStatus> statuses);
```

`Matching` 엔티티에 메서드 없으면 추가 (기존 패턴 따라):
```java
    public void cancel(String reason) {
        this.status = MatchingStatus.CANCELLED;
        // 만약 cancelReason 컬럼이 없으면 metadata 로 audit log 에만 남기고 entity 변경은 status 만
    }
```

> Matching 엔티티에 `cancelReason` 컬럼이 없으면 추가하지 말고 status 변경만, 사유는 audit log 의 `metadata` 로 보존.

- [ ] **Step 3: 실패하는 테스트 작성**

`backend/src/test/java/com/devmatch/service/MentorSwapServiceTest.java`:

```java
package com.devmatch.service;

import com.devmatch.entity.*;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorSwapServiceTest {

    @Mock UserRepository userRepository;
    @Mock MentorProfileRepository mentorProfileRepository;
    @Mock MatchingRepository matchingRepository;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks MentorSwapService service;

    private User userOf(Long id, Role role, UserStatus status) {
        User u = User.builder().email("u").name("u").password("p").role(role).status(status).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private MentorProfile mentorOf(Long profileId, Long userId, MentorStatus status) {
        User mentorUser = userOf(userId, Role.MENTOR, UserStatus.ACTIVE);
        MentorProfile p = MentorProfile.builder().user(mentorUser).status(status).build();
        ReflectionTestUtils.setField(p, "id", profileId);
        return p;
    }

    private Matching matchingOf(Long id, User mentee, User mentor, MatchingStatus status) {
        Matching m = Matching.builder().mentee(mentee).mentor(mentor).status(status).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void swap_정상_old_CANCELLED_new_생성_감사로그() {
        User mentee = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        User oldMentor = userOf(11L, Role.MENTOR, UserStatus.ACTIVE);
        User newMentor = userOf(22L, Role.MENTOR, UserStatus.ACTIVE);
        MentorProfile newMentorProfile = mentorOf(101L, 22L, MentorStatus.APPROVED);
        Matching old = matchingOf(50L, mentee, oldMentor, MatchingStatus.CONFIRMED);

        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findByUserId(22L)).thenReturn(Optional.of(newMentorProfile));
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                eq(7L), anyCollection())).thenReturn(Optional.of(old));
        when(matchingRepository.save(any(Matching.class))).thenAnswer(inv -> {
            Matching m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 60L);
            return m;
        });

        service.swap(1L, 7L, 22L, "멘티 요청");

        assertThat(old.getStatus()).isEqualTo(MatchingStatus.CANCELLED);

        ArgumentCaptor<Matching> captor = ArgumentCaptor.forClass(Matching.class);
        verify(matchingRepository).save(captor.capture());
        Matching newMatching = captor.getValue();
        assertThat(newMatching.getMentee().getId()).isEqualTo(7L);
        assertThat(newMatching.getMentor().getId()).isEqualTo(22L);

        verify(auditLogService).record(
                eq(1L),
                eq(AdminActionType.USER_MENTOR_SWAP),
                eq("USER"),
                eq(7L),
                eq("멘티 요청"),
                eq(Map.of("oldMatchingId", 50L, "oldMentorUserId", 11L, "newMentorUserId", 22L)));
    }

    @Test
    void swap_새_멘토가_APPROVED가_아니면_차단() {
        User mentee = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        MentorProfile pending = mentorOf(101L, 22L, MentorStatus.PENDING);
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findByUserId(22L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.swap(1L, 7L, 22L, "사유"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("승인된 멘토");
    }

    @Test
    void swap_활성_매칭_없으면_차단() {
        User mentee = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        MentorProfile newMentorProfile = mentorOf(101L, 22L, MentorStatus.APPROVED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findByUserId(22L)).thenReturn(Optional.of(newMentorProfile));
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                eq(7L), anyCollection())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.swap(1L, 7L, 22L, "사유"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("활성 매칭");
    }
}
```

- [ ] **Step 4: 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.MentorSwapServiceTest"
```
Expected: 컴파일 실패.

- [ ] **Step 5: `MentorSwapService` 구현**

`backend/src/main/java/com/devmatch/service/MentorSwapService.java`:

```java
package com.devmatch.service;

import com.devmatch.entity.*;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.ResourceNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MentorSwapService {

    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MatchingRepository matchingRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public void swap(Long adminId, Long menteeUserId, Long newMentorUserId, String reason) {
        User mentee = userRepository.findById(menteeUserId)
                .orElseThrow(() -> new ResourceNotFoundException("멘티를 찾을 수 없습니다: " + menteeUserId));
        if (mentee.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("ACTIVE 상태의 멘티만 멘토 교체가 가능합니다.");
        }

        MentorProfile newMentorProfile = mentorProfileRepository.findByUserId(newMentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("멘토 프로필을 찾을 수 없습니다: " + newMentorUserId));
        if (newMentorProfile.getStatus() != MentorStatus.APPROVED) {
            throw new ForbiddenOperationException("승인된 멘토만 교체 대상이 될 수 있습니다.");
        }
        if (newMentorProfile.getUser().getId().equals(menteeUserId)) {
            throw new ForbiddenOperationException("자기 자신을 멘토로 지정할 수 없습니다.");
        }

        Matching old = matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                        menteeUserId, List.of(MatchingStatus.CONFIRMED, MatchingStatus.PENDING))
                .orElseThrow(() -> new ForbiddenOperationException("교체할 활성 매칭이 없습니다."));

        if (old.getMentor().getId().equals(newMentorUserId)) {
            throw new ForbiddenOperationException("동일한 멘토로는 교체할 수 없습니다.");
        }

        Long oldMentorUserId = old.getMentor().getId();
        Long oldMatchingId = old.getId();

        old.cancel(reason);  // 또는 status 만 CANCELLED 로 setter — Matching 엔티티 구현에 맞춤

        Matching neo = Matching.builder()
                .mentee(mentee)
                .mentor(newMentorProfile.getUser())
                .status(MatchingStatus.CONFIRMED)
                .build();
        matchingRepository.save(neo);

        adminAuditLogService.record(adminId, AdminActionType.USER_MENTOR_SWAP,
                "USER", menteeUserId, reason,
                Map.of("oldMatchingId", oldMatchingId,
                       "oldMentorUserId", oldMentorUserId,
                       "newMentorUserId", newMentorUserId));
    }
}
```

> 만약 `Matching` 빌더가 다른 필수 필드(예: `course`, `course` 등) 를 요구하면, 기존 매칭의 그 필드들을 인계: `.course(old.getCourse())` 등.

- [ ] **Step 6: 컨트롤러 엔드포인트 추가**

`AdminUserController.java` 에 추가 (필드에 `MentorSwapService` 추가):

```java
    private final MentorSwapService mentorSwapService;
```

엔드포인트:
```java
    @Operation(summary = "멘티의 멘토 교체")
    @PostMapping("/{menteeId}/swap-mentor")
    public ResponseEntity<ApiResponse<Void>> swapMentor(
            @PathVariable Long menteeId,
            @Valid @RequestBody com.devmatch.dto.admin.MentorSwapRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        mentorSwapService.swap(admin.getUserId(), menteeId, request.getNewMentorId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("멘토가 교체되었습니다", null));
    }
```

- [ ] **Step 7: 테스트 통과 + 커밋**

```
cd backend && ./gradlew.bat test
```

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/service/MentorSwapService.java backend/src/main/java/com/devmatch/controller/AdminUserController.java backend/src/main/java/com/devmatch/repository/MatchingRepository.java backend/src/main/java/com/devmatch/entity/Matching.java backend/src/test/java/com/devmatch/service/MentorSwapServiceTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): MentorSwapService 멘토 교체 (단순)"
```

---

## Task 12: `AdminAccountService` + Controller (TDD)

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/AdminAccountService.java`
- Create: `backend/src/main/java/com/devmatch/controller/AdminAccountController.java`
- Create: `backend/src/test/java/com/devmatch/service/AdminAccountServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/devmatch/service/AdminAccountServiceTest.java`:

```java
package com.devmatch.service;

import com.devmatch.dto.admin.AdminCreateRequest;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.UserRepository;
import com.devmatch.util.PasswordGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordGenerator passwordGenerator;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks AdminAccountService service;

    private AdminCreateRequest req() {
        AdminCreateRequest r = new AdminCreateRequest();
        ReflectionTestUtils.setField(r, "email", "newadmin@x.com");
        ReflectionTestUtils.setField(r, "name", "새관리자");
        ReflectionTestUtils.setField(r, "jobTitle", "운영팀장");
        return r;
    }

    @Test
    void createAdmin_정상_평문_비번_응답() {
        when(userRepository.existsByEmail("newadmin@x.com")).thenReturn(false);
        when(passwordGenerator.generate()).thenReturn("Tmp1!XyzAbc2");
        when(passwordEncoder.encode("Tmp1!XyzAbc2")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 99L);
            return u;
        });

        var resp = service.createAdmin(1L, req());

        assertThat(resp.getTemporaryPassword()).isEqualTo("Tmp1!XyzAbc2");
        assertThat(resp.getUser().getEmail()).isEqualTo("newadmin@x.com");
        assertThat(resp.getUser().getRole()).isEqualTo("ADMIN");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("newadmin@x.com");
        assertThat(saved.getName()).isEqualTo("새관리자");
        assertThat(saved.getJobTitle()).isEqualTo("운영팀장");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getMustChangePassword()).isTrue();
        assertThat(saved.getPassword()).isEqualTo("encoded");

        verify(auditLogService).record(
                eq(1L),
                eq(com.devmatch.entity.AdminActionType.ADMIN_CREATE),
                eq("USER"),
                eq(99L),
                org.mockito.ArgumentMatchers.isNull(),
                eq(java.util.Map.of("email", "newadmin@x.com", "jobTitle", "운영팀장")));
    }

    @Test
    void createAdmin_중복_이메일_예외() {
        when(userRepository.existsByEmail("newadmin@x.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createAdmin(1L, req()))
                .isInstanceOf(com.devmatch.exception.DuplicateEmailException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

```
cd backend && ./gradlew.bat test --tests "com.devmatch.service.AdminAccountServiceTest"
```

- [ ] **Step 3: 서비스 구현**

`backend/src/main/java/com/devmatch/service/AdminAccountService.java`:

```java
package com.devmatch.service;

import com.devmatch.dto.admin.AdminCreateRequest;
import com.devmatch.dto.admin.AdminCreateResponse;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.DuplicateEmailException;
import com.devmatch.repository.UserRepository;
import com.devmatch.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional(readOnly = true)
    public java.util.List<UserResponse> listAdmins() {
        return userRepository.findByRoleIn(java.util.List.of(Role.ADMIN, Role.SUPER_ADMIN))
                .stream().map(UserResponse::from).toList();
    }

    @Transactional
    public AdminCreateResponse createAdmin(Long superAdminId, AdminCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다: " + request.getEmail());
        }

        String temp = passwordGenerator.generate();
        String encoded = passwordEncoder.encode(temp);

        User user = User.builder()
                .email(request.getEmail())
                .password(encoded)
                .name(request.getName())
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .jobTitle(request.getJobTitle())
                .mustChangePassword(true)
                .build();
        userRepository.save(user);

        adminAuditLogService.record(superAdminId, AdminActionType.ADMIN_CREATE,
                "USER", user.getId(), null,
                Map.of("email", request.getEmail(), "jobTitle", request.getJobTitle()));

        return new AdminCreateResponse(UserResponse.from(user), temp);
    }
}
```

> `findByRoleIn(List<Role>)` 가 `UserRepository` 에 없으면 추가:
> ```java
>     java.util.List<User> findByRoleIn(java.util.Collection<Role> roles);
> ```

> `UserResponse.from(User)` 의 시그니처가 `(id, email, name, role, createdAt)` 이라 jobTitle/status 등 새 필드는 안 들어감. Task 13 에서 UserResponse 확장. 일단 이 Task 의 응답은 부분적으로 노출.

- [ ] **Step 4: 컨트롤러**

`backend/src/main/java/com/devmatch/controller/AdminAccountController.java`:

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.AdminCreateRequest;
import com.devmatch.dto.admin.AdminCreateResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Admin Accounts", description = "SUPER_ADMIN 전용 관리자 계정 관리")
@RestController
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(summary = "관리자 계정 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(adminAccountService.listAdmins()));
    }

    @Operation(summary = "신규 관리자 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminCreateResponse>> create(
            @Valid @RequestBody AdminCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails superAdmin) {
        var resp = adminAccountService.createAdmin(superAdmin.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("관리자 계정이 생성되었습니다", resp));
    }
}
```

- [ ] **Step 5: 테스트 통과 + 커밋**

```
cd backend && ./gradlew.bat test
```

```bash
git -C "<worktree-abspath>" add backend/src/main/java/com/devmatch/service/AdminAccountService.java backend/src/main/java/com/devmatch/controller/AdminAccountController.java backend/src/main/java/com/devmatch/repository/UserRepository.java backend/src/test/java/com/devmatch/service/AdminAccountServiceTest.java
git -C "<worktree-abspath>" commit -m "feat(admin-users): AdminAccountService 신규 관리자 계정 생성"
```

---

## Task 13: `UserResponse` 확장 + `/api/auth/change-password` + `mustChangePassword` 응답

**Files:**
- Modify: `backend/src/main/java/com/devmatch/dto/user/UserResponse.java`
- Modify: `backend/src/main/java/com/devmatch/service/AuthService.java`
- Modify: `backend/src/main/java/com/devmatch/controller/AuthController.java`

- [ ] **Step 1: `UserResponse` 에 status, mustChangePassword, jobTitle 필드 추가**

`backend/src/main/java/com/devmatch/dto/user/UserResponse.java`:

```java
package com.devmatch.dto.user;

import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String name;             // displayName 적용
    private String role;
    private String status;
    private String jobTitle;          // ADMIN/SUPER_ADMIN 만 값, 그 외 null
    private boolean mustChangePassword;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                UserDisplay.displayName(user),
                user.getRole().name(),
                user.getStatus().name(),
                user.getJobTitle(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getCreatedAt()
        );
    }
}
```

> 컴파일 영향 검토 — `UserResponse.from(user)` 호출처가 여러 곳에 있을 가능성. `UserResponse` 의 생성자가 변경됐으니 직접 `new UserResponse(...)` 를 호출하는 곳도 같이 수정 필요. 다음 Step 에서 검색.

- [ ] **Step 2: `UserResponse` 직접 생성 사용처 점검**

Grep:
```
grep -rn "new UserResponse(" backend/src --include="*.java"
```

각 호출처를 새 시그니처에 맞게 수정. `UserResponse.from(user)` 만 사용 중이면 별도 수정 불필요.

- [ ] **Step 3: `AuthService` 에 `changePassword` 메서드 추가**

`backend/src/main/java/com/devmatch/service/AuthService.java` 에 추가:

```java
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new com.devmatch.exception.InvalidPasswordChangeException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new com.devmatch.exception.InvalidPasswordChangeException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        user.clearMustChangePassword();
    }
```

`InvalidPasswordChangeException` 신규 작성:
```java
package com.devmatch.exception;

public class InvalidPasswordChangeException extends RuntimeException {
    public InvalidPasswordChangeException(String message) { super(message); }
}
```

`GlobalExceptionHandler` 에 핸들러:
```java
    @ExceptionHandler(InvalidPasswordChangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPasswordChange(InvalidPasswordChangeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }
```

- [ ] **Step 4: `AuthController` 에 `change-password` 엔드포인트 추가**

```java
    @Operation(summary = "비밀번호 변경 (강제 변경 포함)")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal com.devmatch.security.CustomUserDetails user,
            @Valid @RequestBody com.devmatch.dto.auth.PasswordChangeRequest request) {
        authService.changePassword(user.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다", null));
    }
```

import 추가:
```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
```

- [ ] **Step 5: 회귀 테스트**

```
cd backend && ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL. UserResponse 사용처 컴파일 에러가 있다면 모두 새 시그니처에 맞게 수정.

- [ ] **Step 6: 커밋**

```bash
git -C "<worktree-abspath>" add backend/
git -C "<worktree-abspath>" commit -m "feat(auth): UserResponse 확장 + 비밀번호 변경 엔드포인트"
```

---

## Task 14: 시드 데이터 (`seed-lms.sql`) + ROADMAP 배포 체크리스트

**Files:**
- Modify: `backend/src/main/resources/seed-lms.sql`
- Modify: `ROADMAP.md`

- [ ] **Step 1: `seed-lms.sql` 확인**

Read `backend/src/main/resources/seed-lms.sql` — 기존 admin 시드 라인을 찾는다 (`role='ADMIN'` 또는 비슷한 패턴).

- [ ] **Step 2: 기존 admin 시드를 SUPER_ADMIN 으로 변경**

기존 `INSERT INTO users (..., role) VALUES (..., 'ADMIN')` 라인의 `'ADMIN'` 을 `'SUPER_ADMIN'` 으로 변경.

또한 컬럼 추가에 따라 신규 시드는 `status='ACTIVE'`, `must_change_password=false`, `job_title='운영팀'` 같이 명시.

기존 시드가 INSERT 시 컬럼 명시 안 했으면 새 컬럼은 default 값으로 들어가므로 그대로 두고, role 만 SUPER_ADMIN 으로 변경.

- [ ] **Step 3: ROADMAP 배포 체크리스트 추가**

`ROADMAP.md` 의 배포 체크리스트 7번 다음에 8번으로 추가:

```markdown
8. Phase II Feature 1 (회원 관리) 배포 시 다음 SQL 수동 실행
   ```sql
   ALTER TABLE users
     ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
     ADD COLUMN job_title VARCHAR(100),
     ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

   -- 운영자 한 명을 SUPER_ADMIN 으로 승급 (이메일은 환경에 맞게)
   UPDATE users SET role='SUPER_ADMIN' WHERE email='<운영자 이메일>';
   ```
```

- [ ] **Step 4: 커밋**

```bash
git -C "<worktree-abspath>" add backend/src/main/resources/seed-lms.sql ROADMAP.md
git -C "<worktree-abspath>" commit -m "docs(deploy): 회원 관리 마이그레이션 + SUPER_ADMIN 시드"
```

---

## Task 15: 프런트 `UserResponse` 타입 확장 + AuthContext 강제 리다이렉트

**Files:**
- Modify: `frontend/src/lib/types.ts` (혹은 `UserResponse` 가 정의된 곳)
- Modify: `frontend/src/contexts/AuthContext.tsx`

- [ ] **Step 1: 타입 확장**

`frontend/src/lib/types.ts` 또는 동등 파일의 `UserResponse` 에 다음 필드 추가:

```ts
export interface UserResponse {
  id: number;
  email: string;
  name: string;
  role: 'MENTEE' | 'MENTOR' | 'ADMIN' | 'SUPER_ADMIN';
  status: 'ACTIVE' | 'DEACTIVATED' | 'DELETED';
  jobTitle: string | null;
  mustChangePassword: boolean;
  createdAt: string;
}
```

- [ ] **Step 2: AuthContext 에 강제 리다이렉트 추가**

`frontend/src/contexts/AuthContext.tsx` 의 `AuthProvider` 본문에 다음 useEffect 추가 (`useRouter`/`usePathname` import 필요):

```tsx
import { useRouter, usePathname } from 'next/navigation';

// AuthProvider 안에:
const router = useRouter();
const pathname = usePathname();

useEffect(() => {
  if (!user) return;
  if (!user.mustChangePassword) return;
  if (pathname === '/account/change-password') return;
  if (pathname === '/auth/login') return;
  router.replace('/account/change-password');
}, [user, pathname, router]);
```

- [ ] **Step 3: 빌드 확인**

```
cd frontend && npm run build
```
Expected: Compiled successfully.

- [ ] **Step 4: 커밋**

```bash
git -C "<worktree-abspath>" add frontend/src/lib/types.ts frontend/src/contexts/AuthContext.tsx
git -C "<worktree-abspath>" commit -m "feat(auth): UserResponse 확장 + mustChangePassword 강제 리다이렉트"
```

---

## Task 16: 사이드바 SUPER_ADMIN 전용 메뉴

**Files:**
- Modify: `frontend/src/components/admin/AdminSidebar.tsx`

- [ ] **Step 1: 타입과 NAV_ITEMS 확장**

`frontend/src/components/admin/AdminSidebar.tsx` 의 import 에 `ShieldCheck` 추가, `NAV_ITEMS` 타입에 `requireSuperAdmin?: boolean` 추가, 마지막 항목으로 `/admin/admins` 추가:

```tsx
import { UserCheck, Users, CreditCard, FileText, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

const NAV_ITEMS: Array<{
  href: string;
  label: string;
  icon: typeof Users;
  match: (pathname: string) => boolean;
  requireSuperAdmin?: boolean;
}> = [
  // 기존 4개 항목 유지
  ...,
  {
    href: '/admin/admins',
    label: '관리자 계정',
    icon: ShieldCheck,
    match: (p) => p === '/admin/admins' || p.startsWith('/admin/admins/'),
    requireSuperAdmin: true,
  },
];

export default function AdminSidebar() {
  const pathname = usePathname();
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  const items = NAV_ITEMS.filter(it => !it.requireSuperAdmin || isSuperAdmin);
  // ... 기존 렌더링 로직에서 NAV_ITEMS 대신 items 사용
}
```

- [ ] **Step 2: 빌드**

```
cd frontend && npm run build
```

- [ ] **Step 3: 커밋**

```bash
git -C "<worktree-abspath>" add frontend/src/components/admin/AdminSidebar.tsx
git -C "<worktree-abspath>" commit -m "feat(admin-sidebar): SUPER_ADMIN 전용 관리자 계정 메뉴"
```

---

## Task 17: 🚧 Pencil 목업 작성 + 사용자 승인 (CONTROLLER TASK, GATE)

> **이 Task 는 subagent 가 아닌 컨트롤러(상위 Claude 세션) 가 직접 수행한다.** Pencil MCP (mcp__pencil__*) 를 사용하므로 컨트롤러의 도구 접근이 필요. 사용자 승인을 받기 전까지 Task 18 이후는 진행할 수 없다.

**Files:**
- Create: `docs/mockups/admin-users.pen` (Pencil .pen 파일)

- [ ] **Step 1: 새 .pen 문서 생성**

`mcp__pencil__open_document` 로 `new` 호출 → 새 빈 문서 생성. 파일 저장 위치는 `docs/mockups/admin-users.pen`.

- [ ] **Step 2: 5개 화면 + 4개 모달 디자인**

다음 화면을 각각 별도 아트보드로 작성 (스펙 §5.3 요건 충족):

1. `/admin/users` 목록 (역할/상태 탭, 검색, 테이블, 페이지네이션)
2. `/admin/users/[id]` 상세 (3개 카드 + 액션 영역)
3. `/admin/admins` 관리자 계정 목록 + "관리자 추가" 버튼
4. 관리자 추가 모달 (이메일·이름·회사직책)
5. `/account/change-password` 강제 비번 변경

모달 4종 (별도 아트보드):
- 회원 비활성화 모달 (사유 입력)
- 회원 삭제 모달 (사유 입력 + 비가역 경고)
- 비밀번호 리셋 결과 모달 (임시 비번 표시 + 복사)
- 멘토 교체 모달 (멘토 선택 + 사유 + 결제 안내)

- [ ] **Step 3: 스크린샷으로 사용자에게 제시**

`mcp__pencil__get_screenshot` 으로 각 아트보드 캡처 → 사용자에게 표시.

- [ ] **Step 4: 사용자 승인 받기**

사용자에게 메시지:
> "Pencil 목업 9개(5 화면 + 4 모달) 작성 완료. 검토 후 변경 요청 또는 승인 알려 주세요."

- [ ] **Step 5: 변경 요청 시 반복**

사용자가 수정 요청 시 `mcp__pencil__batch_design` 으로 수정 → 다시 스크린샷 → 재확인. 승인 받을 때까지 반복.

- [ ] **Step 6: 승인 후 .pen 파일 커밋**

```bash
git -C "<worktree-abspath>" add docs/mockups/admin-users.pen
git -C "<worktree-abspath>" commit -m "design(admin-users): Pencil 목업 5개 화면 + 4개 모달"
```

---

## Task 18: API 클라이언트 (`frontend/src/lib/admin/users.ts`)

**Files:**
- Create: `frontend/src/lib/admin/users.ts`

- [ ] **Step 1: API 함수 구현**

기존 다른 `lib/` 파일 패턴(예: `frontend/src/lib/auth.ts`) 을 참고해서 작성:

```ts
import { apiClient } from '@/lib/api-client';  // 또는 기존 axios 인스턴스
import type { UserResponse } from '@/lib/types';

export type UserRole = 'MENTEE' | 'MENTOR' | 'ADMIN' | 'SUPER_ADMIN';
export type UserStatus = 'ACTIVE' | 'DEACTIVATED' | 'DELETED';

export interface AdminUserListItem {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  status: UserStatus;
  jobTitle: string | null;
  createdAt: string;
}

export interface AdminUserDetail extends AdminUserListItem {
  provider: string | null;
  updatedAt: string;
  paymentCount: number;
  postCount: number;
  mentorProfileId: number | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;       // current page (0-indexed)
  size: number;
}

export async function listUsers(params: {
  role?: UserRole;
  status?: UserStatus;
  q?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<AdminUserListItem>> {
  const r = await apiClient.get('/api/admin/users', { params });
  return r.data.data;
}

export async function getUserDetail(id: number): Promise<AdminUserDetail> {
  const r = await apiClient.get(`/api/admin/users/${id}`);
  return r.data.data;
}

export async function deactivateUser(id: number, reason: string): Promise<void> {
  await apiClient.post(`/api/admin/users/${id}/deactivate`, { reason });
}

export async function reactivateUser(id: number): Promise<void> {
  await apiClient.post(`/api/admin/users/${id}/reactivate`);
}

export async function deleteUser(id: number, reason: string): Promise<void> {
  await apiClient.post(`/api/admin/users/${id}/delete`, { reason });
}

export async function resetUserPassword(id: number): Promise<{ temporaryPassword: string; mustChangePassword: boolean }> {
  const r = await apiClient.post(`/api/admin/users/${id}/reset-password`);
  return r.data.data;
}

export async function swapMentor(menteeId: number, newMentorId: number, reason: string): Promise<void> {
  await apiClient.post(`/api/admin/users/${menteeId}/swap-mentor`, { newMentorId, reason });
}

export async function listAdmins(): Promise<UserResponse[]> {
  const r = await apiClient.get('/api/admin/admins');
  return r.data.data;
}

export async function createAdmin(payload: { email: string; name: string; jobTitle: string }): Promise<{ user: UserResponse; temporaryPassword: string }> {
  const r = await apiClient.post('/api/admin/admins', payload);
  return r.data.data;
}
```

> `apiClient` import 경로는 기존 frontend 코드 컨벤션을 따른다. 기존 axios 사용처를 grep 으로 확인 후 동일하게.

- [ ] **Step 2: 빌드 확인 + 커밋**

```
cd frontend && npm run build
```

```bash
git -C "<worktree-abspath>" add frontend/src/lib/admin/users.ts
git -C "<worktree-abspath>" commit -m "feat(admin-users): API 클라이언트 함수"
```

---

## Task 19: `/admin/users` 목록 페이지

**Files:**
- Create: `frontend/src/app/admin/users/page.tsx`
- Create: `frontend/src/components/admin/Pagination.tsx`
- Create: `frontend/src/components/admin/DebouncedSearchInput.tsx`

> Pencil 목업 (Task 17) 승인 후에만 진행.

- [ ] **Step 1: `Pagination` 컴포넌트 인라인 작성**

`frontend/src/components/admin/Pagination.tsx`:

```tsx
'use client';

interface Props {
  page: number;        // 0-indexed
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: Props) {
  if (totalPages <= 1) return null;
  const prev = () => onPageChange(Math.max(0, page - 1));
  const next = () => onPageChange(Math.min(totalPages - 1, page + 1));
  // 단순 prev/현재/next 구조. 페이지 번호 1~N 노출은 후속 개선.
  return (
    <div className="flex items-center justify-center gap-3 py-4">
      <button onClick={prev} disabled={page === 0} className="rounded-md border px-3 py-1 text-sm disabled:opacity-50">← 이전</button>
      <span className="text-sm text-slate-700">{page + 1} / {totalPages}</span>
      <button onClick={next} disabled={page === totalPages - 1} className="rounded-md border px-3 py-1 text-sm disabled:opacity-50">다음 →</button>
    </div>
  );
}
```

- [ ] **Step 2: `DebouncedSearchInput`**

`frontend/src/components/admin/DebouncedSearchInput.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';

interface Props {
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  delay?: number;
}

export function DebouncedSearchInput({ value, onChange, placeholder, delay = 300 }: Props) {
  const [internal, setInternal] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => onChange(internal), delay);
    return () => clearTimeout(t);
  }, [internal, delay, onChange]);

  return (
    <input
      value={internal}
      onChange={(e) => setInternal(e.target.value)}
      placeholder={placeholder}
      className="rounded-md border border-slate-300 px-3 py-1.5 text-sm w-72"
    />
  );
}
```

- [ ] **Step 3: 목록 페이지 작성**

`frontend/src/app/admin/users/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import * as api from '@/lib/admin/users';
import { Pagination } from '@/components/admin/Pagination';
import { DebouncedSearchInput } from '@/components/admin/DebouncedSearchInput';

const ROLE_TABS: Array<{ value?: api.UserRole; label: string }> = [
  { value: undefined, label: '전체' },
  { value: 'MENTEE', label: '멘티' },
  { value: 'MENTOR', label: '멘토' },
  { value: 'ADMIN', label: '관리자' },
];

const STATUS_TABS: Array<{ value?: api.UserStatus; label: string }> = [
  { value: undefined, label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'DEACTIVATED', label: '비활성' },
  { value: 'DELETED', label: '삭제' },
];

export default function AdminUsersPage() {
  const sp = useSearchParams();
  const router = useRouter();
  const [role, setRole] = useState<api.UserRole | undefined>(sp.get('role') as api.UserRole ?? undefined);
  const [status, setStatus] = useState<api.UserStatus | undefined>(sp.get('status') as api.UserStatus ?? undefined);
  const [q, setQ] = useState(sp.get('q') ?? '');
  const [page, setPage] = useState(Number(sp.get('page') ?? 0));
  const [data, setData] = useState<api.PageResponse<api.AdminUserListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    api.listUsers({ role, status, q: q || undefined, page, size: 20 })
      .then(setData)
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  }, [role, status, q, page]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">회원 관리</h1>
        <p className="text-sm text-slate-600">전체 회원을 조회하고 상태·비밀번호를 관리합니다.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {ROLE_TABS.map(t => (
          <button key={t.label} onClick={() => { setRole(t.value); setPage(0); }}
            className={`rounded-md px-3 py-1.5 text-sm ${role === t.value ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}>
            {t.label}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map(t => (
          <button key={t.label} onClick={() => { setStatus(t.value); setPage(0); }}
            className={`rounded-md px-3 py-1.5 text-xs ${status === t.value ? 'bg-slate-700 text-white' : 'bg-slate-50 text-slate-600 border'}`}>
            {t.label}
          </button>
        ))}
        <div className="ml-auto">
          <DebouncedSearchInput value={q} onChange={(next) => { setQ(next); setPage(0); }} placeholder="이름·이메일 검색" />
        </div>
      </div>

      {loading && <div className="text-sm text-slate-500">불러오는 중...</div>}
      {error && <div className="text-sm text-red-600">에러: {error}</div>}

      {data && (
        <>
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b text-left text-sm text-slate-500">
                <th className="py-2">이름</th>
                <th>이메일</th>
                <th>역할</th>
                <th>상태</th>
                <th>가입일</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {data.content.map(u => (
                <tr key={u.id} className={`border-b ${u.status === 'DELETED' ? 'text-slate-400' : ''}`}>
                  <td className="py-2">{u.name}</td>
                  <td className="text-sm">{u.email}</td>
                  <td><RoleBadge role={u.role} /></td>
                  <td><StatusBadge status={u.status} /></td>
                  <td className="text-sm">{u.createdAt.slice(0, 10)}</td>
                  <td>
                    <Link href={`/admin/users/${u.id}`} className="text-sm text-blue-600 hover:underline">상세 →</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={data.number} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}

function RoleBadge({ role }: { role: api.UserRole }) {
  const map = {
    MENTEE: 'bg-emerald-100 text-emerald-800',
    MENTOR: 'bg-sky-100 text-sky-800',
    ADMIN: 'bg-violet-100 text-violet-800',
    SUPER_ADMIN: 'bg-red-100 text-red-800',
  } as const;
  return <span className={`rounded px-2 py-0.5 text-xs ${map[role]}`}>{role}</span>;
}

function StatusBadge({ status }: { status: api.UserStatus }) {
  const map = {
    ACTIVE: 'bg-green-100 text-green-800',
    DEACTIVATED: 'bg-amber-100 text-amber-800',
    DELETED: 'bg-zinc-200 text-zinc-700',
  } as const;
  return <span className={`rounded px-2 py-0.5 text-xs ${map[status]}`}>{status}</span>;
}
```

- [ ] **Step 4: 빌드 + 커밋**

```
cd frontend && npm run build
```

```bash
git -C "<worktree-abspath>" add frontend/src/components/admin/Pagination.tsx frontend/src/components/admin/DebouncedSearchInput.tsx frontend/src/app/admin/users/page.tsx
git -C "<worktree-abspath>" commit -m "feat(admin-users): /admin/users 목록 페이지"
```

---

## Task 20: `/admin/users/[id]` 상세 + 액션 모달

**Files:**
- Create: `frontend/src/app/admin/users/[id]/page.tsx`
- Create: `frontend/src/app/admin/users/[id]/_actions.tsx` (모달들 모음)

- [ ] **Step 1: 상세 페이지 작성**

`frontend/src/app/admin/users/[id]/page.tsx` — 카드 3개(기본 정보 / 활동 요약 / 액션) 와 각 액션 모달 호출 버튼. Pencil 목업 §3.1 의 요건을 그대로 구현.

> 구현자 주의: 다음 액션을 모두 포함:
> - 비활성화 (사유 모달 → `deactivateUser`)
> - 재활성화 (확인 모달 → `reactivateUser`)
> - 영구 삭제 (사유 모달 + 경고 → `deleteUser`)
> - 비밀번호 리셋 (확인 → `resetUserPassword` → 결과 모달에 평문 표시)
> - 멘토 교체 (멘티이고 mentorProfileId 가 없는 경우만 활성화) → 모달은 Task 21
>
> 대상 user.role 이 ADMIN 이면 비활성화/삭제 버튼 비활성. SUPER_ADMIN 이면 비번 리셋도 비활성.
> 대상 user.status 가 DELETED 면 모든 액션 비활성.

코드 분량이 많아 한 단위 Task 로 분리. **이 Task 의 핵심: 페이지 골격 + 4개 액션(멘토 교체 제외) 동작.**

대표 코드 골격:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import * as api from '@/lib/admin/users';

export default function AdminUserDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const id = Number(params.id);
  const [user, setUser] = useState<api.AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeAction, setActiveAction] = useState<null | 'deactivate' | 'reactivate' | 'delete' | 'reset' | 'swap'>(null);

  const reload = () => {
    setLoading(true);
    api.getUserDetail(id).then(setUser).finally(() => setLoading(false));
  };
  useEffect(reload, [id]);

  if (loading) return <div>불러오는 중...</div>;
  if (!user) return <div>회원을 찾을 수 없습니다.</div>;

  const isAdminTarget = user.role === 'ADMIN' || user.role === 'SUPER_ADMIN';
  const isSuperAdminTarget = user.role === 'SUPER_ADMIN';
  const isDeleted = user.status === 'DELETED';

  return (
    <div className="space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{user.name}</h1>
          <p className="text-sm text-slate-600">{user.email} · {user.role} · {user.status}</p>
        </div>
        <button onClick={() => router.back()} className="text-sm text-slate-600">← 목록</button>
      </header>

      {/* 기본 정보 카드 */}
      <section className="rounded-lg border p-4">
        <h2 className="mb-2 font-semibold">기본 정보</h2>
        <dl className="grid grid-cols-[120px_1fr] gap-y-1 text-sm">
          <dt className="text-slate-500">이메일</dt><dd>{user.email}</dd>
          <dt className="text-slate-500">가입 경로</dt><dd>{user.provider ?? 'email'}</dd>
          <dt className="text-slate-500">가입일</dt><dd>{user.createdAt}</dd>
          <dt className="text-slate-500">수정일</dt><dd>{user.updatedAt}</dd>
          {user.jobTitle && (<><dt className="text-slate-500">회사직책</dt><dd>{user.jobTitle}</dd></>)}
        </dl>
      </section>

      {/* 활동 요약 */}
      <section className="rounded-lg border p-4">
        <h2 className="mb-2 font-semibold">연관 활동</h2>
        <ul className="text-sm space-y-1">
          <li>결제 {user.paymentCount}건</li>
          <li>게시글 {user.postCount}건</li>
          {user.mentorProfileId && <li>멘토 프로필 #{user.mentorProfileId}</li>}
        </ul>
      </section>

      {/* 액션 영역 */}
      <section className="rounded-lg border p-4 space-y-2">
        <h2 className="font-semibold">관리자 액션</h2>
        <div className="flex flex-wrap gap-2">
          {!isDeleted && user.status === 'ACTIVE' && !isAdminTarget && (
            <button onClick={() => setActiveAction('deactivate')} className="rounded-md bg-amber-600 text-white px-3 py-1.5 text-sm">비활성화</button>
          )}
          {!isDeleted && user.status === 'DEACTIVATED' && !isAdminTarget && (
            <button onClick={() => setActiveAction('reactivate')} className="rounded-md bg-emerald-600 text-white px-3 py-1.5 text-sm">재활성화</button>
          )}
          {!isDeleted && !isAdminTarget && (
            <button onClick={() => setActiveAction('delete')} className="rounded-md bg-red-600 text-white px-3 py-1.5 text-sm">영구 삭제</button>
          )}
          {!isDeleted && !isSuperAdminTarget && (
            <button onClick={() => setActiveAction('reset')} className="rounded-md bg-slate-700 text-white px-3 py-1.5 text-sm">비밀번호 리셋</button>
          )}
          {!isDeleted && user.role === 'MENTEE' && (
            <button onClick={() => setActiveAction('swap')} className="rounded-md bg-blue-600 text-white px-3 py-1.5 text-sm">멘토 교체</button>
          )}
        </div>
        {isDeleted && <p className="text-sm text-slate-500">이미 삭제된 계정입니다.</p>}
      </section>

      {/* 모달 분기 — 다음 Step 에서 별도 컴포넌트 import */}
      {activeAction === 'deactivate' && (
        <DeactivateModal userId={user.id} onClose={() => setActiveAction(null)} onSuccess={reload} />
      )}
      {/* ... 나머지 모달 */}
    </div>
  );
}
```

> **각 모달의 코드는 분량이 크므로 별도 Task 21 로 분리 (멘토 교체 모달 포함).** 이 Task 에서는 골격까지만, 모달 컴포넌트는 stub 으로 두고 다음 Task 에서 채운다.

- [ ] **Step 2: 빌드 + 커밋**

```
cd frontend && npm run build
```

```bash
git -C "<worktree-abspath>" add frontend/src/app/admin/users/[id]/
git -C "<worktree-abspath>" commit -m "feat(admin-users): /admin/users/[id] 상세 페이지 + 액션 분기"
```

---

## Task 21: 액션 모달 4종 구현 (비활성화 / 삭제 / 비번 리셋 / 멘토 교체)

**Files:**
- Create: `frontend/src/app/admin/users/[id]/_DeactivateModal.tsx`
- Create: `frontend/src/app/admin/users/[id]/_DeleteModal.tsx`
- Create: `frontend/src/app/admin/users/[id]/_ResetPasswordModal.tsx`
- Create: `frontend/src/app/admin/users/[id]/_SwapMentorModal.tsx`
- Modify: `frontend/src/app/admin/users/[id]/page.tsx` (모달 import + 사용)

> Pencil 목업의 모달 디자인을 그대로 구현. 모든 모달은 `<dialog>` 또는 단순 fixed overlay 로.

- [ ] **Step 1: `_DeactivateModal.tsx` (사유 입력)**

```tsx
'use client';

import { useState } from 'react';
import * as api from '@/lib/admin/users';

interface Props { userId: number; onClose: () => void; onSuccess: () => void; }

export function DeactivateModal({ userId, onClose, onSuccess }: Props) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const valid = reason.length >= 10 && reason.length <= 500;

  const submit = async () => {
    if (!valid) return;
    setSubmitting(true);
    setErr(null);
    try {
      await api.deactivateUser(userId, reason);
      onSuccess(); onClose();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? String(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Overlay onClose={onClose}>
      <h3 className="text-lg font-semibold">회원 비활성화</h3>
      <p className="text-sm text-slate-600">이 회원은 로그인할 수 없게 됩니다. 데이터는 보존됩니다.</p>
      <label className="block">
        <span className="text-sm">사유 (10~500자)</span>
        <textarea value={reason} onChange={e => setReason(e.target.value)} rows={4} className="w-full rounded-md border p-2 text-sm" />
        <span className="text-xs text-slate-500">{reason.length} / 500</span>
      </label>
      {err && <p className="text-sm text-red-600">{err}</p>}
      <div className="flex justify-end gap-2 pt-2">
        <button onClick={onClose} className="px-3 py-1.5 text-sm">취소</button>
        <button disabled={!valid || submitting} onClick={submit} className="rounded-md bg-amber-600 text-white px-3 py-1.5 text-sm disabled:opacity-50">비활성화 확정</button>
      </div>
    </Overlay>
  );
}

function Overlay({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-lg p-6 w-[480px] space-y-3" onClick={e => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: `_DeleteModal.tsx` (사유 + 비가역 경고)**

`_DeleteModal.tsx` — 위와 동일한 구조, 색상 red, 추가 경고 문구. API: `api.deleteUser(userId, reason)`.

- [ ] **Step 3: `_ResetPasswordModal.tsx` (확인 → 결과 모달)**

```tsx
'use client';

import { useState } from 'react';
import * as api from '@/lib/admin/users';

export function ResetPasswordModal({ userId, onClose, onSuccess }: { userId: number; onClose: () => void; onSuccess: () => void }) {
  const [step, setStep] = useState<'confirm' | 'result'>('confirm');
  const [tempPwd, setTempPwd] = useState<string>('');
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    try {
      const r = await api.resetUserPassword(userId);
      setTempPwd(r.temporaryPassword);
      setStep('result');
      onSuccess();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? String(e));
    }
  };

  if (step === 'confirm') {
    return (
      <Overlay onClose={onClose}>
        <h3 className="text-lg font-semibold">비밀번호 리셋</h3>
        <p className="text-sm">임시 비밀번호가 발급되며, 사용자는 다음 로그인 시 비번을 변경하게 됩니다.</p>
        {err && <p className="text-red-600 text-sm">{err}</p>}
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm">취소</button>
          <button onClick={submit} className="rounded-md bg-slate-700 text-white px-3 py-1.5 text-sm">발급</button>
        </div>
      </Overlay>
    );
  }

  return (
    <Overlay onClose={onClose}>
      <h3 className="text-lg font-semibold">임시 비밀번호 발급 완료</h3>
      <div className="rounded-md bg-slate-100 p-3 font-mono text-lg flex items-center justify-between">
        <span>{tempPwd}</span>
        <button onClick={() => navigator.clipboard.writeText(tempPwd)} className="text-sm text-blue-600">복사</button>
      </div>
      <p className="text-xs text-amber-700">이 화면을 닫으면 다시 볼 수 없습니다. 사용자에게 안전하게 전달하세요.</p>
      <div className="flex justify-end">
        <button onClick={onClose} className="rounded-md bg-slate-900 text-white px-3 py-1.5 text-sm">닫기</button>
      </div>
    </Overlay>
  );
}
```

(`Overlay` 헬퍼는 별도 파일로 빼서 import 또는 각 파일에 복사.)

- [ ] **Step 4: `_SwapMentorModal.tsx`**

```tsx
'use client';

import { useEffect, useState } from 'react';
import * as api from '@/lib/admin/users';
// 멘토 목록을 가져오는 API. 기존 /api/mentors?status=APPROVED 같은 엔드포인트가 있으면 활용.

interface MentorOption { userId: number; name: string; courses: string[]; }

export function SwapMentorModal({ menteeId, onClose, onSuccess }: { menteeId: number; onClose: () => void; onSuccess: () => void }) {
  const [mentors, setMentors] = useState<MentorOption[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    // TODO: 기존 멘토 목록 API 호출 (예: /api/mentors?status=APPROVED)
    // 응답을 MentorOption[] 으로 변환해서 setMentors
  }, []);

  const valid = selected !== null && reason.length >= 10 && reason.length <= 500;

  const submit = async () => {
    if (!valid || selected === null) return;
    try {
      await api.swapMentor(menteeId, selected, reason);
      onSuccess(); onClose();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? String(e));
    }
  };

  return (
    <Overlay onClose={onClose}>
      <h3 className="text-lg font-semibold">멘토 교체</h3>
      <p className="text-xs text-amber-700">기존 매칭은 취소되며 신규 매칭이 생성됩니다. 결제 환불은 결제 관리에서 별도 처리해야 합니다.</p>
      <label className="block">
        <span className="text-sm">새 멘토</span>
        <select value={selected ?? ''} onChange={e => setSelected(Number(e.target.value))} className="w-full rounded-md border p-2 text-sm">
          <option value="">선택</option>
          {mentors.map(m => (
            <option key={m.userId} value={m.userId}>{m.name}</option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="text-sm">사유 (10~500자)</span>
        <textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} className="w-full rounded-md border p-2 text-sm" />
      </label>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <div className="flex justify-end gap-2">
        <button onClick={onClose} className="px-3 py-1.5 text-sm">취소</button>
        <button disabled={!valid} onClick={submit} className="rounded-md bg-blue-600 text-white px-3 py-1.5 text-sm disabled:opacity-50">교체 확정</button>
      </div>
    </Overlay>
  );
}
```

> 멘토 목록 조회 API 가 없다면 `/api/mentors?status=APPROVED` 형태의 신규 엔드포인트가 필요할 수 있다. 기존 코드 확인 후 결정. 없으면 `/api/admin/users?role=MENTOR&status=ACTIVE` 로 대체 (멘토 status APPROVED 보장은 백엔드가 swap 시 검증하므로 UI 는 후보만 보여주면 됨).

- [ ] **Step 5: `page.tsx` 에 모달 import + 사용**

`/admin/users/[id]/page.tsx` 의 모달 분기 부분에 import 후 4개 모달 모두 연결.

- [ ] **Step 6: 빌드 + 커밋**

```
cd frontend && npm run build
```

```bash
git -C "<worktree-abspath>" add frontend/src/app/admin/users/[id]/
git -C "<worktree-abspath>" commit -m "feat(admin-users): 액션 모달 4종 (비활성화/삭제/비번리셋/멘토교체)"
```

---

## Task 22: `/admin/admins` 페이지 + 관리자 추가 모달

**Files:**
- Create: `frontend/src/app/admin/admins/page.tsx`
- Create: `frontend/src/app/admin/admins/_CreateAdminModal.tsx`

- [ ] **Step 1: 페이지 + 모달 구현**

`page.tsx` 에 SUPER_ADMIN 가드 (useAuth 의 role 확인 → 그 외 403 메시지), 관리자 목록 테이블, "관리자 추가" 버튼.

`_CreateAdminModal.tsx`: 이메일/이름/회사직책 폼 → `api.createAdmin()` → 성공 시 임시 비번 결과 모달 (Task 21 의 `_ResetPasswordModal` 결과 단계와 같은 UX).

코드 골격 (페이지):

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import * as api from '@/lib/admin/users';

export default function AdminsPage() {
  const { user } = useAuth();
  const [admins, setAdmins] = useState<any[]>([]);
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => { api.listAdmins().then(setAdmins); }, []);

  if (user?.role !== 'SUPER_ADMIN') {
    return <div className="text-center py-10">SUPER_ADMIN 권한이 필요합니다.</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold">관리자 계정</h1>
        <button onClick={() => setShowCreate(true)} className="rounded-md bg-slate-900 text-white px-3 py-1.5 text-sm">+ 관리자 추가</button>
      </div>
      <table className="w-full">
        <thead>
          <tr className="border-b text-left text-sm">
            <th className="py-2">이름</th><th>이메일</th><th>회사직책</th><th>역할</th><th>상태</th>
          </tr>
        </thead>
        <tbody>
          {admins.map(a => (
            <tr key={a.id} className="border-b">
              <td className="py-2">{a.name}</td>
              <td>{a.email}</td>
              <td>{a.jobTitle ?? '-'}</td>
              <td>{a.role}</td>
              <td>{a.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {showCreate && <CreateAdminModal onClose={() => setShowCreate(false)} onSuccess={() => api.listAdmins().then(setAdmins)} />}
    </div>
  );
}
```

- [ ] **Step 2: 빌드 + 커밋**

```
cd frontend && npm run build
```

```bash
git -C "<worktree-abspath>" add frontend/src/app/admin/admins/
git -C "<worktree-abspath>" commit -m "feat(admin-users): /admin/admins SUPER_ADMIN 전용 관리자 계정 페이지"
```

---

## Task 23: `/account/change-password` 페이지

**Files:**
- Create: `frontend/src/app/account/change-password/page.tsx`
- Create or Modify: `frontend/src/lib/auth.ts` — `changePassword(currentPwd, newPwd)` 추가

- [ ] **Step 1: 클라이언트 API 함수 추가**

`frontend/src/lib/auth.ts` 에 추가:

```ts
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.post('/api/auth/change-password', { currentPassword, newPassword });
}
```

- [ ] **Step 2: 페이지 구현**

`frontend/src/app/account/change-password/page.tsx`:

```tsx
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { changePassword } from '@/lib/auth';

export default function ChangePasswordPage() {
  const { user, refreshUser } = useAuth();
  const router = useRouter();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const valid = current.length > 0 && next.length >= 8 && next === confirm;

  const submit = async () => {
    if (!valid) return;
    setSubmitting(true);
    setErr(null);
    try {
      await changePassword(current, next);
      await refreshUser();
      router.push('/mypage');
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? String(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto py-12 space-y-4">
      <h1 className="text-xl font-bold">비밀번호 변경</h1>
      {user?.mustChangePassword && (
        <p className="text-sm text-amber-700">관리자가 발급한 임시 비밀번호로 로그인하셨습니다. 새 비밀번호로 변경해 주세요.</p>
      )}
      <label className="block">
        <span className="text-sm">현재 비밀번호</span>
        <input type="password" value={current} onChange={e => setCurrent(e.target.value)} className="w-full rounded-md border p-2" />
      </label>
      <label className="block">
        <span className="text-sm">새 비밀번호 (최소 8자)</span>
        <input type="password" value={next} onChange={e => setNext(e.target.value)} className="w-full rounded-md border p-2" />
      </label>
      <label className="block">
        <span className="text-sm">새 비밀번호 확인</span>
        <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)} className="w-full rounded-md border p-2" />
      </label>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <button onClick={submit} disabled={!valid || submitting} className="w-full rounded-md bg-slate-900 text-white py-2 disabled:opacity-50">변경</button>
    </div>
  );
}
```

- [ ] **Step 3: 빌드 + 커밋**

```
cd frontend && npm run build
```

```bash
git -C "<worktree-abspath>" add frontend/src/lib/auth.ts frontend/src/app/account/change-password/
git -C "<worktree-abspath>" commit -m "feat(auth): /account/change-password 페이지"
```

---

## Task 24: 최종 회귀 검증 + PR

**Files:** (없음, 검증만)

- [ ] **Step 1: 백엔드 전체 테스트**

```
cd backend && ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 프런트 빌드**

```
cd frontend && npm run build
```
Expected: Compiled successfully.

- [ ] **Step 3: 수동 통합 스모크**

1. 백엔드/프런트 기동, SUPER_ADMIN 계정 시드 확인
2. `/admin/users` 진입 → 회원 표시
3. MENTEE 한 명 비활성화 → 그 계정 로그인 시도 → 401 차단 확인
4. 재활성화 → 로그인 성공
5. 비밀번호 리셋 → 임시 비번 모달 표시 → 그 비번으로 로그인 → `/account/change-password` 강제 리다이렉트 확인
6. 새 비번 변경 → `/mypage` 정상 진입
7. SUPER_ADMIN 으로 `/admin/admins` 접근 → 관리자 추가 → 새 계정 로그인 → 강제 비번 변경 흐름 확인
8. ADMIN(non-super) 으로 `/admin/admins` 접근 시도 → 403
9. 멘티 상세에서 멘토 교체 → DB 의 `matchings` 에서 old=CANCELLED, new=CONFIRMED 확인
10. 모든 액션 후 `admin_audit_log` 테이블에 row 누적 확인

문제 발생 시 [CLAUDE.md](../../CLAUDE.md) 규칙에 따라 `error/` 에 트러블슈팅 문서 작성.

- [ ] **Step 4: 커밋 로그 점검**

```bash
git -C "<worktree-abspath>" log --oneline main..HEAD
```

- [ ] **Step 5: PR 생성**

`gh pr create --title "feat(admin): Phase II Feature 1 — 회원 관리" --body "..."` (본문은 Common PR 와 동일한 형식: Summary / Spec / Test plan / 배포 체크리스트 참조).

---

## Plan Self-Review Notes

### Spec coverage 매핑

- ✅ Spec §3 결정 7건 → Task 1, 9, 10, 11, 12 등에 구현
- ✅ Spec §4.1 엔티티 → Task 1
- ✅ Spec §4.2 Spring Security → Task 6
- ✅ Spec §4.3 엔드포인트 9개 → Task 8, 9, 10, 11, 12, 13
- ✅ Spec §4.4 가드 정책 8건 → Task 9, 10, 11 의 guard 메서드들
- ✅ Spec §4.5 상태 전파 → Task 5 (AuthService) + Task 4 (displayName) + Task 13 (UserResponse 확장)
- ✅ Spec §4.6 멘토 교체 → Task 11
- ✅ Spec §4.7 비밀번호 리셋 → Task 10 + Task 13 (change-password 엔드포인트)
- ✅ Spec §4.8 관리자 생성 → Task 12
- ✅ Spec §4.9 enum 확장 + Common 스펙 갱신 → Task 2
- ✅ Spec §5.1 라우팅 → Task 19, 20, 22, 23
- ✅ Spec §5.2 사이드바 SUPER_ADMIN → Task 16
- ✅ Spec §5.3 화면 요건 → Task 17 (Pencil 게이트) → Task 19~22 (구현)
- ✅ Spec §5.4 useAuth → Task 15
- ✅ Spec §5.5 displayName → Task 4 (BE) + (FE 는 응답이 이미 마스킹되어 fallback 만 필요, 별도 Task 없음 — 응답 신뢰)
- ✅ Spec §6 마이그레이션 → Task 14
- ✅ Spec §7 테스트 → 각 서비스 Task 의 TDD + Task 24 수동 스모크

### 미커버 항목

- 🔸 Spec §5.6 의 "Phase II Common 의 첫 feature 로서 lazy 추출 시점" — Task 19 가 인라인 구현. 추출은 다음 feature(결제 관리) 스펙 시점.
- 🔸 Spec §8 리스크 의 "전 쿼리 회귀 점검" — Task 24 Step 3 수동 스모크에 포함되나, 자동화는 부족. 후속 통합 테스트로 보강 가능.

### Pencil 게이트 위치 확인

- Task 17 = 백엔드 완성 + 프런트 사전 작업(useAuth, 사이드바) 후, 본격적 admin 화면 구현 직전
- Task 18 (API 클라이언트) 는 Pencil 승인 없이 진행 가능 (UI 없음). 단 본 플랜에서는 안전을 위해 Pencil 승인 후 구현 권장. 분리 가능.

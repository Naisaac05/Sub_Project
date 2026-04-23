# 관리자 콘솔 Phase II — Common Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase II 관리자 콘솔 3개 기능이 공유할 감사 로그 인프라(`AdminAuditLog`) 와 사이드바 확장을 도입하고, Phase I 멘토 승인/반려 플로우에 감사 로그를 소급 적용한다.

**Architecture:** 단일 `AdminAuditLog` 테이블에 모든 관리자 행위를 기록. 호출은 각 도메인 서비스의 `@Transactional` 안에서 `AdminAuditLogService.record(...)` 직접 호출 (Phase I의 `MentorProfileHistory` 는 유지하고 이중 기록). 프런트 사이드바는 기존 설정 배열에 3개 메뉴 항목만 추가.

**Tech Stack:** Spring Boot 3 (JPA + Hibernate ddl-auto), JUnit 5 + Mockito, Next.js 14 (App Router), lucide-react.

**Spec:** `docs/superpowers/specs/2026-04-22-admin-console-common-design.md`

---

## File Structure

### 신규 생성 (6 파일)

| 경로 | 책임 |
|------|------|
| `backend/src/main/java/com/devmatch/entity/AdminActionType.java` | 관리자 행위 유형 enum (6개 값) |
| `backend/src/main/java/com/devmatch/entity/AdminAuditLog.java` | 감사 로그 엔티티 (스펙 §4.1) |
| `backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java` | JpaRepository 표준 |
| `backend/src/main/java/com/devmatch/service/AdminAuditLogService.java` | `record()` 단일 public 메서드 (스펙 §4.3) |
| `backend/src/test/java/com/devmatch/service/AdminAuditLogServiceTest.java` | `record()` 단위 테스트 (4개 시나리오) |
| — | (프런트는 수정만) |

### 수정 (3 파일)

| 경로 | 내용 |
|------|------|
| `backend/src/main/java/com/devmatch/service/MentorService.java` | `AdminAuditLogService` 주입 + `approve/reject`에 `record()` 호출 추가 |
| `backend/src/test/java/com/devmatch/service/MentorServiceTest.java` | `@Mock AdminAuditLogService` 추가 + approve/reject 테스트 신규 작성(기존에 없음) |
| `frontend/src/components/admin/AdminSidebar.tsx` | `NAV_ITEMS` 배열에 회원/결제/게시물 3개 항목 추가 |

---

## Task 1: `AdminActionType` enum 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/AdminActionType.java`

- [ ] **Step 1: enum 파일 작성**

`backend/src/main/java/com/devmatch/entity/AdminActionType.java`:

```java
package com.devmatch.entity;

/**
 * 관리자 행위 유형. AdminAuditLog.actionType 에서 사용한다.
 * Phase II 에서 실제 기록되는 값: USER_ROLE_CHANGE, PAYMENT_REFUND, POST_DELETE,
 * COMMENT_DELETE, MENTOR_APPROVE, MENTOR_REJECT.
 *
 * 새 값 추가 시 반드시 스펙 문서
 * (docs/superpowers/specs/2026-04-22-admin-console-common-design.md) 업데이트.
 */
public enum AdminActionType {
    USER_ROLE_CHANGE,
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,
    MENTOR_APPROVE,
    MENTOR_REJECT
}
```

- [ ] **Step 2: 컴파일 확인**

Run (Windows PowerShell from repo root):
```
cd backend; .\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`. 에러 시 package 경로·파일명 재확인.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/AdminActionType.java
git commit -m "feat(admin-audit): AdminActionType enum 추가"
```

---

## Task 2: `AdminAuditLog` 엔티티 + Repository 생성

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/AdminAuditLog.java`
- Create: `backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java`

- [ ] **Step 1: 엔티티 파일 작성**

`backend/src/main/java/com/devmatch/entity/AdminAuditLog.java`:

```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_audit_admin_created_at", columnList = "admin_id, created_at"),
        @Index(name = "idx_audit_target", columnList = "target_type, target_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private AdminActionType actionType;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Repository 파일 작성**

`backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java`:

```java
package com.devmatch.repository;

import com.devmatch.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    // Phase II 에서는 조회 메서드 불필요. Phase III 대시보드에서 추가 예정.
}
```

- [ ] **Step 3: 애플리케이션 부팅 확인 (스키마 자동 생성)**

Run:
```
cd backend; .\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`. ddl-auto: update 덕에 실제 DB 테이블은 앱 부팅 시 자동 생성됨 (이 Task 에선 컴파일만 검증).

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/AdminAuditLog.java backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java
git commit -m "feat(admin-audit): AdminAuditLog 엔티티 + Repository 추가"
```

---

## Task 3: `AdminAuditLogService` TDD 구현

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/AdminAuditLogServiceTest.java`
- Create: `backend/src/main/java/com/devmatch/service/AdminAuditLogService.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/devmatch/service/AdminAuditLogServiceTest.java`:

```java
package com.devmatch.service;

import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.AdminAuditLog;
import com.devmatch.repository.AdminAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock private AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdminAuditLogService service;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        service = new AdminAuditLogService(repository, objectMapper);
    }

    @Test
    void record_정상_입력시_엔티티가_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(1L, AdminActionType.USER_ROLE_CHANGE,
                "USER", 42L, null, Map.of("from", "MENTEE", "to", "ADMIN"));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        AdminAuditLog saved = captor.getValue();

        assertThat(saved.getAdminId()).isEqualTo(1L);
        assertThat(saved.getActionType()).isEqualTo(AdminActionType.USER_ROLE_CHANGE);
        assertThat(saved.getTargetType()).isEqualTo("USER");
        assertThat(saved.getTargetId()).isEqualTo(42L);
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getMetadata()).contains("\"from\":\"MENTEE\"");
        assertThat(saved.getMetadata()).contains("\"to\":\"ADMIN\"");
    }

    @Test
    void record_metadata가_null이면_metadata_컬럼도_null로_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(1L, AdminActionType.MENTOR_APPROVE,
                "MENTOR_PROFILE", 10L, null, null);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }

    @Test
    void record_metadata가_빈_맵이면_null로_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(1L, AdminActionType.POST_DELETE,
                "POST", 5L, "부적절한 내용", Map.of());

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }

    @Test
    void record_reason이_있으면_그대로_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(2L, AdminActionType.PAYMENT_REFUND,
                "PAYMENT", 100L, "결제 중복 환불 요청", null);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("결제 중복 환불 요청");
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run:
```
cd backend; .\gradlew.bat test --tests "com.devmatch.service.AdminAuditLogServiceTest"
```
Expected: `AdminAuditLogService` 타입 미해결로 컴파일 실패 (`cannot find symbol`).

- [ ] **Step 3: 서비스 최소 구현**

`backend/src/main/java/com/devmatch/service/AdminAuditLogService.java`:

```java
package com.devmatch.service;

import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.AdminAuditLog;
import com.devmatch.repository.AdminAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 관리자 행위 감사 로그를 기록하는 서비스.
 *
 * <p>호출 계약 (스펙 §4.4):
 * <ul>
 *   <li>호출은 도메인 서비스 메서드의 {@code @Transactional} 내부에서만</li>
 *   <li>도메인 변경 직후 호출</li>
 *   <li>{@code metadata} 값 타입은 String/Number/Boolean/Enum.name() 4종만</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public void record(Long adminId, AdminActionType actionType,
                       String targetType, Long targetId,
                       String reason, Map<String, Object> metadata) {
        repository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(serialize(metadata))
                .build());
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("감사 로그 metadata 직렬화 실패", e);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run:
```
cd backend; .\gradlew.bat test --tests "com.devmatch.service.AdminAuditLogServiceTest"
```
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/AdminAuditLogService.java backend/src/test/java/com/devmatch/service/AdminAuditLogServiceTest.java
git commit -m "feat(admin-audit): AdminAuditLogService 구현 + 단위 테스트"
```

---

## Task 4: `MentorService` 에 감사 로그 호출 소급 적용 (TDD)

**Files:**
- Modify: `backend/src/test/java/com/devmatch/service/MentorServiceTest.java`
- Modify: `backend/src/main/java/com/devmatch/service/MentorService.java`

> 기존 `MentorServiceTest` 에는 `approve/reject` 테스트가 **없다**. 이 Task 에서 감사 로그 검증과 함께 테스트를 신규 추가한다.

- [ ] **Step 1: 실패하는 테스트 추가**

`backend/src/test/java/com/devmatch/service/MentorServiceTest.java` 의 기존 `@Mock` 블록 마지막에 다음을 **추가**한다:

```java
@Mock private com.devmatch.service.AdminAuditLogService adminAuditLogService;
```

파일 끝(마지막 `}` 바로 앞)에 다음 테스트들을 **추가**한다:

```java
    @Test
    void approve_성공시_멘토프로필_승인되고_감사로그가_기록된다() {
        User user = User.builder().id(5L).email("m@test").name("멘토").build();
        MentorProfile profile = MentorProfile.builder()
                .user(user).status(MentorStatus.PENDING).courses(new HashSet<>()).build();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 42L);

        when(mentorProfileRepository.findById(42L)).thenReturn(Optional.of(profile));
        when(historyRepository.findTopByUserIdOrderBySubmittedAtDesc(5L))
                .thenReturn(Optional.empty());

        mentorService.approve(42L, 1L);

        assertThat(profile.getStatus()).isEqualTo(MentorStatus.APPROVED);
        verify(adminAuditLogService).record(
                eq(1L),
                eq(com.devmatch.entity.AdminActionType.MENTOR_APPROVE),
                eq("MENTOR_PROFILE"),
                eq(42L),
                isNull(),
                isNull());
    }

    @Test
    void reject_성공시_멘토프로필_반려되고_사유와함께_감사로그가_기록된다() {
        User user = User.builder().id(5L).email("m@test").name("멘토").build();
        MentorProfile profile = MentorProfile.builder()
                .user(user).status(MentorStatus.PENDING).courses(new HashSet<>()).build();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 42L);

        when(mentorProfileRepository.findById(42L)).thenReturn(Optional.of(profile));
        when(historyRepository.findTopByUserIdOrderBySubmittedAtDesc(5L))
                .thenReturn(Optional.empty());

        mentorService.reject(42L, 1L, "경력 증빙 부족");

        assertThat(profile.getStatus()).isEqualTo(MentorStatus.REJECTED);
        verify(adminAuditLogService).record(
                eq(1L),
                eq(com.devmatch.entity.AdminActionType.MENTOR_REJECT),
                eq("MENTOR_PROFILE"),
                eq(42L),
                eq("경력 증빙 부족"),
                isNull());
    }

    @Test
    void approve_이미_승인된_프로필은_예외를_던지고_감사로그는_기록되지_않는다() {
        MentorProfile profile = MentorProfile.builder()
                .status(MentorStatus.APPROVED).build();
        when(mentorProfileRepository.findById(42L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> mentorService.approve(42L, 1L))
                .isInstanceOf(com.devmatch.exception.InvalidMentorReviewStateException.class);

        verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }
```

import 블록에 누락된 타입이 있으면 추가한다 (`static org.mockito.ArgumentMatchers.eq`, `isNull`, `never`).

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run:
```
cd backend; .\gradlew.bat test --tests "com.devmatch.service.MentorServiceTest"
```
Expected: 새 테스트 3개 실패 — `adminAuditLogService.record(...)` 호출이 없어서 `Wanted but not invoked`.

- [ ] **Step 3: `MentorService` 에 `AdminAuditLogService` 주입 추가**

`backend/src/main/java/com/devmatch/service/MentorService.java` 의 필드 선언부(기존 `historyRepository` 선언 근처)에 다음 필드 **추가**:

```java
    private final AdminAuditLogService adminAuditLogService;
```

파일 상단 import 에 **추가**:

```java
import com.devmatch.entity.AdminActionType;
```

- [ ] **Step 4: `approve()` 에 감사 로그 호출 추가**

`backend/src/main/java/com/devmatch/service/MentorService.java:158-173` 의 `approve` 메서드 본문 마지막 `return` 직전에 다음 한 줄 **추가**:

변경 후 전체 메서드 모습:
```java
    @Transactional
    public MentorProfileResponse approve(Long profileId, Long adminUserId) {
        MentorProfile profile = mentorProfileRepository.findById(profileId)
                .orElseThrow(() -> new MentorProfileNotFoundException("멘토 프로필을 찾을 수 없습니다"));

        if (profile.getStatus() != MentorStatus.PENDING) {
            throw new InvalidMentorReviewStateException("이미 심사가 완료된 신청입니다");
        }

        profile.markApproved();

        historyRepository.findTopByUserIdOrderBySubmittedAtDesc(profile.getUser().getId())
                .ifPresent(history -> history.markApproved(adminUserId));

        adminAuditLogService.record(
                adminUserId,
                AdminActionType.MENTOR_APPROVE,
                "MENTOR_PROFILE",
                profileId,
                null,
                null);

        return MentorProfileResponse.from(profile, null);
    }
```

- [ ] **Step 5: `reject()` 에 감사 로그 호출 추가**

`backend/src/main/java/com/devmatch/service/MentorService.java:175-190` 의 `reject` 메서드 본문 마지막 `return` 직전에 다음을 **추가**:

변경 후 전체 메서드 모습:
```java
    @Transactional
    public MentorProfileResponse reject(Long profileId, Long adminUserId, String reason) {
        MentorProfile profile = mentorProfileRepository.findById(profileId)
                .orElseThrow(() -> new MentorProfileNotFoundException("멘토 프로필을 찾을 수 없습니다"));

        if (profile.getStatus() != MentorStatus.PENDING) {
            throw new InvalidMentorReviewStateException("이미 심사가 완료된 신청입니다");
        }

        profile.markRejected();

        historyRepository.findTopByUserIdOrderBySubmittedAtDesc(profile.getUser().getId())
                .ifPresent(history -> history.markRejected(adminUserId, reason));

        adminAuditLogService.record(
                adminUserId,
                AdminActionType.MENTOR_REJECT,
                "MENTOR_PROFILE",
                profileId,
                reason,
                null);

        return MentorProfileResponse.from(profile, reason);
    }
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run:
```
cd backend; .\gradlew.bat test --tests "com.devmatch.service.MentorServiceTest"
```
Expected: `BUILD SUCCESSFUL`, 모든 테스트(기존 + 신규 3개) 통과.

- [ ] **Step 7: 전체 백엔드 테스트 회귀 확인**

Run:
```
cd backend; .\gradlew.bat test
```
Expected: `BUILD SUCCESSFUL`. 다른 테스트가 `AdminAuditLogService` 미주입으로 실패하면 해당 테스트에도 `@Mock AdminAuditLogService` 추가 필요 — 단, `MentorService` 를 주입받는 다른 테스트가 없는 것이 정상(`grep -r 'new MentorService\|InjectMocks.*MentorService' backend/src/test` 로 확인 가능).

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/MentorService.java backend/src/test/java/com/devmatch/service/MentorServiceTest.java
git commit -m "feat(admin-audit): MentorService approve/reject 에 감사 로그 소급 적용"
```

---

## Task 5: 프런트 사이드바에 3개 메뉴 추가

**Files:**
- Modify: `frontend/src/components/admin/AdminSidebar.tsx`

기존 `NAV_ITEMS` 배열에 회원/결제/게시물 3개 항목을 추가한다. 스펙 §5.1 에 맞춰 `lucide-react` 아이콘을 활용.

- [ ] **Step 1: AdminSidebar 파일 수정**

`frontend/src/components/admin/AdminSidebar.tsx` 의 import 와 `NAV_ITEMS` 배열을 다음과 같이 **교체**:

기존:
```tsx
import { Users } from 'lucide-react';

const NAV_ITEMS: Array<{
  href: string;
  label: string;
  icon: typeof Users;
  match: (pathname: string) => boolean;
}> = [
  {
    href: '/admin/mentor',
    label: '멘토 심사',
    icon: Users,
    match: (p) => p === '/admin/mentor' || p.startsWith('/admin/mentor/'),
  },
];
```

교체 후:
```tsx
import { UserCheck, Users, CreditCard, FileText } from 'lucide-react';

const NAV_ITEMS: Array<{
  href: string;
  label: string;
  icon: typeof Users;
  match: (pathname: string) => boolean;
}> = [
  {
    href: '/admin/mentor',
    label: '멘토 심사',
    icon: UserCheck,
    match: (p) => p === '/admin/mentor' || p.startsWith('/admin/mentor/'),
  },
  {
    href: '/admin/users',
    label: '회원 관리',
    icon: Users,
    match: (p) => p === '/admin/users' || p.startsWith('/admin/users/'),
  },
  {
    href: '/admin/payments',
    label: '결제 관리',
    icon: CreditCard,
    match: (p) => p === '/admin/payments' || p.startsWith('/admin/payments/'),
  },
  {
    href: '/admin/posts',
    label: '게시물 관리',
    icon: FileText,
    match: (p) => p === '/admin/posts' || p.startsWith('/admin/posts/'),
  },
];
```

> 주: Phase I 의 멘토 심사 아이콘을 `Users` → `UserCheck` 로 변경해 회원 관리와 시각적 구분을 준다. 기존 "멘토 심사" 활성화 규칙은 유지됨.

- [ ] **Step 2: 타입 체크 + 빌드 확인**

Run (Windows PowerShell):
```
cd frontend; npm run build
```
Expected: `Compiled successfully` (또는 기존 경고만, 신규 에러 없음). lucide-react 아이콘 이름 오타가 있으면 TS 에러.

- [ ] **Step 3: 개발 서버로 수동 스모크 확인**

Run (별도 터미널):
```
cd frontend; npm run dev
```

브라우저에서:
1. ADMIN 계정으로 로그인
2. `/admin/mentor` 접근 — 사이드바에서 "멘토 심사" 가 활성(진한 배경) 표시되는지 확인
3. `/admin/users` 로 URL 직접 이동 — "회원 관리" 가 활성 표시되는지 (페이지 내용은 404/빈 화면이어도 무방. 이 스펙 범위 밖)
4. `/admin/mentor/123` 같은 상세 경로에서도 "멘토 심사" 여전히 활성 확인

`Ctrl+C` 로 dev 서버 종료.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/admin/AdminSidebar.tsx
git commit -m "feat(admin-sidebar): 회원/결제/게시물 관리 메뉴 3종 추가"
```

---

## Task 6: 마이그레이션 체크리스트 업데이트

**Files:**
- Modify: `ROADMAP.md` 또는 배포 체크리스트가 있는 위치

스펙 §4.7 에 따라 prod 배포 시 `admin_audit_log` 테이블 수동 DDL 이 필요하다. 기존 배포 체크리스트에 추가한다.

- [ ] **Step 1: 배포 체크리스트 파일 확인**

Run (프로젝트 루트):
```
grep -rn "배포 체크리스트\|deploy.*checklist\|REFRESH_COOKIE_SECURE" --include="*.md" . | head
```
Expected: `ROADMAP.md:245` 근처에 "배포 체크리스트" 섹션이 있음. (없으면 Step 2 를 `ROADMAP.md` 의 Phase 6 섹션에 추가.)

- [ ] **Step 2: 체크리스트에 DDL 항목 추가**

`ROADMAP.md` 의 "배포 체크리스트" 섹션 끝에 다음 항목 **추가**:

```markdown
7. Phase II 배포 시 `admin_audit_log` 테이블 생성 DDL 수동 실행
   ```sql
   CREATE TABLE admin_audit_log (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     admin_id BIGINT NOT NULL,
     action_type VARCHAR(30) NOT NULL,
     target_type VARCHAR(20) NOT NULL,
     target_id BIGINT NOT NULL,
     reason VARCHAR(500),
     metadata TEXT,
     created_at DATETIME(6) NOT NULL,
     INDEX idx_audit_admin_created_at (admin_id, created_at),
     INDEX idx_audit_target (target_type, target_id)
   );
   ```
```

- [ ] **Step 3: 커밋**

```bash
git add ROADMAP.md
git commit -m "docs(deploy): admin_audit_log 테이블 DDL 배포 체크리스트 추가"
```

---

## Task 7: 최종 회귀 검증 및 PR 준비

**Files:** (없음, 검증만)

- [ ] **Step 1: 전체 백엔드 테스트**

Run:
```
cd backend; .\gradlew.bat test
```
Expected: `BUILD SUCCESSFUL`, 모든 기존 + 신규 테스트 통과.

- [ ] **Step 2: 전체 프런트 빌드**

Run:
```
cd frontend; npm run build
```
Expected: `Compiled successfully`.

- [ ] **Step 3: 커밋 로그 점검**

Run:
```
git log --oneline main..HEAD
```
Expected: 이 플랜의 6개 커밋이 순서대로 보여야 함:
1. `feat(admin-audit): AdminActionType enum 추가`
2. `feat(admin-audit): AdminAuditLog 엔티티 + Repository 추가`
3. `feat(admin-audit): AdminAuditLogService 구현 + 단위 테스트`
4. `feat(admin-audit): MentorService approve/reject 에 감사 로그 소급 적용`
5. `feat(admin-sidebar): 회원/결제/게시물 관리 메뉴 3종 추가`
6. `docs(deploy): admin_audit_log 테이블 DDL 배포 체크리스트 추가`

- [ ] **Step 4: 수동 통합 스모크 (선택적이지만 권장)**

1. 백엔드 기동: `cd backend; .\gradlew.bat bootRun`
2. 프런트 기동: `cd frontend; npm run dev`
3. ADMIN 계정 로그인 → `/admin/mentor` → PENDING 상태의 멘토 한 명 승인 또는 반려
4. DB 접속해서 확인: `SELECT * FROM admin_audit_log ORDER BY id DESC LIMIT 1;`
   - `action_type = MENTOR_APPROVE` (혹은 `MENTOR_REJECT`)
   - `target_id` 가 해당 `mentor_profile.id`
   - reject 였다면 `reason` 이 입력한 값과 일치

실패 시 해당 Task 로 돌아가 원인 파악 → [CLAUDE.md](../../CLAUDE.md) 규칙에 따라 `error/` 폴더에 트러블슈팅 문서 작성.

- [ ] **Step 5: PR 생성 준비**

브랜치 푸시 후 PR 제목/본문 예시:

제목: `feat(admin): Phase II Common — 감사 로그 + 사이드바 확장`

본문:
```markdown
## Summary
- Phase II 관리자 콘솔 3개 기능 공통 기반 도입
- `AdminAuditLog` 엔티티 + `AdminAuditLogService` 신설
- `MentorService.approve/reject` 에 감사 로그 소급 적용 (기존 `MentorProfileHistory` 유지)
- 관리자 사이드바에 회원/결제/게시물 관리 메뉴 추가 (페이지 자체는 후속 PR)

## Spec
docs/superpowers/specs/2026-04-22-admin-console-common-design.md

## Test plan
- [x] `AdminAuditLogServiceTest` 4건 통과
- [x] `MentorServiceTest` approve/reject 신규 3건 통과
- [x] 전체 백엔드 테스트 회귀 통과
- [x] 프런트 빌드 통과
- [x] 수동 스모크: 멘토 승인 시 `admin_audit_log` row 생성 확인
```

---

## 완료 후 후속 작업

이 Plan 이 머지되면:
1. 스펙 §9 에 따라 `docs/superpowers/specs/YYYY-MM-DD-admin-users-design.md` 브레인스토밍
2. `docs/mockups/admin-users.md` 는 Phase II Common 결정(이메일 제외 등)에 맞춰 리비전하며 그 스펙에서 참조

---

## Plan Self-Review Notes

- ✅ Spec §4.1 (엔티티) → Task 2
- ✅ Spec §4.2 (enum) → Task 1
- ✅ Spec §4.3 (서비스) → Task 3
- ✅ Spec §4.4 (호출 규약) → Task 3 Javadoc + Task 4 실제 호출
- ✅ Spec §4.5 (Phase I 소급) → Task 4
- ✅ Spec §4.7 (마이그레이션) → Task 6
- ✅ Spec §5.1 (사이드바) → Task 5
- ✅ Spec §6 (테스트 계획) → Task 3 + Task 4 + Task 7
- ✅ Spec §7 리스크 "사이드바 리팩토링 회귀" → Task 5 Step 3 수동 확인
- ⚠ Spec §5.2 (`/admin` 리다이렉트 변경 없음) — 별도 작업 없음, Plan 에 명시하지 않아도 됨 (의도적 무작업)
- ⚠ Spec §5.3 (공용 FE 컴포넌트 Out of scope) — 의도적으로 Plan 에 포함 안 됨

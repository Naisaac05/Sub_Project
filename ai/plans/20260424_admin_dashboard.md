---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "관리자 콘솔 홈 대시보드 (Phase III) 구현 계획"

---

# 관리자 콘솔 홈 대시보드 (Phase III Feature 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/admin/dashboard` 를 신설해 관리자에게 KPI 4개 + 추이 차트 2개 + 처리 큐 2개 + (SUPER_ADMIN 한정) 감사 로그 피드를 제공하고, `/admin` 루트를 이 페이지로 리다이렉트한다.

**Architecture:** 백엔드는 `AdminDashboardController` → `AdminDashboardService` 2개 메서드(`getSummary`, `getAuditLogFeed`) 구조. 집계 쿼리는 기존 리포지토리에 메서드 추가하는 방식으로 분산. DDL 변경 없음 — Phase II 에서 준비 완료된 컬럼만 사용. 프론트는 `/admin/dashboard/page.tsx` 페이지 셸이 두 API 를 병렬 호출하고 섹션별 컴포넌트로 분할.

**Tech Stack:** Spring Boot 3 (JPA + Hibernate), JUnit 5 + Mockito, `@WebMvcTest` + Spring Security Test, Next.js 14 (App Router), shadcn/ui chart (Recharts), Pencil MCP (.pen 파일).

**Spec:** `docs/superpowers/specs/2026-04-24-admin-dashboard-design.md`

---

## File Structure

### 신규 생성 (백엔드 — 6 파일)

| 경로 | 책임 |
|------|------|
| `backend/src/main/java/com/devmatch/dto/admin/dashboard/AdminDashboardResponse.java` | 메인 응답 record (중첩 record 포함) |
| `backend/src/main/java/com/devmatch/dto/admin/dashboard/AdminAuditLogFeedResponse.java` | 감사 로그 피드 응답 record |
| `backend/src/main/java/com/devmatch/service/AdminDashboardService.java` | KPI/차트/큐 집계 + 감사 로그 포맷 |
| `backend/src/main/java/com/devmatch/controller/AdminDashboardController.java` | 엔드포인트 2개 |
| `backend/src/test/java/com/devmatch/service/AdminDashboardServiceTest.java` | 서비스 단위 테스트 |
| `backend/src/test/java/com/devmatch/controller/AdminDashboardControllerTest.java` | 권한 + 응답 통합 테스트 |

### 수정 (백엔드 — 6 파일)

| 경로 | 변경 |
|------|------|
| `backend/src/main/java/com/devmatch/repository/UserRepository.java` | `countByStatus`, `findDailySignupsSince` |
| `backend/src/main/java/com/devmatch/repository/PaymentRepository.java` | `sumAmountByStatusAndCancelledBetween`, 월별 CONFIRMED / CANCELLED 쿼리 |
| `backend/src/main/java/com/devmatch/repository/MatchingRepository.java` | `countByStatus`, `countByStatusAndCreatedAtBetween` |
| `backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java` | `countByStatus` (없으면 추가) |
| `backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java` | `findTop10ByOrderByCreatedAtDesc` |
| `backend/src/main/java/com/devmatch/entity/AdminActionType.java` | "새 값 추가 시 AdminDashboardService.formatDescription 도 업데이트" 주석 추가 |
| `backend/src/main/java/com/devmatch/config/SecurityConfig.java` | `/api/admin/dashboard/audit-log` → SUPER_ADMIN 라우트 추가 |

### 신규 생성 (프론트 — 8 파일)

| 경로 | 책임 |
|------|------|
| `frontend/src/app/admin/dashboard/page.tsx` | 페이지 셸, 2 API 호출, 섹션 렌더 |
| `frontend/src/lib/admin/dashboard.ts` | API 클라이언트 (`fetchDashboard`, `fetchAuditLog`) 및 타입 |
| `frontend/src/components/admin/dashboard/KpiCards.tsx` | 4 KPI 카드 |
| `frontend/src/components/admin/dashboard/SignupTrendChart.tsx` | Recharts 라인 차트 |
| `frontend/src/components/admin/dashboard/RevenueTrendChart.tsx` | Recharts 바 차트 |
| `frontend/src/components/admin/dashboard/ActionQueue.tsx` | 처리 큐 2항목 |
| `frontend/src/components/admin/dashboard/RecentAuditLog.tsx` | SUPER_ADMIN 전용 피드 |
| `frontend/src/components/admin/dashboard/SectionError.tsx` | 섹션 단위 "에러 · 재시도" 공용 컴포넌트 |

### 수정 (프론트 — 2 파일)

| 경로 | 변경 |
|------|------|
| `frontend/src/app/admin/page.tsx` | `/admin/mentor` → `/admin/dashboard` 리다이렉트 |
| `frontend/src/components/admin/AdminSidebar.tsx` | 📊 대시보드 메뉴 최상단 추가 |

### 문서 / 목업

| 경로 | 변경 |
|------|------|
| `docs/mockups/admin-dashboard.md` | Pencil 목업 export (신규) |
| `docs/mockups/admin-console-overview.md` | 상태 테이블 + 사이드바 다이어그램 업데이트 |
| `ROADMAP.md` | §10 에 "Phase III Feature 1 배포 — DDL/env 변경 없음" 한 줄 |

---

## 주의 — 엔티티 전제

구현 시작 전 다음을 반드시 기억:

- `PaymentStatus` 실제 enum: **`PENDING` / `CONFIRMED` / `CANCELLED` / `FAILED`** (스펙 초안의 `PAID` 는 오기, `CONFIRMED` 가 실제)
- `payments.approved_at` 컬럼 **없음** — CONFIRMED 집계는 `created_at` 기준
- `payments.refund_amount` 컬럼 **없음** — 전액 환불 전제, `payments.amount` 자체를 환불 금액으로 합산
- `AdminActionType` 실제 enum 값: `USER_ROLE_CHANGE`, `PAYMENT_REFUND`, `POST_DELETE`, `COMMENT_DELETE`, `MENTOR_APPROVE`, `MENTOR_REJECT`, `USER_DEACTIVATE`, `USER_REACTIVATE`, `USER_DELETE`, `USER_PASSWORD_RESET`, `USER_MENTOR_SWAP`, `ADMIN_CREATE`
- Spring Security 권한은 `@PreAuthorize` 가 아니라 **`SecurityConfig` URL 패턴** 으로 관리. `/api/admin/**` 는 ADMIN, `/api/admin/admins/**` 는 SUPER_ADMIN. 대시보드 감사 로그도 **URL 패턴 규칙**으로 추가.

---

## Task 1: Pencil 목업 작성 → 사용자 승인

**Files:**
- Create: `docs/mockups/admin-dashboard.md`
- Create: `docs/mockups/admin-dashboard.pen` (Pencil 파일)

- [ ] **Step 1: Pencil MCP 로 빈 문서 생성**

```
open_document('new')
```

- [ ] **Step 2: Pencil 가이드라인 로드**

```
get_guidelines('web')
```

- [ ] **Step 3: 스펙 §4.2 레이아웃(KPI 4 + 차트 2 + 처리 큐 + 감사 로그)을 Pencil 에 그리고 파일 저장**

`batch_design` 으로 섹션 순서대로 작성:
1. 페이지 헤더 "대시보드 / 관리자 콘솔 홈 · YYYY년 M월 D일 기준"
2. KPI 4 카드 그리드 (활성회원 / 이번달매출 / 누적매칭 / 승인멘토)
3. 차트 2 (좌: 일별 신규 가입 라인, 우: 월별 순매출 바)
4. 처리 큐 섹션 (승인 대기 멘토, 실패 결제)
5. 최근 관리자 활동 섹션 (SUPER_ADMIN 표시)

파일 경로: `docs/mockups/admin-dashboard.pen`

- [ ] **Step 4: export 한 PNG 를 `docs/mockups/admin-dashboard.md` 에 삽입**

```md
# 관리자 대시보드 목업

![관리자 대시보드](./admin-dashboard.png)

## 섹션 구성

- 상단 KPI 카드 4개 ...
- 차트 2개 ...
- 처리 큐 2개 ...
- 최근 관리자 활동 (SUPER_ADMIN 한정) ...

## shadcn 컴포넌트 매핑

- KPI 카드: `<Card>`, `<CardHeader>`, `<CardContent>`
- 차트: `<Chart>` (shadcn/ui, Recharts wrapper)
- 큐: `<Card>` + 리스트
- 감사 로그: 단순 `<ul>` 리스트 or `<Card>` 내부
```

- [ ] **Step 5: 사용자 승인 받기 전까지 다음 Task 진행 금지**

응답 기다림. "승인" 받기 전 Task 2 로 넘어가지 말 것 — `feedback_frontend_preview.md` 규칙.

- [ ] **Step 6: 커밋**

```bash
git add docs/mockups/admin-dashboard.pen docs/mockups/admin-dashboard.md docs/mockups/admin-dashboard.png
git commit -m "docs(mockup): 관리자 대시보드 Pencil 목업 (승인본)"
```

---

## Task 2: shadcn chart 컴포넌트 + Recharts 설치

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/components/ui/chart.tsx` (shadcn 생성)

- [ ] **Step 1: frontend 디렉토리에서 shadcn chart 추가**

```bash
cd frontend && npx shadcn@latest add chart
```

Expected: `components/ui/chart.tsx` 생성, `package.json` 에 `recharts` 의존성 추가.

- [ ] **Step 2: 설치 확인**

```bash
cd frontend && npm ls recharts
```

Expected: recharts 버전 출력 (없으면 `npm install recharts` 로 보충).

- [ ] **Step 3: 커밋**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/components/ui/chart.tsx
git commit -m "chore(admin-dashboard): shadcn chart + recharts 설치"
```

---

## Task 3: 백엔드 DTO — AdminDashboardResponse record

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/admin/dashboard/AdminDashboardResponse.java`

- [ ] **Step 1: record 작성**

```java
package com.devmatch.dto.admin.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/admin/dashboard 응답 (ADMIN+).
 */
public record AdminDashboardResponse(
        Kpi kpi,
        List<SignupTrendPoint> signupTrend,
        List<RevenueTrendPoint> revenueTrend,
        Queue queue
) {

    public record Kpi(
            MetricWithDelta totalActiveUsers,
            MetricWithDelta currentMonthRevenue,
            MatchingMetric totalAcceptedMatchings,
            MentorMetric approvedMentors
    ) {}

    /** current: 현재 값, deltaFromLastMonth: 절댓값 차이, deltaPercent: %(지난달 0 이면 null) */
    public record MetricWithDelta(long current, long deltaFromLastMonth, Double deltaPercent) {}

    public record MatchingMetric(long current, long newThisMonth) {}

    public record MentorMetric(long current, long pending) {}

    public record SignupTrendPoint(LocalDate date, long count) {}

    public record RevenueTrendPoint(String month, long grossRevenue, long refundAmount, long netRevenue) {}

    public record Queue(long pendingMentorCount, long failedPaymentCount) {}
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/dto/admin/dashboard/AdminDashboardResponse.java
git commit -m "feat(admin-dashboard): AdminDashboardResponse DTO"
```

---

## Task 4: 백엔드 DTO — AdminAuditLogFeedResponse record

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/admin/dashboard/AdminAuditLogFeedResponse.java`

- [ ] **Step 1: record 작성**

```java
package com.devmatch.dto.admin.dashboard;

import com.devmatch.entity.AdminActionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/admin/dashboard/audit-log 응답 (SUPER_ADMIN).
 * metadata 원문은 의도적으로 포함하지 않는다 — 민감 사유 노출 방지 (스펙 §5.4).
 */
public record AdminAuditLogFeedResponse(List<Item> items) {

    public record Item(
            Long id,
            String adminName,
            AdminActionType actionType,
            String description,
            String targetHref,
            LocalDateTime createdAt
    ) {}
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/dto/admin/dashboard/AdminAuditLogFeedResponse.java
git commit -m "feat(admin-dashboard): AdminAuditLogFeedResponse DTO"
```

---

## Task 5: UserRepository 쿼리 추가

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/UserRepository.java`

- [ ] **Step 1: 메서드 추가**

기존 파일의 `searchAdminUsers` 메서드 아래에 추가:

```java
    long countByStatus(UserStatus status);

    long countByStatusAndCreatedAtBetween(UserStatus status,
                                          java.time.LocalDateTime from,
                                          java.time.LocalDateTime toExclusive);

    /**
     * 최근 N일 일별 신규 가입 집계.
     * DATE(created_at) → count 형태의 native-style 쿼리.
     * H2·MySQL 모두에서 동작하도록 function('date', ...) JPQL 사용.
     */
    @Query("""
           SELECT FUNCTION('DATE', u.createdAt) AS d, COUNT(u) AS c
             FROM User u
            WHERE u.createdAt >= :from
            GROUP BY FUNCTION('DATE', u.createdAt)
            ORDER BY FUNCTION('DATE', u.createdAt) ASC
           """)
    List<Object[]> findDailySignupsSince(@Param("from") java.time.LocalDateTime from);
```

> `List<Object[]>` 로 받고 서비스에서 `(LocalDate, Long)` 매핑. 튜플 인터페이스 대신 단순 배열 사용 (프로젝트 다른 리포지토리와 일관).

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/UserRepository.java
git commit -m "feat(admin-dashboard): UserRepository 집계 쿼리 추가"
```

---

## Task 6: PaymentRepository 쿼리 추가

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/PaymentRepository.java`

- [ ] **Step 1: 메서드 추가**

기존 `countByStatusAndCreatedBetween` 아래에 추가:

```java
    @Query("""
           select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status
              and p.cancelledAt >= :from and p.cancelledAt < :toExclusive
           """)
    long sumAmountByStatusAndCancelledBetween(@Param("status") PaymentStatus status,
                                              @Param("from") LocalDateTime from,
                                              @Param("toExclusive") LocalDateTime toExclusive);

    long countByStatus(PaymentStatus status);
```

> CONFIRMED 합산 쿼리(`sumAmountByStatusAndCreatedBetween`) 는 이미 존재 — 재사용. 새로 추가하는 것은 CANCELLED 를 `cancelled_at` 기준으로 합산하는 쿼리 + 단순 countByStatus.

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/PaymentRepository.java
git commit -m "feat(admin-dashboard): PaymentRepository 환불 집계 쿼리"
```

---

## Task 7: MatchingRepository + MentorProfileRepository 쿼리 추가

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/MatchingRepository.java`
- Modify: `backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java`

- [ ] **Step 1: 각 리포지토리에 `countByStatus` 존재 여부 확인**

```bash
cd backend && grep -n "countByStatus" src/main/java/com/devmatch/repository/MatchingRepository.java src/main/java/com/devmatch/repository/MentorProfileRepository.java
```

- [ ] **Step 2: MatchingRepository 에 메서드 추가 (없다면)**

```java
    long countByStatus(com.devmatch.entity.MatchingStatus status);

    long countByStatusAndCreatedAtBetween(com.devmatch.entity.MatchingStatus status,
                                          java.time.LocalDateTime from,
                                          java.time.LocalDateTime toExclusive);
```

- [ ] **Step 3: MentorProfileRepository 에 메서드 추가 (없다면)**

```java
    long countByStatus(com.devmatch.entity.MentorStatus status);
```

- [ ] **Step 4: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/MatchingRepository.java backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java
git commit -m "feat(admin-dashboard): Matching/MentorProfile countByStatus"
```

---

## Task 8: AdminAuditLogRepository 피드 쿼리 추가

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java`

- [ ] **Step 1: 기존 파일 대체**

기존 파일의 `// Phase II 에서는 조회 메서드 불필요. Phase III 대시보드에서 추가 예정.` 주석 제거 후:

```java
package com.devmatch.repository;

import com.devmatch.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /** 대시보드 피드용 — 최신 10건. admin_audit_log.created_at 인덱스는 adminId 복합 인덱스뿐이라
     *  풀 스캔이지만, 현재 row 수 규모에서 문제 없음. */
    List<AdminAuditLog> findTop10ByOrderByCreatedAtDesc();
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/AdminAuditLogRepository.java
git commit -m "feat(admin-dashboard): AdminAuditLogRepository 최근 10건 피드"
```

---

## Task 9: AdminActionType.java 주석 업데이트

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/AdminActionType.java`

- [ ] **Step 1: 클래스 Javadoc 수정**

기존 Javadoc 의 마지막 줄 `"스펙 문서... 업데이트."` 다음에 한 줄 추가:

```java
/**
 * 관리자 행위 유형. AdminAuditLog.actionType 에서 사용한다.
 *
 * 새 값 추가 시 반드시 다음을 함께 업데이트:
 * - 스펙 문서 (docs/superpowers/specs/2026-04-22-admin-console-common-design.md)
 * - AdminDashboardService.formatDescription (피드 description 포맷)
 */
```

- [ ] **Step 2: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/AdminActionType.java
git commit -m "docs(admin-dashboard): AdminActionType 주석에 formatDescription 연동 명시"
```

---

## Task 10: AdminDashboardService — 집계 로직 (실패 테스트부터)

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/AdminDashboardServiceTest.java`
- Create: `backend/src/main/java/com/devmatch/service/AdminDashboardService.java`

- [ ] **Step 1: 테스트 먼저 작성 (실패 상태)**

```java
package com.devmatch.service;

import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.AdminAuditLogRepository;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock MatchingRepository matchingRepository;
    @Mock MentorProfileRepository mentorProfileRepository;
    @Mock AdminAuditLogRepository auditLogRepository;

    @InjectMocks AdminDashboardService service;

    @Test
    void summary_kpi_delta_percent_is_null_when_last_month_zero() {
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(100L);
        // 이번달 신규 = 이번달 구간 count, 지난달 신규 = 지난달 구간 count = 0
        when(userRepository.countByStatusAndCreatedAtBetween(eq(UserStatus.ACTIVE), any(), any()))
                .thenReturn(10L)   // 이번달
                .thenReturn(0L);   // 지난달
        // 나머지 쿼리 기본값 (0)
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.sumAmountByStatusAndCancelledBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(0L);
        when(matchingRepository.countByStatus(MatchingStatus.ACCEPTED)).thenReturn(0L);
        when(matchingRepository.countByStatusAndCreatedAtBetween(eq(MatchingStatus.ACCEPTED), any(), any()))
                .thenReturn(0L);
        when(mentorProfileRepository.countByStatus(MentorStatus.APPROVED)).thenReturn(0L);
        when(mentorProfileRepository.countByStatus(MentorStatus.PENDING)).thenReturn(0L);
        when(paymentRepository.countByStatus(PaymentStatus.FAILED)).thenReturn(0L);
        when(userRepository.findDailySignupsSince(any())).thenReturn(List.of());

        AdminDashboardResponse res = service.getSummary();

        assertThat(res.kpi().totalActiveUsers().deltaFromLastMonth()).isEqualTo(10L);
        assertThat(res.kpi().totalActiveUsers().deltaPercent()).isNull();
    }

    @Test
    void summary_net_revenue_is_confirmed_minus_cancelled_in_window() {
        stubZeroBase();
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(1_000_000L)  // 이번달 gross
                .thenReturn(800_000L);   // 지난달 gross (MTD 계산용)
        when(paymentRepository.sumAmountByStatusAndCancelledBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(100_000L)    // 이번달 환불
                .thenReturn(50_000L);    // 지난달 환불

        AdminDashboardResponse res = service.getSummary();

        // 이번달 순매출 = 1,000,000 - 100,000 = 900,000
        assertThat(res.kpi().currentMonthRevenue().current()).isEqualTo(900_000L);
        // delta = 900,000 - (800,000 - 50,000) = 900,000 - 750,000 = 150,000
        assertThat(res.kpi().currentMonthRevenue().deltaFromLastMonth()).isEqualTo(150_000L);
    }

    private void stubZeroBase() {
        when(userRepository.countByStatus(any())).thenReturn(0L);
        when(userRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(matchingRepository.countByStatus(any())).thenReturn(0L);
        when(matchingRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(mentorProfileRepository.countByStatus(any())).thenReturn(0L);
        when(paymentRepository.countByStatus(any())).thenReturn(0L);
        when(userRepository.findDailySignupsSince(any())).thenReturn(List.of());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

```bash
cd backend && ./gradlew test --tests com.devmatch.service.AdminDashboardServiceTest
```

Expected: AdminDashboardService 클래스 없음 → compilation error.

- [ ] **Step 3: AdminDashboardService 구현**

```java
package com.devmatch.service;

import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.dto.admin.dashboard.AdminDashboardResponse.*;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final MatchingRepository matchingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminDashboardResponse getSummary() {
        LocalDateTime nowKst = LocalDateTime.now(KST);
        LocalDate today = nowKst.toLocalDate();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime nowSameTimeLastMonth = lastMonthStart.plus(Duration.between(monthStart, nowKst));

        // KPI ─ 회원
        long totalActive = userRepository.countByStatus(UserStatus.ACTIVE);
        long thisMonthSignups = userRepository.countByStatusAndCreatedAtBetween(
                UserStatus.ACTIVE, monthStart, nowKst);
        long lastMonthSignups = userRepository.countByStatusAndCreatedAtBetween(
                UserStatus.ACTIVE, lastMonthStart, nowSameTimeLastMonth);
        MetricWithDelta usersKpi = deltaMetric(totalActive, thisMonthSignups, lastMonthSignups);

        // KPI ─ 매출
        long thisMonthGross = paymentRepository.sumAmountByStatusAndCreatedBetween(
                PaymentStatus.CONFIRMED, monthStart, nowKst);
        long lastMonthGross = paymentRepository.sumAmountByStatusAndCreatedBetween(
                PaymentStatus.CONFIRMED, lastMonthStart, nowSameTimeLastMonth);
        long thisMonthRefund = paymentRepository.sumAmountByStatusAndCancelledBetween(
                PaymentStatus.CANCELLED, monthStart, nowKst);
        long lastMonthRefund = paymentRepository.sumAmountByStatusAndCancelledBetween(
                PaymentStatus.CANCELLED, lastMonthStart, nowSameTimeLastMonth);
        long thisMonthNet = thisMonthGross - thisMonthRefund;
        long lastMonthNet = lastMonthGross - lastMonthRefund;
        MetricWithDelta revenueKpi = deltaMetric(thisMonthNet, thisMonthNet, lastMonthNet);
        // revenue 는 current 가 곧 이번달 값 (누적 X). delta 계산을 위해 this/last 둘 다 이번달/지난달 값.

        // KPI ─ 매칭
        long totalAccepted = matchingRepository.countByStatus(MatchingStatus.ACCEPTED);
        long newThisMonthAccepted = matchingRepository.countByStatusAndCreatedAtBetween(
                MatchingStatus.ACCEPTED, monthStart, nowKst);
        MatchingMetric matchingKpi = new MatchingMetric(totalAccepted, newThisMonthAccepted);

        // KPI ─ 멘토
        long approvedMentors = mentorProfileRepository.countByStatus(MentorStatus.APPROVED);
        long pendingMentors = mentorProfileRepository.countByStatus(MentorStatus.PENDING);
        MentorMetric mentorKpi = new MentorMetric(approvedMentors, pendingMentors);

        // 차트 1 ─ 일별 신규 가입 (rolling 30일)
        LocalDate from30 = today.minusDays(29);
        List<SignupTrendPoint> signupTrend = buildSignupTrend(from30, today);

        // 차트 2 ─ 월별 순매출 (rolling 12개월)
        List<RevenueTrendPoint> revenueTrend = buildRevenueTrend(today);

        // 처리 큐
        long failedPaymentCount = paymentRepository.countByStatus(PaymentStatus.FAILED);
        Queue queue = new Queue(pendingMentors, failedPaymentCount);

        return new AdminDashboardResponse(
                new Kpi(usersKpi, revenueKpi, matchingKpi, mentorKpi),
                signupTrend,
                revenueTrend,
                queue
        );
    }

    private MetricWithDelta deltaMetric(long current, long thisWindow, long lastWindow) {
        long delta = thisWindow - lastWindow;
        Double percent = (lastWindow == 0) ? null : (delta * 100.0) / lastWindow;
        return new MetricWithDelta(current, delta, percent);
    }

    private List<SignupTrendPoint> buildSignupTrend(LocalDate from, LocalDate today) {
        LocalDateTime fromDt = from.atStartOfDay();
        List<Object[]> rows = userRepository.findDailySignupsSince(fromDt);
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d = ((Date) row[0]).toLocalDate();
            long c = ((Number) row[1]).longValue();
            map.put(d, c);
        }
        return IntStream.range(0, 30)
                .mapToObj(i -> {
                    LocalDate d = from.plusDays(i);
                    return new SignupTrendPoint(d, map.getOrDefault(d, 0L));
                })
                .toList();
    }

    private List<RevenueTrendPoint> buildRevenueTrend(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        List<RevenueTrendPoint> out = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
            long gross = paymentRepository.sumAmountByStatusAndCreatedBetween(
                    PaymentStatus.CONFIRMED, start, end);
            long refund = paymentRepository.sumAmountByStatusAndCancelledBetween(
                    PaymentStatus.CANCELLED, start, end);
            out.add(new RevenueTrendPoint(
                    ym.toString(), gross, refund, gross - refund));
        }
        return out;
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd backend && ./gradlew test --tests com.devmatch.service.AdminDashboardServiceTest
```

Expected: 2 tests pass.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/AdminDashboardService.java backend/src/test/java/com/devmatch/service/AdminDashboardServiceTest.java
git commit -m "feat(admin-dashboard): AdminDashboardService getSummary 집계 로직"
```

---

## Task 11: AdminDashboardService — 감사 로그 피드 + 포맷터

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/AdminDashboardService.java`
- Modify: `backend/src/test/java/com/devmatch/service/AdminDashboardServiceTest.java`

- [ ] **Step 1: 테스트 먼저 추가**

`AdminDashboardServiceTest` 에 다음 테스트 추가:

```java
    @Test
    void auditLogFeed_formats_description_for_each_action_type() {
        var admin = com.devmatch.entity.User.builder()
                .id(1L).email("a@d").name("관리자A")
                .role(com.devmatch.entity.Role.ADMIN)
                .status(UserStatus.ACTIVE).build();
        var log = com.devmatch.entity.AdminAuditLog.builder()
                .id(7L).adminId(1L)
                .actionType(com.devmatch.entity.AdminActionType.PAYMENT_REFUND)
                .targetType("PAYMENT").targetId(123L).reason("중복")
                .metadata(null).createdAt(LocalDateTime.of(2026,4,24,9,0)).build();
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(log));
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(admin));

        var res = service.getAuditLogFeed();

        assertThat(res.items()).hasSize(1);
        var item = res.items().get(0);
        assertThat(item.adminName()).isEqualTo("관리자A");
        assertThat(item.description()).isEqualTo("결제 #123 환불");
        assertThat(item.targetHref()).isEqualTo("/admin/payments/123");
    }

    @Test
    void auditLogFeed_falls_back_when_admin_deleted() {
        var log = com.devmatch.entity.AdminAuditLog.builder()
                .id(8L).adminId(999L)
                .actionType(com.devmatch.entity.AdminActionType.POST_DELETE)
                .targetType("POST").targetId(45L).createdAt(LocalDateTime.now()).build();
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(log));
        when(userRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        var res = service.getAuditLogFeed();

        assertThat(res.items().get(0).adminName()).isEqualTo("(삭제된 관리자)");
        assertThat(res.items().get(0).description()).isEqualTo("게시물 #45 삭제");
        assertThat(res.items().get(0).targetHref()).isEqualTo("/admin/posts/45");
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests com.devmatch.service.AdminDashboardServiceTest
```

Expected: `service.getAuditLogFeed()` 메서드 없음 → compilation error.

- [ ] **Step 3: AdminDashboardService 에 메서드 추가**

`AdminDashboardService.java` 끝 (`buildRevenueTrend` 아래) 에 추가:

```java
    public com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse getAuditLogFeed() {
        var logs = auditLogRepository.findTop10ByOrderByCreatedAtDesc();
        var items = logs.stream().map(log -> {
            String adminName = userRepository.findById(log.getAdminId())
                    .map(com.devmatch.entity.User::getName)
                    .orElse("(삭제된 관리자)");
            return new com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse.Item(
                    log.getId(),
                    adminName,
                    log.getActionType(),
                    formatDescription(log),
                    formatTargetHref(log),
                    log.getCreatedAt()
            );
        }).toList();
        return new com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse(items);
    }

    static String formatDescription(com.devmatch.entity.AdminAuditLog log) {
        long id = log.getTargetId();
        return switch (log.getActionType()) {
            case USER_ROLE_CHANGE    -> "회원 #" + id + " 역할 변경";
            case USER_DEACTIVATE     -> "회원 #" + id + " 비활성화";
            case USER_REACTIVATE     -> "회원 #" + id + " 재활성화";
            case USER_DELETE         -> "회원 #" + id + " 삭제";
            case USER_PASSWORD_RESET -> "회원 #" + id + " 비밀번호 초기화";
            case USER_MENTOR_SWAP    -> "회원 #" + id + " 멘토 교체";
            case ADMIN_CREATE        -> "관리자 계정 #" + id + " 생성";
            case PAYMENT_REFUND      -> "결제 #" + id + " 환불";
            case POST_DELETE         -> "게시물 #" + id + " 삭제";
            case COMMENT_DELETE      -> "댓글 #" + id + " 삭제";
            case MENTOR_APPROVE      -> "멘토 #" + id + " 승인";
            case MENTOR_REJECT       -> "멘토 #" + id + " 거절";
        };
    }

    static String formatTargetHref(com.devmatch.entity.AdminAuditLog log) {
        long id = log.getTargetId();
        return switch (log.getTargetType()) {
            case "USER"    -> "/admin/users/" + id;
            case "PAYMENT" -> "/admin/payments/" + id;
            case "POST"    -> "/admin/posts/" + id;
            case "COMMENT" -> "/admin/posts";
            case "MENTOR"  -> "/admin/mentor/" + id;
            case "ADMIN"   -> "/admin/admins";
            default        -> "/admin";
        };
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
cd backend && ./gradlew test --tests com.devmatch.service.AdminDashboardServiceTest
```

Expected: 4 tests pass.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/AdminDashboardService.java backend/src/test/java/com/devmatch/service/AdminDashboardServiceTest.java
git commit -m "feat(admin-dashboard): 감사 로그 피드 + description/href 포맷터"
```

---

## Task 12: SecurityConfig — audit-log 라우트 SUPER_ADMIN 등록

**Files:**
- Modify: `backend/src/main/java/com/devmatch/config/SecurityConfig.java`

- [ ] **Step 1: 기존 파일 확인**

```bash
cd backend && grep -n "admin" src/main/java/com/devmatch/config/SecurityConfig.java
```

`/api/admin/admins/**` 가 SUPER_ADMIN 인 라인 **위에** 추가해야 순서가 중요 (더 구체적인 패턴이 앞에).

- [ ] **Step 2: 라우트 추가**

```java
                .requestMatchers("/api/admin/dashboard/audit-log").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/admin/admins/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
```

`/api/admin/dashboard/audit-log` 는 `/api/admin/**` 보다 구체적이므로 먼저 매칭되어야 함.

- [ ] **Step 3: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/devmatch/config/SecurityConfig.java
git commit -m "feat(admin-dashboard): SecurityConfig audit-log 라우트 SUPER_ADMIN"
```

---

## Task 13: AdminDashboardController 작성

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/AdminDashboardController.java`

- [ ] **Step 1: Controller 작성**

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse;
import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "관리자 콘솔 홈 대시보드 API")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService service;

    @Operation(summary = "대시보드 요약 (KPI + 추이 차트 + 처리 큐)")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(service.getSummary()));
    }

    @Operation(summary = "최근 감사 로그 피드 (SUPER_ADMIN 만)")
    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<AdminAuditLogFeedResponse>> getAuditLog() {
        return ResponseEntity.ok(ApiResponse.success(service.getAuditLogFeed()));
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/controller/AdminDashboardController.java
git commit -m "feat(admin-dashboard): AdminDashboardController 엔드포인트 2개"
```

---

## Task 14: Controller 통합 테스트 (권한)

**Files:**
- Create: `backend/src/test/java/com/devmatch/controller/AdminDashboardControllerTest.java`

- [ ] **Step 1: 기존 AdminPostControllerTest 를 참고하여 테스트 작성**

```bash
cat backend/src/test/java/com/devmatch/controller/AdminPostControllerTest.java | head -50
```

> 참고: 이 프로젝트의 Controller 테스트는 `@WebMvcTest` + `MockMvc` + `@WithMockUser(roles="ADMIN")` 패턴을 쓴다. AdminPostControllerTest 의 첫 50줄을 읽고 동일 구조로 아래 테스트 작성.

- [ ] **Step 2: 테스트 작성**

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse;
import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.service.AdminDashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean AdminDashboardService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_can_get_summary() throws Exception {
        when(service.getSummary()).thenReturn(emptySummary());
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_cannot_get_audit_log() throws Exception {
        mvc.perform(get("/api/admin/dashboard/audit-log")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void super_admin_can_get_audit_log() throws Exception {
        when(service.getAuditLogFeed()).thenReturn(new AdminAuditLogFeedResponse(List.of()));
        mvc.perform(get("/api/admin/dashboard/audit-log")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void mentee_cannot_get_summary() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_gets_401() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    private AdminDashboardResponse emptySummary() {
        return new AdminDashboardResponse(
                new AdminDashboardResponse.Kpi(
                        new AdminDashboardResponse.MetricWithDelta(0, 0, null),
                        new AdminDashboardResponse.MetricWithDelta(0, 0, null),
                        new AdminDashboardResponse.MatchingMetric(0, 0),
                        new AdminDashboardResponse.MentorMetric(0, 0)
                ),
                List.of(), List.of(),
                new AdminDashboardResponse.Queue(0, 0)
        );
    }
}
```

> **주의**: `@WithMockUser(roles="SUPER_ADMIN")` 은 `ROLE_SUPER_ADMIN` 권한 1개만 부여함. 실제 `CustomUserDetails` 에서 SUPER_ADMIN 이 ADMIN 도 함께 부여하던 패턴(스펙에 있음) 을 Controller 테스트에서 재현해야 할 수 있음. AdminAccountControllerTest 가 동일 이슈를 어떻게 해결했는지 참고:

```bash
grep -rn "SUPER_ADMIN" backend/src/test/java/ | grep -i "WithMockUser\|authorit"
```

만약 기존 테스트가 `@WithMockUser(authorities={"ROLE_ADMIN","ROLE_SUPER_ADMIN"})` 식으로 쓰면 동일하게.

- [ ] **Step 3: 테스트 실행**

```bash
cd backend && ./gradlew test --tests com.devmatch.controller.AdminDashboardControllerTest
```

Expected: 5 tests pass.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/test/java/com/devmatch/controller/AdminDashboardControllerTest.java
git commit -m "test(admin-dashboard): Controller 권한 매트릭스"
```

---

## Task 15: 전체 백엔드 테스트 실행

- [ ] **Step 1: 전체 빌드 + 테스트**

```bash
cd backend && ./gradlew clean test
```

Expected: BUILD SUCCESSFUL, 기존 테스트 + 신규 7개 모두 통과.

실패 시: 각 테스트 원인 확인 후 수정 (대시보드 관련이 아닌 기존 테스트가 깨지면 변경 범위 재검토).

---

## Task 16: Frontend API 클라이언트 작성

**Files:**
- Create: `frontend/src/lib/admin/dashboard.ts`

- [ ] **Step 1: 타입 + 함수 작성**

```typescript
import apiClient from '../api';
import type { ApiResponse } from '../types';

// ─── 응답 타입 (backend DTO 와 동일 구조) ───

export interface MetricWithDelta {
  current: number;
  deltaFromLastMonth: number;
  deltaPercent: number | null;
}

export interface MatchingMetric {
  current: number;
  newThisMonth: number;
}

export interface MentorMetric {
  current: number;
  pending: number;
}

export interface DashboardKpi {
  totalActiveUsers: MetricWithDelta;
  currentMonthRevenue: MetricWithDelta;
  totalAcceptedMatchings: MatchingMetric;
  approvedMentors: MentorMetric;
}

export interface SignupTrendPoint {
  date: string; // YYYY-MM-DD
  count: number;
}

export interface RevenueTrendPoint {
  month: string; // YYYY-MM
  grossRevenue: number;
  refundAmount: number;
  netRevenue: number;
}

export interface DashboardQueue {
  pendingMentorCount: number;
  failedPaymentCount: number;
}

export interface DashboardResponse {
  kpi: DashboardKpi;
  signupTrend: SignupTrendPoint[];
  revenueTrend: RevenueTrendPoint[];
  queue: DashboardQueue;
}

export type AuditActionType =
  | 'USER_ROLE_CHANGE' | 'USER_DEACTIVATE' | 'USER_REACTIVATE' | 'USER_DELETE'
  | 'USER_PASSWORD_RESET' | 'USER_MENTOR_SWAP' | 'ADMIN_CREATE'
  | 'PAYMENT_REFUND' | 'POST_DELETE' | 'COMMENT_DELETE'
  | 'MENTOR_APPROVE' | 'MENTOR_REJECT';

export interface AuditLogItem {
  id: number;
  adminName: string;
  actionType: AuditActionType;
  description: string;
  targetHref: string;
  createdAt: string;
}

export interface AuditLogResponse {
  items: AuditLogItem[];
}

// ─── API ───
// 주의: apiClient baseURL 이 '/api' 이므로 호출부는 '/admin/...' 으로 시작 (`/api` 중복 금지)

export async function fetchDashboard(): Promise<DashboardResponse> {
  const res = await apiClient.get<ApiResponse<DashboardResponse>>('/admin/dashboard');
  return res.data.data;
}

export async function fetchAuditLog(): Promise<AuditLogResponse> {
  const res = await apiClient.get<ApiResponse<AuditLogResponse>>('/admin/dashboard/audit-log');
  return res.data.data;
}
```

- [ ] **Step 2: TypeScript 컴파일 확인**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/lib/admin/dashboard.ts
git commit -m "feat(admin-dashboard): 프론트 API 클라이언트 + 타입"
```

---

## Task 17: 공용 SectionError 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/dashboard/SectionError.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
'use client';

interface Props {
  message?: string;
  onRetry: () => void;
}

export function SectionError({ message = '데이터를 불러오지 못했습니다.', onRetry }: Props) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
      <p>{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100"
      >
        재시도
      </button>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/dashboard/SectionError.tsx
git commit -m "feat(admin-dashboard): 공용 SectionError 컴포넌트"
```

---

## Task 18: KpiCards 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/dashboard/KpiCards.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
'use client';

import type { DashboardKpi } from '@/lib/admin/dashboard';
import { Users, Wallet, Handshake, UserCheck } from 'lucide-react';

const KRW = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 });
const NUM = new Intl.NumberFormat('ko-KR');

function DeltaLabel({ value, percent }: { value: number; percent: number | null }) {
  if (percent === null) return <span className="text-slate-400">—</span>;
  const up = value >= 0;
  return (
    <span className={up ? 'text-emerald-600' : 'text-red-600'}>
      {up ? '▲' : '▼'} {Math.abs(percent).toFixed(1)}%
    </span>
  );
}

export function KpiCards({ kpi }: { kpi: DashboardKpi }) {
  const cards = [
    { label: '활성 회원', value: NUM.format(kpi.totalActiveUsers.current), icon: Users,
      sub: <>지난달 대비 <DeltaLabel value={kpi.totalActiveUsers.deltaFromLastMonth} percent={kpi.totalActiveUsers.deltaPercent} /></> },
    { label: '이번달 순매출', value: KRW.format(kpi.currentMonthRevenue.current), icon: Wallet,
      sub: <>지난달 대비 <DeltaLabel value={kpi.currentMonthRevenue.deltaFromLastMonth} percent={kpi.currentMonthRevenue.deltaPercent} /></> },
    { label: '누적 매칭', value: NUM.format(kpi.totalAcceptedMatchings.current), icon: Handshake,
      sub: <span className="text-slate-500">이번달 +{NUM.format(kpi.totalAcceptedMatchings.newThisMonth)}</span> },
    { label: '승인 멘토', value: NUM.format(kpi.approvedMentors.current), icon: UserCheck,
      sub: <span className="text-slate-500">대기 {NUM.format(kpi.approvedMentors.pending)}명</span> },
  ];

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((c) => {
        const Icon = c.icon;
        return (
          <div key={c.label} className="rounded-lg border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-sm font-medium">{c.label}</span>
              <Icon className="h-4 w-4" aria-hidden />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">{c.value}</div>
            <div className="mt-1 text-xs">{c.sub}</div>
          </div>
        );
      })}
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/dashboard/KpiCards.tsx
git commit -m "feat(admin-dashboard): KpiCards 4카드"
```

---

## Task 19: SignupTrendChart 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/dashboard/SignupTrendChart.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
'use client';

import type { SignupTrendPoint } from '@/lib/admin/dashboard';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

export function SignupTrendChart({ data }: { data: SignupTrendPoint[] }) {
  const chartData = data.map((p) => ({
    date: p.date.slice(5),  // MM-DD
    count: p.count,
  }));

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h3 className="mb-4 text-sm font-semibold text-slate-900">일별 신규 가입 (최근 30일)</h3>
      <div className="h-[240px]">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#94a3b8" />
            <YAxis allowDecimals={false} tick={{ fontSize: 11 }} stroke="#94a3b8" />
            <Tooltip
              contentStyle={{ fontSize: 12, borderRadius: 6 }}
              formatter={(v: number) => [`${v}명`, '신규 가입']}
            />
            <Line type="monotone" dataKey="count" stroke="#f97316" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/dashboard/SignupTrendChart.tsx
git commit -m "feat(admin-dashboard): SignupTrendChart 라인 차트"
```

---

## Task 20: RevenueTrendChart 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/dashboard/RevenueTrendChart.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
'use client';

import type { RevenueTrendPoint } from '@/lib/admin/dashboard';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, ReferenceLine } from 'recharts';

const KRW = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 });

export function RevenueTrendChart({ data }: { data: RevenueTrendPoint[] }) {
  const chartData = data.map((p) => ({
    month: p.month.slice(2),  // YY-MM
    gross: p.grossRevenue,
    refund: p.refundAmount,
    net: p.netRevenue,
  }));

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h3 className="mb-4 text-sm font-semibold text-slate-900">월별 순매출 (최근 12개월)</h3>
      <div className="h-[240px]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#94a3b8" />
            <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8"
                   tickFormatter={(v) => `${(v / 1_000_000).toFixed(0)}M`} />
            <Tooltip
              contentStyle={{ fontSize: 12, borderRadius: 6 }}
              formatter={(v: number, name: string) => {
                const label = name === 'gross' ? '매출' : name === 'refund' ? '환불' : '순매출';
                return [KRW.format(v), label];
              }}
            />
            <ReferenceLine y={0} stroke="#64748b" />
            <Bar dataKey="net" fill="#f97316" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/dashboard/RevenueTrendChart.tsx
git commit -m "feat(admin-dashboard): RevenueTrendChart 순매출 바 차트"
```

---

## Task 21: ActionQueue 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/dashboard/ActionQueue.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
'use client';

import Link from 'next/link';
import type { DashboardQueue } from '@/lib/admin/dashboard';
import { UserCheck, CreditCard, ChevronRight } from 'lucide-react';

export function ActionQueue({ queue }: { queue: DashboardQueue }) {
  const items = [
    {
      label: '승인 대기 멘토',
      count: queue.pendingMentorCount,
      href: '/admin/mentor',
      icon: UserCheck,
      emphasize: queue.pendingMentorCount > 0,
    },
    {
      label: '실패 결제',
      count: queue.failedPaymentCount,
      href: '/admin/payments?status=FAILED',
      icon: CreditCard,
      emphasize: queue.failedPaymentCount > 0,
    },
  ];

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-5 py-3">
        <h3 className="text-sm font-semibold text-slate-900">처리 큐</h3>
      </div>
      <ul className="divide-y divide-slate-200">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className="flex items-center justify-between px-5 py-4 hover:bg-slate-50"
              >
                <div className="flex items-center gap-3">
                  <Icon className="h-4 w-4 text-slate-500" aria-hidden />
                  <span className="text-sm text-slate-700">{item.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={
                    'text-sm font-semibold ' +
                    (item.emphasize ? 'text-amber-600' : 'text-slate-400')
                  }>
                    {item.count}{item.label.endsWith('멘토') ? '명' : '건'}
                  </span>
                  <ChevronRight className="h-4 w-4 text-slate-400" aria-hidden />
                </div>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/dashboard/ActionQueue.tsx
git commit -m "feat(admin-dashboard): ActionQueue 처리 큐"
```

---

## Task 22: RecentAuditLog 컴포넌트

**Files:**
- Create: `frontend/src/components/admin/dashboard/RecentAuditLog.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
'use client';

import Link from 'next/link';
import type { AuditLogItem } from '@/lib/admin/dashboard';

function formatRelative(iso: string): string {
  const d = new Date(iso).getTime();
  const now = Date.now();
  const diffMin = Math.round((now - d) / 60_000);
  if (diffMin < 1) return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHr = Math.round(diffMin / 60);
  if (diffHr < 24) return `${diffHr}시간 전`;
  const diffDay = Math.round(diffHr / 24);
  return `${diffDay}일 전`;
}

export function RecentAuditLog({ items }: { items: AuditLogItem[] }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-5 py-3">
        <h3 className="text-sm font-semibold text-slate-900">최근 관리자 활동</h3>
        <p className="text-xs text-slate-500">최대 10건 · SUPER_ADMIN 만 조회</p>
      </div>
      {items.length === 0 ? (
        <p className="px-5 py-6 text-sm text-slate-400">최근 활동 없음</p>
      ) : (
        <ul className="divide-y divide-slate-200">
          {items.map((item) => (
            <li key={item.id}>
              <Link href={item.targetHref} className="block px-5 py-3 hover:bg-slate-50">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-sm text-slate-800">
                      <span className="font-medium">{item.adminName}</span>: {item.description}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs text-slate-500">
                    {formatRelative(item.createdAt)}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/dashboard/RecentAuditLog.tsx
git commit -m "feat(admin-dashboard): RecentAuditLog SUPER_ADMIN 피드"
```

---

## Task 23: 대시보드 페이지 셸

**Files:**
- Create: `frontend/src/app/admin/dashboard/page.tsx`

- [ ] **Step 1: 페이지 작성**

```tsx
'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import * as api from '@/lib/admin/dashboard';
import { KpiCards } from '@/components/admin/dashboard/KpiCards';
import { SignupTrendChart } from '@/components/admin/dashboard/SignupTrendChart';
import { RevenueTrendChart } from '@/components/admin/dashboard/RevenueTrendChart';
import { ActionQueue } from '@/components/admin/dashboard/ActionQueue';
import { RecentAuditLog } from '@/components/admin/dashboard/RecentAuditLog';
import { SectionError } from '@/components/admin/dashboard/SectionError';

export default function AdminDashboardPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  const [summary, setSummary] = useState<api.DashboardResponse | null>(null);
  const [summaryError, setSummaryError] = useState(false);
  const [auditLog, setAuditLog] = useState<api.AuditLogResponse | null>(null);
  const [auditLogError, setAuditLogError] = useState(false);
  const [summaryReloadKey, setSummaryReloadKey] = useState(0);
  const [auditReloadKey, setAuditReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setSummaryError(false);
    setSummary(null);
    api.fetchDashboard()
      .then((r) => { if (!ignore) setSummary(r); })
      .catch(() => { if (!ignore) setSummaryError(true); });
    return () => { ignore = true; };
  }, [summaryReloadKey]);

  useEffect(() => {
    if (!isSuperAdmin) return;
    let ignore = false;
    setAuditLogError(false);
    setAuditLog(null);
    api.fetchAuditLog()
      .then((r) => { if (!ignore) setAuditLog(r); })
      .catch(() => { if (!ignore) setAuditLogError(true); });
    return () => { ignore = true; };
  }, [isSuperAdmin, auditReloadKey]);

  const retrySummary = useCallback(() => setSummaryReloadKey((k) => k + 1), []);
  const retryAudit = useCallback(() => setAuditReloadKey((k) => k + 1), []);

  const today = new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });

  return (
    <div className="space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">대시보드</h1>
        <p className="mt-1 text-sm text-slate-500">관리자 콘솔 홈 · {today} 기준</p>
      </header>

      {/* KPI */}
      <section>
        {summaryError ? (
          <SectionError onRetry={retrySummary} />
        ) : summary ? (
          <KpiCards kpi={summary.kpi} />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[0,1,2,3].map((i) => (
              <div key={i} className="h-[100px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
            ))}
          </div>
        )}
      </section>

      {/* 차트 */}
      <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {summaryError ? (
          <>
            <SectionError onRetry={retrySummary} />
            <SectionError onRetry={retrySummary} />
          </>
        ) : summary ? (
          <>
            <SignupTrendChart data={summary.signupTrend} />
            <RevenueTrendChart data={summary.revenueTrend} />
          </>
        ) : (
          <>
            <div className="h-[280px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
            <div className="h-[280px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
          </>
        )}
      </section>

      {/* 처리 큐 */}
      <section>
        {summaryError ? null : summary ? (
          <ActionQueue queue={summary.queue} />
        ) : (
          <div className="h-[130px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
        )}
      </section>

      {/* 감사 로그 (SUPER_ADMIN 전용) */}
      {isSuperAdmin && (
        <section>
          {auditLogError ? (
            <SectionError onRetry={retryAudit} />
          ) : auditLog ? (
            <RecentAuditLog items={auditLog.items} />
          ) : (
            <div className="h-[200px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
          )}
        </section>
      )}
    </div>
  );
}
```

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/app/admin/dashboard/page.tsx
git commit -m "feat(admin-dashboard): 대시보드 페이지 셸 + 섹션 에러 재시도"
```

---

## Task 24: AdminSidebar 에 대시보드 메뉴 최상단 추가

**Files:**
- Modify: `frontend/src/components/admin/AdminSidebar.tsx`

- [ ] **Step 1: NAV_ITEMS 배열 맨 앞에 대시보드 추가**

기존 파일의 `NAV_ITEMS` 상단 import 수정 (`LayoutDashboard` 추가):

```tsx
import { LayoutDashboard, UserCheck, Users, CreditCard, FileText, ShieldCheck } from 'lucide-react';
```

그리고 NAV_ITEMS 배열 시작 부분에 항목 추가:

```tsx
const NAV_ITEMS: Array<{
  href: string;
  label: string;
  icon: typeof Users;
  match: (pathname: string) => boolean;
  requireSuperAdmin?: boolean;
}> = [
  {
    href: '/admin/dashboard',
    label: '대시보드',
    icon: LayoutDashboard,
    match: (p) => p === '/admin/dashboard' || p.startsWith('/admin/dashboard/'),
  },
  {
    href: '/admin/mentor',
    ...
```

(기존 나머지 항목들은 순서 유지)

- [ ] **Step 2: TypeScript 확인 + 커밋**

```bash
cd frontend && npx tsc --noEmit
git add frontend/src/components/admin/AdminSidebar.tsx
git commit -m "feat(admin-dashboard): AdminSidebar 대시보드 메뉴 최상단 추가"
```

---

## Task 25: /admin 루트 리다이렉트 대상 변경

**Files:**
- Modify: `frontend/src/app/admin/page.tsx`

- [ ] **Step 1: 리다이렉트 대상 변경**

```tsx
import { redirect } from 'next/navigation';

/**
 * /admin 진입 시 대시보드로 리다이렉트.
 */
export default function AdminIndex() {
  redirect('/admin/dashboard');
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/app/admin/page.tsx
git commit -m "feat(admin-dashboard): /admin 루트 대시보드로 리다이렉트"
```

---

## Task 26: 프론트 빌드 검증

- [ ] **Step 1: TypeScript 전체 체크**

```bash
cd frontend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 2: Next.js 프로덕션 빌드**

```bash
cd frontend && npm run build
```

Expected: `✓ Compiled successfully`, `/admin/dashboard` 라우트가 빌드 결과에 포함.

실패 시 오류 메시지 따라 수정.

---

## Task 27: 로컬 수동 검증

- [ ] **Step 1: 백엔드 시작**

```bash
cd backend && ./gradlew bootRun
```

(다른 터미널) 프론트 시작:

```bash
cd frontend && npm run dev
```

- [ ] **Step 2: SUPER_ADMIN 으로 로그인 → /admin 접속**

- `/admin` 이 `/admin/dashboard` 로 리다이렉트되는지 확인
- 4 KPI 카드, 2 차트, 처리 큐, 감사 로그 섹션 모두 렌더링 확인
- 네트워크 탭에서 `/api/admin/dashboard` + `/api/admin/dashboard/audit-log` 두 요청 확인

- [ ] **Step 3: ADMIN 으로 로그인 → /admin/dashboard 접속**

- 감사 로그 섹션 **자체가 DOM 에 없음** 확인 (DevTools 로 검증)
- 네트워크 탭에서 `/api/admin/dashboard/audit-log` 가 **호출되지 않음** 확인

- [ ] **Step 4: MENTEE 로 로그인 → /admin/dashboard 접속**

- 403 또는 AdminLayout "접근 권한 없음" 렌더 확인

- [ ] **Step 5: 빈 데이터 시뮬레이션**

시드 없이 실행 → KPI 가 모두 0, delta 가 "—", 차트가 빈 그리드, "최근 활동 없음" 플레이스홀더 확인.

- [ ] **Step 6: 실패 시나리오**

백엔드 중단 → 프론트 새로고침 → 각 섹션에 "에러 · 재시도" 버튼 렌더, 백엔드 재시작 후 버튼 클릭 → 정상 재요청.

---

## Task 28: 문서 업데이트

**Files:**
- Modify: `docs/mockups/admin-console-overview.md`
- Modify: `ROADMAP.md`

- [ ] **Step 1: admin-console-overview.md 업데이트**

"Phase별 메뉴 로드맵" 테이블의 "대시보드 / 통계" 행을 업데이트:

```md
| III | 대시보드 / 통계 | [admin-dashboard.md](./admin-dashboard.md) | 2026-04-24 구현 완료 |
```

사이드바 다이어그램도 📊 대시보드를 최상단으로 이동:

```
│ 📊 대시보드   │
│ 🧑 멘토 심사 │
│ 👥 회원 관리 │
│ 💳 결제 관리 │
│ 📝 게시물    │
```

- [ ] **Step 2: ROADMAP.md §10 배포 체크리스트에 항목 추가**

§10 의 "10. Phase II Feature 3 (게시물 관리) 배포 시..." 아래에:

```md
11. Phase III Feature 1 (관리자 대시보드) 배포 시 추가 작업 **없음**
    - DDL 변경 없음 (Phase II Feature 1~3 의 컬럼 재사용)
    - 환경변수 없음
    - feature flag 없음
    - `/admin` → `/admin/dashboard` 리다이렉트 변경은 프런트 배포로 즉시 반영
```

§10 상단 Admin API 테이블의 `GET /api/admin/dashboard` 행에 ✅ 체크 표시 (예: `GET /api/admin/dashboard` ✅).

- [ ] **Step 3: 커밋**

```bash
git add docs/mockups/admin-console-overview.md ROADMAP.md
git commit -m "docs(admin-dashboard): overview 상태 + ROADMAP 배포 메모 업데이트"
```

---

## Task 29: 최종 검증 + 푸시

- [ ] **Step 1: 전체 백엔드 테스트**

```bash
cd backend && ./gradlew clean test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 전체 프론트 빌드**

```bash
cd frontend && npm run build
```

Expected: 성공.

- [ ] **Step 3: git log 확인**

```bash
git log --oneline -20
```

커밋이 작업 순서대로 깔끔히 정렬되었는지 확인. 의도한 4 논리 그룹:
1. mockup + chart 의존성
2. 백엔드 (DTO → Repo → Service → Controller → Test)
3. 프런트 (API → 컴포넌트 → 페이지 → 사이드바/리다이렉트)
4. 문서 업데이트

- [ ] **Step 4: push + PR 생성**

```bash
git push -u origin claude/admin-dashboard
gh pr create --title "feat(admin-dashboard): Phase III Feature 1 — 관리자 콘솔 홈 대시보드" --body "$(cat <<'EOF'
## Summary

- `/admin/dashboard` 신설 — KPI 4 + 추이 차트 2 + 처리 큐 2 + (SUPER_ADMIN 한정) 감사 로그 피드
- 엔드포인트 2개: `GET /api/admin/dashboard` (ADMIN+), `GET /api/admin/dashboard/audit-log` (SUPER_ADMIN)
- DDL 변경 없음 — Phase II Feature 1~3 에서 준비된 컬럼만 사용
- `/admin` 루트 리다이렉트 대상을 `/admin/dashboard` 로 변경

## Test plan

- [x] 백엔드 `./gradlew clean test` 통과 (Service 4 + Controller 5 신규)
- [x] 프론트 `npm run build` 성공
- [x] 로컬 수동 검증 완료 (SUPER_ADMIN / ADMIN / MENTEE / 빈데이터 / 재시도)

## Spec

`docs/superpowers/specs/2026-04-24-admin-dashboard-design.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of Scope (스펙 §8 재확인)

- ❌ CSV/Excel export
- ❌ 기간 선택 UI
- ❌ 차트 드릴다운
- ❌ Redis 캐시 / `@Cacheable`
- ❌ 실시간 업데이트 (WebSocket/SSE)
- ❌ 신고 기반 처리 큐

# Phase II Feature 2 — 관리자 결제 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 `/admin/payments` 에서 전체 결제를 조회 · 요약 파악 · 강제 환불(전액) 할 수 있게 한다. 환불 시 연관 매칭이 소프트 캐스케이드로 취소되어 LMS 접근이 자동 차단된다.

**Architecture:** 접근법 1(기존 엔티티 확장). `AdminPaymentService` 가 기존 `TossPaymentService.cancelPayment` 를 재사용하고 `AdminAuditLogService`(Common) 를 호출. `PaymentRepository` 에 `JpaSpecificationExecutor` 를 믹스인하여 다중 필터. FE 는 shadcn `popover + calendar` 신규 설치 후 목록·상세 페이지 + 환불 다이얼로그. Feature 2 재사용용 공용 UI 3종(`AdminListHeader / AdminTabs / AdminStatusBadge`) 은 본 피처 착수 직전 신규 설계·생성하고 Feature 1 도 이 컴포넌트를 사용하도록 리팩터 (기존 `Pagination / DebouncedSearchInput` 은 이미 공용화되어 있어 이동 없이 재사용).

**Tech Stack:** Spring Boot 3 / Java 17 / JPA(Hibernate) / Spring Security 6 / JUnit 5 + Mockito — Next.js App Router / React 18 / shadcn-ui / react-hook-form + zod / TanStack Query — MySQL 8 / Redis.

**선행 조건:**
- PR #42 (Phase II Common + Feature 1 회원 관리) 가 main 에 머지되어 있을 것
- 본 브랜치는 post-merge main 에서 분기 (worktree 생성은 `superpowers:using-git-worktrees` 로)
- 스펙 문서: `docs/superpowers/specs/2026-04-23-admin-payments-design.md`

---

## 파일 구조 요약

### 백엔드

**신규 파일**

- `backend/src/main/java/com/devmatch/controller/AdminPaymentController.java`
- `backend/src/main/java/com/devmatch/service/AdminPaymentService.java`
- `backend/src/main/java/com/devmatch/config/TossCancelProperties.java`
- `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentFilter.java`
- `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentListItemResponse.java`
- `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentDetailResponse.java`
- `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentSummaryResponse.java`
- `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentRefundRequest.java`
- `backend/src/main/java/com/devmatch/repository/spec/PaymentSpecifications.java`
- `backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`

**수정 파일**

- `backend/src/main/java/com/devmatch/entity/Payment.java` (컬럼 2개 + `markProcessedByAdmin` 메서드)
- `backend/src/main/java/com/devmatch/entity/MatchingStatus.java` (CANCELLED 추가)
- `backend/src/main/java/com/devmatch/entity/Matching.java` (`cancel(reason)` 메서드 추가)
- `backend/src/main/java/com/devmatch/repository/PaymentRepository.java` (JpaSpecificationExecutor, 요약 쿼리)
- `backend/src/main/java/com/devmatch/config/DataInitializer.java` (결제 seed 7건 추가)
- `backend/src/main/resources/application.yml` (`app.payment.toss-cancel-enabled`)
- `backend/src/main/resources/application-prod.yml` (동일, `true`)
- `backend/src/main/resources/application-dev.yml` (신규 생성 필요 시, `false`)

### 프런트엔드

**신규 파일**

- `frontend/src/app/admin/payments/page.tsx` — 목록
- `frontend/src/app/admin/payments/[id]/page.tsx` — 상세
- `frontend/src/app/admin/payments/_components/PaymentListTable.tsx`
- `frontend/src/app/admin/payments/_components/PaymentSummaryCards.tsx`
- `frontend/src/app/admin/payments/_components/PaymentDetailSection.tsx`
- `frontend/src/app/admin/payments/_components/PaymentRefundDialog.tsx`
- `frontend/src/app/admin/payments/_api/adminPaymentApi.ts`
- `frontend/src/app/admin/payments/_types.ts`

**공용 컴포넌트 신규 파일 (Task 0, Task 13)**

- `frontend/src/components/admin/AdminListHeader.tsx` (Task 0)
- `frontend/src/components/admin/AdminTabs.tsx` (Task 0)
- `frontend/src/components/admin/AdminStatusBadge.tsx` (Task 0)
- `frontend/src/components/admin/AdminDateRangePicker.tsx` (Task 13 — popover+calendar 설치 후)

**기존 공용 컴포넌트 (변경·이동 없음, import 만)**

- `frontend/src/components/admin/Pagination.tsx`
- `frontend/src/components/admin/DebouncedSearchInput.tsx`

### 문서

- `docs/smoke/<스모크-실행일>-admin-payments-smoke.md` (신규)
- `ROADMAP.md` (§10 배포 체크리스트에 ALTER SQL 추가)

---

## Task 0: 공용 FE 컴포넌트 신규 생성 + Feature 1 리팩터

**목적:** Feature 2 가 재사용할 3개의 공용 UI (`AdminListHeader`, `AdminTabs`, `AdminStatusBadge`) 를 신규 설계·생성하고, Feature 1 (회원 관리) 를 이 컴포넌트들을 사용하도록 리팩터. 기존 `Pagination` / `DebouncedSearchInput` 은 이미 `frontend/src/components/admin/` 에 존재하므로 이동·변경하지 않는다.

**전제 확인 (2026-04-23 컨트롤러 검증):**
- Feature 1 실제 경로: `frontend/src/app/admin/users/page.tsx` (목록), `frontend/src/app/admin/users/[id]/page.tsx` (상세), `frontend/src/app/admin/admins/page.tsx` (SUPER_ADMIN 전용) — route group 미사용
- Feature 1 은 인라인으로 `ROLE_TABS / STATUS_TABS` 배열을 선언하고 `<button>` 매핑 jsx 를 직접 작성 (컴포넌트 아님)
- Feature 1 은 인라인으로 `ROLE_BADGE / STATUS_BADGE: Record<Enum, string>` 맵을 선언하고 `<span className={MAP[value]}>` 매핑 jsx 를 직접 작성 (컴포넌트 아님)
- `AdminListHeader` 에 해당하는 것은 페이지 상단 제목 + 설명 + 우측 액션(있으면)  의 인라인 JSX — 재사용 가능한 형태로 뽑히지 않았다
- 따라서 "추출" 이 아니라 "신규 설계 + Feature 1 마이그레이션"

**Files:**
- Create: `frontend/src/components/admin/AdminListHeader.tsx`
- Create: `frontend/src/components/admin/AdminTabs.tsx`
- Create: `frontend/src/components/admin/AdminStatusBadge.tsx`
- Modify: `frontend/src/app/admin/users/page.tsx`
- Modify: `frontend/src/app/admin/users/[id]/page.tsx`
- Modify: `frontend/src/app/admin/admins/page.tsx` (존재할 경우)
- (Task 13 에서 `AdminDateRangePicker.tsx` 추가 — 본 피처에서 신규 설치하는 popover+calendar 의존)

**비 범위 (하지 않음):**
- `Pagination.tsx` / `DebouncedSearchInput.tsx` 의 위치 이동 (불필요한 churn)
- 이 컴포넌트들의 내부 동작 변경

### Step 1: `AdminStatusBadge.tsx` 생성

라벨 + 색상 클래스로 배지를 렌더하는 제네릭 컴포넌트. value 를 모르는 채로 매핑 책임을 호출 측에 둔다.

```tsx
// frontend/src/components/admin/AdminStatusBadge.tsx
type Props = {
  label: string;
  className?: string; // e.g. "bg-emerald-100 text-emerald-800"
};

export function AdminStatusBadge({ label, className = 'bg-zinc-100 text-zinc-700' }: Props) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
```

### Step 2: `AdminTabs.tsx` 생성

제네릭 탭 스트립. value/label 배열과 현재 값·onChange 만 받는다 (shadcn tabs 재사용 여부는 구현자 판단).

```tsx
// frontend/src/components/admin/AdminTabs.tsx
type TabItem<V extends string> = { value: V; label: string };

type Props<V extends string> = {
  items: TabItem<V>[];
  value: V;
  onChange: (next: V) => void;
  ariaLabel?: string;
};

export function AdminTabs<V extends string>({ items, value, onChange, ariaLabel }: Props<V>) {
  return (
    <div role="tablist" aria-label={ariaLabel} className="inline-flex gap-1 rounded-lg border border-slate-200 bg-white p-1">
      {items.map((t) => {
        const active = t.value === value;
        return (
          <button
            key={t.value}
            role="tab"
            aria-selected={active}
            onClick={() => onChange(t.value)}
            className={`px-3 py-1.5 text-sm rounded-md transition ${
              active ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'
            }`}
          >
            {t.label}
          </button>
        );
      })}
    </div>
  );
}
```

- 구현자는 Feature 1 의 기존 탭 시각 규격 (색/패딩) 을 먼저 확인해 일치시킨다. 위 클래스는 초안이며, Feature 1 의 현재 스타일이 다르면 **Feature 1 규격을 그대로 복사** 한다 (시각적 회귀 금지).

### Step 3: `AdminListHeader.tsx` 생성

페이지 상단 (제목 + 설명 + 우측 액션 슬롯). 액션은 `actions?: ReactNode` 로 받아 호출 측에서 자유 구성.

```tsx
// frontend/src/components/admin/AdminListHeader.tsx
import type { ReactNode } from 'react';

type Props = {
  title: string;
  description?: string;
  actions?: ReactNode;
};

export function AdminListHeader({ title, description, actions }: Props) {
  return (
    <header className="flex items-end justify-between gap-4 pb-4 border-b border-slate-200 mb-6">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="shrink-0">{actions}</div>}
    </header>
  );
}
```

- Feature 1 의 현재 제목 영역 스타일을 확인해 시각적 회귀 없이 이식할 것.

### Step 4: Feature 1 `users/page.tsx` 리팩터

- `ROLE_TABS` / `STATUS_TABS` 배열은 유지 (데이터). 기존 인라인 `<button>` 매핑 JSX 를 `<AdminTabs items={ROLE_TABS} value={role} onChange={setRole} ariaLabel="역할 필터">` 로 대체.
- 목록 행의 `<span className={ROLE_BADGE[u.role]}>{ROLE_KO[u.role]}</span>` 를 `<AdminStatusBadge label={ROLE_KO[u.role]} className={ROLE_BADGE[u.role]} />` 로 대체.
- 상단 제목 영역을 `<AdminListHeader title="회원 관리" description="..." />` 로 대체 (현재 문구 유지).

### Step 5: Feature 1 `users/[id]/page.tsx` 리팩터

- 인라인 `ROLE_BADGE / STATUS_BADGE / ROLE_KO / STATUS_KO` 유지 (상세 페이지에서도 같은 맵 사용). 배지 렌더만 `<AdminStatusBadge label={...} className={...} />` 로 교체.
- 페이지 상단 타이틀 영역이 있으면 `<AdminListHeader />` 로 교체. (백 버튼/편집 액션이 있으면 `actions` 슬롯에 배치)

### Step 6: Feature 1 `admins/page.tsx` 리팩터 (파일 존재 시)

동일 패턴 적용. 파일이 없으면 스킵.

### Step 7: 타입 체크 + 빌드

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: 타입 에러 0, 빌드 성공.

### Step 8: Feature 1 수동 시각 회귀 확인

dev 서버 실행 후 `/admin/users` 목록·상세, `/admin/admins` 진입. 다음 요소가 리팩터 전후 동일해야 함:
- 탭 스트립의 색·간격·활성 표시
- 역할/상태 배지의 색상·라벨
- 페이지 상단 타이틀 영역

시각적 회귀가 발견되면 해당 컴포넌트의 className 을 Feature 1 원본과 일치시킨다.

### Step 9: Commit

```bash
git add frontend/src/components/admin frontend/src/app/admin/users frontend/src/app/admin/admins
git commit -m "refactor(admin): 공용 AdminListHeader/AdminTabs/AdminStatusBadge 컴포넌트 신규 생성 + Feature 1 적용"
```

---

## Task 1: Payment 엔티티 컬럼 확장

**목적:** `processed_by_admin_id`, `cancelled_at` 컬럼 추가 + `markProcessedByAdmin` 도메인 메서드. 기존 `cancel(reason)` 은 유지 (사용자 자기 취소 경로 재사용).

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/Payment.java`

- [ ] **Step 1: 컬럼 필드 2개 추가**

`Payment.java` 의 `cancelReason` 필드 바로 아래에 추가:

```java
@Column(name = "processed_by_admin_id")
private Long processedByAdminId;

@Column(name = "cancelled_at")
private java.time.LocalDateTime cancelledAt;
```

- [ ] **Step 2: `markProcessedByAdmin` 메서드 추가**

기존 `cancel(String)` 바로 아래:

```java
/**
 * 관리자 강제 환불 경로에서 호출. 처리자 + 처리일시 기록.
 * 반드시 cancel(reason) 이후에 호출되어야 한다 (순서 의존).
 */
public void markProcessedByAdmin(Long adminId, java.time.LocalDateTime at) {
    this.processedByAdminId = adminId;
    this.cancelledAt = at;
}
```

- [ ] **Step 3: 애플리케이션 부팅으로 ddl-auto=update 적용 확인**

Run: `cd backend && ./gradlew bootRun` 실행 후 MySQL 에서 컬럼 존재 확인:
```sql
DESCRIBE payments;
```
Expected: `processed_by_admin_id BIGINT NULL`, `cancelled_at DATETIME(6) NULL`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/Payment.java
git commit -m "feat(payment): processed_by_admin_id/cancelled_at 컬럼 + markProcessedByAdmin 메서드"
```

---

## Task 2: MatchingStatus.CANCELLED + Matching.cancel(reason)

**목적:** 환불 시 매칭 상태를 `CANCELLED` 로 전이. `LmsAccessService` 의 allow-list(`TRIAL`/`ACCEPTED` 만 허용) 덕에 가드 확장 불필요.

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/MatchingStatus.java`
- Modify: `backend/src/main/java/com/devmatch/entity/Matching.java`

- [ ] **Step 1: enum 에 CANCELLED 추가**

`MatchingStatus.java`:
```java
public enum MatchingStatus {
    PENDING, ACCEPTED, REJECTED, TRIAL, SWAPPED, CANCELLED
}
```

- [ ] **Step 2: `Matching.cancel(reason)` 메서드 추가**

`Matching.java` 의 `confirmAfterTrial()` 아래:

```java
/**
 * 관리자 강제 환불에 의한 매칭 취소. 기존 rejectedReason 컬럼을 재사용해 사유를 남긴다.
 */
public void cancel(String reason) {
    this.status = MatchingStatus.CANCELLED;
    this.rejectedReason = reason;
}
```

- [ ] **Step 3: `LmsAccessService` 가 CANCELLED 를 이미 차단하는지 확인**

Run:
```bash
rg -n "status != MatchingStatus" backend/src/main/java/com/devmatch/service/LmsAccessService.java
```
Expected: `if (status != MatchingStatus.TRIAL && status != MatchingStatus.ACCEPTED)` — allow-list 패턴 확인. 변경 없음.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/MatchingStatus.java backend/src/main/java/com/devmatch/entity/Matching.java
git commit -m "feat(matching): CANCELLED 상태 + cancel(reason) 도메인 메서드"
```

---

## Task 3: TossCancelProperties 설정 바인딩

**목적:** 환경별 토스 환불 API 호출 여부를 플래그로 제어.

**Files:**
- Create: `backend/src/main/java/com/devmatch/config/TossCancelProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-prod.yml`
- Create: `backend/src/main/resources/application-dev.yml` (없을 경우)

- [ ] **Step 1: `TossCancelProperties` 레코드 생성**

```java
package com.devmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payment")
public record TossCancelProperties(boolean tossCancelEnabled) {
    public TossCancelProperties {
        // 기본값: Kotlin-like record 에서는 ConfigurationProperties 가 null 바인딩 시
        // boolean 기본값 false 로 처리. yml 에서 명시적으로 세팅한다.
    }
}
```

- [ ] **Step 2: 메인 애플리케이션에 바인딩 활성화**

`DevMatchApplication.java` 에 이미 `@ConfigurationPropertiesScan` 또는 `@EnableConfigurationProperties` 가 있는지 확인:

Run:
```bash
rg -n "EnableConfigurationProperties|ConfigurationPropertiesScan" backend/src/main/java/com/devmatch/DevMatchApplication.java
```

없으면 클래스 선언부에 추가:
```java
@SpringBootApplication
@ConfigurationPropertiesScan("com.devmatch.config")
public class DevMatchApplication { ... }
```

- [ ] **Step 3: yml 에 키 추가 (공통)**

`application.yml` 의 최하단에 추가:
```yaml
# ===== 관리자 환불 =====
app:
  payment:
    toss-cancel-enabled: true
```

`application-prod.yml` 에도 동일하게 명시(prod 에서 절대 false 가 되지 않도록):
```yaml
app:
  payment:
    toss-cancel-enabled: true
```

- [ ] **Step 4: dev 프로파일 파일 생성/갱신**

`application-dev.yml` (없으면 신규):
```yaml
app:
  payment:
    toss-cancel-enabled: false
```

- [ ] **Step 5: 바인딩 검증**

Run: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'` 후 컨텍스트 부팅 확인. 에러 없이 뜨면 OK.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/config/TossCancelProperties.java backend/src/main/java/com/devmatch/DevMatchApplication.java backend/src/main/resources/application*.yml
git commit -m "feat(payment): app.payment.toss-cancel-enabled 플래그 + 환경별 yml"
```

---

## Task 4: PaymentRepository — Specification + 요약 쿼리

**목적:** 다중 옵셔널 필터(status · q · 기간) 지원 및 기간별 요약 집계.

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/PaymentRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/spec/PaymentSpecifications.java`

- [ ] **Step 1: Repository 에 JpaSpecificationExecutor 믹스인 + 요약 쿼리 추가**

```java
package com.devmatch.repository;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    // 기존 메서드들 (변경 없음) ...
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByMatchingId(Long matchingId);
    boolean existsByApplicationId(Long applicationId);
    long countByUserIdAndStatus(Long userId, PaymentStatus status);
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    // ===== Phase II 관리자 =====

    @Query("""
           select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status
              and p.createdAt >= :from and p.createdAt < :toExclusive
           """)
    long sumAmountByStatusAndCreatedBetween(@Param("status") PaymentStatus status,
                                            @Param("from") LocalDateTime from,
                                            @Param("toExclusive") LocalDateTime toExclusive);

    @Query("""
           select count(p) from Payment p
            where p.status = :status
              and p.createdAt >= :from and p.createdAt < :toExclusive
           """)
    long countByStatusAndCreatedBetween(@Param("status") PaymentStatus status,
                                        @Param("from") LocalDateTime from,
                                        @Param("toExclusive") LocalDateTime toExclusive);
}
```

- [ ] **Step 2: `PaymentSpecifications` 유틸 생성**

```java
package com.devmatch.repository.spec;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PaymentSpecifications {
    private PaymentSpecifications() {}

    public static Specification<Payment> withFilter(PaymentStatus status,
                                                    String q,
                                                    LocalDateTime from,
                                                    LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (toExclusive != null) {
                ps.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim() + "%";
                // orderId 우선 매칭 (가장 흔한 케이스). 사용자 이름/이메일은 서비스 레이어에서
                // userId IN (SELECT ...) 형태로 선조회 후 필터링해도 된다.
                ps.add(cb.like(root.get("orderId"), like));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
```

> 참고: 사용자 이름/이메일 검색은 `UserRepository.findByNameContainingOrEmailContaining` 의 결과 userId 집합을 Spec 의 `root.get("userId").in(ids)` 로 추가 결합. 구현은 Task 7 참조.

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devmatch/repository/PaymentRepository.java backend/src/main/java/com/devmatch/repository/spec/PaymentSpecifications.java
git commit -m "feat(payment): PaymentRepository Specification + 요약 집계 쿼리"
```

---

## Task 5: Admin 결제 DTO 5개

**목적:** 컨트롤러 ↔ 서비스 계약 확정.

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentFilter.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentListItemResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentDetailResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentSummaryResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/payment/AdminPaymentRefundRequest.java`

- [ ] **Step 1: `AdminPaymentFilter`**

```java
package com.devmatch.dto.admin.payment;

import com.devmatch.entity.PaymentStatus;
import java.time.LocalDate;

public record AdminPaymentFilter(
        PaymentStatus status, // null = ALL
        String q,             // null/blank 허용
        LocalDate from,       // null 이면 서비스 레이어에서 기본 최근 30일 적용
        LocalDate to          // null 이면 today
) {}
```

- [ ] **Step 2: `AdminPaymentListItemResponse`**

```java
package com.devmatch.dto.admin.payment;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import java.time.LocalDateTime;

public record AdminPaymentListItemResponse(
        Long id,
        String orderId,
        Long userId,
        String userName,
        String userEmail,
        Integer amount,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        Long matchingId
) {
    public static AdminPaymentListItemResponse of(Payment p, String userName, String userEmail) {
        return new AdminPaymentListItemResponse(
                p.getId(),
                p.getOrderId(),
                p.getUserId(),
                userName,
                userEmail,
                p.getAmount(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getCancelledAt(),
                p.getMatchingId()
        );
    }
}
```

- [ ] **Step 3: `AdminPaymentDetailResponse`**

```java
package com.devmatch.dto.admin.payment;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import java.time.LocalDateTime;

public record AdminPaymentDetailResponse(
        Long id,
        String orderId,
        String paymentKey,
        Integer amount,
        Integer discountApplied,
        Integer installmentMonths,
        String courseType,
        Integer monthsBundled,
        Integer renewalCount,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        UserSection user,
        ApplicationSection application,
        MatchingSection matching,  // nullable
        RefundSection refund       // nullable — CANCELLED 일 때 세팅
) {
    public record UserSection(Long id, String name, String email, String role) {}
    public record ApplicationSection(Long id, String category) {}
    public record MatchingSection(Long id, String mentorName, String status) {}
    public record RefundSection(Long processedByAdminId, String processedByAdminName, LocalDateTime cancelledAt, String reason) {}
}
```

- [ ] **Step 4: `AdminPaymentSummaryResponse`**

```java
package com.devmatch.dto.admin.payment;

public record AdminPaymentSummaryResponse(
        long totalAmount,
        long confirmedCount,
        long refundedAmount,
        double refundRate  // 0.0 ~ 1.0, 소수 3자리 이하 반올림은 FE 표시에서 처리
) {}
```

- [ ] **Step 5: `AdminPaymentRefundRequest`**

```java
package com.devmatch.dto.admin.payment;

import jakarta.validation.constraints.Size;

public record AdminPaymentRefundRequest(
        @Size(min = 10, max = 500, message = "사유는 10~500자로 입력해주세요")
        String reason
) {}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/dto/admin/payment
git commit -m "feat(admin-payment): DTO 5개 (filter/listItem/detail/summary/refund)"
```

---

## Task 6: AdminPaymentService — 스켈레톤 + 조회 메서드 (TDD)

**목적:** `@Transactional(readOnly=true)` 하에 `listPayments` · `getSummary` · `getDetail` 구현. 단위 테스트 먼저.

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`
- Create: `backend/src/main/java/com/devmatch/service/AdminPaymentService.java`

- [ ] **Step 1: 테스트 스켈레톤 — 실패하는 `getSummary_refundRate_분모0_반환0` 테스트 먼저**

```java
package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.AdminPaymentSummaryResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private MatchingRepository matchingRepository;
    @Mock private UserRepository userRepository;
    @Mock private TossPaymentService tossPaymentService;
    @Mock private AdminAuditLogService auditLogService;

    private TossCancelProperties props(boolean enabled) {
        return new TossCancelProperties(enabled);
    }

    @Test
    void getSummary_확정_환불_0건이면_환불률은_0() {
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(0L);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(false));

        AdminPaymentSummaryResponse res = svc.getSummary(LocalDate.of(2026,4,1), LocalDate.of(2026,4,30));

        assertThat(res.refundRate()).isEqualTo(0.0);
        assertThat(res.totalAmount()).isZero();
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 에러(서비스 없음)로 FAIL 확인**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 컴파일 실패 (`AdminPaymentService` 없음) — 기대되는 FAIL.

- [ ] **Step 3: `AdminPaymentService` 스켈레톤 구현**

`AdminAuditLogService` 는 PR #42 Common 에서 도입됨. 실제 시그니처 (main 기준 확인 완료):
```java
void record(Long adminId,
            AdminActionType actionType,    // enum (String 아님)
            String targetType,
            Long targetId,
            String reason,                 // 환불 사유 등 도메인 사유 문자열
            Map<String, Object> metadata); // 서비스가 JSON 직렬화 담당
```
- `actionType` 는 `AdminActionType.PAYMENT_REFUND` enum 값을 사용 (이미 main 에 존재)
- `metadata` 값 타입은 String/Number/Boolean/Enum.name() 4종만 (스펙 §4.4)

```java
package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.*;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final MatchingRepository matchingRepository;
    private final UserRepository userRepository;
    private final TossPaymentService tossPaymentService;
    private final AdminAuditLogService auditLogService;
    private final TossCancelProperties tossCancelProperties;

    public AdminPaymentSummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDateTime[] range = resolveRange(from, to);
        long totalAmount   = paymentRepository.sumAmountByStatusAndCreatedBetween(PaymentStatus.CONFIRMED, range[0], range[1]);
        long confirmedCnt  = paymentRepository.countByStatusAndCreatedBetween(PaymentStatus.CONFIRMED, range[0], range[1]);
        long refundedAmt   = paymentRepository.sumAmountByStatusAndCreatedBetween(PaymentStatus.CANCELLED, range[0], range[1]);
        long refundedCnt   = paymentRepository.countByStatusAndCreatedBetween(PaymentStatus.CANCELLED, range[0], range[1]);
        double refundRate  = (confirmedCnt + refundedCnt) == 0
                ? 0.0
                : (double) refundedCnt / (confirmedCnt + refundedCnt);
        return new AdminPaymentSummaryResponse(totalAmount, confirmedCnt, refundedAmt, refundRate);
    }

    static LocalDateTime[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate toDate   = to   != null ? to   : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(30);
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from 이 to 보다 늦을 수 없습니다");
        }
        return new LocalDateTime[]{
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay() // to-exclusive
        };
    }
}
```

- [ ] **Step 4: 테스트 실행 — PASS 확인**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: `getSummary_확정_환불_0건이면_환불률은_0` PASS.

- [ ] **Step 5: 요약 해피 패스 테스트 추가**

`AdminPaymentServiceTest` 에 추가:
```java
@Test
void getSummary_요약_계산_정상() {
    when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
            .thenReturn(12_450_000L);
    when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
            .thenReturn(142L);
    when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
            .thenReturn(820_000L);
    when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
            .thenReturn(8L);

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    AdminPaymentSummaryResponse res = svc.getSummary(LocalDate.of(2026,3,22), LocalDate.of(2026,4,22));

    assertThat(res.totalAmount()).isEqualTo(12_450_000);
    assertThat(res.confirmedCount()).isEqualTo(142);
    assertThat(res.refundedAmount()).isEqualTo(820_000);
    assertThat(res.refundRate()).isEqualTo(8.0 / (142 + 8));
}
```

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 2 테스트 모두 PASS.

- [ ] **Step 6: 기간 역전 테스트 추가**

```java
@Test
void getSummary_기간_역전은_IllegalArgumentException() {
    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));
    assertThatThrownBy(() -> svc.getSummary(LocalDate.of(2026,5,1), LocalDate.of(2026,4,1)))
            .isInstanceOf(IllegalArgumentException.class);
}
```

(`import static org.assertj.core.api.Assertions.assertThatThrownBy;` 추가)

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 3 PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/AdminPaymentService.java backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java
git commit -m "feat(admin-payment): AdminPaymentService.getSummary + 기간 가드 (TDD)"
```

---

## Task 7: AdminPaymentService.listPayments + getDetail

**목적:** 목록/상세 조회 메서드 + 테스트.

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/AdminPaymentService.java`
- Modify: `backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`

- [ ] **Step 1: 목록 메서드 실패 테스트 작성**

```java
@Test
void listPayments_status_필터_적용() {
    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));
    // Page 빈 결과 stub
    when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(Page.empty());

    Page<AdminPaymentListItemResponse> page = svc.listPayments(
            new AdminPaymentFilter(PaymentStatus.CONFIRMED, null, null, null),
            PageRequest.of(0, 20)
    );

    assertThat(page.getContent()).isEmpty();
    // Specification 이 repository 에 전달됐음을 verify 로 확인
    ArgumentCaptor<Specification<Payment>> captor = ArgumentCaptor.forClass(Specification.class);
    verify(paymentRepository).findAll(captor.capture(), any(Pageable.class));
    assertThat(captor.getValue()).isNotNull();
}
```

필요 import:
```java
import com.devmatch.dto.admin.payment.AdminPaymentFilter;
import com.devmatch.dto.admin.payment.AdminPaymentListItemResponse;
import com.devmatch.entity.Payment;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import static org.mockito.Mockito.verify;
```

- [ ] **Step 2: 실행 — FAIL (메서드 없음)**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest.listPayments_status_필터_적용`
Expected: 컴파일 에러.

- [ ] **Step 3: `listPayments` 구현**

```java
public Page<AdminPaymentListItemResponse> listPayments(AdminPaymentFilter filter, Pageable pageable) {
    LocalDateTime[] range = resolveRange(filter.from(), filter.to());
    Specification<Payment> spec = PaymentSpecifications.withFilter(
            filter.status(), filter.q(), range[0], range[1]);

    // 사용자 이름/이메일 검색: q 가 있을 때 userId 집합을 구해 추가 Spec 결합
    if (filter.q() != null && !filter.q().isBlank()) {
        var userIds = userRepository.findByNameContainingOrEmailContaining(
                        filter.q(), filter.q(), Pageable.unpaged())
                .map(u -> u.getId()).getContent();
        if (!userIds.isEmpty()) {
            Specification<Payment> byUser = (root, query, cb) -> root.get("userId").in(userIds);
            // orderId LIKE(Spec 내부) OR userId IN 은 or 결합
            spec = spec.or(byUser);
        }
    }

    Page<Payment> page = paymentRepository.findAll(spec, pageable);

    // N+1 방지 위해 userId 들을 한 번에 조회
    var userIds = page.getContent().stream().map(Payment::getUserId).distinct().toList();
    var userMap = userRepository.findAllById(userIds).stream()
            .collect(java.util.stream.Collectors.toMap(u -> u.getId(), u -> u));

    return page.map(p -> {
        var u = userMap.get(p.getUserId());
        return AdminPaymentListItemResponse.of(
                p,
                u != null ? u.getName() : "(알 수 없음)",
                u != null ? u.getEmail() : "");
    });
}
```

import:
```java
import com.devmatch.repository.spec.PaymentSpecifications;
import com.devmatch.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
```

- [ ] **Step 4: 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 4 PASS.

- [ ] **Step 5: `getDetail` 실패 테스트**

```java
@Test
void getDetail_존재하지_않는_id_는_PaymentNotFoundException() {
    when(paymentRepository.findById(999L)).thenReturn(Optional.empty());
    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    assertThatThrownBy(() -> svc.getDetail(999L))
            .isInstanceOf(PaymentNotFoundException.class);
}
```

import:
```java
import com.devmatch.exception.PaymentNotFoundException;
import java.util.Optional;
```

- [ ] **Step 6: `getDetail` 구현**

```java
public AdminPaymentDetailResponse getDetail(Long paymentId) {
    var payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));
    var user = userRepository.findById(payment.getUserId()).orElse(null);
    var matching = payment.getMatchingId() != null
            ? matchingRepository.findById(payment.getMatchingId()).orElse(null)
            : null;
    var admin = payment.getProcessedByAdminId() != null
            ? userRepository.findById(payment.getProcessedByAdminId()).orElse(null)
            : null;

    // ApplicationSection 은 ApplicationRepository 로 조회하되 실패 시 null 로 내려보낸다.
    // 본 Task 는 최소 시그니처만 맞추고 Application 정보는 Task 9 컨트롤러 통합 시 확장.
    AdminPaymentDetailResponse.ApplicationSection appSection = null;

    AdminPaymentDetailResponse.UserSection userSection = user == null ? null
            : new AdminPaymentDetailResponse.UserSection(
                    user.getId(), user.getName(), user.getEmail(), user.getRole().name());

    AdminPaymentDetailResponse.MatchingSection matchingSection = matching == null ? null
            : new AdminPaymentDetailResponse.MatchingSection(
                    matching.getId(),
                    matching.getMentor() != null ? matching.getMentor().getName() : "",
                    matching.getStatus().name());

    AdminPaymentDetailResponse.RefundSection refundSection =
            (payment.getStatus() == PaymentStatus.CANCELLED && payment.getCancelledAt() != null)
                    ? new AdminPaymentDetailResponse.RefundSection(
                            payment.getProcessedByAdminId(),
                            admin != null ? admin.getName() : null,
                            payment.getCancelledAt(),
                            payment.getCancelReason())
                    : null;

    return new AdminPaymentDetailResponse(
            payment.getId(), payment.getOrderId(), payment.getPaymentKey(),
            payment.getAmount(), payment.getDiscountApplied(), payment.getInstallmentMonths(),
            payment.getCourseType(), payment.getMonthsBundled(), payment.getRenewalCount(),
            payment.getStatus(), payment.getCreatedAt(), payment.getCancelledAt(),
            payment.getCancelReason(),
            userSection, appSection, matchingSection, refundSection
    );
}
```

- [ ] **Step 7: Detail 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 5 PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/AdminPaymentService.java backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java
git commit -m "feat(admin-payment): listPayments + getDetail (TDD)"
```

---

## Task 8: AdminPaymentService.refundPayment (TDD)

**목적:** 환불 트랜잭션 플로우 구현 + 가드 + 소프트 캐스케이드 + 토스 연동 + 감사 로그. 테스트 8건.

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/AdminPaymentService.java`
- Modify: `backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`

- [ ] **Step 1: 해피 패스 테스트 (CONFIRMED + Matching ACCEPTED)**

```java
@Test
void refund_해피패스_Matching_ACCEPTED_도_CANCELLED_로_전이() {
    Payment p = Payment.builder()
            .id(1L).userId(10L).applicationId(100L).matchingId(50L)
            .orderId("ord_1").paymentKey("pk_live_abc").amount(150_000)
            .status(PaymentStatus.CONFIRMED).build();
    Matching m = Matching.builder().id(50L).status(MatchingStatus.ACCEPTED).build();
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(p));
    when(matchingRepository.findById(50L)).thenReturn(Optional.of(m));
    when(userRepository.findById(any())).thenReturn(Optional.empty());
    when(tossPaymentService.cancelPayment(eq("pk_live_abc"), anyString())).thenReturn(true);

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    svc.refundPayment(1L, 99L, "결제 중복 — 고객 요청에 따라 환불");

    assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(p.getProcessedByAdminId()).isEqualTo(99L);
    assertThat(p.getCancelledAt()).isNotNull();
    assertThat(m.getStatus()).isEqualTo(MatchingStatus.CANCELLED);
    verify(tossPaymentService).cancelPayment(eq("pk_live_abc"), anyString());
    verify(auditLogService).record(
            eq(99L),
            eq(AdminActionType.PAYMENT_REFUND),
            eq("PAYMENT"),
            eq(1L),
            anyString(),              // reason
            anyMap());                // metadata
}
```

필요 import:
```java
import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.Payment;
import static org.mockito.ArgumentMatchers.anyString;
```

- [ ] **Step 2: 실패 테스트 실행 — 메서드 없어서 컴파일 실패**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest.refund_해피패스*`
Expected: 컴파일 실패.

- [ ] **Step 3: `refundPayment` 최소 구현**

```java
@Transactional
public AdminPaymentDetailResponse refundPayment(Long paymentId, Long adminId, String reason) {
    var payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));

    // 1) status 가드 — CONFIRMED 만 환불 가능
    if (payment.getStatus() != PaymentStatus.CONFIRMED) {
        throw new PaymentFailedException("승인된 결제만 환불할 수 있습니다. 현재 상태: " + payment.getStatus());
    }

    // 2) 토스 호출 (플래그 on + paymentKey 존재)
    if (tossCancelProperties.tossCancelEnabled()) {
        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new PaymentFailedException("환불을 위한 결제키가 없습니다 (paymentKey NULL)");
        }
        boolean ok = tossPaymentService.cancelPayment(payment.getPaymentKey(), reason);
        if (!ok) {
            throw new PaymentFailedException("토스 환불에 실패했습니다");
        }
    } else {
        log.warn("[AdminPayment] toss-cancel-enabled=false — 토스 호출 skip, 내부 상태만 변경 (paymentId={})", paymentId);
    }

    // 3) Payment 상태 전이
    payment.cancel(reason);
    payment.markProcessedByAdmin(adminId, LocalDateTime.now());

    // 4) Matching 소프트 캐스케이드
    boolean matchingAffected = false;
    Long matchingId = payment.getMatchingId();
    if (matchingId != null) {
        var matching = matchingRepository.findById(matchingId).orElse(null);
        if (matching != null &&
                (matching.getStatus() == MatchingStatus.PENDING ||
                 matching.getStatus() == MatchingStatus.ACCEPTED ||
                 matching.getStatus() == MatchingStatus.TRIAL)) {
            matching.cancel(reason);
            matchingAffected = true;
        }
    }

    // 5) 감사 로그
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("refundAmount", payment.getAmount());
    metadata.put("matchingAffected", matchingAffected);
    if (matchingId != null) {
        metadata.put("matchingId", matchingId);
    }
    auditLogService.record(
            adminId,
            AdminActionType.PAYMENT_REFUND,
            "PAYMENT",
            payment.getId(),
            reason,
            metadata
    );

    log.info("[AdminPayment] 환불 완료 — paymentId={}, adminId={}, matchingAffected={}",
            paymentId, adminId, matchingAffected);

    return getDetail(paymentId);
}
```

import:
```java
import com.devmatch.entity.AdminActionType;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
```

- [ ] **Step 4: 해피 패스 테스트 PASS 확인**

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 6 PASS.

- [ ] **Step 5: Matching null (PENDING 단계) 테스트**

```java
@Test
void refund_Matching_null_이면_캐스케이드_없이_Payment_만_CANCELLED() {
    Payment p = Payment.builder()
            .id(2L).userId(10L).applicationId(100L).matchingId(null)
            .orderId("ord_2").paymentKey("pk_2").amount(990_000)
            .status(PaymentStatus.CONFIRMED).build();
    when(paymentRepository.findById(2L)).thenReturn(Optional.of(p));
    when(tossPaymentService.cancelPayment(any(), any())).thenReturn(true);

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    svc.refundPayment(2L, 99L, "신청서 오입력 환불 요청 — 접수");

    assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    verify(matchingRepository, never()).findById(any());
}
```

import:
```java
import static org.mockito.Mockito.never;
```

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 7 PASS.

- [ ] **Step 6: Matching REJECTED 스킵 테스트**

```java
@Test
void refund_Matching_REJECTED_은_skip() {
    Payment p = Payment.builder()
            .id(3L).userId(10L).applicationId(100L).matchingId(70L)
            .orderId("ord_3").paymentKey("pk_3").amount(990_000)
            .status(PaymentStatus.CONFIRMED).build();
    Matching m = Matching.builder().id(70L).status(MatchingStatus.REJECTED).build();
    when(paymentRepository.findById(3L)).thenReturn(Optional.of(p));
    when(matchingRepository.findById(70L)).thenReturn(Optional.of(m));
    when(tossPaymentService.cancelPayment(any(), any())).thenReturn(true);

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    svc.refundPayment(3L, 99L, "이중 청구로 환불 처리합니다");

    assertThat(m.getStatus()).isEqualTo(MatchingStatus.REJECTED); // 변경 없음
}
```

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 8 PASS.

- [ ] **Step 7: PENDING 환불 시도 가드 테스트**

```java
@Test
void refund_PENDING_결제_환불_시도는_PaymentFailedException() {
    Payment p = Payment.builder().id(4L).status(PaymentStatus.PENDING).build();
    when(paymentRepository.findById(4L)).thenReturn(Optional.of(p));

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    assertThatThrownBy(() -> svc.refundPayment(4L, 99L, "환불 요청에 따른 처리"))
            .isInstanceOf(PaymentFailedException.class);
    verifyNoInteractions(tossPaymentService);
}
```

import:
```java
import static org.mockito.Mockito.verifyNoInteractions;
```

Run: `cd backend && ./gradlew test --tests AdminPaymentServiceTest`
Expected: 9 PASS.

- [ ] **Step 8: CANCELLED 재환불 방지 테스트**

```java
@Test
void refund_CANCELLED_재환불_시도는_PaymentFailedException() {
    Payment p = Payment.builder().id(5L).status(PaymentStatus.CANCELLED).build();
    when(paymentRepository.findById(5L)).thenReturn(Optional.of(p));

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    assertThatThrownBy(() -> svc.refundPayment(5L, 99L, "재환불 시도"))
            .isInstanceOf(PaymentFailedException.class);
}
```

Run. Expected: 10 PASS.

- [ ] **Step 9: 토스 플래그 false 일 때 호출 skip 테스트**

```java
@Test
void refund_플래그_false_면_토스_호출_skip() {
    Payment p = Payment.builder()
            .id(6L).userId(10L).matchingId(null)
            .orderId("ord_6").paymentKey(null).amount(990_000)
            .status(PaymentStatus.CONFIRMED).build();
    when(paymentRepository.findById(6L)).thenReturn(Optional.of(p));

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(false));

    svc.refundPayment(6L, 99L, "dev 환경 테스트 환불");

    verifyNoInteractions(tossPaymentService);
    assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    verify(auditLogService).record(
            eq(99L),
            eq(AdminActionType.PAYMENT_REFUND),
            eq("PAYMENT"),
            eq(6L),
            anyString(),              // reason
            anyMap());                // metadata
}
```

Run. Expected: 11 PASS.

- [ ] **Step 10: 플래그 true + paymentKey NULL 가드 테스트**

```java
@Test
void refund_플래그_true_인데_paymentKey_NULL_은_PaymentFailedException() {
    Payment p = Payment.builder()
            .id(7L).paymentKey(null).status(PaymentStatus.CONFIRMED).build();
    when(paymentRepository.findById(7L)).thenReturn(Optional.of(p));

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    assertThatThrownBy(() -> svc.refundPayment(7L, 99L, "prod 환경 토스키 결여 케이스"))
            .isInstanceOf(PaymentFailedException.class);
    verifyNoInteractions(tossPaymentService);
}
```

Run. Expected: 12 PASS.

- [ ] **Step 11: 토스 호출 실패 시 예외 전파 테스트**

```java
@Test
void refund_토스_호출_실패는_PaymentFailedException_전파() {
    Payment p = Payment.builder()
            .id(8L).paymentKey("pk_x").status(PaymentStatus.CONFIRMED).build();
    when(paymentRepository.findById(8L)).thenReturn(Optional.of(p));
    when(tossPaymentService.cancelPayment(eq("pk_x"), any()))
            .thenThrow(new PaymentFailedException("토스 4xx"));

    AdminPaymentService svc = new AdminPaymentService(
            paymentRepository, matchingRepository, userRepository,
            tossPaymentService, auditLogService, props(true));

    assertThatThrownBy(() -> svc.refundPayment(8L, 99L, "토스 실패 케이스 테스트"))
            .isInstanceOf(PaymentFailedException.class);
    // Payment 는 CONFIRMED 유지 (롤백 효과 — 단위 테스트에선 실제 롤백은 재현 못하나
    // 변경 메서드가 호출되지 않았음을 상태로 검증)
    assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    verifyNoInteractions(auditLogService);
}
```

Run. Expected: 13 PASS.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/AdminPaymentService.java backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java
git commit -m "feat(admin-payment): refundPayment + 가드 + 소프트 캐스케이드 + 감사 로그 (TDD 8 cases)"
```

---

## Task 9: AdminPaymentController (얇은 위임)

**목적:** HTTP 바인딩 + `@AuthenticationPrincipal` 으로 관리자 id 추출.

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/AdminPaymentController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.payment.*;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Admin Payment", description = "관리자 결제 관리 API")
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @Operation(summary = "결제 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminPaymentListItemResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable
    ) {
        Page<AdminPaymentListItemResponse> page = adminPaymentService.listPayments(
                new AdminPaymentFilter(status, q, from, to), pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @Operation(summary = "결제 요약 카드")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminPaymentSummaryResponse>> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.getSummary(from, to)));
    }

    @Operation(summary = "결제 상세 조회")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<AdminPaymentDetailResponse>> detail(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.getDetail(paymentId)));
    }

    @Operation(summary = "관리자 강제 환불")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<AdminPaymentDetailResponse>> refund(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long paymentId,
            @Valid @RequestBody AdminPaymentRefundRequest request
    ) {
        AdminPaymentDetailResponse res = adminPaymentService.refundPayment(
                paymentId, admin.getUserId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success("환불 처리되었습니다", res));
    }
}
```

- [ ] **Step 2: 빌드 + 서버 부팅으로 수동 spot-check**

Run: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`

별도 터미널에서 ADMIN 토큰으로:
```bash
curl -s -H "Authorization: Bearer <ADMIN_JWT>" \
  "http://localhost:8080/api/admin/payments/summary?from=2026-03-22&to=2026-04-22"
```
Expected: 200 응답, JSON 구조 확인.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devmatch/controller/AdminPaymentController.java
git commit -m "feat(admin-payment): AdminPaymentController 4 endpoints (list/summary/detail/refund)"
```

---

## Task 10: DataInitializer — 결제 seed 7건 추가

**목적:** 스모크 테스트 및 dev 환경에서 쓸 결제 데이터 보강.

**Files:**
- Modify: `backend/src/main/java/com/devmatch/config/DataInitializer.java`

- [ ] **Step 1: 메서드 추가 + 호출 연결**

`DataInitializer` 의 `initDefaultAdmin()` 또는 시드 파이프라인 뒤에 `initSamplePayments()` 호출 추가. 내부 구현 예시 (실제 코드는 기존 seed 메서드 패턴에 맞춤):

```java
private void initSamplePayments() {
    if (paymentRepository.count() > 0) {
        log.info("Payment seed skip — 이미 데이터 존재");
        return;
    }
    // 가정: user seed 에서 멘티 id 가 기존처럼 생성돼 있음. 실제 id 는 환경에 따라 다를 수 있어
    // "첫 MENTEE" 를 userRepository 에서 찾는 식으로 유연하게 처리.
    User mentee = userRepository.findFirstByRole(Role.MENTEE).orElse(null);
    if (mentee == null) {
        log.warn("Payment seed 스킵 — MENTEE 가 없음");
        return;
    }
    // 7건: CONFIRMED-with-matching × 2, CONFIRMED-no-matching × 1, PENDING × 1,
    //      CANCELLED-by-user × 1, CANCELLED-by-admin × 1, FAILED × 1
    // 각각 orderId/amount/createdAt 를 다르게. paymentKey 는 FAKE_PK_xxx.
    // (실제 필드 세팅은 Application/Matching seed 와 일관되게)
    // — 여기서는 뼈대만. 구현 시점에 기존 user/application/matching 시드 id 와 결합.
    log.info("Payment seed 완료 — 7건 생성");
}
```

주의: 이 Task 는 위 의사코드를 실제 도메인에 맞춰 구체화해야 한다. 실제 시드 작성 시 다음 필드를 필수로 세팅:

- `orderId` — UUID-like unique
- `userId` — 기존 MENTEE user id
- `applicationId` — 기존 application id (FK 제약)
- `matchingId` — 존재 시 Matching id
- `paymentKey` — `"FAKE_PK_" + orderId` 형태
- `amount` — 990_000 기본
- `status` — 위 7개 분포
- `processedByAdminId`, `cancelledAt` — admin-cancelled 케이스만
- `cancelReason` — cancelled 케이스만

- [ ] **Step 2: dev 기동 후 DB 확인**

Run: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'` 기동 후:
```sql
SELECT id, user_id, status, processed_by_admin_id, cancelled_at FROM payments;
```
Expected: 7건 반환.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devmatch/config/DataInitializer.java
git commit -m "chore(seed): 관리자 결제 관리 스모크용 결제 7건 시드"
```

---

## Task 11: ROADMAP 에 프로덕션 마이그레이션 SQL 추가

**Files:**
- Modify: `ROADMAP.md` (§10 배포 체크리스트)

- [ ] **Step 1: §10 에 결제 관리용 SQL 블록 추가**

`ROADMAP.md` §10 배포 체크리스트 하위에 다음 섹션 추가 (기존 Feature 1 SQL 아래):

````markdown
### Phase II Feature 2 (결제 관리) 마이그레이션 SQL

```sql
-- 1) Payment 컬럼 추가
ALTER TABLE payments
  ADD COLUMN processed_by_admin_id BIGINT NULL,
  ADD COLUMN cancelled_at TIMESTAMP NULL;

-- 2) 과거 CANCELLED 레코드의 cancelled_at backfill (선택)
UPDATE payments
   SET cancelled_at = updated_at
 WHERE status = 'CANCELLED' AND cancelled_at IS NULL;
```

Matching 테이블은 DDL 변경 없음 (enum VARCHAR 재사용).

프로필 변수 확인:
- `app.payment.toss-cancel-enabled=true` (application-prod.yml 에 명시)
````

- [ ] **Step 2: Commit**

```bash
git add ROADMAP.md
git commit -m "docs(roadmap): Phase II Feature 2 프로덕션 마이그레이션 SQL 추가"
```

---

## Task 12: Pencil 목업 게이트 (FE 착수 전 사용자 승인 필수)

> **중요: FE 구현 Task 를 시작하기 전에 반드시 이 게이트를 통과해야 한다.** 이 게이트는 `feedback_frontend_preview` 규약에 따른 것으로, 실제 shadcn 컴포넌트·데이터 바인딩을 코드로 쓰기 전에 Pencil 목업으로 사용자에게 시각 확인을 받는다.

**Files:**
- (새로 작성하는 Pencil 캔버스는 `.pen` 파일 — 인메모리 동작으로 디스크 저장 안 됨)
- (결정 사항은 `docs/mockups/admin-payments.md` 상단 주석 블록에 반영)

- [ ] **Step 1: Pencil 목업 3화면 렌더**

`mcp__pencil__*` 툴로 아래 3화면 구성:
- `/admin/payments` 목록 (요약 카드 4개 + 탭 + 필터 + 테이블 + 페이지네이션)
- `/admin/payments/[id]` 상세 (주문/사용자/매칭/환불이력 섹션 + sticky footer)
- 환불 확인 다이얼로그 (전액 고정 + 사유 textarea + alert destructive + 취소/환불 확정)

스펙 §5 프런트엔드 섹션과 `docs/mockups/admin-payments.md` 의 ASCII 목업을 바탕으로 함.

- [ ] **Step 2: 사용자에게 보여주고 피드백 수집**

사용자에게 목업 스크린샷 또는 Pencil 화면을 공유하고 수정 요청을 받는다.

- [ ] **Step 3: 변경 사항을 `docs/mockups/admin-payments.md` 주석 블록에 반영 + 커밋**

Pencil 결정 사항(배지 색상, 문구, 간격 등)을 mockup 상단 주석 블록 `<!-- Pencil 결정 (YYYY-MM-DD): ... -->` 으로 기록.

```bash
git add docs/mockups/admin-payments.md
git commit -m "docs(mockups): admin-payments Pencil 렌더 결정 반영"
```

- [ ] **Step 4: 사용자 명시적 "진행" 승인 획득**

승인 없이 Task 14+ 로 진행 금지.

---

## Task 13: shadcn popover + calendar 설치

**Files:**
- Modify: `frontend/package.json` (새 의존성 추가되는 경우)
- Create: `frontend/src/components/ui/popover.tsx`, `frontend/src/components/ui/calendar.tsx`
- Create: `frontend/src/components/admin/AdminDateRangePicker.tsx`

- [ ] **Step 1: shadcn 명령 실행**

Run:
```bash
cd frontend && npx shadcn@latest add popover calendar
```
Expected: `components/ui/popover.tsx`, `components/ui/calendar.tsx` 생성. `package.json` 에 `react-day-picker`, `@radix-ui/react-popover` 추가.

- [ ] **Step 2: `AdminDateRangePicker` 공용 컴포넌트 작성**

```tsx
// frontend/src/components/admin/AdminDateRangePicker.tsx
"use client";

import * as React from "react";
import { format } from "date-fns";
import { ko } from "date-fns/locale";
import { Calendar as CalendarIcon } from "lucide-react";
import type { DateRange } from "react-day-picker";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

export interface AdminDateRangePickerProps {
  value: DateRange | undefined;
  onChange: (range: DateRange | undefined) => void;
  placeholder?: string;
}

export function AdminDateRangePicker({ value, onChange, placeholder = "기간 선택" }: AdminDateRangePickerProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className={cn("w-[260px] justify-start text-left font-normal",
                         !value && "text-muted-foreground")}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {value?.from ? (
            value.to ? (
              <>
                {format(value.from, "yyyy-MM-dd", { locale: ko })} ~{" "}
                {format(value.to, "yyyy-MM-dd", { locale: ko })}
              </>
            ) : (
              format(value.from, "yyyy-MM-dd", { locale: ko })
            )
          ) : (
            <span>{placeholder}</span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="range"
          selected={value}
          onSelect={onChange}
          numberOfMonths={2}
          locale={ko}
        />
      </PopoverContent>
    </Popover>
  );
}
```

- [ ] **Step 3: 타입 체크 + 빌드**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: 성공.

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/components/ui/popover.tsx frontend/src/components/ui/calendar.tsx frontend/src/components/admin/AdminDateRangePicker.tsx
git commit -m "chore(frontend): shadcn popover/calendar 설치 + AdminDateRangePicker 공용"
```

---

## Task 14: FE — `/admin/payments` 목록 + 요약 카드 + 필터

**Files:**
- Create: `frontend/src/app/admin/payments/_types.ts`
- Create: `frontend/src/app/admin/payments/_api/adminPaymentApi.ts`
- Create: `frontend/src/app/admin/payments/_components/PaymentSummaryCards.tsx`
- Create: `frontend/src/app/admin/payments/_components/PaymentListTable.tsx`
- Create: `frontend/src/app/admin/payments/page.tsx`

- [ ] **Step 1: 타입 정의**

```ts
// frontend/src/app/admin/payments/_types.ts
export type PaymentStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "FAILED";

export interface AdminPaymentListItem {
  id: number;
  orderId: string;
  userId: number;
  userName: string;
  userEmail: string;
  amount: number;
  status: PaymentStatus;
  createdAt: string;
  cancelledAt: string | null;
  matchingId: number | null;
}

export interface AdminPaymentSummary {
  totalAmount: number;
  confirmedCount: number;
  refundedAmount: number;
  refundRate: number;
}

export interface AdminPaymentDetail {
  id: number;
  orderId: string;
  paymentKey: string | null;
  amount: number;
  discountApplied: number;
  installmentMonths: number;
  courseType: string;
  monthsBundled: number;
  renewalCount: number;
  status: PaymentStatus;
  createdAt: string;
  cancelledAt: string | null;
  cancelReason: string | null;
  user: { id: number; name: string; email: string; role: string } | null;
  application: { id: number; category: string } | null;
  matching: { id: number; mentorName: string; status: string } | null;
  refund: {
    processedByAdminId: number;
    processedByAdminName: string | null;
    cancelledAt: string;
    reason: string;
  } | null;
}
```

- [ ] **Step 2: API 클라이언트**

```ts
// frontend/src/app/admin/payments/_api/adminPaymentApi.ts
import { apiClient } from "@/lib/apiClient"; // 프로젝트의 공용 fetch 래퍼
import type {
  AdminPaymentListItem,
  AdminPaymentSummary,
  AdminPaymentDetail,
} from "../_types";

export interface ListParams {
  status?: string;
  q?: string;
  from?: string; // yyyy-MM-dd
  to?: string;
  page?: number;
  size?: number;
}

export const adminPaymentApi = {
  list: async (params: ListParams) => {
    const qs = new URLSearchParams();
    if (params.status && params.status !== "ALL") qs.set("status", params.status);
    if (params.q) qs.set("q", params.q);
    if (params.from) qs.set("from", params.from);
    if (params.to) qs.set("to", params.to);
    if (params.page != null) qs.set("page", String(params.page));
    if (params.size != null) qs.set("size", String(params.size));
    return apiClient.get<{ content: AdminPaymentListItem[]; totalElements: number; totalPages: number; number: number }>(
      `/api/admin/payments?${qs.toString()}`
    );
  },
  summary: async (from?: string, to?: string) => {
    const qs = new URLSearchParams();
    if (from) qs.set("from", from);
    if (to) qs.set("to", to);
    return apiClient.get<AdminPaymentSummary>(`/api/admin/payments/summary?${qs.toString()}`);
  },
  detail: async (id: number) =>
    apiClient.get<AdminPaymentDetail>(`/api/admin/payments/${id}`),
  refund: async (id: number, reason: string) =>
    apiClient.post<AdminPaymentDetail>(`/api/admin/payments/${id}/refund`, { reason }),
};
```

> 참고: `apiClient` 의 실제 이름/위치는 프로젝트 기존 fetch 래퍼를 따른다 (Feature 1 에서 사용한 이름 확인).

- [ ] **Step 3: 요약 카드 컴포넌트**

```tsx
// frontend/src/app/admin/payments/_components/PaymentSummaryCards.tsx
"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { AdminPaymentSummary } from "../_types";

function formatKRW(n: number): string {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}

export function PaymentSummaryCards({ summary }: { summary: AdminPaymentSummary | undefined }) {
  const s = summary ?? { totalAmount: 0, confirmedCount: 0, refundedAmount: 0, refundRate: 0 };
  return (
    <div className="grid grid-cols-4 gap-4">
      <SummaryCard title="총 결제액" value={formatKRW(s.totalAmount)} />
      <SummaryCard title="확정 건수" value={`${s.confirmedCount}건`} />
      <SummaryCard title="환불액" value={formatKRW(s.refundedAmount)} />
      <SummaryCard title="환불률" value={`${(s.refundRate * 100).toFixed(1)}%`} />
    </div>
  );
}

function SummaryCard({ title, value }: { title: string; value: string }) {
  return (
    <Card>
      <CardHeader className="pb-2"><CardTitle className="text-sm text-muted-foreground">{title}</CardTitle></CardHeader>
      <CardContent><div className="text-2xl font-semibold">{value}</div></CardContent>
    </Card>
  );
}
```

- [ ] **Step 4: 목록 테이블 컴포넌트**

```tsx
// frontend/src/app/admin/payments/_components/PaymentListTable.tsx
"use client";

import Link from "next/link";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AdminStatusBadge } from "@/components/admin/AdminStatusBadge";
import type { AdminPaymentListItem } from "../_types";

function formatKRW(n: number) {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}

const STATUS_COLORS = {
  PENDING: "amber", CONFIRMED: "emerald", CANCELLED: "red", FAILED: "zinc",
} as const;
const STATUS_LABELS = {
  PENDING: "대기", CONFIRMED: "확정", CANCELLED: "취소", FAILED: "실패",
} as const;

export function PaymentListTable({ rows }: { rows: AdminPaymentListItem[] }) {
  if (!rows.length) return <div className="py-12 text-center text-muted-foreground">조건에 맞는 결제가 없습니다</div>;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>주문ID</TableHead>
          <TableHead>사용자</TableHead>
          <TableHead className="text-right">금액</TableHead>
          <TableHead>상태</TableHead>
          <TableHead>결제일</TableHead>
          <TableHead className="text-right">액션</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((p) => (
          <TableRow key={p.id}>
            <TableCell className="font-mono text-xs">{p.orderId}</TableCell>
            <TableCell>{p.userName}<div className="text-xs text-muted-foreground">{p.userEmail}</div></TableCell>
            <TableCell className="text-right">{formatKRW(p.amount)}</TableCell>
            <TableCell>
              <AdminStatusBadge color={STATUS_COLORS[p.status]} label={STATUS_LABELS[p.status]} />
            </TableCell>
            <TableCell className="text-sm text-muted-foreground">
              {new Date(p.createdAt).toLocaleString("ko-KR")}
            </TableCell>
            <TableCell className="text-right">
              <Link href={`/admin/payments/${p.id}`} className="text-sm text-primary underline-offset-4 hover:underline">
                상세 ›
              </Link>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

- [ ] **Step 5: 목록 페이지 (URL searchParams 동기화)**

```tsx
// frontend/src/app/admin/payments/page.tsx
"use client";

import { useSearchParams, useRouter, usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import type { DateRange } from "react-day-picker";
import { AdminListHeader } from "@/components/admin/AdminListHeader";
import { AdminTabs } from "@/components/admin/AdminTabs";
import { DebouncedSearchInput } from "@/components/admin/DebouncedSearchInput";
import { Pagination } from "@/components/admin/Pagination";
import { AdminDateRangePicker } from "@/components/admin/AdminDateRangePicker";
import { PaymentSummaryCards } from "./_components/PaymentSummaryCards";
import { PaymentListTable } from "./_components/PaymentListTable";
import { adminPaymentApi } from "./_api/adminPaymentApi";

const STATUS_TABS = [
  { key: "ALL",       label: "전체" },
  { key: "PENDING",   label: "대기" },
  { key: "CONFIRMED", label: "확정" },
  { key: "CANCELLED", label: "취소" },
  { key: "FAILED",    label: "실패" },
] as const;

export default function AdminPaymentsPage() {
  const sp = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const status = sp.get("status") ?? "ALL";
  const q      = sp.get("q") ?? "";
  const from   = sp.get("from") ?? "";
  const to     = sp.get("to")   ?? "";
  const page   = Number(sp.get("page") ?? 0);

  const updateParams = (mutator: (u: URLSearchParams) => void) => {
    const u = new URLSearchParams(sp.toString());
    mutator(u);
    router.push(`${pathname}?${u.toString()}`);
  };

  const dateRange: DateRange | undefined = useMemo(() => {
    if (!from && !to) return undefined;
    return {
      from: from ? new Date(from) : undefined,
      to:   to   ? new Date(to)   : undefined,
    };
  }, [from, to]);

  const summaryQ = useQuery({
    queryKey: ["admin-payments-summary", from, to],
    queryFn: () => adminPaymentApi.summary(from || undefined, to || undefined),
  });

  const listQ = useQuery({
    queryKey: ["admin-payments-list", status, q, from, to, page],
    queryFn: () => adminPaymentApi.list({
      status: status, q: q || undefined,
      from: from || undefined, to: to || undefined,
      page, size: 20,
    }),
  });

  return (
    <div className="space-y-6">
      <AdminListHeader title="결제 관리" subtitle="결제 내역을 조회하고 취소·환불 요청을 처리합니다." />

      <PaymentSummaryCards summary={summaryQ.data} />

      <div className="flex items-center justify-between gap-4">
        <AdminTabs
          items={[...STATUS_TABS]}
          active={status}
          onSelect={(k) => updateParams((u) => { u.set("status", k); u.set("page", "0"); })}
        />
        <div className="flex items-center gap-2">
          <AdminDateRangePicker
            value={dateRange}
            onChange={(r) => updateParams((u) => {
              if (r?.from) u.set("from", r.from.toISOString().slice(0, 10)); else u.delete("from");
              if (r?.to)   u.set("to",   r.to.toISOString().slice(0, 10));   else u.delete("to");
              u.set("page", "0");
            })}
          />
          <DebouncedSearchInput
            value={q}
            placeholder="사용자·주문ID"
            onChange={(next) => updateParams((u) => { if (next) u.set("q", next); else u.delete("q"); u.set("page", "0"); })}
          />
        </div>
      </div>

      <PaymentListTable rows={listQ.data?.content ?? []} />

      <Pagination
        page={listQ.data?.number ?? 0}
        totalPages={listQ.data?.totalPages ?? 0}
        onChange={(nextPage) => updateParams((u) => u.set("page", String(nextPage)))}
      />
    </div>
  );
}
```

- [ ] **Step 6: 타입 체크 + 빌드**

Run: `cd frontend && npx tsc --noEmit && npm run build`

- [ ] **Step 7: dev 서버에서 `/admin/payments` 스폿 체크**

시드된 7건이 보이는지, 탭·기간·검색이 URL 에 반영되는지 브라우저에서 확인.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/admin/payments
git commit -m "feat(admin-payment): /admin/payments 목록 + 요약 카드 + 필터"
```

---

## Task 15: FE — `/admin/payments/[id]` 상세 + 환불 다이얼로그

**Files:**
- Create: `frontend/src/app/admin/payments/[id]/page.tsx`
- Create: `frontend/src/app/admin/payments/_components/PaymentDetailSection.tsx`
- Create: `frontend/src/app/admin/payments/_components/PaymentRefundDialog.tsx`

- [ ] **Step 1: 상세 섹션 컴포넌트**

```tsx
// frontend/src/app/admin/payments/_components/PaymentDetailSection.tsx
"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { AdminPaymentDetail } from "../_types";

function formatKRW(n: number) {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}

export function PaymentDetailSection({ detail }: { detail: AdminPaymentDetail }) {
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader><CardTitle>주문 정보</CardTitle></CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-y-2 text-sm">
            <dt className="text-muted-foreground">주문ID</dt><dd className="font-mono">{detail.orderId}</dd>
            <dt className="text-muted-foreground">결제키</dt><dd className="font-mono">{detail.paymentKey ?? "-"}</dd>
            <dt className="text-muted-foreground">금액</dt><dd>{formatKRW(detail.amount)}</dd>
            <dt className="text-muted-foreground">할인 적용액</dt><dd>{formatKRW(detail.discountApplied)}</dd>
            <dt className="text-muted-foreground">할부</dt><dd>{detail.installmentMonths === 0 ? "일시불" : `${detail.installmentMonths}개월`}</dd>
            <dt className="text-muted-foreground">코스 타입</dt><dd>{detail.courseType}</dd>
            <dt className="text-muted-foreground">묶음 개월</dt><dd>{detail.monthsBundled}</dd>
            <dt className="text-muted-foreground">연장 회차</dt><dd>{detail.renewalCount} ({detail.renewalCount === 0 ? "최초" : "연장"})</dd>
          </dl>
        </CardContent>
      </Card>

      {detail.user && (
        <Card>
          <CardHeader><CardTitle>사용자</CardTitle></CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-y-2 text-sm">
              <dt className="text-muted-foreground">이름</dt><dd>{detail.user.name}</dd>
              <dt className="text-muted-foreground">이메일</dt><dd>{detail.user.email}</dd>
              <dt className="text-muted-foreground">역할</dt><dd>{detail.user.role}</dd>
            </dl>
          </CardContent>
        </Card>
      )}

      {detail.matching && (
        <Card>
          <CardHeader><CardTitle>연결된 매칭</CardTitle></CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-y-2 text-sm">
              <dt className="text-muted-foreground">매칭 ID</dt><dd>#{detail.matching.id}</dd>
              <dt className="text-muted-foreground">멘토</dt><dd>{detail.matching.mentorName}</dd>
              <dt className="text-muted-foreground">상태</dt><dd>{detail.matching.status}</dd>
            </dl>
            <p className="mt-3 text-xs text-muted-foreground">환불 시 매칭이 함께 취소되며, 멘티의 LMS 접근이 차단됩니다.</p>
          </CardContent>
        </Card>
      )}

      {detail.refund && (
        <Card>
          <CardHeader><CardTitle>환불 이력</CardTitle></CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-y-2 text-sm">
              <dt className="text-muted-foreground">처리일</dt><dd>{new Date(detail.refund.cancelledAt).toLocaleString("ko-KR")}</dd>
              <dt className="text-muted-foreground">처리자</dt><dd>{detail.refund.processedByAdminName ?? `admin#${detail.refund.processedByAdminId}`}</dd>
              <dt className="text-muted-foreground">사유</dt><dd className="col-span-1 whitespace-pre-wrap">{detail.refund.reason}</dd>
            </dl>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
```

- [ ] **Step 2: 환불 다이얼로그**

```tsx
// frontend/src/app/admin/payments/_components/PaymentRefundDialog.tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

import { adminPaymentApi } from "../_api/adminPaymentApi";
import type { AdminPaymentDetail } from "../_types";

const schema = z.object({
  reason: z.string().min(10, "10자 이상 입력하세요").max(500, "500자 이하로 입력하세요"),
});
type Values = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  detail: AdminPaymentDetail;
  onSuccess: (next: AdminPaymentDetail) => void;
}

function formatKRW(n: number) {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}

export function PaymentRefundDialog({ open, onOpenChange, detail, onSuccess }: Props) {
  const [serverError, setServerError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { reason: "" },
  });

  const mutation = useMutation({
    mutationFn: (v: Values) => adminPaymentApi.refund(detail.id, v.reason),
    onSuccess: (next) => {
      toast.success("환불 처리되었습니다");
      onSuccess(next);
      onOpenChange(false);
    },
    onError: (e: any) => {
      setServerError(e?.message ?? "환불 처리에 실패했습니다");
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>결제 환불</DialogTitle>
          <p className="text-sm text-muted-foreground">{detail.orderId} 주문을 환불 처리합니다.</p>
        </DialogHeader>

        <form onSubmit={form.handleSubmit((v) => { setServerError(null); mutation.mutate(v); })}
              className="space-y-4">
          <div className="space-y-1">
            <label className="text-sm font-medium">환불 금액</label>
            <div className="rounded-md border px-3 py-2 text-sm font-mono bg-muted/40">
              {formatKRW(detail.amount)}
              <span className="ml-2 text-xs text-muted-foreground">(전액 환불 고정)</span>
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium">환불 사유 <span className="text-red-500">*</span></label>
            <Textarea rows={4} placeholder="환불 사유를 10~500자로 입력하세요" {...form.register("reason")} />
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>{form.formState.errors.reason?.message}</span>
              <span>{form.watch("reason").length} / 500 (최소 10)</span>
            </div>
          </div>

          <Alert variant="destructive">
            <AlertTitle>환불 처리 시</AlertTitle>
            <AlertDescription className="space-y-1">
              <div>• 토스페이먼츠에 환불 요청이 전송됩니다 (취소 불가).</div>
              <div>• 연결된 매칭이 함께 취소되어 멘티의 LMS 접근이 차단됩니다.</div>
              <div>• 본 작업은 감사 로그에 기록됩니다.</div>
            </AlertDescription>
          </Alert>

          {serverError && (
            <Alert variant="destructive">
              <AlertTitle>처리 실패</AlertTitle>
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)} disabled={mutation.isPending}>취소</Button>
            <Button type="submit" variant="destructive" disabled={mutation.isPending}>
              {mutation.isPending ? "처리 중..." : "환불 확정"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
```

> 의존성: `@hookform/resolvers`, `zod`, `react-hook-form`, `sonner` 이미 Feature 1 에서 도입 가정. 없으면 `cd frontend && npm install @hookform/resolvers zod react-hook-form sonner` 실행.

- [ ] **Step 3: 상세 페이지**

```tsx
// frontend/src/app/admin/payments/[id]/page.tsx
"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { AdminStatusBadge } from "@/components/admin/AdminStatusBadge";
import { PaymentDetailSection } from "../_components/PaymentDetailSection";
import { PaymentRefundDialog } from "../_components/PaymentRefundDialog";
import { adminPaymentApi } from "../_api/adminPaymentApi";
import type { AdminPaymentDetail } from "../_types";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "대기", CONFIRMED: "확정", CANCELLED: "취소", FAILED: "실패",
};
const STATUS_COLORS: Record<string, "amber" | "emerald" | "red" | "zinc"> = {
  PENDING: "amber", CONFIRMED: "emerald", CANCELLED: "red", FAILED: "zinc",
};

export default function AdminPaymentDetailPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const qc = useQueryClient();
  const [refundOpen, setRefundOpen] = useState(false);

  const { data: detail } = useQuery({
    queryKey: ["admin-payment", id],
    queryFn: () => adminPaymentApi.detail(id),
  });

  if (!detail) return <div className="py-12 text-center text-muted-foreground">불러오는 중...</div>;

  const d = detail as AdminPaymentDetail;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="space-y-1">
          <Link href="/admin/payments" className="text-sm text-muted-foreground hover:underline">← 목록</Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold font-mono">{d.orderId}</h1>
            <AdminStatusBadge color={STATUS_COLORS[d.status]} label={STATUS_LABELS[d.status]} />
          </div>
          <p className="text-sm text-muted-foreground">
            결제일 {new Date(d.createdAt).toLocaleString("ko-KR")} · 금액{" "}
            {new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(d.amount)}
          </p>
        </div>
      </div>

      <PaymentDetailSection detail={d} />

      {d.status === "CONFIRMED" && (
        <div className="sticky bottom-0 bg-background border-t pt-4 flex justify-end">
          <Button variant="destructive" onClick={() => setRefundOpen(true)}>환불 처리</Button>
        </div>
      )}
      {d.status === "PENDING" && (
        <div className="text-center text-sm text-muted-foreground">결제 대기 상태입니다</div>
      )}
      {d.status === "FAILED" && (
        <div className="text-center text-sm text-muted-foreground">결제 실패 — 액션 불가</div>
      )}

      <PaymentRefundDialog
        open={refundOpen}
        onOpenChange={setRefundOpen}
        detail={d}
        onSuccess={(next) => {
          qc.setQueryData(["admin-payment", id], next);
          qc.invalidateQueries({ queryKey: ["admin-payments-list"] });
          qc.invalidateQueries({ queryKey: ["admin-payments-summary"] });
        }}
      />
    </div>
  );
}
```

- [ ] **Step 4: 타입 체크 + 빌드 + dev 스폿 체크**

```bash
cd frontend && npx tsc --noEmit && npm run build
```

시드에서 CONFIRMED 결제 id 를 골라 `/admin/payments/<id>` 진입, 환불 버튼 → 다이얼로그 → 사유 입력 → 제출 → 토스트 + 상세 갱신 확인.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/admin/payments
git commit -m "feat(admin-payment): /admin/payments/[id] 상세 + 환불 다이얼로그"
```

---

## Task 16: 스모크 문서 작성 + 실행 + 결과 기록

**Files:**
- Create: `docs/smoke/<실행일>-admin-payments-smoke.md`

- [ ] **Step 1: Feature 1 스모크 템플릿 복제 후 결제용으로 수정**

Feature 1 `docs/smoke/2026-04-23-admin-users-smoke.md` 를 복사해서 새 파일명으로 만들고 아래 6시나리오로 교체:

```markdown
# Phase II Feature 2 관리자 결제 관리 스모크

> 실행일: YYYY-MM-DD (작성일 기준)
> 전제: ADMIN 계정 로그인 + Payment seed 7건

## 시나리오

### 1. 목록 + 요약 카드
- [ ] `/admin/payments` 진입 시 요약 카드 4개 렌더
- [ ] 탭 전환 (전체/대기/확정/취소/실패) 시 행 수 변화

### 2. 필터 URL 동기화
- [ ] 기간 · 검색 · 탭 변경 후 새로고침 시 필터 유지

### 3. CONFIRMED 결제 환불
- [ ] 상세 진입 → 환불 → 사유 12자 이상 → 확정 → toast 성공
- [ ] dev: 토스 호출 skip 로그 확인
- [ ] 상세 하단 환불 이력 카드 렌더

### 4. Matching 연쇄 CANCELLED
- [ ] 환불된 결제의 Matching.status = CANCELLED
- [ ] 해당 멘티로 로그인 → LMS 페이지 접근 → 403

### 5. 비-ADMIN 접근
- [ ] MENTEE/MENTOR 토큰으로 `/admin/payments` 호출 → 403

### 6. 중복 환불 방지
- [ ] CANCELLED 결제 재환불 시도 → API 400 에러 + 에러 토스트
```

- [ ] **Step 2: 수동 6시나리오 실행 후 각 체크박스 기록**

실행 결과(성공/이슈) 를 체크박스에 표시하고 이슈는 "follow-up" 섹션에 기록.

- [ ] **Step 3: Commit**

```bash
git add docs/smoke/
git commit -m "docs(smoke): Phase II Feature 2 관리자 결제 관리 스모크 가이드"
```

---

## Self-Review

> 플랜 작성 완료 후 자체 점검

**1. 스펙 커버리지 체크**

| 스펙 섹션 | 커버 Task |
|----------|----------|
| §4.1 데이터 모델 | Task 1, 2 |
| §4.2 API 4종 | Task 9 |
| §4.3 서비스 레이어 | Task 6, 7, 8 |
| §4.4 설정 플래그 | Task 3 |
| §4.5 Repository | Task 4 |
| §5 프런트엔드 | Task 0, 13, 14, 15 |
| §6 에러/엣지 | Task 8 (테스트로 커버) |
| §7 테스트 전략 | Task 6-8 (12 케이스) + Task 16 (수동 스모크) |
| §8 마이그레이션 | Task 11 |
| §9 구현 순서 | 전체 Task 순서에 반영 |
| Pencil 목업 규약 | Task 12 게이트 |

**2. 플레이스홀더 스캔**
- Task 10 Seed: 구체 필드 세팅은 "의사코드" 로 남아있음 → 구현 시점에 기존 Application/Matching seed id 와 결합하여 구체화 필요. 플랜 레벨에선 각 Payment 필드의 요구사항을 모두 열거했으므로 "TBD" 는 아님. (실제 id 는 런타임에 결정되는 정보라 플랜에 하드코딩하면 외려 깨짐)
- Task 13 의 `apiClient` 참조는 "Feature 1 에서 사용한 이름 확인" 으로 남음 → Feature 1 구현을 읽어 동일 이름 사용. 플랜 레벨엔 의도가 명확.

**3. 타입 일관성**
- `TossCancelProperties` — 모든 Task 에서 `new TossCancelProperties(boolean)` 로 사용. Task 3 정의와 Task 6-8 소비 일치.
- `AdminPaymentDetailResponse` — Task 5 정의 / Task 7, 8 반환 / Task 15 FE 소비. 필드명 camelCase 로 일관.
- `AdminPaymentListItemResponse.of(Payment, String, String)` — Task 5 정의, Task 7 호출 시그니처 일치.

**4. 잔여 의존성 (Feature 1 / Common)**
- `AdminAuditLogService.record(Long, AdminActionType, String, Long, String, Map<String,Object>)` — main 기준 실제 시그니처 확인 완료 (2026-04-23). Task 6-8 에 반영됨.
- `AdminActionType.PAYMENT_REFUND` 는 Common 이 이미 도입 — enum 추가 작업 불필요.
- `UserRepository.findFirstByRole(Role)` (Task 10 seed) 가 존재하지 않으면 `userRepository.findAll().stream()...first()` 로 대체.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-23-admin-payments.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — 각 Task 마다 fresh subagent 를 dispatch, Task 사이마다 리뷰, 빠른 반복.

**2. Inline Execution** — 같은 세션에서 executing-plans 스킬로 체크포인트 단위 batch 실행.

**어느 방식으로 진행할까요?**

---

## 변경 이력

- 2026-04-23: 최초 작성. 스펙 §1~§9 + Pencil 규약 반영. 16개 Task.

# 관리자 콘솔 홈 대시보드 설계

> Phase III Feature 1 — 관리자가 `/admin/dashboard` 에 진입하면 회원/매출/매칭 현황 KPI, 추이 차트, 처리 큐, (SUPER_ADMIN 한정) 감사 로그 피드를 한 페이지에서 본다.

- **작성일**: 2026-04-24
- **대상 브랜치 후보**: `claude/admin-dashboard`
- **관련 문서**:
  - `ROADMAP.md` §10 (Admin API 테이블, `GET /api/admin/dashboard`)
  - `docs/mockups/admin-console-overview.md` (사이드바 구조)
  - `docs/superpowers/specs/2026-04-22-admin-console-common-design.md` (공용 사이드바·페이지네이션 패턴)

---

## 1. 목표와 비목표

### 목표

1. 관리자가 콘솔 홈에서 **현 시점 현황(KPI 4)** 을 즉시 본다.
2. **추이 차트 2개** (30일 가입 추이, 12개월 순매출 추이) 로 "데이터 감각" 을 전달한다. 포트폴리오 인상 목적.
3. **처리 큐** (승인 대기 멘토, 실패 결제) 가 카운트와 함께 한 클릭 이내로 이동 가능하다.
4. **SUPER_ADMIN** 은 다른 관리자의 최근 행동을 **감사 로그 피드** 로 확인한다.

### 비목표 (Out of Scope)

- CSV/Excel export, 기간 선택 UI, 차트 드릴다운, Redis 캐시, 실시간 업데이트(WebSocket/SSE), 신고 기반 처리 큐 — 모두 YAGNI. 필요하면 추후 별도 spec.

---

## 2. 아키텍처 개요

### 2.1 컴포넌트 경계

```
┌──────────────────────── Frontend ────────────────────────┐
│ app/admin/dashboard/page.tsx        (페이지 셸, 데이터 fetch) │
│ ├─ <KpiCards />                    (KPI 4개, summary API) │
│ ├─ <SignupTrendChart />            (line, 30일)           │
│ ├─ <RevenueTrendChart />           (bar, 12개월, 순매출)   │
│ ├─ <ActionQueue />                 (승인 대기 멘토 / 실패 결제) │
│ └─ <RecentAuditLog /> (SUPER_ADMIN) (감사 로그 10건)       │
│                                                            │
│ app/admin/page.tsx                 (리다이렉트: → /admin/dashboard) │
│ components/admin/AdminSidebar.tsx  (📊 대시보드 최상단 추가)  │
└──────────────────────────────────────────────────────────┘
                              │
                              │ (2개 API 호출, 프론트에서 병렬)
                              ▼
┌──────────────────────── Backend ─────────────────────────┐
│ AdminDashboardController                                  │
│ ├─ GET /api/admin/dashboard          → DashboardResponse  │
│ └─ GET /api/admin/dashboard/audit-log → AuditLogResponse  │
│        (@PreAuthorize SUPER_ADMIN)                        │
│                                                            │
│ AdminDashboardService                                      │
│ ├─ getSummary()      → KPI 4 + queue 2 + charts 2         │
│ └─ getAuditLogFeed() → 최근 10건 (admin name join + 포맷)  │
└──────────────────────────────────────────────────────────┘
```

### 2.2 데이터 흐름

1. 페이지 마운트 → 두 API 호출 병렬. 단 `audit-log` 는 `user.role === 'SUPER_ADMIN'` 일 때만.
2. 각 섹션은 독립 로딩/에러 상태 — 감사 로그 실패가 KPI 렌더를 막지 않음.
3. 캐시 없음. 매 요청 실시간 JPA 집계.

### 2.3 핵심 가드레일 (기존 admin 페이지 패턴 준수)

- 프론트: `ignore` race-guard, `Number.isFinite()` 검증 (id 해석 시), 섹션별 "에러 · 재시도" 버튼
- 백엔드: `@PreAuthorize("hasRole('ADMIN')")` (클래스 레벨) + `hasRole('SUPER_ADMIN')` (메서드 오버라이드)
- 타임존: `Asia/Seoul` (서버 = DB 전제)

---

## 3. 백엔드 설계

### 3.1 신규 클래스

| 레이어 | 파일 | 역할 |
|-------|------|------|
| Controller | `AdminDashboardController.java` | 엔드포인트 2개, `@PreAuthorize` 분리 |
| Service | `AdminDashboardService.java` | 집계 로직, 감사 로그 포맷 |
| DTO (response) | `AdminDashboardResponse.java` | 메인 응답 record |
| DTO (response) | `AdminAuditLogFeedResponse.java` | 피드 응답 record |

### 3.2 DTO 구조 (JSON 스키마)

```jsonc
// GET /api/admin/dashboard
{
  "kpi": {
    "totalActiveUsers":     { "current": 1234, "deltaFromLastMonth": 42,  "deltaPercent": 3.5  },
    "currentMonthRevenue":  { "current": 8500000, "deltaFromLastMonth": -120000, "deltaPercent": -1.4 },
    "totalAcceptedMatchings": { "current": 321, "newThisMonth": 12 },
    "approvedMentors":      { "current": 45, "pending": 3 }
  },
  "signupTrend":   [{ "date": "2026-03-26", "count": 5 }],   // 30개
  "revenueTrend":  [{ "month": "2025-05", "grossRevenue": 9000000, "refundAmount": 500000, "netRevenue": 8500000 }],  // 12개
  "queue": {
    "pendingMentorCount": 3,
    "failedPaymentCount": 2
  }
}

// GET /api/admin/dashboard/audit-log  (SUPER_ADMIN only, 최대 10건)
{
  "items": [
    {
      "id": 142,
      "adminName": "관리자 A",
      "actionType": "REFUND_PAYMENT",
      "description": "결제 #123 환불",
      "targetHref": "/admin/payments/123",
      "createdAt": "2026-04-24T09:32:10"
    }
  ]
}
```

### 3.3 집계 로직 (핵심 결정)

**순매출 (net revenue) — 환불은 환불이 발생한 달에 귀속:**

```
월 N 순매출
  = sum(payments.amount where status='PAID' and approved_at in 월 N)
  - sum(payments.refund_amount where cancelled_at in 월 N)
```

→ 원결제 달이 아니라 **환불 달에 차감**. 월별 캐시 플로우 관점.

**MTD vs Rolling — 각 지표의 시간 범위:**

| 지표 | 시간 범위 | 기준 컬럼 |
|------|----------|----------|
| `totalActiveUsers` | 현재 시점 (누적) | `users.status = ACTIVE` |
| `currentMonthRevenue` | **MTD** (이번달 1일 00:00 ~ 지금, Asia/Seoul) | `payments.approved_at`, `cancelled_at` |
| `totalAcceptedMatchings` | 누적 + MTD 신규 | `matchings.created_at / status` |
| `approvedMentors` | 누적 + 전체 PENDING | `mentor_profiles.status` |
| `signupTrend` | **Rolling 30일** | `users.created_at` |
| `revenueTrend` | **Rolling 12개월** | `payments.approved_at / cancelled_at` |

### 3.4 Repository 쿼리 (신규 메서드)

```java
// UserRepository
long countByStatus(UserStatus status);
long countByStatusAndCreatedAtBetween(UserStatus, LocalDateTime from, LocalDateTime to);
@Query("select function('date', u.createdAt) as d, count(u) " +
       "from User u where u.createdAt >= :from " +
       "group by function('date', u.createdAt)")
List<SignupDailyRow> countDailySignupsSince(@Param("from") LocalDateTime from);

// PaymentRepository
@Query("select coalesce(sum(p.amount), 0) from Payment p " +
       "where p.status = com.devmatch.entity.PaymentStatus.PAID " +
       "and p.approvedAt between :f and :t")
long sumPaidAmountBetween(@Param("f") LocalDateTime f, @Param("t") LocalDateTime t);

@Query("select coalesce(sum(p.refundAmount), 0) from Payment p " +
       "where p.status = com.devmatch.entity.PaymentStatus.CANCELLED " +
       "and p.cancelledAt between :f and :t")
long sumRefundAmountCancelledBetween(@Param("f") LocalDateTime f, @Param("t") LocalDateTime t);

// 월별 순매출: approved_at 기준 grossRevenue + cancelled_at 기준 refundAmount 를
// 12개월 row 로 조합하여 반환. 구현 시 2개의 쿼리를 month 키로 머지.
List<MonthlyRevenueRow> findMonthlyRevenueSince(LocalDateTime from);

long countByStatus(PaymentStatus status);   // FAILED 용

// MatchingRepository
long countByStatus(MatchingStatus status);
long countByStatusAndCreatedAtBetween(MatchingStatus, LocalDateTime, LocalDateTime);

// MentorProfileRepository
long countByStatus(MentorStatus status);    // APPROVED / PENDING

// AdminAuditLogRepository
List<AdminAuditLog> findTop10ByOrderByCreatedAtDesc();
```

> **구현 메모**: JPQL `function('date', ...)` 가 H2·MySQL 방언 모두 동작하는지는 Plan 단계에서 확인. 필요 시 네이티브 쿼리로 대체.

### 3.5 감사 로그 description 포맷

`AdminActionType` enum 값마다 서비스에서 사람이 읽을 수 있는 description 생성:

```java
// AdminDashboardService.formatDescription(AdminAuditLog log)
String desc = switch (log.getActionType()) {
  case CHANGE_USER_ROLE -> "회원 #%d 역할 변경".formatted(log.getTargetId());
  case SUSPEND_USER     -> "회원 #%d 정지".formatted(log.getTargetId());
  case REFUND_PAYMENT   -> "결제 #%d 환불".formatted(log.getTargetId());
  case DELETE_POST      -> "게시물 #%d 삭제".formatted(log.getTargetId());
  case DELETE_COMMENT   -> "댓글 #%d 삭제".formatted(log.getTargetId());
  // 새 enum 값 추가 시 여기도 업데이트 (AdminActionType.java 에 TODO 주석)
  default               -> "%s 실행".formatted(log.getActionType().name());
};

String href = switch (log.getTargetType()) {
  case "USER"    -> "/admin/users/" + log.getTargetId();
  case "PAYMENT" -> "/admin/payments/" + log.getTargetId();
  case "POST"    -> "/admin/posts/" + log.getTargetId();
  case "COMMENT" -> "/admin/posts";  // 댓글은 게시물 리스트로
  default        -> "/admin";
};
```

> `AdminActionType.java` 파일에 `// 주의: AdminDashboardService.formatDescription 도 업데이트` 주석을 추가하여 새 액션 추가 시 놓치지 않도록.

### 3.6 권한

```java
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")  // 클래스 레벨
class AdminDashboardController {

  @GetMapping
  public AdminDashboardResponse get() { ... }

  @GetMapping("/audit-log")
  @PreAuthorize("hasRole('SUPER_ADMIN')")  // 메서드 레벨 오버라이드
  public AdminAuditLogFeedResponse getAuditLog() { ... }
}
```

### 3.7 DDL 변경

**없음.** 모든 필요한 테이블·컬럼은 Phase II Feature 1~3 에서 이미 추가됨 (`users.status`, `payments.cancelled_at`, `admin_audit_log` 전체). 프로덕션 SQL 실행 불필요.

---

## 4. 프론트엔드 설계

### 4.1 파일 변경 · 추가

| 파일 | 작업 | 설명 |
|------|------|------|
| `frontend/src/app/admin/dashboard/page.tsx` | **신규** | 페이지 셸, 2 API 호출, 섹션 렌더 |
| `frontend/src/app/admin/page.tsx` | 수정 | `redirect('/admin/dashboard')` (현재 `/admin/mentor`) |
| `frontend/src/components/admin/AdminSidebar.tsx` | 수정 | `NAV_ITEMS` 최상단에 📊 대시보드 추가 |
| `frontend/src/components/admin/dashboard/KpiCards.tsx` | **신규** | 4 KPI 카드 |
| `frontend/src/components/admin/dashboard/SignupTrendChart.tsx` | **신규** | Recharts 라인 차트 |
| `frontend/src/components/admin/dashboard/RevenueTrendChart.tsx` | **신규** | Recharts 바 차트 (단일 net 바) |
| `frontend/src/components/admin/dashboard/ActionQueue.tsx` | **신규** | 처리 큐 2항목 |
| `frontend/src/components/admin/dashboard/RecentAuditLog.tsx` | **신규** | SUPER_ADMIN 전용 |
| `frontend/src/lib/api/admin-dashboard.ts` | **신규** | `fetchDashboard()`, `fetchAuditLog()` |
| shadcn 컴포넌트 | 추가 | `npx shadcn@latest add chart` |
| `package.json` | 의존성 | `recharts` (chart 추가시 자동 설치) |

### 4.2 페이지 레이아웃

```
┌──────────────────────────────────────────────────────┐
│ 대시보드                                              │
│ 관리자 콘솔 홈 · 2026년 4월 24일 기준                  │
├──────────────────────────────────────────────────────┤
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐          │
│ │활성회원 │ │이번달매출│ │누적매칭 │ │승인멘토 │  KPI 4   │
│ │ 1,234  │ │₩8.5M   │ │  321   │ │  45    │          │
│ │ +42 🔺 │ │ -1.4% 🔻│ │이번달+12│ │대기 3명│          │
│ └────────┘ └────────┘ └────────┘ └────────┘          │
├──────────────────────────────────────────────────────┤
│ ┌────────────────────┐ ┌──────────────────────────┐  │
│ │ 일별 신규 가입 (30일)│ │ 월별 순매출 (12개월)       │  │
│ │    line chart       │ │    bar chart              │  │
│ └────────────────────┘ └──────────────────────────┘  │
├──────────────────────────────────────────────────────┤
│ 처리 큐                                               │
│ ┌────────────────────────────────────────────────┐   │
│ │ 🧑 승인 대기 멘토 3명        → 멘토 심사로 이동    │   │
│ │ 💳 실패 결제 2건             → 결제 관리로 이동    │   │
│ └────────────────────────────────────────────────┘   │
├──────────────────────────────────────────────────────┤
│ 최근 관리자 활동 (SUPER_ADMIN 만)                     │
│ • 관리자 A: 결제 #123 환불 (3분 전)                   │
│ • 관리자 B: 게시물 #45 삭제 (1시간 전)                │
│ • ... (총 10건)                                       │
└──────────────────────────────────────────────────────┘
```

- 감사 로그 섹션은 SUPER_ADMIN 이 아니면 **DOM 렌더 자체 안 함** (기존 `/app/admin/admins/page.tsx` 패턴).

### 4.3 데이터 페치 패턴

```ts
// app/admin/dashboard/page.tsx
'use client';
const [summary, setSummary] = useState<DashboardResponse | null>(null);
const [summaryError, setSummaryError] = useState(false);
const [auditLog, setAuditLog] = useState<AuditLogResponse | null>(null);
const [auditLogError, setAuditLogError] = useState(false);
const { user } = useAuth();

useEffect(() => {
  let ignore = false;  // race-guard

  fetchDashboard()
    .then(r => { if (!ignore) setSummary(r); })
    .catch(() => { if (!ignore) setSummaryError(true); });

  if (user?.role === 'SUPER_ADMIN') {
    fetchAuditLog()
      .then(r => { if (!ignore) setAuditLog(r); })
      .catch(() => { if (!ignore) setAuditLogError(true); });
  }

  return () => { ignore = true; };
}, [user?.role]);
```

- 각 섹션에 스켈레톤 UI (`<Skeleton>`) + 에러 시 "에러 · 재시도" 버튼.
- 두 API 모두 해당 섹션 단위로 재시도 가능.

### 4.4 차트 세부

- **SignupTrendChart**: Recharts `<LineChart>` + `<Line type="monotone">`. X축 = 날짜(MM/DD), Y축 = 가입수. 툴팁.
- **RevenueTrendChart**: Recharts `<BarChart>`. X축 = 월(YYYY-MM), Y축 = 순매출. 툴팁에 gross/refund/net 3줄 분해. 단일 net 바 (스택 아님) — 환불이 매출보다 많으면 바가 음수 영역으로.

### 4.5 포맷팅 유틸

- 통화: `Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' })`
- 날짜: `new Date(iso).toLocaleDateString('ko-KR')`
- 상대 시간: 기존 프로젝트에 유틸 있는지 확인 후 재사용, 없으면 `Intl.RelativeTimeFormat` 경량 구현

### 4.6 Pencil 목업

구현 착수 전 **Pencil 로 목업 작성 → 사용자 승인 → shadcn 매핑** 플로우 (프로젝트 전역 규칙, `feedback_frontend_preview.md`).
출력물은 `docs/mockups/admin-dashboard.md` 로 commit.

---

## 5. 권한 · 엣지 케이스 · 에러 핸들링

### 5.1 권한 매트릭스

| 사용자 | `/admin/dashboard` 페이지 | `/api/admin/dashboard` | `/api/admin/dashboard/audit-log` |
|--------|-------------------------|-----------------------|---------------------------------|
| 비로그인 | 로그인 리다이렉트 | 401 | 401 |
| MENTEE / MENTOR | 403 (AdminLayout 가드) | 403 | 403 |
| ADMIN | ✅ 페이지 접근, 감사 로그 섹션은 DOM 렌더 안 함 | ✅ 200 | 403 |
| SUPER_ADMIN | ✅ 모든 섹션 | ✅ 200 | ✅ 200 |

- 프론트 방어 + 백엔드 강제 이중 방어.
- 기존 `/app/admin/admins/page.tsx` SUPER_ADMIN 가드 패턴 재사용.

### 5.2 엣지 케이스

| 케이스 | 처리 |
|--------|------|
| 데이터 0 (가입자 0명) | KPI "0", 이전달도 0이면 `deltaPercent = null` → UI "—" |
| 매출 0 · 환불만 있는 달 | `netRevenue < 0` 허용. 차트 바 음수 영역. 툴팁에 명시 |
| 감사 로그 0건 | "최근 활동 없음" 플레이스홀더 |
| `audit_log.admin_id` 가 삭제된 관리자 | `users` LEFT JOIN → `adminName = "(삭제된 관리자)"` |
| audit_log target 이 삭제된 리소스 | `targetHref` 그대로 링크 유지 (목적지가 404 떠도 기록은 유지) |
| 지난달 0 → % 분모 0 | `deltaPercent = null` → UI "—" |
| 타임존 | `Asia/Seoul`. "이번달" 경계는 `LocalDate.now(ZoneId.of("Asia/Seoul"))` |
| `approved_at` NULL 인 PAID | Plan 단계에서 Payment 엔티티 검토 후 확정. Fallback 은 `created_at`, 없으면 무시. |

### 5.3 에러 핸들링

- 백엔드 예외 → 기존 `GlobalExceptionHandler` (500 반환, 민감 정보 노출 금지)
- 프론트 네트워크/500 → 섹션 단위 "에러 · 재시도" 버튼 (기존 admin 페이지 동일 패턴)
- 401 → `AuthContext` 인터셉터가 자동 로그아웃·리다이렉트
- 403 → AdminLayout 의 "접근 권한 없음" 렌더

### 5.4 보안

- `audit-log` 응답에 `metadata` JSON 원문을 **내려보내지 않음**. `description`, `targetHref` 만 노출 — metadata 에 민감 사유 원문이 포함될 수 있어 서비스 레이어에서 필터링.
- KPI 의 매출 합계는 ADMIN 전원 노출 (포트폴리오 목적). 실서비스면 SUPER_ADMIN 전용으로 올리는 선택지 있지만 현재는 YAGNI.

---

## 6. 테스트 전략

### 6.1 백엔드 (JUnit5 + Spring Boot Test)

| 테스트 클래스 | 커버 | 케이스 |
|-------------|-----|--------|
| `AdminDashboardServiceTest` | Service 단위 | 정상 집계 / 환불이 환불 달에 귀속 / 지난달 0 → deltaPercent null / 감사 로그 포맷 switch / 삭제된 관리자 fallback |
| `AdminDashboardControllerTest` | Controller + Security | ADMIN `/dashboard` 200 / ADMIN `/audit-log` 403 / SUPER_ADMIN 둘 다 200 / MENTEE 403 / 비로그인 401 |
| `MonthlyRevenueRowMappingTest` (선택) | 네이티브 쿼리 매핑 | 12개월 row 완전성 |

### 6.2 프론트엔드

Phase II 기존 관리자 페이지(users/payments/posts) 에 Jest/RTL 테스트가 있는지 Plan 단계에서 확인:
- **있으면 동일 수준** 으로 `<KpiCards>`, `<ActionQueue>` 등 컴포넌트 단위 테스트 추가
- **없으면** TypeScript 타입 검증 + `npm run build` 성공 + 브라우저 수동 검증 으로 대체

### 6.3 수동 검증 (로컬)

1. Pencil 목업 사용자 승인 완료 후 구현 착수
2. 시드 데이터로 여러 달·여러 날짜 데이터 확보 후 `npm run dev` 실행
3. ADMIN 로그인 → 감사 로그 섹션 없음, 나머지 모두 렌더
4. SUPER_ADMIN 로그인 → 감사 로그까지 모두 렌더
5. 네트워크 탭으로 2 API 호출 확인 (일반 ADMIN 은 1개만)
6. 빈 데이터 케이스 시뮬레이션 (시드 비우고 재확인)

---

## 7. 배포 전략

### 7.1 DDL · 환경변수 · feature flag

- **DDL 변경: 없음** — Phase II Feature 1~3 에서 모두 완료.
- **환경변수: 없음.**
- **feature flag: 없음** — 즉시 노출.

### 7.2 ROADMAP 업데이트

- §10 "Phase II Feature 1~3 배포" 섹션 다음에 "Phase III Feature 1 (대시보드) 배포 — DDL/환경변수 변경 없음" 한 줄.
- §10 상단 Admin API 테이블의 `GET /api/admin/dashboard` 행에 ✅ 체크.

### 7.3 사이드바 리다이렉트 영향

- `/admin` → `/admin/dashboard` 로 리다이렉트 변경 (현재 `/admin/mentor`).
- 기존 직접 딥링크(`/admin/mentor` 등) 는 그대로 동작. root `/admin` 만 영향.

### 7.4 문서 업데이트

| 파일 | 변경 |
|------|------|
| `docs/mockups/admin-console-overview.md` | 상태 테이블 Phase III Feature 1 업데이트, 사이드바 다이어그램에 📊 최상단 추가 |
| `docs/mockups/admin-dashboard.md` | **신규** (Pencil 목업 export) |
| `ROADMAP.md` §10 | 7.2 체크리스트 |

### 7.5 커밋 · PR

- **브랜치**: `claude/admin-dashboard`
- **커밋 분할 (의도)**:
  1. `feat(admin-dashboard): 목업 + 설계 문서`
  2. `feat(admin-dashboard): 백엔드 엔드포인트 + 서비스 + 테스트`
  3. `feat(admin-dashboard): 프론트 페이지 + 사이드바/루트 리다이렉트`
  4. `docs(roadmap): Phase III Feature 1 배포 메모`
- **PR 제목**: `feat(admin-dashboard): Phase III Feature 1 — 관리자 콘솔 홈 대시보드`

---

## 8. Out of Scope (재확인)

- ❌ CSV/Excel export
- ❌ 기간 선택 UI (date range picker) — 범위 고정 (MTD / 30일 / 12개월)
- ❌ 차트 클릭 드릴다운
- ❌ Redis 캐시 / `@Cacheable`
- ❌ 실시간 업데이트 (WebSocket/SSE)
- ❌ 신고 기반 처리 큐 (신고 기능 자체 없음)

추후 필요 시 별도 spec.

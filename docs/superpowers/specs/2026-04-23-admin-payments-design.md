# 관리자 콘솔 Phase II — 결제 관리 (Feature 2) 설계 (Design Spec)

> 작성일: 2026-04-23
> 대상 Phase: II / Feature 2
> Common 의존: `docs/superpowers/specs/2026-04-22-admin-console-common-design.md` (PR #42 에서 도입 예정, 본 스펙 구현 시점엔 main 에 머지되어 있어야 함)
> 관련 mockup (pre-brainstorming 초안, 본 스펙으로 갱신됨): `docs/mockups/admin-payments.md` (PR #42 에서 main 유입)
> 선행 PR: #42 (Common + Feature 1) — 본 스펙의 구현은 PR #42 머지 이후 main 에서 새 브랜치 분기

---

## 1. 배경

Phase II Common (PR #42) 으로 감사 로그 인프라(`AdminAuditLog` + `AdminAuditLogService`) 와 관리자 사이드바 4개 메뉴 확장이 완료된다. 본 스펙은 두 번째 feature 인 **결제 관리** 의 상세 설계다.

### 핵심 변화 (pre-brainstorming 초안 대비)

1. **부분 환불 제외** — MVP 는 전액 환불만. 부분 환불은 Phase III 로 이관.
2. **이메일 알림 제외** — Common 결정 (Phase II 는 인프라 없이 진행).
3. **연관 엔티티 소프트 캐스케이드** — Payment → Matching 상태 전이만. LMS/세션/과제 데이터는 보존 (감사/분쟁 대비). 접근 차단은 기존 `LmsAccessService` 의 allow-list 가드에 의존.
4. **토스 호출 설정 플래그** — `app.payment.toss-cancel-enabled` 로 dev/prod 제어. Dev 시드 데이터에 실제 `paymentKey` 없어도 동작.
5. **요약 카드 포함** — Q1 에서 "B(조회+환불)" 를 선택했지만 목록 쿼리에 저렴하게 GROUP BY 를 덧붙일 수 있어 관리자 현황 파악 가치를 인정해 포함.
6. **공용 FE 컴포넌트 추출** — Feature 2 는 lazy 추출 시점. Feature 1 에서 인라인 구현된 공통 UI 를 `components/admin/common/` 으로 이관.

## 2. 스코프

### In scope

- `/admin/payments` — 결제 목록 (상태 탭 · 기간 필터 · 검색 · 페이지네이션 · 요약 카드 4개)
- `/admin/payments/[id]` — 결제 상세 + 환불 액션 (CONFIRMED 일 때만 노출)
- 백엔드 신규 4개 엔드포인트 (`AdminPaymentController`)
- `Payment` 엔티티 확장: `processedByAdminId`, `cancelledAt`
- `MatchingStatus` enum 확장: `CANCELLED` 추가 + `Matching.cancel(reason)` 메서드
- 토스 환불 API 재사용 (`TossPaymentService.cancelPayment`) + 설정 플래그
- 환불 처리의 `AdminAuditLog` 기록 (Common 호출 규약 준수)
- 공용 FE 컴포넌트 6개를 `components/admin/common/` 으로 이관 (Feature 1 인라인 버전에서 추출)
- `docs/smoke/<스모크 실행일 yyyy-mm-dd>-admin-payments-smoke.md` 수동 스모크 가이드 작성

### Out of scope (명시적 제외)

| 항목 | 이유 |
|------|------|
| 부분 환불 | 토스 API 바디 확장 + 남은 세션 기준 계산 로직 필요 → 스펙 비대화. Phase III |
| 재환불 / 환불 취소 | 단일 처리자 원칙 (`processedByAdminId` 단일 FK) |
| 이메일/푸시 알림 | Common 결정 — Phase III |
| 멘티/멘토에게 환불 안내 | 위 알림 의존, Phase III |
| 환불에 따른 미래 `MentoringSession` 일정 명시적 취소 | 기존 LMS 가드(allow-list)가 매칭 CANCELLED 를 차단 → 간접 처리. Phase III 에서 명시적 상태 전이 |
| 결제 통계 (시계열 차트, 멘토별 매출) | Phase III 대시보드 |
| 환불 승인 워크플로 (2인 확인) | 학생 프로젝트엔 과함 |
| 낙관적 락(`@Version`) | 서비스 레이어 가드(`processedByAdminId != null`) 로 충분. Phase III |

## 3. 결정 사항 요약

| 항목 | 결정 | 대안 / 탈락 이유 |
|------|------|-----------------|
| MVP 스코프 | B — 조회 + 관리자 강제 환불 (+요약) | A(조회만)는 관리 가치 부족, D(부분환불)는 범위 과대 |
| 연관 엔티티 처리 | **A: 소프트 캐스케이드** (Payment+Matching 만, LMS/세션 보존) | B(풀 캐스케이드)는 접근 가드로 해결 가능한 것을 이중 처리, C(세션 진행 시 환불 거부)는 "거부 기준" 스펙 확대 |
| 토스 API 호출 방식 | **A: 기존 경로 재사용 + 설정 플래그** | B(내부 상태만)는 prod 이전 별도 작업 부담, C(무조건 호출)는 dev 시드 셋업 비용 ↑ |
| 요약 카드 포함 여부 | **A: 4개 카드 포함** (별도 summary 엔드포인트) | B(제외)는 관리자 초기 체감 가치 부족 |
| 엔티티 설계 접근법 | **1: Payment 컬럼 확장 + Matching enum 확장** | 2(AuditLog-only) 는 상세 화면 쿼리 복잡, 3(별도 Refund 테이블)은 부분 환불 없이는 과한 구조 |
| 처리자 추적 | `Payment.processedByAdminId` 컬럼 (읽기 편의) + `AdminAuditLog` 레코드 (감사 소스) | AuditLog-only 는 상세 화면 N+1 유발 |
| 환불 가능 상태 | `CONFIRMED` 만 | PENDING 은 토스 자동 만료, CANCELLED/FAILED 는 종료 상태 |
| 중복 환불 방지 | `status != CONFIRMED` 가드만으로 충분 (이미 CANCELLED 인 결제는 자연스럽게 차단됨) + 토스 측 `ALREADY_CANCELED_PAYMENT` 안전망 | 별도 `processedByAdminId` 가드는 status 가드와 중복 (YAGNI). 낙관적 락(`@Version`) 은 현 단계엔 과함 |

## 4. 백엔드 설계

### 4.1 데이터 모델 변경

**`Payment` 엔티티 신규 컬럼**

| 컬럼 | 타입 | nullable | 용도 |
|------|------|----------|------|
| `processed_by_admin_id` | BIGINT (FK users.id, 논리) | YES | 관리자 강제 환불 시만 세팅. 사용자 자기 취소는 NULL |
| `cancelled_at` | TIMESTAMP | YES | `status` 가 CANCELLED 로 전환되는 시점 |

- 기존 `cancel_reason` 은 관리자/사용자 공용으로 재사용
- 기존 `Payment.cancel(String)` 시그니처는 유지. 별도 `markProcessedByAdmin(Long adminId, LocalDateTime at)` 메서드 신설 → 관리자 환불 경로에서만 호출

**`MatchingStatus` enum 확장**

```java
public enum MatchingStatus {
    PENDING, ACCEPTED, REJECTED, TRIAL, SWAPPED, CANCELLED
}
```

- `Matching.cancel(String reason)` 신규 메서드 — `status = CANCELLED`, `rejectedReason = reason` (기존 컬럼 재사용, 라벨만 환불 사유로 쓰임)
- DB 컬럼은 VARCHAR 이므로 DDL 변경 **불필요**
- `LmsAccessService.java:31` 의 allow-list 가드(`TRIAL`/`ACCEPTED` 만 허용) 덕분에 CANCELLED 는 자동으로 차단됨 → 가드 확장 불필요

**`AdminAuditLog` (Common PR #42 도입)**

- 본 피처에선 `AdminAuditLogService.record()` 호출만
- 액션 타입: `"PAYMENT_REFUND"`
- 타겟: `targetType="PAYMENT"`, `targetId=payment.id`
- metadata (TEXT JSON): `{ "refundAmount": 150000, "reason": "...", "matchingAffected": true, "matchingId": 54 }`

### 4.2 엔드포인트

모두 `/api/admin/payments` 하위. SecurityConfig 공통 가드로 `ROLE_ADMIN` 이상 요구.

| 메서드 | 경로 | 설명 | 주요 에러 |
|--------|------|------|-----------|
| GET | `/api/admin/payments` | 목록 + 필터 + 페이지네이션 | 400 (기간 역전) |
| GET | `/api/admin/payments/summary` | 요약 카드 (기간 집계) | 400 (기간 역전) |
| GET | `/api/admin/payments/{id}` | 상세 (결제+사용자+신청서+매칭+환불이력) | 404 |
| POST | `/api/admin/payments/{id}/refund` | 관리자 강제 환불 | 422 (상태/중복), 500 (토스 실패) |

**`GET /api/admin/payments` 쿼리 파라미터**

- `status`: `ALL|PENDING|CONFIRMED|CANCELLED|FAILED` (기본 `ALL`)
- `q`: 사용자 이름/이메일/orderId LIKE 검색 (escape 처리, 기존 `LikeEscaper` 재사용)
- `from`, `to`: `yyyy-MM-dd` (기본: 최근 30일)
- `page`, `size`: 기본 `0/20`
- 정렬: `createdAt DESC` 고정

**`GET /api/admin/payments/summary` 응답**

```json
{
  "totalAmount": 12450000,
  "confirmedCount": 142,
  "refundedAmount": 820000,
  "refundRate": 0.052
}
```

- `refundRate = refundedCount / (confirmedCount + refundedCount)`, 분모 0 이면 0.0

**`POST /api/admin/payments/{id}/refund` 요청/응답**

```
Request:  { "reason": "결제 중복 — 고객 요청에 따라 환불" }
Response: AdminPaymentDetailResponse (환불 후 상태)
```

- `reason`: `@Size(min=10, max=500)` 검증

### 4.3 서비스 레이어

**신규 `AdminPaymentService` (`@Service @Transactional(readOnly = true)`)**

의존성: `PaymentRepository`, `MatchingRepository`, `UserRepository`, `TossPaymentService`, `AdminAuditLogService`, `TossCancelProperties`

공개 메서드:

- `Page<AdminPaymentListItemResponse> listPayments(AdminPaymentFilter filter, Pageable pageable)`
- `AdminPaymentSummaryResponse getSummary(LocalDate from, LocalDate to)`
- `AdminPaymentDetailResponse getDetail(Long paymentId)`
- `AdminPaymentDetailResponse refundPayment(Long paymentId, Long adminId, String reason)` — `@Transactional`

**`refundPayment` 트랜잭션 플로우**

```
1. Payment 조회 (없으면 PaymentNotFoundException → 404)
2. 가드:
   - payment.status != CONFIRMED → 422 "승인된 결제만 환불할 수 있습니다"
     (사용자 자기 취소/관리자 환불/실패 모두 이 가드로 차단됨 — CANCELLED 는 재환불 불가)
3. 토스 호출 (tossCancelProperties.enabled 가 true 이고 paymentKey != null 일 때):
   - tossPaymentService.cancelPayment(paymentKey, reason)
   - 실패 시 PaymentFailedException → 500 (트랜잭션 롤백)
   - paymentKey == null 이고 enabled == true 이면 422 "환불을 위한 결제키가 없습니다"
4. Payment 상태 전이:
   - payment.cancel(reason)
   - payment.markProcessedByAdmin(adminId, LocalDateTime.now())
5. 연관 Matching 소프트 캐스케이드:
   - payment.matchingId != null 이면 Matching 조회
   - status ∈ {PENDING, ACCEPTED, TRIAL} 일 때만 matching.cancel(reason)
   - REJECTED/SWAPPED/CANCELLED 는 skip (matchingAffected=false 로 AuditLog 에 기록)
6. adminAuditLogService.record(adminId, "PAYMENT_REFUND",
     targetType="PAYMENT", targetId=payment.id,
     metadata=JSON{ refundAmount, reason, matchingAffected, matchingId })
7. 갱신된 AdminPaymentDetailResponse 반환
```

### 4.4 설정

`application.yml`:

```yaml
app:
  payment:
    toss-cancel-enabled: true
```

`application-dev.yml`: `toss-cancel-enabled: false` (시드 데이터에 실제 paymentKey 없음)
`application-prod.yml`: `toss-cancel-enabled: true`

`@ConfigurationProperties("app.payment")` → `TossCancelProperties` record.

### 4.5 Repository 변경

- `PaymentRepository` 에 Specification 기반 조회를 위한 `JpaSpecificationExecutor<Payment>` 믹스인 추가
- 요약 집계를 위한 JPQL 2개 (SUM CASE WHEN status=CONFIRMED, COUNT CASE WHEN status=CANCELLED 등) 또는 단일 쿼리에 GROUP BY
- `UserRepository.findByNameContainingOrEmailContaining` 은 PR #42 (Feature 1) 에서 이미 추가됨 — 본 피처는 재사용만
- 검색어 특수문자(`%`, `_`) 이스케이프는 본 피처 범위에 포함하지 않음. Spring Data `Containing` 키워드는 이스케이프하지 않지만, 관리자 전용 엔드포인트 · 검색 결과 페이지네이션 제한으로 영향 경미. Phase III 에서 공용 이스케이프 유틸 도입 검토

## 5. 프런트엔드 설계

### 5.1 페이지 / 라우트

```
app/(admin)/payments/
  page.tsx                            /admin/payments   목록
  [id]/page.tsx                       /admin/payments/[id]   상세
  _components/
    PaymentListTable.tsx
    PaymentSummaryCards.tsx
    PaymentDetailSection.tsx
    PaymentRefundDialog.tsx
  _api/adminPaymentApi.ts
  _types.ts
```

### 5.2 공용 FE 컴포넌트 추출 (Feature 2 = lazy 추출 시점)

Feature 1 에서 인라인 구현된 UI 를 `components/admin/common/` 으로 이관. 기능 차이 없는 순수 이동.

| 컴포넌트 | 역할 |
|---------|------|
| `AdminListHeader` | 제목 + muted 설명 + 오른쪽 액션 슬롯 |
| `AdminTabs` | 상태 탭 + 카운트 배지 (제네릭) |
| `AdminPagination` | shadcn `pagination` 래퍼 + URL searchParams 동기화 |
| `AdminSearchInput` | debounce 300ms + URL 동기화 |
| `AdminDateRangePicker` | `popover + calendar` 조합 (본 피처에서 신규 설치, 추출 동시에) |
| `AdminStatusBadge` | 엔티티별 상태 → 색상 매핑 테이블 |

**추출 전략**: 본 피처 플랜의 Task 0(선행 작업) 로 배치. Feature 1 의 import 경로도 본 Task 에서 함께 업데이트.

### 5.3 URL ↔ 상태 동기화

목록 페이지의 필터는 URL searchParams 로 1:1 반영. `useSearchParams` + `useRouter.push` 조합. 브라우저 뒤로가기/공유 URL 복원 가능.

```
/admin/payments?status=CONFIRMED&q=kim&from=2026-03-22&to=2026-04-22&page=0
```

### 5.4 환불 확인 모달 (`PaymentRefundDialog`)

- shadcn `Dialog` + `Textarea` + `Alert variant="destructive"`
- `react-hook-form` + `zod` (`z.string().min(10).max(500)`)
- 제출 중: `isPending` → 버튼 disabled + spinner
- 성공: `toast.success("환불 처리되었습니다")` + 모달 닫기 + `router.refresh()`
- 실패: 모달 내 `Alert` 로 서버 에러 inline 렌더 (모달 유지 → 재시도 가능)

### 5.5 shadcn 신규 설치

- `popover`, `calendar` (이 피처에서 처음 사용). Feature 3 에서도 재사용 예정
- 이미 설치(Common/Feature 1): `dialog`, `tabs`, `table`, `badge`, `card`, `sonner`, `select`, `pagination`

### 5.6 접근 제어

`(admin)` layout 의 역할 체크(Feature 1 도입)에 의존. 본 피처는 별도 가드 로직 추가하지 않음.

### 5.7 요약 카드 UX

- 4개 카드를 `grid-cols-4 gap-4` 로 상단 고정
- 기간 필터와 **연동** — 필터 변경 시 summary 도 재조회 (React Query `useQuery` 의존성에 `from`/`to` 포함)
- 로딩 상태: skeleton 카드

## 6. 에러 / 엣지 케이스

### 6.1 동시성 (두 관리자 동시 환불)

두 트랜잭션이 모두 status=CONFIRMED 를 읽고 진행 가능. 토스 `cancel` API 는 paymentKey 기준 idempotent — 두 번째 호출은 `ALREADY_CANCELED_PAYMENT` 4xx 응답 → `PaymentFailedException` → 500 (트랜잭션 롤백). DB 는 첫 번째 커밋만 반영되어 최종 일관성 유지. 본 MVP 에서는 application-level 직렬화(@Version/SELECT FOR UPDATE) 불필요.

### 6.2 토스 API 실패 시나리오

- 4xx (이미 취소된 paymentKey 등): `PaymentFailedException` → 500. 트랜잭션 롤백으로 Payment 유지, 관리자 재시도 가능
- 5xx/네트워크: 동일 롤백. `log.error` 원본 응답 기록
- **복구 엣지**: 토스 cancel 성공 후 커밋 전 애플리케이션 크래시 → 토스 측 취소, DB 는 CONFIRMED. 재시도 시 토스가 `ALREADY_CANCELED_PAYMENT` 반환 → 500. 수동 복구 필요. 운영 런북은 Phase III 에서 보강

### 6.3 paymentKey NULL 인 CONFIRMED 결제

- 시드/마이그레이션 잔재 데이터
- `toss-cancel-enabled=true` + paymentKey NULL → 422 "환불을 위한 결제키가 없습니다"
- dev 에선 `enabled=false` 로 우회 가능 (토스 호출 skip)

### 6.4 Matching 상태 이상

- `matchingId == null` (PENDING 단계에서 환불): 캐스케이드 생략, AuditLog metadata.matchingAffected=false
- Matching REJECTED/SWAPPED/CANCELLED: 이미 종료 상태, 캐스케이드 skip

### 6.5 기간 필터 경계

- `from > to` → 400 (`ValidationException`)
- `from` 만: `to = today`
- `to` 만: `from = to - 30일`
- 둘 다 없음: 최근 30일
- `from` 이 오늘보다 미래: 빈 결과 (에러 아님)

### 6.6 검색어 이상

- `q` 길이 2자 미만: 서버는 그대로 LIKE 처리 (관리자 전용 엔드포인트, 성능 우려 낮음). FE 에서만 debounce 로 과호출 방지
- 특수문자(`%`, `_`): 본 피처에서는 별도 이스케이프 없이 Spring Data `Containing` 기본 동작 사용. 한계 및 Phase III 이관은 §4.5 참조

### 6.7 권한 우회

- JWT 없음 → 401 (기존 Security 필터)
- 비-ADMIN 토큰 → 403 (`/api/admin/**` 공통 가드)
- **관리자가 자기 자신의 결제 환불**: 본 피처 범위에선 차단하지 않음. 단 FE 상세 화면에서 `adminId == payment.userId` 인 경우 노란 경고 배너 표시 (감사 가능성 강조)

### 6.8 AuditLog 기록 실패

- DB 제약 위반 등으로 `auditLogService.record()` 가 throw → 트랜잭션 롤백 → 전체 환불 취소. "감사 없이 처리 금지" 원칙
- 토스는 이미 cancel 된 상태라 §6.2 마지막 엣지와 동일 수동 복구 필요

## 7. 테스트 전략

### 7.1 백엔드 `AdminPaymentServiceTest` (Mockito, 기존 `MentorServiceTest` 패턴)

**환불 해피 패스 (3)**

- CONFIRMED + Matching ACCEPTED → Payment CANCELLED, Matching CANCELLED, Toss 1회, AuditLog 1회
- CONFIRMED + Matching null → Payment CANCELLED, Matching 미호출
- CONFIRMED + Matching TRIAL → TRIAL 도 cancel 대상

**환불 가드 (4)**

- PENDING 환불 시도 → 422
- CANCELLED 재환불 시도 → 422 (중복 환불은 status 가드로 차단)
- FAILED 환불 시도 → 422
- Matching REJECTED → Payment 만 CANCELLED, matching.cancel 미호출
- Matching SWAPPED → 동일

**토스 플래그 (2)**

- `enabled=false` → `TossPaymentService.cancelPayment` 호출 안 됨 (`verify(...).never()`)
- `enabled=true` + Toss 예외 → 예외 전파, 서비스 레이어 롤백 의도 확인

**조회/요약 (2)**

- 목록 필터 조합 (status + 기간) → Specification 조건 생성 확인
- Summary `refundRate` 계산 (분모 0 케이스 포함)

### 7.2 컨트롤러 웹 테스트

기존 프로젝트에 `@WebMvcTest` 샘플이 없으므로 본 피처에서도 **도입하지 않음**. 컨트롤러는 얇은 위임만 유지 (DTO 바인딩, `@AuthenticationPrincipal` 주입). HTTP 레벨 검증은 §7.4 수동 스모크로.

### 7.3 프런트엔드 테스트

프로젝트 FE 테스트 인프라 없음 → 본 피처에서도 도입하지 않음. `tsc --noEmit` 과 빌드 통과가 CI 검증선.

### 7.4 수동 스모크 (`docs/smoke/YYYY-MM-DD-admin-payments-smoke.md`)

Feature 1 스모크 문서 포맷을 재사용. 6개 시나리오:

1. 목록 진입 + 탭 전환 + 요약 카드 렌더
2. 기간 필터 + 검색 필터 URL 동기화 (새로고침 유지)
3. CONFIRMED 결제 환불 → 토스 호출(dev 에선 skip) → 상세 이력 표시
4. 연결된 Matching 이 CANCELLED 전환 + 해당 멘티의 LMS 접근 시 차단 확인
5. 비-ADMIN 접근 시 403
6. 중복 환불 시도 → 422 에러 토스트

### 7.5 테스트 데이터 (Seed 보강)

기존 `AdminSeed` (PR #39) 에 아래 결제 레코드 추가:

- CONFIRMED 결제 (Matching ACCEPTED 연결) × 2
- CONFIRMED 결제 (Matching 미연결) × 1
- PENDING 결제 × 1
- CANCELLED (사용자 자기 취소, processedByAdminId=NULL) × 1
- CANCELLED (관리자 환불, processedByAdminId 세팅) × 1
- FAILED 결제 × 1

`paymentKey` 는 `FAKE_PK_xxx` 더미. Dev 프로파일에서 `toss-cancel-enabled=false` 로 토스 호출 우회.

## 8. 마이그레이션

### 8.1 개발 환경

- JPA `ddl-auto=update` 로 `payments` 테이블 컬럼 2개 자동 추가
- `matchings` 테이블 DDL 변경 없음 (VARCHAR 재활용)

### 8.2 프로덕션 배포 SQL

```sql
-- 1) Payment 컬럼 추가
ALTER TABLE payments
  ADD COLUMN processed_by_admin_id BIGINT NULL,
  ADD COLUMN cancelled_at TIMESTAMP NULL;

-- (선택) 과거 CANCELLED 데이터의 cancelled_at 을 updated_at 으로 backfill
UPDATE payments
   SET cancelled_at = updated_at
 WHERE status = 'CANCELLED' AND cancelled_at IS NULL;
```

- Matching 은 DDL 없음. 애플리케이션 레벨 enum 에 CANCELLED 가 append 되므로 과거 레코드 영향 없음
- `ROADMAP.md §10` 배포 체크리스트에 본 SQL 추가

## 9. 구현 순서 (권장)

1. **Task 0 — 공용 FE 컴포넌트 추출** (§5.2). Feature 1 인라인 → `components/admin/common/`. Feature 1 import 경로 동시 업데이트. 동작 변경 없음.
2. **Task 1 — Payment 엔티티 확장** (`processedByAdminId`, `cancelledAt`, `markProcessedByAdmin` 메서드)
3. **Task 2 — MatchingStatus enum 확장** (`CANCELLED` append + `Matching.cancel(reason)` 메서드)
4. **Task 3 — 토스 설정 플래그** (`TossCancelProperties`, yml 3개 반영)
5. **Task 4 — DTO 및 Repository 변경** (Specification, 요약 쿼리, Admin* DTO 5개)
6. **Task 5 — `AdminPaymentService` 구현 + 단위 테스트 12케이스**
7. **Task 6 — `AdminPaymentController` 구현** (얇은 위임)
8. **Task 7 — Seed 보강** (결제 7건)
9. **Task 8 — FE `/admin/payments` 목록 + 요약 카드**
10. **Task 9 — FE `/admin/payments/[id]` 상세 + 환불 다이얼로그**
11. **Task 10 — shadcn `popover`/`calendar` 설치 커밋**
12. **Task 11 — 수동 스모크 문서 작성 + 실행 결과 기록**

---

## 변경 이력

- 2026-04-23: 최초 작성 (브레인스토밍 Q1-Q4 + 접근법 1 + 섹션별 승인 완료 후)

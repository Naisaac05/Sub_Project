# Phase II Feature 2 관리자 결제 관리 스모크

> 작성일: 2026-04-24
> 대상 브랜치: `claude/frosty-jackson-dc3071`
> 대상 스펙: [admin-payments](../superpowers/specs/2026-04-23-admin-payments-design.md)
> 대상 플랜: [plans/2026-04-23-admin-payments.md](../superpowers/plans/2026-04-23-admin-payments.md)

본 문서는 Phase II Feature 2 (관리자 결제 관리) 머지 전 필수로 수행할 수동 통합 스모크 가이드다.
백엔드 단위 테스트(12 케이스)·프런트 타입 체크·빌드는 CI 에서 녹색이지만, **통합 플로우(토스 환불 skip 로그, Matching 연쇄 취소, LMS 접근 차단, 권한 경계, 중복 환불 가드)는 런타임에서만 검증 가능**하다.

---

## 0. 사전 조건

### 0.1 서버 기동

| 컴포넌트 | 명령 | 기대 |
|---|---|---|
| DB | (dev: H2 인메모리, `ddl-auto: update`) | `payments.processed_by_admin_id`, `payments.cancelled_at` 자동 반영 |
| 백엔드 | `./gradlew bootRun` | `initSamplePayments()` 로그에 시드 7건 생성 확인 |
| 프런트 | `cd frontend && npm run dev` | 기본 `localhost:3000` |

**⚠ dev 환경 한정**: prod 배포 전엔 `ROADMAP.md §10` 의 마이그레이션 SQL 을 수동 적용해야 한다 (`ALTER TABLE payments ADD COLUMN processed_by_admin_id BIGINT NULL, ADD COLUMN cancelled_at TIMESTAMP NULL` + 기존 `CANCELLED` row 에 `cancelled_at` backfill).

**⚠ dev 토스 호출 플래그**: `application-dev.yml` 의 `app.payment.toss-cancel-enabled=false` 상태여야 환불 시 실제 토스 API 호출 없이 내부 상태 전이만 수행된다. 프로덕션 배포 시 반드시 `true` 로 변경.

### 0.2 테스트 계정 · 시드

DataInitializer 기준:

| 계정 | 이메일 | 비번 | 비고 |
|---|---|---|---|
| SUPER_ADMIN | `admin@devmatch.com` | `Admin1234!` | 스모크 주체 |
| MENTEE | 시드 멘티 중 1명 | (시드 참조) | 결제 주체 + LMS 접근 차단 대상 |
| MENTOR | APPROVED 멘토 | — | 매칭 연쇄 취소 대상 |

결제 시드 7건 (`SEED-ORD-001` ~ `SEED-ORD-007`):

| orderId | status | matching | 용도 |
|---|---|---|---|
| `SEED-ORD-001` | CONFIRMED | O | 일반 환불 플로우 (시나리오 3) |
| `SEED-ORD-002` | CONFIRMED | O | 매칭 연쇄 취소 (시나리오 4) |
| `SEED-ORD-003` | CONFIRMED | ✕ | 매칭 없는 환불 |
| `SEED-ORD-004` | PENDING | — | 환불 불가 분기 |
| `SEED-ORD-005` | CANCELLED (user) | — | 이미 취소 — 재환불 방지 (시나리오 6) |
| `SEED-ORD-006` | CANCELLED (admin) | — | 관리자 취소 이력 렌더 |
| `SEED-ORD-007` | FAILED | — | 실패 분기 |

### 0.3 DB 접근

- H2 Console: `http://localhost:8080/h2-console` (JDBC URL 은 `application-dev.yml` 참조)
- 주요 조회:

```sql
-- 결제 전체 + 환불 이력
SELECT id, order_id, status, amount, cancelled_at, processed_by_admin_id, cancel_reason
FROM payments
ORDER BY id;

-- 결제 환불 감사 로그
SELECT id, admin_id, action, target_type, target_id, reason, created_at, metadata
FROM admin_audit_log
WHERE action = 'PAYMENT_REFUND'
ORDER BY id DESC;
```

---

## 1. 시나리오 1 — 목록 + 요약 카드 + 탭

**목표**: `/admin/payments` 진입 시 요약 카드 + 상태 탭이 모두 렌더되고 시드 7건이 노출.

### 조작

1. `admin@devmatch.com / Admin1234!` 로 로그인
2. 사이드바 "결제 관리" → `/admin/payments`
3. 요약 카드 4개 확인 (총 결제액 / 확정 건수 / 환불액 / 환불률)
4. 탭 전환: 전체(7) → 대기(1) → 확정(3) → 취소(2) → 실패(1)
5. 하단 페이지네이션 (size=20) 의 "1 / 1" 표시 확인

### 기대

- [ ] 요약 카드 4개 — 총 결제액·환불액은 `KRW` 포맷, 환불률은 소수점 1자리 `%`
- [ ] 테이블 컬럼: 주문ID(monospace) / 사용자(이름+이메일) / 금액(우측정렬) / 상태(배지) / 결제일 / 상세 링크
- [ ] 상태 배지 색: 대기=amber / 확정=emerald / 취소=red / 실패=zinc
- [ ] 확정 3건 금액 합계 = 요약 카드 "총 결제액"

### 실패 시 확인

- 요약 카드가 빈 상태 (`0원` / `0.0%`) → 기본 30일 범위에 시드가 포함되지 않음. `getSummary()` 의 `resolveRange` 가 `LocalDate.now().minusDays(30)` 부터 계산하는지 확인
- 행 수가 7 보다 적음 → 시드가 일부 skip. `DataInitializer.initSamplePayments()` 로그에서 "skip" 원인 메시지 확인

---

## 2. 시나리오 2 — 필터 URL 동기화

**목표**: 탭·기간·검색 변경이 URL 쿼리로 반영되고 새로고침 시 상태 유지.

### 조작

1. `/admin/payments` 에서 탭을 "확정" 으로 전환
2. 기간 선택기에서 최근 7일 범위 선택
3. 검색창에 `SEED-ORD-001` 입력 (300ms debounce)
4. 현재 URL 이 `?status=CONFIRMED&from=YYYY-MM-DD&to=YYYY-MM-DD&q=SEED-ORD-001` 형태인지 확인
5. **브라우저 새로고침 (F5)** — 탭·기간·검색이 그대로 유지되는지
6. 탭을 "전체" 로 변경 → URL 에서 `status` 파라미터가 제거되는지 (ALL 은 쿼리에 포함 안 함)
7. `?page=abc` 처럼 비정상 값을 주소창에 직접 입력 후 진입 → 페이지 1(0) 로 안전 복구되는지

### 기대

- [ ] 모든 필터가 URL 에 반영 + 새로고침 후 유지
- [ ] 필터 변경 시 `page` 가 0 으로 리셋
- [ ] 잘못된 `?page=...` / `?from=not-a-date` 진입 시 페이지가 깨지지 않음 (코드리뷰에서 수정된 NaN / Invalid Date 가드)
- [ ] 검색창의 debounce: 빠르게 타이핑해도 요청이 300ms 단위로 모여 나감 (네트워크 탭 확인)

### 실패 시 확인

- 새로고침 후 필터 손실 → `useSearchParams()` 초기값 반영 누락
- `?page=abc` 로 접근 시 빈 페이지 → `page.tsx:42` 의 NaN 가드 회귀

---

## 3. 시나리오 3 — CONFIRMED 결제 환불 (매칭 있음)

**목표**: 매칭 있는 CONFIRMED 결제 → 환불 다이얼로그 → 사유 검증 → 성공 → 상세 갱신.

### 조작

1. `/admin/payments` → 목록에서 `SEED-ORD-002` (CONFIRMED · 매칭 O) "상세" 클릭
2. 상세 헤더 확인: orderId (monospace 24px) + 상태 배지(확정·emerald) + "결제일 ... · 금액 ..." 부제
3. "주문 정보" 카드: paymentKey = `FAKE_PK_SEED-ORD-002`, 금액, 할인, 할부, 코스 타입, 묶음 개월, 갱신 횟수 렌더
4. "사용자" 카드: 결제자 정보 렌더
5. "연결된 매칭" 카드: `#{matchingId}`, 멘토 이름, 상태, 그리고 안내 문구 **"환불 시 매칭이 함께 취소되며, 멘티의 LMS 접근이 차단됩니다."**
6. 하단 sticky 영역 "환불 처리" (destructive 빨강) 클릭 → 다이얼로그 오픈
7. 다이얼로그 확인:
   - [ ] 제목 "결제 환불"
   - [ ] 부제 "{orderId} 주문을 환불 처리합니다."
   - [ ] 읽기전용 "환불 금액" 박스 (formatKRW + "(전액 환불 고정)")
   - [ ] 사유 textarea (rows=4, placeholder "환불 사유를 10~500자로 입력하세요")
   - [ ] 카운터 "0 / 500 (최소 10)" — 9자 입력 시 빨강, 10~500자 내에서는 기본 색, 501자 이상이면 빨강
   - [ ] destructive Alert 의 3 bullets:
     - "토스페이먼츠에 환불 요청이 전송됩니다 (취소 불가)."
     - "연결된 매칭이 함께 취소되어 멘티의 LMS 접근이 차단됩니다."
     - "본 작업은 감사 로그에 기록됩니다."
   - [ ] 취소(ghost) / 환불 확정(destructive), 둘 다 submitting 중 disabled
8. 사유 "스모크 테스트 환불 처리" (12자) 입력 → "환불 확정"
9. 성공 시: toast "환불 처리되었습니다" + 다이얼로그 닫힘 + 상세가 갱신되어:
   - [ ] 상태 배지가 "취소" (red) 로 바뀜
   - [ ] sticky 영역이 "🔒 재환불 불가" 로 변경
   - [ ] "환불 이력" 카드가 새로 렌더 (처리자=admin, 처리일=방금, 사유)
10. 백엔드 로그에서 `[AdminPayment] toss-cancel-enabled=false — 토스 호출 skip, 내부 상태만 변경 (paymentId=...)` 경고 1줄 확인

### 기대 (DB)

```sql
SELECT status, cancelled_at, cancel_reason, processed_by_admin_id
FROM payments
WHERE order_id = 'SEED-ORD-002';
-- status=CANCELLED, cancelled_at≈NOW, cancel_reason='스모크 테스트 환불 처리', processed_by_admin_id=<SUPER_ADMIN id>

SELECT action, target_type, target_id, reason, metadata
FROM admin_audit_log
WHERE action = 'PAYMENT_REFUND'
ORDER BY id DESC LIMIT 1;
-- action=PAYMENT_REFUND, target_type=PAYMENT, reason 동일, metadata 에 refundAmount / matchingAffected=true / matchingId
```

### 실패 시 확인

- 다이얼로그 닫힘 후 상태가 CONFIRMED 그대로 → `setDetail(next)` 미호출 / `refundPayment` 응답이 detail 대신 void
- toast 는 떴는데 DB 는 그대로 → 트랜잭션 롤백. `AdminPaymentService.refundPayment` 의 `@Transactional` 확인
- `toss-cancel-enabled=true` 상태에서 토스 실패 → `PaymentFailedException("토스 환불에 실패했습니다")` 가 에러 다이얼로그로 렌더되는지 (dev 에서는 이 케이스를 의도적으로 재현하지 않음)

---

## 4. 시나리오 4 — Matching 연쇄 CANCELLED + LMS 접근 차단

**목표**: 시나리오 3 에서 환불된 매칭이 `CANCELLED` 로 전이 + 해당 멘티의 LMS 접근이 403.

### 조작

1. 시나리오 3 성공 후 DB 확인:

```sql
SELECT id, status, mentor_id, mentee_id, cancel_reason
FROM matchings
WHERE id = <matchingId from SEED-ORD-002>;
-- status=CANCELLED, cancel_reason 동일
```

2. **시크릿 창**으로 해당 매칭의 MENTEE 계정 로그인
3. LMS 진입 시도 (`/lms` 또는 세션·과제 페이지 URL 직접 입력)
4. 백엔드가 매칭 상태 기반 접근 체크를 수행하는지 확인

### 기대

- [ ] 매칭 status = CANCELLED
- [ ] MENTEE 가 LMS 관련 페이지 진입 시 403 또는 "활성 매칭 없음" 안내
- [ ] 감사 로그 metadata 에 `matchingAffected=true`, `matchingId=<id>`

### 실패 시 확인

- 매칭 status 가 그대로 ACCEPTED → `AdminPaymentService.refundPayment` 의 소프트 캐스케이드 (PENDING/ACCEPTED/TRIAL → CANCELLED) 분기 누락
- 매칭이 CANCELLED 인데 LMS 진입됨 → 가드 미구현 (Feature 2 범위 밖이면 follow-up 으로 기록)

---

## 5. 시나리오 5 — 비-ADMIN 접근 거부

**목표**: MENTEE/MENTOR 토큰으로는 `/admin/payments` 엔드포인트 전부 403.

### 조작

1. MENTEE 계정으로 로그인
2. 주소창에 `/admin/payments` 직접 입력 → 프런트 가드로 리다이렉트/404 확인
3. 개발자도구 네트워크 탭 + MENTEE 토큰으로 아래 4개 엔드포인트 직접 호출:
   - `GET /api/admin/payments`
   - `GET /api/admin/payments/summary`
   - `GET /api/admin/payments/1`
   - `POST /api/admin/payments/1/refund` (body: `{"reason":"test test test"}`)
4. MENTOR 계정으로 같은 과정 반복

### 기대

- [ ] 4개 엔드포인트 모두 **403** 반환
- [ ] 프런트 `/admin/payments` 직접 진입 시 관리자 아님 안내 또는 홈 리다이렉트

### 실패 시 확인

- MENTEE 토큰으로 200 응답 → `SecurityConfig` 에서 `/api/admin/**` 가 `hasRole('ADMIN')` 로 보호되어 있는지 확인
- 프런트 진입이 가능한데 API 만 403 → 프런트 가드 누락 (서버 쪽 정답이면 허용 가능)

---

## 6. 시나리오 6 — 중복 환불 방지

**목표**: 이미 CANCELLED 상태인 결제에 대해 재환불 API 호출 시 400 + 에러 메시지.

### 조작

1. `/admin/payments/<시나리오3에서 환불한 id>` 재진입
2. sticky 영역이 "🔒 재환불 불가" 로 표시되고 버튼이 노출되지 않는지 확인
3. 개발자도구에서 직접 `POST /api/admin/payments/<id>/refund` 호출 (body: `{"reason":"중복 환불 시도"}`)

### 기대

- [ ] 프런트: 버튼이 없으므로 일반 사용자는 재환불 UI 에 접근 불가
- [ ] 백엔드: 400 응답 + 메시지 "승인된 결제만 환불할 수 있습니다. 현재 상태: CANCELLED" (`PaymentFailedException` 매핑)
- [ ] DB: `payments` 상태·`admin_audit_log` 에 추가 row 없음 (가드로 조기 리턴)

### 실패 시 확인

- 400 이 아닌 500 → `@ControllerAdvice` 의 `PaymentFailedException` 핸들러 확인
- 200 반환 → 가드 (`payment.getStatus() != PaymentStatus.CONFIRMED`) 누락

---

## 7. 최종 체크리스트 (머지 직전)

- [ ] 시나리오 1~6 모두 위 기대대로 동작
- [ ] 백엔드 로그에 ERROR 없음 (`toss-cancel-enabled=false` 경고만 허용)
- [ ] 프런트 콘솔 에러 없음 (`[admin-payments] summary fetch failed` 가 아닌 일반 케이스)
- [ ] `npx tsc --noEmit` · `npm run build` 성공
- [ ] `./gradlew test` 성공 (AdminPaymentService 12 케이스 포함)

**체크리스트 모두 통과 시** PR 을 "Ready for review" 로 전환 → 셀프 머지 또는 리뷰어 요청.

---

## 8. 알려진 잔여 follow-up (이 PR 범위 외)

1. 환불 실패 시 Toss/DB 불일치 방어 (현재: Toss 실패 → 예외, Toss 성공 + DB 실패 → orphan)
2. 동시 환불 요청 레이스 (두 관리자가 동시에 CONFIRMED → CANCELLED 처리 시도)
3. 요약 카드 기간 필터가 목록 기간과 독립 (목록은 기본 30일, 요약도 동일하지만 사용자가 목록 기간만 바꾸면 요약은 그대로 — 의도적 / 혼란 여부 확인 필요)
4. 환불 이력 카드의 처리자 표시 — `admin#{id}` fallback 대신 "알 수 없는 관리자" 문구 고려

스모크 중 위 4건이 드러나면 "문제 없음" 으로 간주하고 follow-up PR 에서 처리.

---

## 9. 실행 기록

> 실행일: ____________ (실제 실행자가 채움)
> 실행자: ____________

각 시나리오의 체크박스에 결과(✓/✗) 표시 후 이슈 발견 시 하단에 기록:

```
시나리오 N - <짧은 제목>
증상: ...
재현 절차: ...
원인 추정: ...
조치: follow-up PR / 이 PR 범위 내 수정 / 무시 (이유)
```

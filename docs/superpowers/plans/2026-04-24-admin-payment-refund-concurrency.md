# AdminPayment 환불 동시성 제어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AdminPaymentService.refundPayment` 동시 호출 시 Toss API 중복 호출과 감사 로그 중복을 차단한다.

**Architecture:** 1차 방어로 `PaymentRepository.findByIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`)를 도입해 동시 환불 중 후속 트랜잭션을 블록·재읽기 하도록 하고, 2차 방어로 `Payment` 엔티티에 `@Version`을 추가해 환불 외 다른 경로의 동시 수정도 잡는다. Toss API는 부수효과이므로 commit 시점에 충돌을 감지하는 낙관적 잠금만으로는 부족하다 — PESSIMISTIC_WRITE가 핵심.

**Tech Stack:** Spring Data JPA (Hibernate), JUnit 5, Mockito, Spring Boot 3.x.

**Spec:** `docs/superpowers/specs/2026-04-24-admin-payment-refund-concurrency-design.md`

**Branch:** `claude/admin-payment-refund-concurrency` (이미 `claude/frosty-jackson-dc3071` 에서 분기 + spec 커밋 완료)

---

## File Structure

| 파일 | 변경 종류 | 책임 |
|---|---|---|
| `backend/src/main/java/com/devmatch/entity/Payment.java` | Modify | `@Version` 필드 추가 |
| `backend/src/main/java/com/devmatch/repository/PaymentRepository.java` | Modify | `findByIdForUpdate` (PESSIMISTIC_WRITE) 메서드 추가 |
| `backend/src/main/java/com/devmatch/service/AdminPaymentService.java` | Modify | `refundPayment` 안에서 `findById` → `findByIdForUpdate` 교체 |
| `backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java` | Modify | 동시 환불 가드 테스트 + 낙관적 잠금 충돌 테스트 추가 |
| `ROADMAP.md` | Modify | Feature 2 마이그레이션 SQL 블록에 `payments.version` 컬럼 추가 |

---

## Working Directory

모든 명령은 다음 디렉토리에서 실행:
```
C:/Users/aucu2/Sub_Project/.claude/worktrees/admin-payment-refund-concurrency
```

---

## Task 1: PESSIMISTIC_WRITE 락 도입 + 기존 환불 동작 회귀 검증

### Files
- **Modify:** `backend/src/main/java/com/devmatch/repository/PaymentRepository.java`
- **Modify:** `backend/src/main/java/com/devmatch/service/AdminPaymentService.java` (line 145)

### Steps

- [ ] **Step 1.1: `PaymentRepository`에 import 3개 추가**

`backend/src/main/java/com/devmatch/repository/PaymentRepository.java` 상단 import 블록에 다음 3줄 추가 (이미 있으면 skip):

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
```

기존 `import org.springframework.data.jpa.repository.Query;` 와 `import org.springframework.data.repository.query.Param;` 는 그대로 둔다.

- [ ] **Step 1.2: `findByIdForUpdate` 메서드 추가**

`PaymentRepository` 인터페이스 본문에서 기존 `findByMatchingId(Long matchingId);` 다음 줄에 다음 메서드 추가:

```java
    // ===== 동시성 제어 — 환불 흐름에서 row lock 으로 중복 Toss 호출 차단 =====
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
```

- [ ] **Step 1.3: `AdminPaymentService.refundPayment`에서 `findById` → `findByIdForUpdate` 교체**

`backend/src/main/java/com/devmatch/service/AdminPaymentService.java` 145번 라인 근처의 다음 코드를:

```java
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));
```

다음으로 변경:

```java
        var payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));
```

다른 메서드(`getDetail` 등)에서 `findById`를 사용하는 위치는 변경하지 않는다 — 락이 필요한 곳은 환불 흐름뿐.

- [ ] **Step 1.4: 컴파일 + 기존 환불 테스트 회귀 검증**

```bash
cd /c/Users/aucu2/Sub_Project/.claude/worktrees/admin-payment-refund-concurrency
./backend/gradlew -p backend test --tests "com.devmatch.service.AdminPaymentServiceTest"
```

기대 결과: 기존 12개 환불·요약·목록 테스트가 모두 PASS. 만약 환불 테스트가 FAIL하면 `findById` mock이 `findByIdForUpdate`로 교체된 시그니처와 안 맞아서다 — 다음 Step에서 함께 수정한다.

> **참고:** 기존 환불 테스트(`refund_*`)는 `when(paymentRepository.findById(N)).thenReturn(Optional.of(p))` 패턴이라 시그니처 변경 후 mock이 매칭되지 않아 FAIL할 가능성이 높다. 이 경우 Step 1.5로 진행.

- [ ] **Step 1.5: 기존 `refund_*` 테스트의 mock을 `findByIdForUpdate`로 변경**

`backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`에서 다음 8개 라인에서 `findById` → `findByIdForUpdate` 교체 (정확한 매칭을 위해 `paymentRepository.findById(` 패턴으로 검색):

- 라인 171: `when(paymentRepository.findById(1L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(1L)`
- 라인 202: `when(paymentRepository.findById(2L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(2L)`
- 라인 222: `when(paymentRepository.findById(3L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(3L)`
- 라인 238: `when(paymentRepository.findById(4L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(4L)`
- 라인 252: `when(paymentRepository.findById(5L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(5L)`
- 라인 268: `when(paymentRepository.findById(6L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(6L)`
- 라인 291: `when(paymentRepository.findById(7L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(7L)`
- 라인 306: `when(paymentRepository.findById(8L)).thenReturn(Optional.of(p));` → `findByIdForUpdate(8L)`

⚠️ `getDetail_존재하지_않는_id_는_PaymentNotFoundException` 테스트(라인 130, ID 999L)는 **변경하지 말 것** — `getDetail`은 여전히 `findById`를 사용한다. 환불 테스트 8개만 교체.

- [ ] **Step 1.6: 다시 회귀 테스트 실행**

```bash
./backend/gradlew -p backend test --tests "com.devmatch.service.AdminPaymentServiceTest"
```

기대 결과: 13개 테스트 모두 PASS.

- [ ] **Step 1.7: 전체 백엔드 테스트로 부수효과 확인**

```bash
./backend/gradlew -p backend test
```

기대 결과: 모든 테스트 PASS. (PaymentRepository 변경이 다른 서비스에 영향이 없는지 확인.)

- [ ] **Step 1.8: feat 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/PaymentRepository.java \
        backend/src/main/java/com/devmatch/service/AdminPaymentService.java \
        backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java
git commit -m "$(cat <<'EOF'
feat(admin-payment): refundPayment 에 PESSIMISTIC_WRITE 락 도입

PaymentRepository.findByIdForUpdate(@Lock(PESSIMISTIC_WRITE)) 추가하고
AdminPaymentService.refundPayment 가 이 메서드로 Payment 를 읽도록 교체.
동시 환불 시도 시 후속 트랜잭션은 SELECT FOR UPDATE 에서 블록되었다가
unblock 후 재읽기에서 CANCELLED 상태를 만나 status 가드로 거절 — Toss API
중복 호출과 감사 로그 중복이 모두 차단된다.

기존 refund_* 단위 테스트의 findById mock 8건을 findByIdForUpdate 로 교체.
getDetail 흐름은 락이 불필요하므로 findById 그대로 유지.

후속 PR — 코드 품질 리뷰어가 commit 2c8e2ba 에서 플래그한 사항.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 동시 환불 가드 테스트 + 낙관적 잠금 충돌 테스트 추가

### Files
- **Modify:** `backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`

### Steps

- [ ] **Step 2.1: `OptimisticLockingFailureException` import 추가**

`backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java` 상단의 `import org.springframework.data.jpa.domain.Specification;` 다음 줄에 추가:

```java
import org.springframework.dao.OptimisticLockingFailureException;
```

또한 `import static org.mockito.Mockito.times;` 추가 (`org.mockito.Mockito.verify` import 옆):

```java
import static org.mockito.Mockito.times;
```

- [ ] **Step 2.2: 동시 환불 가드 테스트 추가**

`AdminPaymentServiceTest` 클래스 본문 마지막 메서드(`refund_토스_호출_실패는_PaymentFailedException_전파`) 다음 위치(파일 끝의 `}` 직전)에 다음 테스트 메서드 추가:

```java
    @Test
    void refund_동시요청_두번째는_상태가드에서_차단되어_Toss_미호출() {
        // Admin1 의 트랜잭션이 이미 commit 된 직후 Admin2 가 락을 획득해 재읽기한 상황을 시뮬레이션.
        // 첫 호출은 CONFIRMED, 두 번째 호출은 CANCELLED 를 반환.
        Payment first = Payment.builder()
                .id(100L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_concurrent").paymentKey("pk_concurrent").amount(150_000)
                .status(PaymentStatus.CONFIRMED).build();
        Payment second = Payment.builder()
                .id(100L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_concurrent").paymentKey("pk_concurrent").amount(150_000)
                .status(PaymentStatus.CANCELLED).build();

        when(paymentRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(second));
        when(tossPaymentService.cancelPayment(eq("pk_concurrent"), anyString())).thenReturn(true);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        // Admin1: 정상 환불
        svc.refundPayment(100L, 99L, "Admin1 환불 처리");
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // Admin2: 락이 풀린 뒤 재읽기 결과가 CANCELLED → status 가드에서 차단
        assertThatThrownBy(() -> svc.refundPayment(100L, 88L, "Admin2 동시 환불 시도"))
                .isInstanceOf(PaymentFailedException.class);

        // Toss 환불 API 는 정확히 1회만 호출되어야 한다 (중복 차단의 핵심 검증)
        verify(tossPaymentService, times(1)).cancelPayment(eq("pk_concurrent"), anyString());
        // 감사 로그도 1회만 기록
        verify(auditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.PAYMENT_REFUND),
                eq("PAYMENT"),
                eq(100L),
                anyString(),
                anyMap());
    }
```

- [ ] **Step 2.3: 새 테스트 실행 → PASS 확인**

```bash
./backend/gradlew -p backend test --tests "com.devmatch.service.AdminPaymentServiceTest.refund_동시요청_두번째는_상태가드에서_차단되어_Toss_미호출"
```

기대 결과: PASS. 만약 FAIL하면 Task 1의 `findByIdForUpdate` 도입이 누락된 것 — Task 1으로 되돌아가 확인.

- [ ] **Step 2.4: 낙관적 잠금 충돌 테스트 추가**

같은 파일에서 Step 2.2의 테스트 메서드 다음에 추가:

```java
    @Test
    void refund_낙관적잠금_충돌시_OptimisticLockingFailureException_전파() {
        // 다른 코드 경로(예: 자동 배치, 결제 confirm 흐름)가 같은 Payment 를 동시 수정한 경우를 시뮬레이션.
        // findByIdForUpdate 는 정상 반환하지만, save 시점에 @Version 충돌이 발생.
        // 주의: 이 시점엔 이미 Toss 호출이 일어났을 수 있음 — 그래서 1차 방어가 PESSIMISTIC_WRITE 인 이유.
        Payment p = Payment.builder()
                .id(200L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_optlock").paymentKey("pk_optlock").amount(150_000)
                .status(PaymentStatus.CONFIRMED).build();
        when(paymentRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(p));
        when(tossPaymentService.cancelPayment(eq("pk_optlock"), anyString())).thenReturn(true);
        // matchingId 가 null 이라 cascade 미발생 → 감사 로그 기록 단계에서 충돌이 일어나는 시나리오.
        // auditLogService.record 가 RuntimeException 의 한 종류로 OptimisticLockingFailureException 을 던지도록 stub.
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("version mismatch"))
                .when(auditLogService).record(
                        eq(99L), eq(AdminActionType.PAYMENT_REFUND),
                        eq("PAYMENT"), eq(200L), anyString(), anyMap());

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.refundPayment(200L, 99L, "낙관적 잠금 충돌 시나리오"))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
```

- [ ] **Step 2.5: 새 테스트 실행 → PASS 확인**

```bash
./backend/gradlew -p backend test --tests "com.devmatch.service.AdminPaymentServiceTest.refund_낙관적잠금_충돌시_OptimisticLockingFailureException_전파"
```

기대 결과: PASS.

- [ ] **Step 2.6: 전체 `AdminPaymentServiceTest` 실행 → 회귀 없음 확인**

```bash
./backend/gradlew -p backend test --tests "com.devmatch.service.AdminPaymentServiceTest"
```

기대 결과: 15개 테스트(기존 13 + 신규 2) 모두 PASS.

- [ ] **Step 2.7: test 커밋**

```bash
git add backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java
git commit -m "$(cat <<'EOF'
test(admin-payment): refundPayment 동시 요청 가드 + 낙관적 잠금 충돌 테스트

- 동시 환불: findByIdForUpdate 가 첫 호출 CONFIRMED / 두번째 호출 CANCELLED
  를 반환하도록 stub 하여, 락이 풀린 뒤 재읽기에서 status 가드가 작동해
  Toss API 와 감사 로그가 정확히 1회만 호출되는지 검증.
- 낙관적 잠금 충돌: auditLogService.record 가 OptimisticLockingFailureException
  을 던지도록 stub 하여, 다른 경로의 동시 수정 시 예외가 호출자에게
  전파되는지 검증.

학생 포트폴리오 환경 — TossPaymentService 는 @Mock 이라 실 API 호출 없음.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `Payment` 엔티티에 `@Version` 추가

### Files
- **Modify:** `backend/src/main/java/com/devmatch/entity/Payment.java`

### Steps

- [ ] **Step 3.1: `@Version` 필드 추가**

`backend/src/main/java/com/devmatch/entity/Payment.java`에서 기존 `@Column(name = "cancel_reason", length = 500) private String cancelReason;` 다음 줄에 다음 필드 추가:

```java
    // 낙관적 잠금 — 환불 외 다른 경로의 동시 수정 방어
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
```

`@Version`은 `jakarta.persistence.Version`. 파일 상단에 이미 `import jakarta.persistence.*;` 가 있으므로 별도 import 불필요.

> **`@Builder.Default` 와 0L 명시 이유:**
> Lombok `@Builder` 가 만드는 builder 는 명시되지 않은 필드를 null 로 초기화한다. `Long version` 의 기본값이 null 이면 `@Column(nullable = false)` 제약과 충돌해 Hibernate INSERT 가 실패할 수 있다. `@Builder.Default` + `0L` 명시로 builder 경유 생성 시에도 0 으로 초기화되도록 보장한다.

- [ ] **Step 3.2: 컴파일 + 전체 테스트 회귀 확인**

```bash
./backend/gradlew -p backend test
```

기대 결과: 모든 테스트 PASS. `@Version` 도입은 새 컬럼 추가이고 기존 테스트는 모두 in-memory mock 또는 H2 dev DB 위에서 동작하므로 회귀 가능성은 낮다. 만약 H2 기반 통합 테스트에서 NULL 제약 위반이 나면 Step 3.1의 `@Builder.Default` 가 누락되었는지 확인.

- [ ] **Step 3.3: feat 커밋 (entity 변경분)**

```bash
git add backend/src/main/java/com/devmatch/entity/Payment.java
git commit -m "$(cat <<'EOF'
feat(payment): Payment 엔티티에 @Version 컬럼 추가 (낙관적 잠금)

환불 흐름은 이미 PESSIMISTIC_WRITE 락으로 동시 호출이 차단되지만, 향후
추가될 다른 코드 경로(자동 환불 배치, 매칭 상태 변경 cascade 등)에서
같은 Payment 를 동시 수정하는 시나리오를 잡기 위한 2차 방어.

@Builder.Default + 0L 로 builder 경유 생성 시 NULL 제약 위반 방지.
기존 테스트는 모두 PASS (회귀 없음).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: ROADMAP 마이그레이션 SQL 추가

### Files
- **Modify:** `ROADMAP.md` (Feature 2 마이그레이션 SQL 블록, 라인 286 근처)

### Steps

- [ ] **Step 4.1: `payments.version` 컬럼 ALTER 추가**

`ROADMAP.md` 라인 286~288의 다음 블록을:

```sql
   -- 1) Payment 컬럼 추가
   ALTER TABLE payments
     ADD COLUMN processed_by_admin_id BIGINT NULL,
     ADD COLUMN cancelled_at TIMESTAMP NULL;
```

다음으로 변경:

```sql
   -- 1) Payment 컬럼 추가
   ALTER TABLE payments
     ADD COLUMN processed_by_admin_id BIGINT NULL,
     ADD COLUMN cancelled_at TIMESTAMP NULL,
     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

기존 row 는 모두 version=0 으로 backfill 된다 — 별도 UPDATE 불필요.

- [ ] **Step 4.2: docs 커밋**

```bash
git add ROADMAP.md
git commit -m "$(cat <<'EOF'
docs(roadmap): payments.version 컬럼 마이그레이션 SQL

@Version 도입에 따라 prod 배포 시 payments 테이블에 version BIGINT NOT NULL
DEFAULT 0 컬럼 추가 필요. Feature 2 마이그레이션 블록에 합쳐 1회 ALTER
TABLE 로 적용.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 최종 검증 + PR 준비

### Steps

- [ ] **Step 5.1: 전체 백엔드 테스트 1회 더 실행**

```bash
./backend/gradlew -p backend test
```

기대 결과: 모든 테스트 PASS.

- [ ] **Step 5.2: 커밋 히스토리 확인**

```bash
git log --oneline claude/frosty-jackson-dc3071..HEAD
```

기대 결과 (5개 커밋):
1. `docs(admin-payment): 환불 동시성 design spec` (Task 0, 이미 커밋됨)
2. `feat(admin-payment): refundPayment 에 PESSIMISTIC_WRITE 락 도입` (Task 1)
3. `test(admin-payment): refundPayment 동시 요청 가드 + 낙관적 잠금 충돌 테스트` (Task 2)
4. `feat(payment): Payment 엔티티에 @Version 컬럼 추가 (낙관적 잠금)` (Task 3)
5. `docs(roadmap): payments.version 컬럼 마이그레이션 SQL` (Task 4)

- [ ] **Step 5.3: PR 생성 (draft, depends-on 명시)**

먼저 admin-payments PR(`claude/frosty-jackson-dc3071`)의 PR 번호를 확인:

```bash
gh pr list --head claude/frosty-jackson-dc3071 --state open
```

해당 PR 번호를 `<UPSTREAM_PR_NUMBER>` 자리에 채워 넣고 push + PR 생성:

```bash
git push -u origin claude/admin-payment-refund-concurrency

gh pr create --draft --base main --head claude/admin-payment-refund-concurrency \
  --title "feat(admin-payment): refundPayment 동시성 제어 (PESSIMISTIC_WRITE + @Version)" \
  --body "$(cat <<'EOF'
## Summary
- `AdminPaymentService.refundPayment` 동시 호출 시 Toss API 중복 호출과 감사 로그 중복을 차단
- 1차 방어: `PaymentRepository.findByIdForUpdate(@Lock(PESSIMISTIC_WRITE))` — 동시 환불 시 후속 트랜잭션 블록·재읽기로 status 가드 작동
- 2차 방어: `Payment.@Version` — 다른 경로(자동 배치, confirm 흐름 등)의 동시 수정 보호
- 새 단위 테스트 2건 (동시 환불 가드 / 낙관적 잠금 충돌 전파)
- 마이그레이션 SQL: `ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0`

## Background
코드 품질 리뷰어가 admin-payments PR 의 commit `2c8e2ba` 에서 플래그한 follow-up. status 가드만으로는 두 admin 의 동시 클릭에서 두 트랜잭션 모두 가드를 통과 → Toss 환불 API 중복 호출 + 감사 로그 중복 발생 가능.

## Depends on
PR #<UPSTREAM_PR_NUMBER> (admin-payments). 머지 후 main 으로 rebase 예정.

## Test plan
- [ ] CI: `./gradlew test` 모든 테스트 PASS
- [ ] 신규 테스트 2건 PASS 확인
- [ ] 학생 포트폴리오 환경 — Toss 실 API 호출 없음 확인 (TossPaymentService 는 @Mock)
- [ ] prod 배포 시 ROADMAP 의 마이그레이션 SQL 수동 실행

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5.4: 사용자에게 PR URL 공유 + 후속 안내**

PR URL 을 사용자에게 공유하고 다음 안내:
- admin-payments PR(`claude/frosty-jackson-dc3071`) 이 main 에 머지되면 이 PR 을 main 으로 rebase
- rebase 후 draft 해제 (ready for review)

---

## Self-Review Notes

이 plan 의 잠재 위험:

1. **`OptimisticLockingFailureException` import 패키지** — `org.springframework.dao.OptimisticLockingFailureException` 가 맞는지 확인. Spring Data JPA 가 Hibernate 의 `StaleObjectStateException` 을 이 예외로 자동 wrapping 한다.
2. **Task 2 의 OptimisticLockingFailureException 시뮬레이션 위치** — 실제로는 commit 시점에 발생하지만 단위 테스트에서는 `auditLogService.record` 가 던지는 것으로 시뮬레이션. "어디서 던져지는지"가 아니라 "예외가 호출자까지 전파되는지"가 검증 대상이므로 합리적.
3. **`@Version` 도입이 다른 Payment 수정 흐름에 영향** — `PaymentService.confirm`, `cancel`, `linkMatching` 등 모두 단일 트랜잭션 내 read-modify-write 라 영향 없음. Step 3.2 의 전체 테스트 실행으로 확인.
4. **테스트 메서드 한글명** — 기존 코드베이스 규약 (`refund_*` 등 한글 메서드명 사용)을 따름.

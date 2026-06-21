---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AdminPayment 환불 동시성 제어 설계 상세 요구사항 및 기능 동작 명세서"

---

# AdminPayment 환불 동시성 제어 설계

- 작성일: 2026-04-24
- 대상 파일: `backend/src/main/java/com/devmatch/service/AdminPaymentService.java` `refundPayment` (commit `2c8e2ba`에서 코드 품질 리뷰어 플래그)
- 후속 PR — admin-payments PR(`claude/frosty-jackson-dc3071`)에서 분리하여 별도 PR로 진행
- 의존: admin-payments PR(#TBD)

## 배경 / 문제

`AdminPaymentService.refundPayment(Long paymentId, Long adminId, String reason)` 의 현재 흐름:

1. `paymentRepository.findById(paymentId)`
2. `status != CONFIRMED` 가드
3. **`tossPaymentService.cancelPayment(paymentKey, reason)` 호출 (네트워크 부수효과)**
4. `payment.cancel(reason)` + `markProcessedByAdmin(adminId, now)`
5. Matching 소프트 캐스케이드
6. 감사 로그 기록

두 명의 관리자가 동일 `paymentId`에 대해 환불 버튼을 거의 동시에 누르면 두 트랜잭션 모두 status 가드를 통과한다. 결과:

- **Toss 환불 API가 두 번 호출됨.** (두 번째는 Toss 측에서 거절될 가능성이 높지만, 네트워크 트래픽 낭비 + 감사 로그 PAYMENT_REFUND 이벤트 2건이 남는다.)
- 데이터 정합성도 위태롭다. 두 트랜잭션 모두 `payment.cancel()` + `markProcessedByAdmin`을 시도하면 마지막 write가 이전을 덮어쓰면서 `processed_by_admin_id`가 어느 한쪽으로만 기록된다.

## 목표 / 비목표

**목표**
- 동시 환불 시도에서 Toss API 호출이 정확히 1회만 일어남을 보장
- 두 번째 클릭은 status 가드("승인된 결제만 환불할 수 있습니다. 현재 상태: CANCELLED")로 자연스럽게 거절
- 감사 로그도 1건만 기록됨
- 다른 코드 경로(향후 자동 환불 배치, confirm 흐름 등)에서 같은 Payment를 동시 수정하는 경우에도 방어

**비목표**
- 분산 환경(여러 백엔드 인스턴스 + Redis 락 등) — 현재 단일 백엔드 인스턴스 가정
- Toss API의 idempotency-key 도입 — 별도 작업
- 사용자 친화적 retry 메시지 UX — 별도 작업

## 전략

**1차 방어: 비관적 행 락 (PESSIMISTIC_WRITE)**

`payments` row를 `SELECT ... FOR UPDATE`로 잠근다. Admin1 트랜잭션이 commit(또는 rollback) 될 때까지 Admin2의 SELECT FOR UPDATE는 블록된다. Admin1이 정상 커밋한 뒤 Admin2가 unblock되면 재읽기 시 status가 `CANCELLED`로 보이므로 가드에서 거절된다 — Toss API 호출 자체가 일어나지 않는다.

이게 핵심 방어책. 부수효과(Toss 호출)가 commit *이전에* 실행되는 흐름이라 낙관적 잠금만으로는 부족하다(낙관적 잠금은 commit 시점에 충돌을 검출하므로 그 시점엔 이미 Toss 호출이 끝나 있다).

**2차 방어: 낙관적 잠금 (`@Version`)**

`Payment` 엔티티에 `@Version`을 추가한다. 환불 외 다른 경로에서 같은 Payment를 수정하면 commit 시 `OptimisticLockingFailureException`이 발생한다. 1차 방어가 환불-환불 충돌을 막는다면 2차 방어는 향후 추가될 다른 흐름(예: 자동 환불 배치, 매칭 상태 변경에 따른 결제 상태 변경 등)에서의 의도치 않은 동시 수정을 잡아낸다.

비용은 마이그레이션 1줄(`ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0`).

## 코드 변경

### A. `Payment` 엔티티

`backend/src/main/java/com/devmatch/entity/Payment.java`

```java
@Version
@Column(nullable = false)
private Long version;
```

- JPA가 첫 INSERT 시 0 자동 세팅, save마다 +1.
- `@Builder`와 함께 동작 — `@Builder.Default`는 불필요(JPA 책임).

### B. `PaymentRepository`

`backend/src/main/java/com/devmatch/repository/PaymentRepository.java`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Payment p where p.id = :id")
Optional<Payment> findByIdForUpdate(@Param("id") Long id);
```

- 메서드 이름 컨벤션은 명시적 `ForUpdate` 접미사로 호출자가 락 의도를 인지할 수 있도록 한다.
- `@Query`로 명시적 JPQL — Spring Data 메서드 이름 파싱에 의존하지 않음.

### C. `AdminPaymentService.refundPayment`

`backend/src/main/java/com/devmatch/service/AdminPaymentService.java:145`

기존:
```java
var payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(...));
```

변경:
```java
var payment = paymentRepository.findByIdForUpdate(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(...));
```

- 한 줄 교체. 메서드는 이미 `@Transactional` 붙어 있어 락이 트랜잭션 경계에서 보유/해제된다.
- 나머지 흐름(status 가드, Toss 호출, cancel, 캐스케이드, 감사 로그)은 변경 없음.

## 테스트

`backend/src/test/java/com/devmatch/service/AdminPaymentServiceTest.java`

기존 패턴(Mockito 단위 테스트, `@Mock PaymentRepository`)을 유지하고 2개 케이스 추가.

### 1. 동시 환불 가드 작동 검증

```
@Test
void refundPayment_동시요청_두번째는_상태가드에서_차단되어_Toss_미호출()
```

- `findByIdForUpdate(1L)` 첫 호출: CONFIRMED 상태 Payment
- `findByIdForUpdate(1L)` 두 번째 호출: CANCELLED 상태 Payment (Admin1 트랜잭션이 이미 커밋된 상황을 시뮬레이션)
- 첫 호출: 정상 환불 성공 + `tossPaymentService.cancelPayment` 1회 호출 검증
- 두 번째 호출: `PaymentFailedException` 발생 + `tossPaymentService.cancelPayment` **추가 호출 없음** 검증 (`verify(toss, times(1))`)

이 테스트는 "락이 풀린 뒤 재읽기로 변경된 상태를 만났을 때 status 가드가 작동하는가"를 검증한다. PESSIMISTIC_WRITE 락 자체의 블록 동작은 Spring Data JPA가 보장하므로 검증 대상 아님.

### 2. 낙관적 잠금 충돌 시 예외 전파

```
@Test
void refundPayment_낙관적잠금_충돌시_OptimisticLockingFailureException_전파()
```

- `findByIdForUpdate(1L)`: CONFIRMED 정상 반환
- 그 외 경로(예: matching cascade, audit log 등이 동작하다가) 어떤 시점의 mock이 `OptimisticLockingFailureException`을 던지도록 stub
- 예외가 호출자에게 전파되는지 검증
- 주석으로 "이 시점엔 이미 Toss 호출이 일어났을 수 있음 — 그래서 1차 방어가 PESSIMISTIC_WRITE인 이유" 명시

### Toss 실호출 안 됨 보장

`TossPaymentService`는 `@Mock`이므로 stub되지 않은 호출은 자동으로 noop이고 실제 네트워크 트래픽이 일어나지 않는다. 별도 가드 불필요.

학생 포트폴리오 환경 — 운영/스테이징/개발 어느 환경에서도 실제 Toss API가 호출되면 안 된다. 기존에 `app.payment.toss-cancel-enabled` 플래그(`TossCancelProperties`)로 게이트되어 있고 이 흐름은 변경하지 않는다.

## 마이그레이션

`ROADMAP.md` "Phase II Feature 2 프로덕션 마이그레이션 SQL" 섹션(0905582 커밋)에 1줄 추가:

```sql
-- 환불 동시성 제어용 낙관적 잠금 컬럼
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

dev 환경은 JPA `ddl-auto: update`가 자동으로 컬럼을 추가한다.

## 위험 / 트레이드오프

- **락 보유 시간**: Toss API 응답 시간(보통 수 초) 동안 row lock을 보유한다. 그러나 (a) `payments` 단일 row이고 (b) 환불은 빈도가 매우 낮은 관리자 액션이므로 실제 영향은 미미하다.
- **데드락 가능성**: 현재 `refundPayment`만 PESSIMISTIC_WRITE를 사용하고 다른 코드 경로는 일반 select이므로 데드락 시나리오는 없다. 향후 다른 락이 도입되면 락 획득 순서를 일관되게 유지해야 한다.
- **`@Version` 도입의 잠재적 영향**: 기존에 Payment를 수정하는 모든 코드 경로(`PaymentService.confirm`, `cancel`, `linkMatching` 등)가 `@Version` 동작을 받게 된다. 단일 트랜잭션 내 수정은 문제없지만, 같은 Payment를 두 트랜잭션에서 수정하면 두 번째에서 예외가 발생한다 — 이게 의도한 방어. 기존 테스트가 깨지지 않는지 전체 테스트 실행으로 확인 필요.

## PR 절차

1. 브랜치: `claude/admin-payment-refund-concurrency` (`claude/frosty-jackson-dc3071` 에서 분기, 이미 생성됨)
2. 커밋 단위:
   - `docs(admin-payment): 환불 동시성 design spec`
   - `feat(admin-payment): refundPayment 동시성 — PESSIMISTIC_WRITE + @Version 방어`
   - `test(admin-payment): refundPayment 동시 요청 가드 + 낙관적 잠금 충돌 테스트`
   - `docs(roadmap): payments.version 컬럼 마이그레이션 SQL`
3. 전체 테스트(`./gradlew test`) 실행해서 `@Version` 도입으로 깨지는 테스트 없는지 확인
4. PR을 **draft**로 열고 description에 "Depends on PR #N (admin-payments)" 명시
5. admin-payments PR이 main에 머지되면 main으로 rebase 후 ready for review로 전환

# 결제 중복 방어 공백 — application_id 유니크 제약 부재 + 멱등성 키 미적용

- 발생 일시: 2026-07-27
- 영역: backend / DB
- 심각도: high

## 증상

운영 중 실제로 터진 장애는 아니고, 토스페이 결제 시스템에 멱등성 키(Idempotency-Key)가 적용돼 있는지
점검하다가 발견한 **구조적 중복 결제 취약점**이다. 세 가지가 동시에 비어 있었다.

1. 같은 신청서로 결제 생성 요청이 거의 동시에 들어오면 `payments` 행이 2건 생길 수 있다.
2. 결제 승인(`confirm`) 중 버튼 더블클릭·네트워크 재시도가 발생하면 토스 승인 API 가 2회 호출될 수 있다.
3. 토스 호출 성공 직후 DB 저장 전에 프로세스가 죽으면, 재시도가 **이미 집행된 결제를 다시 집행**할 수 있다.

## 원인

**(1) `application_id` 유니크 제약 부재 — check-then-act 경쟁**

`PaymentService.createPayment` 는 `existsByApplicationId` 로 중복을 선검사한 뒤 `save` 하는
전형적인 check-then-act(TOCTOU) 구조였다. 두 요청이 동시에 선검사를 통과하면 둘 다 INSERT 된다.
`Payment` 엔티티는 `order_id`, `payment_key`, `matching_id` 에는 `unique=true` 가 있었지만
`application_id` 에는 `nullable=false` 만 있어 **DB 레벨 최후 방어가 없었다**.

스키마는 Flyway 등 마이그레이션 없이 Hibernate `ddl-auto` 가 엔티티 애노테이션 그대로 생성한다
(dev `update`, prod `validate`). 즉 애노테이션에 없으면 DB 에도 없다.

**(2) 승인 흐름에 동시성 제어·멱등성 체크 없음**

`confirmPayment` 는 주문 조회 → 검증 → 토스 호출 순으로 진행하는데, 조회와 토스 호출 사이에
잠금이 없고 `payment.getStatus()` 를 확인하지도 않았다. `@Transactional` 은 DB 작업만 롤백하므로
**이미 나간 외부 API 호출은 되돌리지 못한다**(부수효과가 커밋 이전에 실행되는 흐름).

**(3) 토스 `Idempotency-Key` 헤더 미전송**

`TossPaymentConfig.createTossHeaders()` 는 `Authorization` 과 `Content-Type` 만 설정했다.
설계 문서 `ai/specs/20260424_admin_payment-refund-concurrency-design.md:43` 에서 멱등키 도입을
"별도 작업"으로 미뤄둔 상태였고, `ai/specs/20260423_admin_payments-design.md:273` 은 취소 API 가
paymentKey 기준으로 토스 측에서 멱등하니 불필요하다고 판단했다. 그러나 그 판단은 **취소 경로에만**
해당하며, 승인/생성 경로의 멱등성은 어느 문서에서도 다뤄진 적이 없었다.

## 해결 방법

3중 방어를 계층별로 채웠다. 각 계층이 닫는 창이 다르다.

**① DB 유니크 제약 (최후의 보루)**
- `backend/src/main/java/com/devmatch/entity/Payment.java:25` — `application_id` 에 `unique = true` 추가
- `backend/src/main/java/com/devmatch/service/PaymentService.java:113` — `save` 후 `flush()` 로 제약 위반을
  즉시 검출하고, `DataIntegrityViolationException` 을 `DuplicatePaymentException`(409) 으로 변환

**② 분산 락 + 멱등성 상태 체크 (동시 요청·시간차 재시도)**
- `backend/src/main/java/com/devmatch/support/DistributedLock.java` (신규) — `SET NX EX` 획득,
  해제는 Lua CAS 로 **owner 일치 시에만** 삭제(TTL 만료 후 남의 락을 지우는 사고 방지)
- `backend/src/main/java/com/devmatch/service/PaymentService.java:151` — `confirmPayment` 진입 시
  `pay:confirm:lock:{orderId}` 락 획득. 실패 시 `PaymentInProgressException`(409)
- `backend/src/main/java/com/devmatch/service/PaymentService.java:172` — 이미 `CONFIRMED` 면 토스 재호출 없이
  기존 결과 반환(멱등 반환)
- 락 해제는 `TransactionSynchronization.afterCompletion` 으로 **커밋 이후**에 수행
  (`PaymentService.java:212`). 커밋 전에 풀면 대기 중이던 요청이 아직 반영 안 된 `PENDING` 을 읽고
  토스를 재호출한다.

**③ 토스 멱등성 키 (내부 방어로 못 닫는 크래시 갭)**
- `backend/src/main/java/com/devmatch/config/TossPaymentConfig.java:47` — `createTossHeaders(String)` 오버로드
  추가(300자 제한 절삭 포함)
- `backend/src/main/java/com/devmatch/service/TossPaymentService.java:44` — 승인 키 `confirm:{orderId}`
- `backend/src/main/java/com/devmatch/service/TossPaymentService.java:90` — 취소 키 `cancel:{paymentKey}`

**테스트**
- `backend/src/test/java/com/devmatch/service/PaymentServiceTest.java` (신규 7건) — 락 실패 시 토스 미호출,
  이미 승인된 결제의 멱등 반환, 유니크 제약 위반의 예외 변환 등

검증: `./gradlew test` 236개 중 25개 실패. 이 25개는 **변경 전 baseline(222개 중 25개 실패)과 동일한**
기존 실패로, MySQL 미기동 환경에서 `@SpringBootTest` 통합 테스트가 `Unable to determine Dialect` 로
깨지는 것이다. 이번 변경으로 추가된 14개 테스트는 전부 통과.

## 재발 방지 / 메모

**⚠️ prod 마이그레이션 필요 — 자동 반영되지 않음**

prod 는 `ddl-auto: validate` 라서 애노테이션만 추가해도 인덱스가 생기지 않고, 오히려 검증 실패로
부팅이 막힐 수 있다. 배포 전 아래를 **순서대로** 수행해야 한다.

```sql
-- 1) 기존 중복 데이터 확인 (결과가 있으면 먼저 정리)
SELECT application_id, COUNT(*) c FROM payments GROUP BY application_id HAVING c > 1;

-- 2) 중복이 없을 때만 유니크 인덱스 생성
ALTER TABLE payments ADD CONSTRAINT uk_payments_application_id UNIQUE (application_id);
```

**dev 도 자동 반영되지 않는다 (2026-07-27 실측 확인).** `ddl-auto: update` 는 **기존 테이블의 컬럼에
유니크 제약을 추가하지 않는다** — 테이블을 새로 생성할 때만 반영된다. 실제로 앱을 기동해 확인한 결과,
Hibernate 는 `alter table payments modify column status ...` 만 실행했고 `application_id` 유니크 제약
DDL 은 **아예 생성하지 않았다**. 중복 데이터가 없었음에도 그랬다.

즉 **dev/prod 모두 위 SQL 을 수동 실행해야 한다.** 실행 후 아래로 검증한다.

```sql
-- 인덱스 존재 확인
SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS
 WHERE TABLE_SCHEMA='devmatch' AND TABLE_NAME='payments' AND COLUMN_NAME='application_id';

-- 실제 거부 확인 (ERROR 1062 가 나야 정상)
INSERT INTO payments (user_id, application_id, matching_id, order_id, amount, status, version, created_at, updated_at)
VALUES (1, <기존_application_id>, 9999, 'DUP-TEST-001', 990000, 'PENDING', 0, NOW(), NOW());
```

dev 에서 위 절차로 검증했고 `ERROR 1062 Duplicate entry '1' for key 'payments.uk_payments_application_id'`
로 정상 거부됨을 확인했다.

**남은 리스크**

- 분산 락 TTL 은 10초(`PaymentService.CONFIRM_LOCK_TTL`). 토스 응답이 그보다 오래 걸리면 락이 먼저
  풀려 두 요청이 겹칠 수 있다. 이 창은 ③ 토스 멱등키가 최종 방어한다.
- 락은 "동시 실행"만 막고 결과를 기억하지 않는다. 멱등성의 기억 역할은 `Payment.status` 가 한다.
  더 견고하게 가려면 전용 `idempotency_keys` 테이블(키 + 상태 + 저장된 응답)로 응답 자체를 재생하는
  방식이 실무 표준이며, 이는 후속 작업으로 남겨둔다.
- 환불 경로(`AdminPaymentService.refundPayment`)의 "토스 성공 후 DB 실패 → 고아 환불" 시나리오는
  여전히 수동 복구 대상이다. 정기 reconciliation 잡은 미도입 상태.

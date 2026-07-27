---
type: spec
category: payment
status: active
updated: 2026-07-27
description: "결제 중복 방어 3계층(분산 락·멱등성·유니크 제약)과 Redis 대기열 아키텍처 설계 및 구현 기록"

---

# 결제 정합성 3중 방어 + Redis 대기열 설계

- 작성일: 2026-07-27
- 브랜치: `feat/payment-idempotency-waiting-queue`
- 관련 에러 기록: `error/2026-07-27-payment-idempotency-and-application-id-unique.md`
- 선행 문서: `ai/specs/20260424_admin_payment-refund-concurrency-design.md`(멱등키를 "별도 작업"으로 유예),
  `ai/specs/20260423_admin_payments-design.md:273`(취소 API 는 토스가 멱등하다는 판단)

## 배경

토스페이 결제에 멱등성 키가 적용돼 있는지 점검하다 세 계층이 모두 비어 있음을 확인했다.
본 문서는 그 공백을 메운 설계와, 대규모 트래픽을 전제로 한 대기열 구조를 함께 정리한다.

## 1. 개념 정리 — 셋은 다른 일을 한다

혼동하기 쉬우나 역할이 다르며, 경쟁 관계가 아니라 **팀**이다.

| 개념 | 정의 | 성질 | 막는 것 |
|---|---|---|---|
| 분산 락 | "지금은 나만 이 작업을 한다" | 휘발성 (풀리면 기억 없음) | 동시 요청 |
| 멱등성 | "전에 했으면 그 결과를 그대로 준다" | 영속 (기록) | 시간차 재시도 |
| 유니크 제약 | "물리적으로 중복 행을 못 만든다" | DB 강제 | 위 둘이 뚫린 경우 |

핵심: **락은 멱등성이 아니다.** 락은 동시성만 제어하고 결과를 기억하지 않는다.
멱등성의 "기억" 역할은 영속 저장소가 맡아야 하며, 본 프로젝트에서는 `Payment.status` 가 그 역할을 한다.

## 2. 방어가 닫는 창 — 바깥으로 갈수록 근본적

```
Redis 분산 락      →  동시 요청 창을 닫음                       (가장 안쪽)
Payment.status     →  시간차 재시도 창을 닫음
DB 유니크 제약      →  위 둘이 뚫린 중복 INSERT 를 거부
토스 Idempotency-Key →  "토스 성공 후 DB 저장 전 크래시" 창을 닫음  (가장 바깥, 최종)
```

마지막 항목이 중요하다. 우리 시스템의 모든 내부 방어는 **"우리 DB 에 저장이 됐다"를 전제**로 하는데,
크래시는 정확히 그 저장 직전에 일어난다. 토스에는 돈이 빠졌으나 우리 DB 엔 흔적이 없는 이 틈은
**내부 잠금으로는 구조적으로 닫을 수 없고**, 같은 멱등키를 받은 토스만 막아줄 수 있다.

## 3. 구현 — 결제 정합성

### 3.1 DB 유니크 제약

`backend/src/main/java/com/devmatch/entity/Payment.java:25` — `application_id` 에 `unique = true`.
`createPayment` 의 `existsByApplicationId` 선검사는 check-then-act 라 동시 요청에 취약하므로,
DB 제약이 실제 불변식("신청서 1건당 결제 1건")을 강제한다.

`PaymentService.createPayment` 는 `save` 직후 `flush()` 하여 제약 위반을 즉시 검출하고,
`DataIntegrityViolationException` → `DuplicatePaymentException`(409) 으로 변환한다.

### 3.2 분산 락

`backend/src/main/java/com/devmatch/support/DistributedLock.java` (신규).

- 획득: `SET key owner NX EX ttl` — Redis 의 원자적 명령. `NX` 는 키가 없을 때만 세팅하므로
  동시 요청 중 하나만 성공한다. `EX` 는 홀더가 죽어도 자동 해제되게 해 데드락을 막는다.
- 해제: Lua 스크립트로 `get` → `del` 을 원자적으로 수행하되 **owner 가 일치할 때만** 삭제한다.
  내 락이 TTL 로 만료된 뒤 다른 요청이 잡은 락을 실수로 지우는 사고를 막기 위함이다.

> 참고: Redis 에 "락"이라는 기능은 없다. 원자적 명령 위에 얹은 약속(패턴)이며, 그래서 TTL·owner
> 검증·크래시 갭 같은 함정이 우리 책임으로 남는다.

### 3.3 멱등성 상태 체크와 락 해제 시점

`PaymentService.confirmPayment` 흐름:

1. 대기열 입장권 검사 (비활성 시 항상 통과)
2. `pay:confirm:lock:{orderId}` 락 획득 — 실패 시 `PaymentInProgressException`(409)
3. 주문 조회 → 소유자 검증
4. **이미 `CONFIRMED` 면 토스 호출 없이 기존 결과 반환** (멱등 반환)
5. 금액 검증 → 토스 승인 → 상태 전이

**락 해제는 반드시 트랜잭션 커밋 이후**(`TransactionSynchronization.afterCompletion`)에 한다.
커밋 전에 풀면 대기 중이던 요청이 아직 반영되지 않은 `PENDING` 을 읽고 토스를 재호출한다.
트랜잭션 동기화가 없는 단위 테스트 경로에서는 즉시 해제하며, 어느 경우든 TTL 이 최후 안전망이다.

### 3.4 토스 멱등성 키

`TossPaymentConfig.createTossHeaders(String)` 오버로드로 `Idempotency-Key` 헤더를 추가(300자 절삭).

- 승인: `confirm:{orderId}`
- 취소: `cancel:{paymentKey}`

키의 필수 조건은 **같은 결제 시도 → 항상 같은 키 / 다른 결제 → 반드시 다른 키**다.
매번 새 UUID 를 만들면 토스 입장에서 전부 새 요청이 되어 멱등성이 무력화된다.

## 4. 구현 — Redis 대기열 (Waiting Room)

### 4.1 목적과 경계

대기열은 **부하 조절(traffic shaping)** 장치다. DB·PG 가 감당 가능한 속도로 요청을 흘려보낸다.
**대기열은 중복을 막지 않는다** — 입장한 사용자가 결제 버튼을 두 번 누르는 것은 3장의 몫이다.
두 관심사를 섞지 않는다.

### 4.2 키 구조

```
waiting:payment              ZSET    대기 줄 (member=userId, score=발급 순번)
waiting:payment:counter      STRING  번호표 발급기 (INCR)
waiting:payment:active:{u}   STRING  입장권. TTL 로 자리 자동 반납
```

Sorted Set 을 쓰는 이유는 대기열에 필요한 세 연산을 모두 지원하기 때문이다:
줄 세우기(`ZADD`), **내 순번 조회**(`ZRANK`), 앞에서 N명 꺼내기(`ZPOPMIN`).
List 로도 큐는 되지만 "앞에 몇 명" 조회가 불가능해 대기 화면을 만들 수 없다.

### 4.3 흐름

| 단계 | 동작 | 구현 |
|---|---|---|
| 진입 | `INCR` 로 번호표 → `ZADD NX` 로 줄 서기 | `WaitingQueueService.enter` |
| 조회 | `ZRANK`(내 앞 인원) + `ZCARD`(전체) | `WaitingQueueService.status` |
| 승격 | `ZPOPMIN N` → `SET active:{u} EX ttl` | `WaitingQueueService.promote` |
| 검사 | `EXISTS active:{u}` | `WaitingQueueService.isActive` |

**`ZADD NX` 가 핵심이다.** NX 가 없으면 대기 중 새로고침마다 점수가 갱신되어 순번이 밀리거나
튄다. "이미 줄 서 있으면 건드리지 않는다"는 의미다.

승격은 `WaitingQueuePromoter` 스케줄러가 주기적으로 수행하며,
`promote-batch-size / promote-interval-ms` 의 비율이 곧 하류로 흘리는 초당 처리량이 된다.
이 두 값이 전체 시스템 부하를 조절하는 손잡이다.

### 4.4 엣지 케이스

| 상황 | 문제 | 대응 |
|---|---|---|
| 새로고침 연타 | 순번 변동 | `ZADD NX` 로 기존 점수 보존 ✔ 구현됨 |
| 입장 후 미결제 | 자리 낭비 | `active` 키 TTL 자동 반납 ✔ 구현됨 |
| 대기 중 이탈 | 유령이 줄 차지 | 오래된 score 주기적 정리 — **미구현(후속)** |
| 입장권으로 중복 결제 | 이중 결제 | 3장의 락·멱등성·유니크 제약이 담당 ✔ |

### 4.5 설정

`app.waiting-queue.enabled` 기본 `false` — 켜지 않으면 모든 요청이 즉시 통과하며 기존 동작과 동일하다.
`WaitingQueuePromoter` 는 `@ConditionalOnProperty` 로 활성 시에만 빈이 등록된다.

## 5. 확장 시나리오 (미구현 — 트래픽이 실제로 커질 때)

현 구조는 단일 백엔드 인스턴스를 가정한다. 100배 규모를 가정하면:

- **읽기 확장**: DB 읽기 복제본 + 조회 캐시
- **쓰기 평준화**: 결제 저장을 메시지 큐 뒤로 이동. 이때 **DB 저장 / 알림 / 로그를 별도 소비자로 분리**해야
  한다. 셋을 한 작업으로 묶으면 알림 실패가 결제를 롤백시키는 사고가 난다.
- **비동기 UX 결정**: 저장이 큐 뒤로 가면 "결제 처리 중" → 상태 폴링 방식이 필요하다.
  사용자가 무엇을 기다리고 무엇이 백그라운드로 가는지 명시적으로 정해야 한다.
- **멱등성 강화**: 전용 `idempotency_keys` 테이블(키 UNIQUE + `STARTED/COMPLETED` + 저장된 응답)로
  전환. INSERT 자체가 락 역할을 하고 응답까지 재생하므로, 락 없이도 멱등성이 완결된다.
- **좀비 레코드 청소**: `STARTED` 로 멈춘 행과 Toss↔DB 불일치를 잡는 정기 reconciliation 잡.

## 6. 검증 (2026-07-27 실측)

**단위·통합 테스트**: MySQL·Redis 기동 후 `./gradlew test` **236개 전량 통과 (BUILD SUCCESSFUL)**.
(DB 미기동 시에는 25개가 `Unable to determine Dialect` 로 실패하는데, 이는 변경 전 baseline 과
동일한 환경 문제였다.)

**런타임 검증** — 앱을 `WAITING_QUEUE_ENABLED=true`, 배치 2, 주기 3초로 기동해 확인:

| 항목 | 방법 | 결과 |
|---|---|---|
| 앱 기동 | `bootRun` | 14.9초 정상 기동 |
| 분산 락 | 같은 키로 `SET NX` 2회 | 1번째 `OK`, 2번째 `nil` — 정확히 차단 |
| 유니크 제약 | 중복 `application_id` INSERT | `ERROR 1062 Duplicate entry` — 물리적 거부 확인 |
| `ZADD NX` | 순번 1인 멤버를 99로 재등록 | NX 있음 → 점수 1 보존 / NX 없음 → 99로 밀림 |
| 승격 스케줄러 | 로그 관찰 | `[WaitingQueue] 2명 입장 승격 (TTL 300s)` 주기적 기록 |
| 입장권 TTL | `TTL active:{u}` | 300초 정상 설정 |
| API 인증 | 미인증 호출 | 403 (인증 필요) |

> 검증 중 수동 Redis 테스트가 계속 비워지는 현상이 있었는데, 원인은 **실행 중인 앱의 승격
> 스케줄러가 실제로 큐를 소비하고 있었기 때문**이었다. 기능이 정상 동작한다는 반증이 된 셈이다.

## 7. 스키마 반영 — Flyway 로 전환됨 (2026-07-27 후속)

최초에는 `ddl-auto: update` 가 기존 테이블 컬럼에 유니크 제약을 추가하지 않아 **dev/prod 모두
수동 `ALTER TABLE` 이 필요**했다. 이 문제를 근본 해결하기 위해 같은 날 Flyway 를 도입했다.

```
backend/src/main/resources/db/migration/
  V1__baseline_schema.sql                        기존 38개 테이블 스냅샷
  V2__add_unique_application_id_on_payments.sql  본 설계의 유니크 제약
  V3__make_payments_matching_id_nullable.sql     도입 중 발견한 드리프트 교정
```

- `ddl-auto` 를 `validate` 로 전환 — 구조 변경은 Flyway 가 유일한 주체, Hibernate 는 검사만.
- `baseline-on-migrate: true` — 기존 DB 는 V1 을 건너뛰고, 빈 DB 는 V1 부터 전체 적용.
- **수동 DDL 절차는 더 이상 필요 없다.** 앱 기동 시 자동 적용된다.

도입 과정에서 결함 3건(베이스라인 FK 순서, H2 테스트 충돌, `payments.matching_id` NOT NULL
드리프트로 인한 **결제 생성 상시 실패**)이 드러나 함께 수정했다.
자세한 내용은 `error/2026-07-27-flyway-adoption-schema-drift.md` 참조.

> 주의: `ddl-auto: validate` 는 테이블/컬럼 존재와 타입만 검사하고 **nullability·유니크 제약은
> 검사하지 않는다.** 통과했다고 스키마가 정합하다는 뜻이 아니다.

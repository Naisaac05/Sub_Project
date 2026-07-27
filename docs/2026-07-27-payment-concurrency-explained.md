# 결제 동시성·멱등성과 Redis 대기열 — 코드로 이해하기 (2026-07-27)

> "토스페이 결제에 멱등성 키가 적용돼 있나?" 라는 점검에서 출발해, 결제 중복 방어 3계층과
> Redis 대기열을 구현하기까지의 **학습 기록**입니다. 신입 개발자 관점에서 개념 → 코드 → 실측 순으로 풀어 씁니다.
>
> 짧은 원인/조치 기록은 `error/2026-07-27-payment-idempotency-and-application-id-unique.md`,
> 설계 명세는 `ai/specs/20260727_payment_idempotency-and-waiting-queue-design.md` 에 따로 있습니다.

---

## 0. 출발점 — 무엇이 비어 있었나

코드를 뒤져보니 결제 도메인에 중복 방어가 **세 군데 모두** 없었다.

| 확인한 것 | 상태 |
|---|---|
| 토스 `Idempotency-Key` 헤더 | ❌ 없음 (`TossPaymentConfig` 는 Authorization·Content-Type 만) |
| 결제 승인 시 동시성 제어 | ❌ 없음 (조회 → 토스 호출 사이 잠금 없음) |
| `application_id` DB 유니크 제약 | ❌ 없음 (`order_id`·`payment_key`·`matching_id` 에만 있었음) |

흥미롭게도 **문서에는 흔적이 있었다.** `ai/specs/20260424_admin_payment-refund-concurrency-design.md:43` 은
멱등키 도입을 "별도 작업"으로 미뤄뒀고, `ai/specs/20260423_admin_payments-design.md:273` 은
"취소 API 는 토스가 paymentKey 기준으로 멱등하니 불필요"라고 판단했다.
그 판단은 **취소 경로에만** 맞고, 승인·생성 경로는 어느 문서에서도 다뤄진 적이 없었다.

---

## 1. 가장 중요한 개념 정리 — 셋은 다른 도구다

처음에 가장 헷갈렸던 부분. 이 셋을 섞으면 뒤가 전부 꼬인다.

| 개념 | 한 줄 정의 | 비유 |
|---|---|---|
| **분산 락** | "지금은 나만 이 작업을 한다. 남들은 기다려." | 화장실 문 잠그기 🚻 |
| **멱등성 키** | "이 작업 전에 한 적 있어? 있으면 그때 결과 그대로 줘." | 영수증 번호 🧾 |
| **DB 유니크 제약** | "물리적으로 중복 행을 못 만들게 한다." | 좌석 지정 💺 |

핵심 차이:
- **락은 *동시에* 들어오는 걸 막는다**(시간 축). 풀리면 아무 기억도 남지 않는다.
- **멱등성은 *이미 끝난* 걸 다시 안 하게 한다**(기록 축). 결과를 기억한다.
- **유니크 제약은 위 둘이 뚫려도** 마지막에 DB 가 거부한다.

### ★ 락은 멱등성이 아니다

가장 큰 오해였다. 화장실 문은 잠겨 있을 때만 남을 막지, 내가 나간 뒤 "아까 누가 다녀갔는지"는 모른다.
그래서 **락이 풀린 뒤에 오는 재시도는 락으로 못 막는다.**

> 멱등성의 본질은 "동시성 차단"이 아니라 **"기억"** 이고, 기억은 영속 저장소에 있어야 한다.

우리 코드에서 그 기억 역할은 **`Payment.status` 컬럼**이 한다. 즉 멱등성을 만든 건 락이 아니라
"이미 CONFIRMED 면 그대로 반환"하는 세 줄이었다.

---

## 2. Redis 기초 — 락을 이해하려면 먼저 알아야 할 것

### 2.1 Redis 는 무엇인가

> **메모리(RAM)에 사는 초고속 Key-Value 저장소.**

- **MySQL = 창고** 🏢 — 디스크에 안전 보관. 느림. **원본 데이터**
- **Redis = 책상 위** 🖥️ — 즉시 집음. 작고 휘발 위험. **캐시·세션·락**

판단 기준: **"이 데이터가 날아가면 다시 만들 수 있나?"** → 예(캐시·세션)면 Redis, 아니오(결제 원장)면 MySQL.

### 2.2 5가지 자료구조 (이 프로젝트는 3개를 이미 쓰고 있었다)

| 타입 | 용도 | 프로젝트 사용처 |
|---|---|---|
| **String** | 텍스트·카운터·락 | 폐기 토큰 블랙리스트, AI 답변 캐시, 락 |
| **Hash** | 객체 하나(필드-값 묶음) | `session:{id}` 세션 정보 |
| **Set** | 중복 없는 집합 | `user_sessions:{userId}` 다중 기기 |
| List | 순서 있는 줄 | 미사용 |
| **Sorted Set** | 점수순 정렬 → **대기열** | 미사용 → 이번에 도입 |

### 2.3 TTL — 킬러 기능

key 에 유효기간을 걸면 **저절로 사라진다.** 만료 데이터를 지우는 배치가 필요 없다.
`RefreshSessionService` 의 세션 만료, 락의 자동 해제가 전부 이 원리다.

### 2.4 ★ "Redis 의 락 기능"은 존재하지 않는다

가장 중요한 오해 교정. **Redis 에는 `LOCK` 명령어도, 락이라는 타입도 없다.**
Redis 가 주는 건 **원자적 명령어**뿐이다.

```
SET key value NX EX 10
      │        │   └─ 10초 뒤 자동 만료 (데드락 방지)
      │        └───── 키가 "없을 때만" 세팅 (NX = Not eXists)
      └────────────── 값에는 소유자 식별용 토큰
```

"락"은 이 명령 위에 얹은 **약속(패턴)** 이다. 모든 코드가 "적기 전에 확인한다"는 규칙을 지켜야 성립한다.
Redis 가 강제로 막아주는 게 아니다.

그럼 Redis 덕분인 건 뭘까? → **원자성.** Redis 는 명령을 한 번에 하나씩(single-threaded) 처리해서,
두 클라이언트가 동시에 `SET NX` 를 쏴도 **딱 하나만 성공**한다.

만약 이걸 "① GET 으로 확인 → ② 비었으면 SET" 두 단계로 하면 그 사이에 남이 끼어든다.
**반드시 `SET NX` 한 방이어야 한다.**

---

## 3. 코드 — 무엇을 어떻게 넣었나

### 3.1 DistributedLock — 락 패턴 캡슐화

`backend/src/main/java/com/devmatch/support/DistributedLock.java`

```java
public String tryLock(String key, Duration ttl) {
    String owner = UUID.randomUUID().toString();
    Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, ttl);  // = SET NX EX
    return Boolean.TRUE.equals(acquired) ? owner : null;
}
```

세부 학습 포인트:
- `setIfAbsent` 가 스프링에서의 `SET NX` 다. 이름만 다르다.
- `Boolean.TRUE.equals(acquired)` — 반환 타입이 `Boolean`(객체)이라 **null 일 수 있다.**
  `if (acquired)` 로 쓰면 NPE. 신입이 자주 밟는 지뢰.

해제가 진짜 핵심이다.

```java
private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
    Long.class);
```

왜 그냥 `delete` 가 아니라 Lua 스크립트인가?

> A 가 락 획득 → A 가 느려서 TTL 만료로 자동 해제 → B 가 새 락 획득 →
> 그때 A 가 뒤늦게 `delete` 호출 → **B 의 락을 지워버림** 💥

그래서 "내 것일 때만 지운다"가 필요한데, `get` 확인과 `del` 사이에도 틈이 생긴다.
Lua 는 Redis 가 **통째로 원자 실행**하므로 그 틈이 없다. 이 패턴을 **CAS(Compare-And-Swap)** 라 한다.

### 3.2 ★ 가장 미묘한 코드 — 락을 언제 푸는가

`backend/src/main/java/com/devmatch/service/PaymentService.java:212`

```java
private void releaseLockAfterCommit(String lockKey, String lockOwner) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                distributedLock.unlock(lockKey, lockOwner);
            }
        });
    } else {
        distributedLock.unlock(lockKey, lockOwner);   // 트랜잭션 없는 단위테스트 경로
    }
}
```

왜 `finally` 로 풀면 안 되는가:

```
[잘못된 방식 — finally 해제]
  A: 락 획득 → 토스 승인 → payment.confirm()   (아직 메모리에만)
  A: finally 에서 락 해제                       ← 아직 DB 커밋 전!
  B: 락 획득 → DB 조회 → 여전히 PENDING
  B: "결제 안 됐네?" → 토스 재호출 💥
```

`@Transactional` 은 메서드가 **끝난 뒤** 커밋한다. 그래서 메서드 안의 `finally` 는 **커밋보다 먼저** 돈다.
이 순서 때문에 락이 무용지물이 된다.

> **원칙: 락의 수명은 DB 트랜잭션보다 길어야 한다.**

### 3.3 멱등성의 정체는 세 줄

`PaymentService.java:172`

```java
if (payment.getStatus() == PaymentStatus.CONFIRMED) {
    log.info("[Payment] 멱등 반환 — 이미 승인된 결제 orderId: {}", request.getOrderId());
    return PaymentResponse.from(payment);
}
```

화려한 기술이 아니다. **"이미 했으면 그때 결과를 그대로 준다"** 가 멱등성의 전부다.

### 3.4 check-then-act 를 DB 로 받치기

`PaymentService.java:113`

```java
try {
    saved = paymentRepository.save(payment);
    paymentRepository.flush();          // ← 유니크 제약 위반을 여기서 즉시 검출
} catch (DataIntegrityViolationException e) {
    throw new DuplicatePaymentException("이미 해당 신청서에 대한 결제가 존재합니다");
}
```

**`flush()` 가 왜 필요한가?** JPA 는 성능을 위해 SQL 을 모아뒀다가 커밋 시점에 보낸다(쓰기 지연).
그러면 제약 위반 예외가 **이 try 블록 밖에서** 터져 잡을 수 없다. `flush()` 는 "지금 보내"라는 뜻.

역할 분담:
- 선검사 `existsByApplicationId` = **정상 케이스의 친절한 안내**
- DB 유니크 제약 = **경쟁 상황의 진짜 방어**

### 3.5 토스 멱등성 키 — 내부 방어로 못 닫는 창

`TossPaymentConfig.java:47`, `TossPaymentService.java:44,90`

- 승인 키: `confirm:{orderId}`
- 취소 키: `cancel:{paymentKey}`

**필수 조건: 같은 결제 시도 → 항상 같은 키 / 다른 결제 → 반드시 다른 키.**
매번 새 UUID 를 만들면 토스 입장에선 전부 새 요청이라 멱등성이 무력화된다.

---

## 4. ★ 클라이맥스 — 왜 토스 멱등키가 반드시 필요한가

가장 위험한 시나리오: **A 가 토스 호출 직후, DB 저장 직전에 크래시.**

```
t1  A: 락 획득
t2  A: 토스 호출 성공 → 실제로 돈이 빠짐 💳
t3  A: 💥 크래시 (저장 못함, 상태는 PENDING 그대로)
t4  ⏱ 락 TTL 만료 → 자동 해제 (A 는 죽어서 못 풀었음)
t5  B: 락 획득 → 상태 조회 → PENDING ("아직 결제 안 됐네?")
t6  B: 토스 두 번째 호출 → 이중 결제 💥
```

여기서 깨달은 것:

> **우리 시스템의 모든 내부 방어(락·status·유니크 제약)는 "우리 DB 에 저장이 됐다"를 전제로 한다.
> 그런데 크래시는 정확히 그 저장 직전에 일어난다.**

토스에는 돈이 빠졌는데 우리 DB 엔 흔적이 없는 이 틈은 **우리 쪽 어떤 잠금으로도 못 닫는다.**
같은 멱등키를 받은 토스만 "이미 처리함"으로 막아줄 수 있다.

그래서 세 무기는 각자 **다른 창**을 닫고, 바깥으로 갈수록 근본적이다.

```
Redis 분산 락        →  동시 요청 창              (가장 안쪽)
Payment.status      →  시간차 재시도 창
DB 유니크 제약        →  중복 INSERT
토스 Idempotency-Key →  "돈은 빠졌는데 저장 전 크래시" 창  (가장 바깥, 최종)
```

**첫 질문("토스에 멱등성 있다던데 따로 만들어야 하나요?")의 진짜 답**: 그렇다.
그게 우리가 논리적으로 절대 못 닫는 마지막 틈을 닫는 **유일한** 장치이기 때문이다.

---

## 5. Redis 대기열 — Sorted Set 으로 번호표 만들기

### 5.1 왜 Sorted Set 인가

대기열에 필요한 3가지를 ZSet 이 전부 지원한다.

| 필요한 것 | 명령 |
|---|---|
| 선착순 줄 세우기 | `ZADD` (점수 = 순번) |
| **"내 앞에 몇 명?"** | `ZRANK` |
| 앞에서 N명 입장 | `ZPOPMIN` |

List 로도 큐는 되지만 **순번 조회가 안 된다.** "앞에 12,345명" 화면을 못 만든다.

### 5.2 키 구조

```
waiting:payment              ZSET    대기 줄 (member=userId, score=번호표)
waiting:payment:counter      STRING  번호표 발급기 (INCR)
waiting:payment:active:{u}   STRING  입장권. TTL 로 자리 자동 반납
```

### 5.3 ★ `ZADD NX` 가 핵심

`WaitingQueueService.enter()`

```java
Boolean added = redis.opsForZSet().addIfAbsent(QUEUE_KEY, member, nextTicket());
```

`addIfAbsent` = `ZADD NX`. **NX 가 없으면 사용자가 새로고침할 때마다 점수가 갱신되어 순번이 밀린다.**
실측으로 확인한 대조 실험:

```
[NX 사용] 점수 1인 멤버를 99로 재등록 → 결과 0(무시), 점수 1 유지 ✅
[NX 없음] 같은 시도               → 점수 99로 밀림 ❌
```

### 5.4 대기열은 중복을 막지 않는다

혼동하기 쉬운 경계.

- **대기열** = **부하** 조절 (몇 명을 들일까)
- **락·멱등성·유니크 제약** = **중복** 방지 (한 명이 두 번 하지 못하게)

입장한 사용자가 결제 버튼을 두 번 누르는 건 여전히 3장의 몫이다. 두 관심사를 섞지 않는다.

승격 배치 크기 ÷ 주기 = **하류(DB·PG)로 흘려보내는 초당 처리량**. 이 두 값이 부하 조절 손잡이다.

---

## 6. ★ 실측에서 얻은 교훈 — "코드에 썼으니 됐겠지"는 검증이 아니다

Docker 를 띄우고 실제로 앱을 기동해 확인했더니, **엔티티에 `unique = true` 를 썼는데 DB 에는
인덱스가 생기지 않았다.**

```
PRIMARY                      id
UK35yqdahtiysne6iij9ske72bj  payment_key
UK51m1gkdcevrqj4pof90j6sure  matching_id
UK8vo36cen604as7etdfwmyjsxt  order_id
                             ← application_id 가 없다!
```

로그를 보니 Hibernate 는 `alter table payments modify column status ...` 만 실행했고
**유니크 제약 DDL 은 아예 생성하지 않았다** (중복 데이터도 없었는데).

원인: **`ddl-auto: update` 는 기존 테이블의 컬럼에 유니크 제약을 추가하지 않는다.**
테이블을 새로 만들 때만 반영한다.

| 상황 | 자동 반영 |
|---|---|
| 새 테이블/새 컬럼 | ✅ |
| **기존 컬럼에 유니크 제약 추가** | ❌ |
| 컬럼 삭제·타입 축소 | ❌ |

**가장 무서운 상태**: 코드도 문서도 "방어가 있다"고 말하는데 실제 DB 엔 없는 것. 아무도 의심하지 않는다.

수동으로 `ALTER TABLE` 을 실행한 뒤에야 진짜로 막혔다.

```
ERROR 1062 (23000): Duplicate entry '1' for key 'payments.uk_payments_application_id'
```

> **교훈: DB 에 실제로 걸렸는지 눈으로 확인해야 한다.**

또 하나 재밌던 일 — 수동 Redis 테스트가 자꾸 비워져서 당황했는데, 원인은 **실행 중인 앱의 승격
스케줄러가 진짜로 큐를 소비하고 있어서**였다. 버그가 아니라 기능이 작동한다는 증거였다.

---

## 7. 실측 검증 결과

| 항목 | 방법 | 결과 |
|---|---|---|
| 전체 테스트 | `./gradlew test` (DB 기동 후) | **236개 전량 통과** |
| 앱 기동 | `bootRun` | 14.9초 정상 |
| 분산 락 | 같은 키로 `SET NX` 2회 | 1번째 `OK`, 2번째 `nil` ✅ |
| 유니크 제약 | 중복 `application_id` INSERT | `ERROR 1062` 물리적 거부 ✅ |
| `ZADD NX` | 점수 1 멤버를 99로 재등록 | NX 있음 보존 / 없음 밀림 ✅ |
| 승격 스케줄러 | 로그 관찰 | `[WaitingQueue] 2명 입장 승격 (TTL 300s)` ✅ |
| API 인증 | 미인증 호출 | 403 ✅ |

---

## 8. 남은 과제 (정직하게)

1. **락 TTL 10초 < 토스 응답 지연** 가능성 — 이 틈은 토스 멱등키가 최종 방어. 구조적으로 완전 제거 불가.
2. **대기 중 이탈한 유령 사용자** 정리 미구현 — 오래된 score 를 주기적으로 `ZREMRANGEBYSCORE` 필요.
3. **멱등성이 `status` 의존** — 더 견고한 실무 표준은 전용 `idempotency_keys` 테이블
   (키 UNIQUE + `STARTED/COMPLETED` + 저장된 응답). INSERT 자체가 락이 되고 응답까지 재생한다.
4. **스키마 드리프트** — `matching_id` 가 DB 는 NOT NULL 인데 엔티티는 nullable. 이번 범위 밖.
5. **★ Flyway 미도입** — 6장의 문제를 근본 해결하는 다음 단계. 아래 참조.

---

## 9. 다음 단계 — 왜 Flyway 인가

6장의 문제는 결국 **`ddl-auto` 가 "추측"하기 때문**이다.

```
엔티티  →  Hibernate 가 DB 와 비교해 차이를 추측  →  알아서 SQL 생성
                        ↑ 못 하는 건 조용히 건너뜀
```

Flyway 는 발상을 뒤집는다. **개발자가 SQL 을 파일로 적고, Flyway 는 순서대로 실행하고 기록만 한다.**

```
db/migration/
  V1__create_payments.sql
  V2__add_unique_application_id.sql
```

`flyway_schema_history` 테이블이 "어디까지 적용됐는지"를 **DB 스스로 기억**한다.
앱 기동 시 아직 실행 안 된 것만 골라 순서대로 실행한다.

| | `ddl-auto: update` (현재) | Flyway |
|---|---|---|
| SQL 을 누가 만드나 | Hibernate 가 **추측** | 개발자가 **명시** |
| 못 하는 변경 | 조용히 **건너뜀** | 없음 |
| 적용 이력 | **없음** (사람 기억) | 히스토리 테이블 |
| 환경 간 일관성 | 보장 안 됨 | 같은 파일 → 같은 순서 |
| 실패하면 | 조용히 넘어감 | **앱 기동 중단** |
| 코드 리뷰 | DB 변경이 안 보임 | **SQL 이 PR 에 보임** |

핵심 규칙:
1. **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다** (checksum 검증. 고칠 땐 새 버전을 추가)
2. 되돌리기 어렵다고 가정하고, 위험한 변경은 여러 단계로 쪼갠다
3. 이미 데이터가 있는 DB 에 도입할 땐 `baseline` 으로 현재 상태를 시작점 선언

도입 시 `ddl-auto` 를 `validate` 로 바꾸는 게 **역할 분담의 선언**이다:
Flyway 가 구조 변경의 유일한 주체, Hibernate 는 검사만. 그러면 오늘 같은
"코드엔 있는데 DB 엔 없는" 상태가 **구조적으로 불가능**해진다.

---

## 10. Flyway 도입 실행 — 점검 루프에서 나온 것들

9장의 계획대로 실제 도입했다. **"바꾸고 → 띄우고 → 깨진 걸 고치고 → 다시 띄우는"** 점검 루프를
돌렸는데, 그 과정에서 **숨어 있던 결함 3건**이 드러났다. 이게 도입의 진짜 수확이다.

### 구성

```
backend/src/main/resources/db/migration/
  V1__baseline_schema.sql                      기존 스키마 스냅샷 (38개 테이블)
  V2__add_unique_application_id_on_payments.sql 유니크 제약
  V3__make_payments_matching_id_nullable.sql    드리프트 교정
```

```yaml
flyway:
  enabled: true
  baseline-on-migrate: true   # 기존 DB 는 V1 을 "적용됨" 표시만 하고 건너뜀
  baseline-version: 1
jpa:
  hibernate:
    ddl-auto: validate        # 구조 변경은 Flyway, Hibernate 는 검사만
```

`baseline-on-migrate` 덕분에 **기존 DB 와 신규 DB 가 같은 파일로 다르게 동작**한다.
- 기존 DB(테이블 이미 있음): V1 을 실행하지 않고 "적용됨"으로 기록 → V2, V3 만 실행
- 빈 DB(신규 환경): V1 이 실제로 38개 테이블을 만들고 → V2, V3 순서로 진행

실제 히스토리 테이블:

| version | description | type | success |
|---|---|---|---|
| 1 | << Flyway Baseline >> | BASELINE | ✓ |
| 2 | add unique application id on payments | SQL | ✓ |
| 3 | make payments matching id nullable | SQL | ✓ |

### 발견 1 — 베이스라인이 빈 DB 에서 실패했다 (FK 순서)

`mysqldump` 는 테이블을 **알파벳순**으로 출력한다. 그래서 `ai_review_candidate_audits` 가
아직 만들어지지 않은 `ai_review_candidates` 를 외래키로 참조하면서 터졌다.

```
ERROR 1824 (HY000): Failed to open the referenced table 'ai_review_candidates'
```

기존 DB 에서는 V1 이 아예 실행되지 않으니 **아무도 몰랐을** 문제다. 신규 환경 배포에서만 터진다.
해결은 생성 구간에만 FK 검사를 끄는 것:

```sql
SET FOREIGN_KEY_CHECKS = 0;
-- ... CREATE TABLE 38개 ...
SET FOREIGN_KEY_CHECKS = 1;
```

> **교훈**: 베이스라인은 만들어만 두면 안 되고 **빈 DB 에 실제로 적용해봐야** 한다.

### 발견 2 — H2 테스트가 MySQL 마이그레이션을 실행하려 했다

`@DataJpaTest` 는 H2 임베디드 DB 를 쓰는데, Flyway 를 켜자 H2 에 MySQL 전용 DDL 을 실행하려다
문법 오류로 깨졌다.

```java
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"   // 스키마는 엔티티로부터 생성
})
```

> **교훈**: 마이그레이션 SQL 은 **특정 DB 방언에 묶인다.** 테스트가 다른 DB 를 쓰면 분리해야 한다.

### ★ 발견 3 — `validate` 도 못 잡는 드리프트 (진짜 버그)

`ddl-auto: validate` 로 바꿨는데 앱이 **그냥 떴다.** 그런데 안심하면 안 됐다.

> **`validate` 는 테이블/컬럼 존재와 타입만 검사한다. NOT NULL 여부·유니크 제약·기본값은 안 본다.**

그래서 38개 테이블 × 36개 엔티티를 직접 대조했더니 **1건**이 나왔다.

| 테이블.컬럼 | DB | 엔티티 | 결과 |
|---|---|---|---|
| `payments.matching_id` | `NOT NULL` | `Long` (nullable) | 결제 생성이 **항상 실패** |

`PaymentService.createPayment` 는 `matchingId` 를 세팅하지 않으므로 NULL 로 INSERT 된다.

```
ERROR 1048 (23000): Column 'matching_id' cannot be null
```

`DataInitializer` 도 시드 결제 5건을 `matchingId(null)` 로 저장하므로, **빈 DB 부팅 시 시드 단계에서
먼저 터진다.**

원인은 전형적인 드리프트다. 엔티티는 처음부터 nullable 을 의도했지만
(주석 "처음에는 null" + `linkMatching()` 메서드), DB 컬럼은 과거 `ddl-auto` 가 NOT NULL 로
만들어놓은 뒤 **`update` 모드가 제약을 완화하지 않아 그대로 굳었다.**

수정 방향은 **DB 를 엔티티 의도에 맞추는 것**(V3). 엔티티에 `nullable = false` 를 붙이는 게 아니다.

> **교훈**: `validate` 통과 = 안전이 아니다. `validate` 가 보는 범위를 알고,
> 나머지는 사람이 확인해야 한다.

### 검증 결과

| 항목 | 결과 |
|---|---|
| 전체 테스트 | 236개 전량 통과 |
| 기존 DB 기동 | V2 → V3 자동 적용 후 정상 기동 |
| 빈 DB 에서 V1 적용 | 38개 테이블 생성 확인 |
| 유니크 제약 (V2) | 중복 INSERT → `ERROR 1062` 거부 |
| 드리프트 수정 (V3) | 이전에 실패하던 NULL INSERT 성공 |

---

## 11. 역방향 드리프트 정리 — 같은 병, 다른 증상

6장의 드리프트가 "DB 는 NOT NULL, 엔티티는 nullable"이었다면, 반대 방향도 있다.
**"DB 는 NULL 허용, 엔티티는 값이 항상 있다고 가정"** — 이쪽은 증상이 다르다.

| 방향 | 증상 | 언제 터지나 |
|---|---|---|
| DB NOT NULL ↔ 엔티티 nullable | `Column 'x' cannot be null` | **저장할 때** |
| DB NULL 허용 ↔ 엔티티 non-null | NPE, 또는 기본값이 무시됨 | **읽은 뒤 사용할 때** |

38개 테이블 × 36개 엔티티를 세 유형으로 나눠 다시 훑었다.

| 유형 | 정의 | 위험 | 결과 |
|---|---|---|---|
| **A** | 엔티티가 `int`/`boolean` 등 **primitive** | 조회 자체가 예외 (가장 위험) | **0건** |
| **B** | 엔티티 `@Column(nullable = false)` | 선언과 실제가 다름 | 5건 |
| **C** | `@Builder.Default` 로 기본값 부여 | 기본값이 무시되고 null 유입 | (B 와 같은 5건) |

### ★ 실제 NPE 지뢰 — `matchings.swap_count`

```java
public void swap() {
    this.swapCount++;   // ← Integer 언박싱. null 이면 NPE
}
```

개발 DB 를 열어보니 **`ACCEPTED`(활성) 상태이면서 `swap_count` 가 NULL 인 매칭이 1건** 있었다.
그 멘티가 멘토 교체를 누르면(`MentorSwapService.java:54`) 그 자리에서 죽는다.

```
id | status    | swap_count
 4 | ACCEPTED  | NULL        ← 지뢰
```

### 방어 코드로 위장하고 있던 부채 — `payments.*`

```java
// CurriculumService.java:105
int months = p.getMonthsBundled() != null ? p.getMonthsBundled() : 1;
```

엔티티는 `@Builder.Default private Integer monthsBundled = 1;` 로 "항상 1 이상"이라고 선언한다.
그런데 호출부는 null 을 체크하고 있다. **선언을 믿지 못해서 생긴 우회 코드**다.
이런 방어 코드가 여기저기 쌓이면, 나중엔 아무도 "이 필드가 null 일 수 있나?"를 확신하지 못하게 된다.

### 조치 — 엔티티의 의도에 DB 를 맞춘다

기본값이 선언돼 있다는 건 곧 **"항상 값이 있다"는 의도**다. 그러니 DB 를 그 의도에 맞춘다.

```sql
-- V4: 백필 후 조이기
UPDATE matchings SET swap_count = 0 WHERE swap_count IS NULL;
ALTER TABLE matchings MODIFY COLUMN swap_count INT NOT NULL DEFAULT 0;
```

**순서가 중요하다.** NULL 행이 남아 있으면 `NOT NULL` 로 바꾸는 순간 실패한다.
그래서 반드시 **백필 먼저, 제약 나중**이다.

엔티티에도 `@Column(nullable = false)` 를 붙여 선언과 DB 가 같은 말을 하게 했다.

### 의도적으로 남긴 nullable — 전부 조이는 게 정답은 아니다

| 컬럼 | 남긴 이유 |
|---|---|
| `matchings.trial_end_date` | 체험 시작 시에만 세팅. `isInTrialPeriod()` 가 null 을 **명시적으로 검사**한다 |
| `payments.course_type` | `@Builder.Default` 가 없어 엔티티도 선택 값으로 취급 |
| `applications.is_cs_major` | 미응답/예/아니오 **3-state** 의도일 수 있음 → 제품 결정 사항 |

> **판단 기준**: "값이 없다"가 **의미를 갖는가?** 체험이 시작되지 않았다는 사실은 정보다.
> 반면 "할인 없음"은 NULL 이 아니라 **0** 이 정확한 표현이다.

### ★ Flyway 규칙을 실제로 만났다

`application_rejected_mentors.mentor_id`(컬렉션 원소 컬럼)도 같은 문제였다.
V4 에 넣고 싶었지만 — **V4 는 이미 적용된 뒤였다.**

파일을 고치면 checksum 이 달라져 다음 기동이 막힌다. 그래서 **V5 로 추가**했다.

```
V4__fix_reverse_nullability_drift.sql          (적용됨 — 수정 불가)
V5__not_null_on_element_collection_column.sql  (새로 추가)
```

> 마이그레이션은 **forward-only** 다. 고칠 일이 생기면 되돌리는 게 아니라 앞으로 나아간다.
> 문서로 읽을 땐 규칙이지만, 직접 겪으면 몸에 남는다.

### 최종 상태

| version | description |
|---|---|
| 1 | << Flyway Baseline >> |
| 2 | add unique application id on payments |
| 3 | make payments matching id nullable |
| 4 | fix reverse nullability drift |
| 5 | not null on element collection column |

전체 테스트 통과, 기존 DB·빈 DB 양쪽 기동 확인.

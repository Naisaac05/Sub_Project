# Flyway 도입 중 드러난 스키마 결함 3건 (베이스라인 FK 순서 / H2 테스트 / matching_id 드리프트)

- 발생 일시: 2026-07-27
- 영역: backend / DB / infra
- 심각도: high (결제 생성이 항상 실패하는 버그 포함)

## 증상

`ddl-auto: update` 가 기존 컬럼에 유니크 제약을 추가하지 않는 문제
(`error/2026-07-27-payment-idempotency-and-application-id-unique.md`)를 근본 해결하려
Flyway 를 도입했다. 도입 과정에서 **기존에 숨어 있던 결함 3건**이 연달아 드러났다.

1. 베이스라인 SQL 을 빈 DB 에 적용하면 외래키 오류로 실패한다.
2. `@DataJpaTest`(H2) 가 컨텍스트 로딩 단계에서 깨진다.
3. **결제 생성이 항상 실패한다** — `Column 'matching_id' cannot be null`.

## 원인

### 1) 베이스라인 FK 순서

`mysqldump` 는 테이블을 **알파벳순**으로 출력한다. `ai_review_candidate_audits` 가 아직 생성되지
않은 `ai_review_candidates` 를 FK 로 참조하면서 실패했다.

```
ERROR 1824 (HY000): Failed to open the referenced table 'ai_review_candidates'
```

기존 DB 에서는 `baseline-on-migrate` 로 V1 이 **실행되지 않으므로** 아무도 몰랐다.
신규 환경(빈 DB) 배포에서만 터지는 종류의 결함이다.

### 2) H2 테스트가 MySQL 마이그레이션을 실행

`@DataJpaTest` 는 H2 임베디드 DB 를 쓴다. Flyway 를 켜자 MySQL 전용 DDL 인 V1 을 H2 에
실행하려다 문법 오류가 났다. 마이그레이션 SQL 은 특정 DB 방언에 묶인다는 점을 놓쳤다.

### 3) ★ `payments.matching_id` 드리프트 (진짜 버그)

`ddl-auto: validate` 로 전환했는데 **앱이 그냥 떴다.** 그러나 `validate` 는
**테이블/컬럼 존재와 타입만 검사하고 nullability·유니크 제약·기본값은 보지 않는다.**

38개 테이블 × 36개 엔티티를 전수 대조한 결과 드리프트 1건을 발견했다.

| 대상 | DB | 엔티티 |
|---|---|---|
| `payments.matching_id` | `NOT NULL` | `Long matchingId` (nullable) |

`PaymentService.createPayment` 는 `Payment.builder()` 에 `matchingId` 를 넣지 않으므로
**결제 생성이 항상 실패**한다. `DataInitializer` 도 시드 결제를 `matchingId(null)` 로 저장한다.

엔티티는 처음부터 nullable 을 의도했다 —
`backend/src/main/java/com/devmatch/entity/Payment.java:28`("결제 후 추천→선택 완료 시 세팅됨,
처음에는 null") + `linkMatching(Long)` 메서드. 그런데 DB 컬럼은 과거 `ddl-auto` 가 NOT NULL 로
만든 뒤 `update` 모드가 제약을 완화하지 않아 그대로 굳었다. 즉 **DB 쪽이 잘못 굳은 것**이다.

## 해결 방법

**Flyway 도입**
- `backend/build.gradle:56` — `flyway-core`, `flyway-mysql` 추가
- `backend/src/main/resources/application.yml:12` — `flyway.baseline-on-migrate: true`,
  `baseline-version: 1`
- `backend/src/main/resources/application.yml:24` — `ddl-auto` 를 `update` → `validate`
  (구조 변경은 Flyway 담당, Hibernate 는 검사만)

**마이그레이션 3종** (`backend/src/main/resources/db/migration/`)
- `V1__baseline_schema.sql` — 기존 38개 테이블 스냅샷. 생성 구간에만 `SET FOREIGN_KEY_CHECKS = 0`
  으로 FK 순서 문제 해결 (**결함 1 수정**)
- `V2__add_unique_application_id_on_payments.sql` — 유니크 제약 (수동 DDL 을 대체)
- `V3__make_payments_matching_id_nullable.sql` — `ALTER TABLE payments MODIFY COLUMN
  matching_id BIGINT NULL` (**결함 3 수정**)

**테스트 격리** (**결함 2 수정**)
- `backend/src/test/java/com/devmatch/repository/AiReviewMessageRepositoryTest.java:19` —
  `@DataJpaTest(properties = {"spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop"})`

**회귀 테스트 추가**
- `backend/src/test/java/com/devmatch/service/PaymentCreationIntegrationTest.java` (신규) —
  실제 DB 왕복으로 `createPayment` 를 호출한다. 단위 테스트는 리포지토리를 모킹하므로
  스키마 드리프트를 못 잡는다. 이 테스트는 V3 이전이었다면 실패한다.

## 검증

| 항목 | 결과 |
|---|---|
| 전체 테스트 | **BUILD SUCCESSFUL** (회귀 테스트 2건 포함) |
| 기존 DB 기동 | V2 → V3 자동 적용, 정상 기동 |
| **빈 DB 신규 배포** | V1 → V2 → V3 전체 적용 후 정상 기동(19.7초), 39개 테이블 생성 |
| 유니크 제약(V2) | 중복 INSERT → `ERROR 1062` 거부 |
| 드리프트 수정(V3) | 이전에 `ERROR 1048` 로 실패하던 NULL INSERT 성공 |

개발 DB 최종 히스토리:

| version | description | type |
|---|---|---|
| 1 | << Flyway Baseline >> | BASELINE |
| 2 | add unique application id on payments | SQL |
| 3 | make payments matching id nullable | SQL |

## 재발 방지 / 메모

**`validate` 통과 = 안전이 아니다.** Hibernate 의 `validate` 가 검사하는 범위:

| 검사함 | 검사 안 함 |
|---|---|
| 테이블 존재, 컬럼 존재, 컬럼 타입 | **nullability**, 유니크 제약, 기본값, 인덱스 |

오른쪽 항목은 사람이 확인해야 한다. 특히 nullability 드리프트는 **런타임 INSERT 실패**로
이어지므로, 엔티티를 실제 DB 에 저장하는 통합 테스트가 최소 하나는 있어야 한다.

**앞으로 스키마를 바꿀 때**
- 엔티티만 고치면 반영되지 않는다. **반드시 `V{n}__*.sql` 마이그레이션을 함께 추가**한다.
- **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다** (checksum 검증으로 기동이 막힌다).
  고칠 일이 생기면 새 버전을 추가한다.
- 새 마이그레이션은 **빈 DB 에서도** 검증한다 (`CREATE DATABASE` → 앱 기동).

**prod 배포 시**
- `application-prod.yml` 의 `ddl-auto` 는 이미 `validate` 이고, Flyway 설정은 `application.yml`
  에서 상속된다. 별도 수동 DDL 은 더 이상 필요 없다.
- prod DB 는 기존 데이터가 있으므로 `baseline-on-migrate` 로 V1 은 건너뛰고 V2, V3 가 적용된다.
- 롤백이 필요하면 마이그레이션을 되돌리는 게 아니라 **새 버전으로 앞으로 고친다**(forward-only).

**남은 것**: 역방향 드리프트(DB 는 nullable 인데 엔티티에 기본값이 있는 경우 —
`matchings.swap_count`, `payments.course_type`, `payments.months_bundled` 등)는 INSERT 실패로
이어지지 않아 이번 범위 밖으로 뒀다. 의도적인지 확인해둘 가치는 있다.

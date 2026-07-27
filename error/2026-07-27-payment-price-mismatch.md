# 결제 플랜 표시가와 백엔드 청구액이 약 111만원 차이 (가격 정책이 두 곳에 따로 존재)

- 발생 일시: 2026-07-27
- 영역: backend / frontend
- 심각도: high

## 증상

결제 플랜 카드에는 4,680,000원(즉시 시작) / 4,580,000원(얼리버드 1차) / 4,480,000원(얼리버드 2차)이
표시되는데, 백엔드 가격 정책 엔진이 계산하는 금액은 세 플랜 모두 3,564,000원이었다. 약 111만원 차이.

또한 결제 페이지는 백엔드를 아예 거치지 않고 프론트에 하드코딩된 금액을 그대로 토스 위젯에 넣고,
`orderId` 마저 `Math.random()` 으로 직접 만들고 있었다. 그래서 서버에는 해당 주문이 존재하지 않았고,
`PaymentService.confirmPayment` 의 금액 검증(요청 금액 == 서버가 확정한 금액)은 애초에 탈 수 없는 코드였다.

## 원인

가격 정책이 **서로 모르는 두 곳에 각각 구현**되어 있었다.

1. 프론트 `frontend/src/lib/course-catalog.ts` 의 `getEnrollmentPlans()` 가 플랜별 금액을 리터럴로 보유
2. 백엔드 `backend/src/main/java/com/devmatch/service/PaymentService.java` 가 `월 단가 × 개월 수 − 묶음 할인` 으로 계산

두 계산은 애초에 같은 값이 나올 수 없었다. 백엔드 정책에는 **플랜(수강 방식) 개념 자체가 없었기** 때문이다.
`getUnitPrice(renewalCount)` 와 `getBundleDiscountRate(months)` 는 연장 회차와 개월 수만 보므로,
얼리버드 차수에 따라 10만원씩 깎이는 프론트의 차등가를 표현할 방법이 없었다.
`Payment.courseType` 컬럼은 존재했지만 저장만 될 뿐 가격 계산에 전혀 쓰이지 않았다.

즉 "한쪽 숫자가 오타"인 문제가 아니라, **단일 기준(single source of truth)이 없는 구조 문제**였다.

## 해결 방법

제품 결정으로 **프론트 표시가(4,680,000 / 4,580,000 / 4,480,000)를 정답으로 채택**하고,
백엔드가 그 금액을 계산할 수 있도록 플랜 개념을 추가한 뒤, 프론트에서는 가격을 완전히 제거했다.

### 1. 백엔드에 플랜 개념 추가

- `backend/src/main/java/com/devmatch/entity/EnrollmentPlan.java` (신규)
  `IMMEDIATE(0)` / `EARLY_BIRD_1(25,000)` / `EARLY_BIRD_2(50,000)` — 값은 **월 단위** 얼리버드 할인액.
  월 단위로 정의해야 묶음 개월 수가 달라져도 할인 폭이 비례한다 (1개월 결제에 4개월치 할인이 붙지 않음).

- `backend/src/main/java/com/devmatch/service/PaymentService.java:34-37`
  기준 단가를 990,000 → **1,300,000** 으로 조정. 4개월 묶음 10% 할인을 적용하면
  `1,300,000 × 4 × 0.9 = 4,680,000` 으로 즉시 시작 플랜 표시가와 정확히 일치한다.
  연장 사다리도 기존 정책 의도(1회 동가 / 2회 −10% / 3회+ −20%)를 유지하도록 함께 스케일했다
  (1,300,000 / 1,300,000 / 1,170,000 / 1,040,000).

- 최종 계산식: `정가(단가 × 개월) − 묶음 할인 − 플랜 할인(월 할인액 × 개월)`

- `PaymentService.createPayment` 가 금액을 **직접 다시 계산하지 않고** `calculatePricing()` 을 호출하도록 변경.
  미리보기와 청구 경로가 물리적으로 같은 코드를 타므로 둘이 갈라질 수 없다.
  플랜 파싱 실패 시 저장소 접근 전에 `PaymentFailedException` 으로 막는다.

### 2. 가격 미리보기 엔드포인트

- `backend/src/main/java/com/devmatch/controller/PaymentController.java` — `GET /api/payments/pricing?months=4`
- `backend/src/main/java/com/devmatch/dto/payment/PlanPricingResponse.java` (신규)
- `backend/src/main/java/com/devmatch/config/SecurityConfig.java:52-53` — GET 만 `permitAll`
  (멘토 상세 / 결제 페이지가 로그인 전에도 카드 가격을 그려야 하므로)
- 비로그인(`userId == null`)이면 저장소를 조회하지 않고 연장 회차 0 기준가를 반환한다.

### 3. 프론트에서 가격을 완전히 제거

- `frontend/src/lib/course-catalog.ts` — `getEnrollmentPlans()` 는 이제 **표시 정보만**
  (차수 이름 / 마감 배지 / 설명) 반환한다. `originalPrice`, `price`, `monthly` 필드 삭제.
- `frontend/src/lib/payment.ts` (신규) — `fetchPlanPricing()`, `createPayment()`, `confirmPayment()`
- `frontend/src/lib/use-enrollment-plans.ts` (신규) — 표시 정보 + 서버 가격을 합쳐 카드 목록 생성.
  가격을 못 받아오면 금액을 **추측해서 표시하지 않고** "가격 문의" 를 노출한다.
- `frontend/src/app/apply/payment/page.tsx` — `POST /api/payments` 로 결제를 생성하고
  서버가 돌려준 `orderId` / `amount` 를 토스 위젯과 결제 버튼에 사용. 서버 가격을 못 받으면 결제 버튼 비활성.
- `frontend/src/app/mentors/[id]/page.tsx` — 같은 훅으로 전환.

### 4. 테스트로 고정

- `backend/src/test/java/com/devmatch/service/PaymentServiceTest.java` (신규, 18개)
  플랜별 기대 금액(4,680,000 / 4,580,000 / 4,480,000), 할인 분해, 개월 수 변화,
  연장 회차 사다리, `createPayment` 저장 금액, 알 수 없는 플랜 거부,
  그리고 `toss-confirm-enabled` 플래그 동작(아래 5번)을 검증.
- `backend/src/test/java/com/devmatch/controller/PaymentPricingControllerTest.java` (신규, 3개)
  비로그인 공개 접근, 로그인 시 연장 회차 반영, `months` 기본값 4.

## 재발 방지 / 메모

- **가격 리터럴은 프론트에 두지 않는다.** 이번 버그의 근본 원인은 숫자가 틀린 게 아니라
  숫자가 두 곳에 있었다는 것이다. `course-catalog.ts` 에 가격 필드를 되살리지 말 것.
- 가격 정책을 바꿀 때는 `PaymentService` 의 상수와 `PaymentServiceTest` 의 기대 금액을 **함께** 갱신한다.
  프론트는 손댈 필요가 없다 (서버 값을 그대로 그림).
- 프론트에는 테스트 하네스가 없어서(jest/vitest 미설치) 표시 금액을 테스트로 고정하지는 못했다.
  대신 프론트에서 계산 가능한 가격 소스를 아예 없애는 방식으로 구조적으로 막았다.

### 5. 승인 경로 연결 (같은 날 후속 작업)

처음 수정 직후에는 **승인 경로가 여전히 `PaymentService.confirmPayment` 를 타지 않아**
서버의 금액 대조 검증이 실행되지 않는 상태로 남아 있었다.
`frontend/src/app/payment/success/page.tsx` 가 토스의 `paymentKey`/`orderId`/`amount`
쿼리 파라미터를 무시하고 매칭 생성 API 만 호출했기 때문이다.

바로 연결하지 못한 이유는 **승인 경로에 킬 스위치가 없었다**는 점이다.
환불은 `toss-cancel-enabled` 로 외부 호출을 막을 수 있는데,
`app.payment.toss-confirm-enabled` 라는 설정은 코드베이스에 존재하지 않았다 (grep 0건).
그대로 연결하면 학생 포트폴리오 정책(실제 토스 API 호출 금지)을 위반하게 된다.

그래서 환불 쪽과 같은 구조로 플래그를 먼저 만들고 연결했다.

- `backend/src/main/java/com/devmatch/config/TossConfirmProperties.java` (신규)
  `TossCancelProperties` 와 동일한 `app.payment` 프리픽스 / 기본 false.
- `backend/src/main/java/com/devmatch/service/PaymentService.java` — 플래그가 꺼져 있으면
  토스 호출을 건너뛰고 내부 상태만 CONFIRMED 로 전이하되,
  `paymentKey` 를 **`MOCK-` 접두사**로 저장해 실제 승인 건과 구분할 수 있게 했다
  (`PaymentService.MOCK_PAYMENT_KEY_PREFIX`).
- `application.yml` / `application-prod.yml` — `toss-confirm-enabled: ${TOSS_CONFIRM_ENABLED:false}`
- `frontend/src/app/payment/success/page.tsx` — 매칭 생성 **전에**
  `POST /api/payments/confirm` 을 호출하도록 순서를 잡았다.

**중요: 금액 검증은 플래그와 무관하게 항상 수행된다.** 금액 대조는 토스 호출보다 먼저 일어나므로,
플래그가 꺼진 상태에서도 "요청 금액 ≠ 서버가 확정한 금액"이면 결제는 FAILED 로 전이하고 거절된다.
이 동작을 `PaymentServiceTest.금액이_다르면_플래그가_꺼져_있어도_승인되지_않는다` 로 고정했다.

#### 기본값을 false 로 정한 이유

일반적인 관점에서는 "설정을 깜빡하면 무료 수강이 되는" 위험 때문에 기본값 true 가 안전해 보인다.
하지만 이 프로젝트는 `application-prod.yml` 에 **"학생 포트폴리오 정책: 기본 false"** 가 명시돼 있고
prod 조차 `toss-cancel-enabled: false` 다. 즉 "설정을 깜빡한 상태"가 아니라 그게 의도된 운영 상태다.
`MOCK-` 접두사가 있으면 실제로 돈이 들어온 건을 언제든 구분할 수 있으므로,
기본 false + MOCK 표시 조합이 이 프로젝트에 맞다.

# Payment confirm auto match and application phone

- 발생 일시: 2026-07-10
- 영역: backend / frontend
- 심각도: medium

## 증상
멘토-멘티 매칭 흐름을 확인하던 중 두 가지 누락을 발견했다.

1. 서버의 Toss 결제 확정 경로가 결제 상태만 확정하고 신청서 결제 확정 및 자동 매칭 생성을 호출하지 않았다.
2. 신청서 입력에는 연락처가 포함되어 있었지만, 저장/응답/멘토 상세 모달 경로에서 연락처가 이어지지 않아 멘토가 신청서의 주요 연락 정보를 확인할 수 없었다.

## 원인
프론트 결제 성공 페이지가 별도로 신청서 결제 확정 API를 호출하는 흐름에 의존하고 있어, `PaymentService.confirmPayment()` 자체에는 신청서 확정 위임이 빠져 있었다.

또한 `ApplicationRequest.phone`과 `Application.phone` 필드는 있었지만, `ApplicationService.submitApplication()` 빌더와 `ApplicationResponse` 변환 DTO에 `phone` 매핑이 누락되어 응답까지 전달되지 않았다.

## 해결 방법
- 결제 확정 성공 시 신청서 결제 확정 및 자동 매칭 생성까지 이어지도록 `PaymentService`에 `ApplicationService`를 주입하고 `applicationService.confirmPayment(userId, payment.getApplicationId())`를 호출했다. 관련 파일: `backend/src/main/java/com/devmatch/service/PaymentService.java:32`, `backend/src/main/java/com/devmatch/service/PaymentService.java:141`
- 신청서 제출 저장 및 응답 변환에 연락처 매핑을 추가했다. 관련 파일: `backend/src/main/java/com/devmatch/service/ApplicationService.java:55`, `backend/src/main/java/com/devmatch/service/ApplicationService.java:153`, `backend/src/main/java/com/devmatch/dto/application/ApplicationResponse.java:39`
- 프론트 신청서 응답 타입과 멘토 매칭 상세 모달에 연락처 표시를 추가했다. 관련 파일: `frontend/src/lib/types.ts:285`, `frontend/src/app/matching/page.tsx:202`
- 회귀 테스트를 추가해 신청서 연락처 응답과 결제 확정 자동 매칭 위임을 고정했다. 관련 파일: `backend/src/test/java/com/devmatch/service/ApplicationServiceTest.java:36`, `backend/src/test/java/com/devmatch/service/PaymentServiceTest.java:31`

## 재발 방지 / 메모
결제 성공 후 매칭처럼 도메인 상태가 함께 전이되는 흐름은 프론트의 후속 호출에만 의존하지 말고, 서버의 결제 확정 유스케이스 안에서 한 번에 보장해야 한다. 신청서 필드를 추가할 때는 request/entity/response/frontend type/detail view를 한 세트로 확인한다.

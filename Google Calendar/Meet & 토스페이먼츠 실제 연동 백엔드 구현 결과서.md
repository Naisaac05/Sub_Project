# Google Calendar/Meet & 토스페이먼츠 실제 연동 백엔드 구현 결과서

> **담당자**: 본인
> **작업 일시**: 2026-04-02
> **브랜치**: `isc/feature/front` (팀원의 Phase 2, 3 코드와 통합 예정)

---

## 1. 개요
팀원이 설계 및 스텁(Stub) 처리해둔 Phase 4(Google Calendar & Meet)와 Phase 5 일부(토스페이먼츠) 파트를 맡아 **실제 API 연동 로직**과 **관련 도메인 클래스(Entity, Repository, DTO 등) 일체**를 구현 완료했습니다. 

팀원들이 만들고 있는 다른 기능(인증/인가, 문제 매칭, 커뮤니티 등)을 침범하지 않도록 철저히 격리(Isolation)하여 코드를 작성했습니다.

---

## 2. 주요 구현 내용

### 📌 A. Google Calendar & Google Meet 자동 연동 (Phase 4 영역)
멘티와 멘토 매칭이 완료되어 일정을 잡을 때, 양측의 Google Calendar에 일정을 자동 등록하고 원격 화상 멘토링을 위한 Google Meet 링크를 발급하는 로직입니다.

- **GoogleCalendarConfig**: 
  - `google-credentials.json`을 성공적으로 불러올 수 없을 때를 대비해 스텁(Stub) 모드로 안전하게 폴백(Fallback)하도록 설정.
- **GoogleCalendarService**: 
  - `createMentoringEvent()`: 멘토와 멘티 이메일을 `attendees`에 추가하고 `ConferenceData`를 이용해 화상 링크(Meet) 생성 확인.
- **SessionService & Controller**: 
  - `/api/sessions` API 전반 (세션 예약 생성 시도, 본인 세션 취소 로직 등).
- **Entities**: `MentoringSession`, `MentorAvailability` 및 관련 Enum/Repository.

### 📌 B. 토스페이먼츠 서버 연동 (Phase 5 파트 영역)
클라이언트가 토스 위젯으로 결제를 진행한 뒤 토스 측 서버에 확실하게 **결제 승인**을 요청하고, 결제 취소 사유 발생 시 환불을 요청하는 핵심 서버 로직입니다.

- **TossPaymentConfig**: 
  - 토스 시크릿키를 이용해 Base64 `Basic Auth` 형태의 토스용 Authorization HTTP Header를 자동 매핑.
- **TossPaymentService**: 
  - `confirmPayment()`: API 통신 성공 여부 파악 및 실패 시 서버 에러 로그 기록.
  - `cancelPayment()`: 취소 사유 포함하여 실제 카드 결제 단 등을 환불 요청.
- **PaymentService & Controller**: 
  - `/api/payments` API 전반 (결제 요청 식별자 `orderId` 생성 방어 및 최종 금액 비교 등 횡령/결제 변조 검증 로직 적용).
- **Entities**: `Payment`, `PaymentStatus` 및 관련 Repository.

---

## 3. 팀원 코드와의 충돌 방어 전략

1. **Phase 2 스텁 의존성 보호** 
   - 에러가 나지 않도록 팀원의 Phase 2 문서에 명시된 형태만 띄는 비어있는 클래스(`User`, `Role`, `ApiResponse` 등)를 생성해놓아 우리 측 코드만 완벽하게 컴파일 시킬 수 있게 조치했습니다.
2. **독자적인 파일 사용**
   - 기존 팀원이 건드려야 할 `Matching`, `Auth`, `Post` 파트 등은 단 1줄의 수정도 가하지 않아, 향후 병합(Merge) 과정에서 매우 스무스하게 넘어갈 수 있습니다.
3. **독립된 예외 핸들링**
   - 전국의 모든 에러를 담당할 `GlobalExceptionHandler`에 제가 만든 예외 패키지만 깔끔하게 추가하여 에러 코드 포맷팅을 통일했습니다.

---

## 4. 향후 남은 작업 및 테스팅

- [ ] **Google Cloud API Console 설정**: `google-credentials.json`을 다운로드 후 `resources/`에 넣기
- [ ] **토스 API 설정**: `application.yml` 내 토스 테스트 시크릿 키 설정
- [ ] 팀원이 `Matching` 엔티티와 비즈니스 서비스(Phase 3) 등 작성을 완료하면 멘토링 세션 로직 안에 하드코딩된 Mentee/Mentor ID 추출 부분 교체

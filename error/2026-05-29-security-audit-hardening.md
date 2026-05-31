# 보안 감사 하드닝 — 인증/IDOR/결제/설정 10건

- 발생 일시: 2026-05-29
- 영역: backend
- 심각도: high (CRITICAL 2건 포함)

> 다차원 보안 감사(Dynamic Workflow)로 발견 → 각 발견을 적대적 검증으로 거른 뒤 수정. 별도 `security-hardening` 브랜치(main 기준)에서 작업.

## 증상

런타임 장애로 드러난 적은 없으나, 코드베이스 전반에 다음과 같은 익스플로잇 가능한 결함이 잠재해 있었다.

- 특정 이메일/문자열만 알면 비밀번호 검증 없이 로그인 가능
- 일반 사용자가 남의 신청서를 결제완료 처리하거나 실제 환불을 자가 집행 가능
- 남의 멘토링 세션 화상회의 URL을 임의로 덮어쓰기(피싱 링크 주입) 가능

## 원인

| # | 심각도 | 위치 | 원인 |
|---|---|---|---|
| ① | CRITICAL | `service/AuthService.java` (login) | `ganada@devmatch.com`/`password123` 하드코딩 비밀번호 우회. 모든 프로파일(prod 포함)에 컴파일됨 |
| ② | CRITICAL | `service/PaymentService.java` `cancelPayment` | 사용자向 결제취소가 `toss-cancel-enabled` 플래그·관리자 권한·감사로그를 모두 우회하고 실제 Toss 환불 직접 호출 |
| ③ | high | `controller/ApplicationController.java` confirm-payment | 소유권 검사 부재 → 아무 신청서나 결제완료 처리 + 매칭 트리거 (IDOR) |
| ④ | high | `controller/ApplicationController.java` submit | 신청자를 요청 본문 `menteeId`로 결정 → 사칭 |
| ⑤ | high | `controller/VideoMeetingController.java` | 세션 소유권 검사 부재 → 남의 세션 Meet URL 덮어쓰기/유출 (IDOR) |
| ⑥ | high | `controller/InternalAiReviewCandidateCaptureController.java` | "internal" 캡처 엔드포인트가 서비스 토큰 미검증 → 지식베이스 오염 |
| ⑦ | medium | `service/AuthService.java` `changePassword`, `service/UserService.java` | 비밀번호 변경 시 기존 refresh 세션 미폐기 |
| ⑧⑩ | medium | `application-prod.yml`, `application.yml:68` | prod에서 `REFRESH_COOKIE_SECURE` 미설정 시 Secure 없이 refresh 쿠키 발급, fail-fast 부재 |
| ⑨ | medium | `service/PaymentService.java` `cancelPayment` | 외부 호출 후 DB 보상 처리/감사 부재 (②와 동일 경로) |

> 참고: 적대적 검증이 **반증 2건**(CSRF 비활성 — 헤더 기반 JWT라 구조적으로 안전 / 약한 기본 JWT 시크릿 — prod는 fallback 없어 부팅 실패로 차단)을 거짓 양성으로 제거했다.

## 해결 방법

- **①** [`AuthService.java:68`](../backend/src/main/java/com/devmatch/service/AuthService.java) 우회 로직 삭제 → 일반 `passwordEncoder.matches` 경로로 통일. 데모 계정은 [`devmatch-data-only.sql:552`](../backend/data/devmatch-data-only.sql)에서 `password123`의 bcrypt 해시로 시드 교체(데모 로그인 유지). 해시는 `bcrypt.checkpw` 로 매칭 확인.
- **②⑨** [`PaymentService.java`](../backend/src/main/java/com/devmatch/service/PaymentService.java) `cancelPayment` 및 [`PaymentController.java`](../backend/src/main/java/com/devmatch/controller/PaymentController.java) `/cancel` 엔드포인트 제거. 환불은 [`AdminPaymentService.refundPayment`](../backend/src/main/java/com/devmatch/service/AdminPaymentService.java) (플래그+행잠금+AdminAuditLog)로만. 프론트 호출자 0건이라 무위험.
- **③④** [`ApplicationController.java`](../backend/src/main/java/com/devmatch/controller/ApplicationController.java)에 `@AuthenticationPrincipal` 추가, [`ApplicationService.java`](../backend/src/main/java/com/devmatch/service/ApplicationService.java) `submitApplication(userId, ...)`/`confirmPayment(userId, ...)` 로 변경 — 신청자는 JWT에서, confirm은 소유권 검사.
- **⑤** [`VideoMeetingService.java`](../backend/src/main/java/com/devmatch/service/VideoMeetingService.java) `assertParticipant`로 세션 멘토/멘티만 접근. 컨트롤러에 `@AuthenticationPrincipal` 주입.
- **⑥** [`InternalAiReviewCandidateCaptureController.java`](../backend/src/main/java/com/devmatch/controller/InternalAiReviewCandidateCaptureController.java)에서 `X-AI-Service-Token` 상수시간 검증(파이썬 `app/security.py`와 대칭). [`SecurityConfig.java`](../backend/src/main/java/com/devmatch/config/SecurityConfig.java)는 `/api/internal/**`를 permitAll(토큰 게이트로 전환).
- **⑦** [`AuthService.java`](../backend/src/main/java/com/devmatch/service/AuthService.java) `changePassword` + [`UserService.java`](../backend/src/main/java/com/devmatch/service/UserService.java) `updateMyProfile`에서 비밀번호 변경 시 `refreshSessionService.revokeAllForUser(userId)`.
- **⑧⑩** [`application-prod.yml`](../backend/src/main/resources/application-prod.yml)에 `app.auth.refresh-cookie.secure: true` 고정.

검증: `gradlew compileJava compileTestJava` + 전체 `gradlew test` 통과, bcrypt 해시 매칭 확인.

## 재발 방지 / 메모

- **잔여 리스크 — 회귀 테스트 부재**: 변경한 IDOR/인증 경로(application·video-meeting·payment-cancel·internal-token)는 기존 테스트가 전무했다. "권한 없는 호출 → 403", "남의 id → 거부" 회귀 테스트 추가 권장(미작성). 이게 없으면 다음 사람이 소유권 검사를 실수로 제거해도 green.
- **⑥ 토큰 미설정(dev)**: 로컬에서 `AI_REVIEW_SERVICE_TOKEN` 미설정이면 검증을 건너뛴다(파이썬과 동일). 운영은 prod validator가 토큰 미설정을 부팅 차단하므로 안전.
- **데모 계정 비밀번호**: `ganada@devmatch.com` / `password123` (이제 정상 bcrypt 검증). 시드 재적재 시 새 해시가 반영되어야 데모 로그인 동작.
- **머지 주의**: `ApplicationController`, `AiReviewProductionConfigValidator`는 `isc/feature/ai_review_test_session_reset` 브랜치에서도 수정됨 → 두 브랜치 머지 시 이 2개 파일에 소규모 충돌 예상.

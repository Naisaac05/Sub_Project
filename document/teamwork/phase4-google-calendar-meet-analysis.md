# Phase 4: Google Calendar & Meet 연동 필요성 분석

> 작성일: 2026-04-04 | 프로젝트: DevMatch | 참고: https://f-lab.kr/

---

## 1. 분석 목적

ROADMAP Phase 4에서 Google Calendar & Meet 연동을 계획했으나, F-Lab 실제 운영 방식을 조사하여 이 기능이 정말 필요한지 검토합니다.

---

## 2. F-Lab 실제 멘토링 운영 방식 (조사 결과)

### 2.1 멘토링 세션 진행

F-Lab Java Backend 코스 페이지에서 확인한 내용:

| 항목 | F-Lab 실제 방식 |
|------|----------------|
| 세션 형태 | **화상 멘토링** (주 1회 1시간) |
| 일정 선택 | 요일/시간 선택 가능 |
| 일정 관리 도구 | **자체 시스템** ("F-Lab 시스템 내부에서 멘토님의 멘토링 가능 시간대를 확인하며 직접 선택") |
| 화상 회의 도구 | **특정 도구 미공개** (Google Meet이라는 언급 없음) |
| 실시간 소통 | **Slack** (메신저 상시 소통) |
| 코드 리뷰 | **GitHub** (상시 코드 리뷰) |
| 세션 기록 | **녹음본/스크립트** 평생 제공 |
| LMS | **자체 LMS** 운영 (사전 학습 자료 제공, 채용 제휴 시스템 포함) |

### 2.2 핵심 발견

- F-Lab은 **Google Calendar를 사용하지 않음** → 자체 일정 시스템 운영
- F-Lab은 **Google Meet를 사용하지 않음** → 화상 멘토링이라고만 언급, 구체적 도구 비공개
- F-Lab의 경쟁력은 외부 API 연동이 아니라 **자체 시스템 + Slack + GitHub** 조합

---

## 3. DevMatch 현재 구현 상태 (백엔드)

### 3.1 이미 구현된 기능

| 기능 | 파일 | API | 상태 |
|------|------|-----|------|
| 멘토 가용 시간 관리 | AvailabilityService, AvailabilityController | `POST/GET/DELETE /api/availability` | 완료 |
| 멘토링 세션 관리 | SessionService, SessionController | `POST/GET/PUT /api/sessions` | 완료 |
| Google Calendar 연동 | GoogleCalendarService | 세션 생성 시 호출 | **스텁 모드** (실제 연동 안됨) |
| 토스페이먼츠 결제 | PaymentService, TossPaymentService | `POST/GET /api/payments` | 완료 |

### 3.2 GoogleCalendarService 현재 상태

```java
// GoogleCalendarService.java - 현재 스텁 모드
public GoogleCalendarService(@Nullable Calendar calendar) {
    this.calendar = calendar;
    if (calendar == null) {
        log.info("[GoogleCalendar] 스텁 모드로 초기화됨 — credentials 없이 동작합니다.");
    }
}
```

- Google Cloud Console 설정 전이므로 로그만 기록하고 null 반환
- 세션 생성은 Google API 실패와 무관하게 정상 동작하도록 설계됨
- meetLink 필드는 nullable로 설계되어 있음

---

## 4. Google Calendar/Meet 연동의 문제점

### 4.1 F-Lab과의 괴리

| 비교 항목 | F-Lab | DevMatch (현재 계획) |
|-----------|-------|---------------------|
| 일정 관리 | 자체 시스템 | Google Calendar 의존 |
| 화상 회의 | 미공개 (자체/Zoom 추정) | Google Meet 의존 |
| 핵심 도구 | Slack + GitHub | Google API 연동 |

F-Lab을 참고한 프로젝트에서 F-Lab이 사용하지 않는 도구를 연동하는 것은 방향이 맞지 않습니다.

### 4.2 기술적 비용

| 항목 | 비용/리스크 |
|------|-----------|
| Google Cloud Console 프로젝트 설정 | OAuth 동의 화면, API 활성화, 검토 과정 필요 |
| OAuth2 credentials 관리 | Client ID/Secret 발급, 환경변수 관리 |
| 사용자별 토큰 관리 | Access/Refresh Token 저장, 갱신 로직 |
| Google API 할당량 제한 | Calendar API 일일 호출 제한 (무료 계정) |
| 에러 처리 | 네트워크 실패, 토큰 만료, API 변경 대응 |
| 테스트 환경 구성 | 실제 Google 계정 필요, Mock 처리 복잡 |

### 4.3 사용자 경험 문제

- 멘티/멘토 모두 **Google 계정이 필수** → 사용자 진입 장벽
- Google Calendar 권한 요청 → 개인정보 우려
- Google Meet 링크가 **플랫폼 밖으로** 사용자를 이탈시킴

---

## 5. 권장 방향

### 5.1 결론: Google Calendar/Meet 연동 불필요

Google Calendar/Meet 연동 코드(`GoogleCalendarService.java`)는 **스텁 상태를 유지하거나 제거**하고, F-Lab처럼 자체 시스템에 집중합니다.

### 5.2 실제 필요한 것 vs 불필요한 것

| 필요한 것 (F-Lab 기반) | DevMatch 상태 | 우선순위 |
|------------------------|---------------|----------|
| 자체 일정 예약 시스템 | **백엔드 완료**, 프론트 미구현 | 높음 |
| 멘토링 세션 관리 (예약/완료/취소) | **백엔드 완료**, 프론트 미구현 | 높음 |
| 결제 → 매칭 확정 흐름 | **백엔드 완료**, 프론트 미구현 | 높음 |
| 화상 회의 링크 관리 | meetLink 필드 활용 (멘토가 직접 입력) | 중간 |
| Slack/메신저 소통 | 외부 서비스로 대체 가능 | 낮음 |
| GitHub 코드 리뷰 연동 | 외부 서비스로 대체 가능 | 낮음 |

| 불필요한 것 | 이유 |
|------------|------|
| Google Calendar 연동 | F-Lab도 미사용, 자체 시스템으로 충분 |
| Google Meet 자동 생성 | F-Lab도 미사용, meetLink 수동 입력으로 대체 |
| Google OAuth2 토큰 관리 | 유지보수 비용 대비 실익 없음 |

### 5.3 화상 회의 링크 대안

현재 `MentoringSession.meetLink` 필드(nullable, VARCHAR 500)를 그대로 활용합니다.

```
멘토가 세션 생성/수정 시 화상 회의 링크를 직접 입력:
- Zoom 링크: https://zoom.us/j/xxx
- Discord 채널: https://discord.gg/xxx
- Google Meet (멘토 자체 생성): https://meet.google.com/xxx
- 기타 도구: 자유롭게 입력
```

이 방식이 F-Lab과 동일한 접근입니다. 플랫폼이 특정 도구에 종속되지 않습니다.

---

## 6. Phase 4 수정된 진행 방향

### 변경 전 (기존 계획)
```
Phase 4: Google Calendar & Meet 연동
  → Google OAuth2 설정
  → Calendar API 이벤트 생성
  → Meet 링크 자동 생성
  → 프론트엔드 연동
```

### 변경 후 (수정 계획)
```
Phase 4: 결제 + 세션 관리 프론트엔드 구현
  → 결제 페이지 (토스 SDK 연동)
  → 멘토 스케줄 관리 페이지 (가용 시간 설정)
  → 멘토링 세션 예약/관리 페이지
  → 매칭 → 결제 → 세션 생성 전체 흐름 완성
  → 화상 회의 링크는 멘토가 직접 입력
```

### 백엔드 조치

| 파일 | 조치 |
|------|------|
| `GoogleCalendarService.java` | 스텁 유지 (향후 필요 시 실제 연동 가능) |
| `GoogleCalendarConfig.java` | 스텁 유지 |
| `SessionService.java` | 변경 없음 (이미 Google API 실패에도 세션 생성 성공하도록 설계됨) |
| `MentoringSession.meetLink` | 유지 — 멘토가 수동 입력하는 용도로 활용 |

---

## 7. 요약

| 항목 | 결정 |
|------|------|
| Google Calendar 연동 | **불필요** — F-Lab도 자체 시스템 사용 |
| Google Meet 연동 | **불필요** — F-Lab도 미사용, meetLink 수동 입력으로 대체 |
| 기존 스텁 코드 | **유지** — 제거할 필요 없이 스텁 모드로 동작 |
| Phase 4 핵심 작업 | **프론트엔드** 결제/세션/스케줄 페이지 구현 |
| 화상 회의 | 멘토가 원하는 도구의 링크를 직접 입력 (Zoom, Discord 등) |
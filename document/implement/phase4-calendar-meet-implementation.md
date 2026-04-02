# Phase 4: Google Calendar & Meet 연동 — Backend 구현 결과서

> 구현일: 2026-04-02 | 프로젝트: DevMatch | 기반: 05-phase4-calendar-meet-plan.md

---

## 1. 구현 완료 요약

| 항목 | 수량 |
|------|------|
| Phase 4 신규 Java 파일 | 17개 |
| Phase 4 수정 Java 파일 | 1개 (GlobalExceptionHandler) |
| 신규 API 엔드포인트 | 8개 |
| 전체 Java 파일 (Phase 2+3+4) | 84개 |
| 전체 API 엔드포인트 (Phase 2+3+4) | 25개 |

`gradle compileJava` 빌드 성공 확인 완료.

---

## 2. 신규 파일 구조

```
backend/src/main/java/com/devmatch/
├── entity/
│   ├── SessionStatus.java           ← Enum (SCHEDULED/COMPLETED/CANCELLED)
│   ├── MentoringSession.java        ← 멘토링 세션 Entity
│   └── MentorAvailability.java      ← 멘토 가용 시간 Entity
├── repository/
│   ├── MentoringSessionRepository.java
│   └── MentorAvailabilityRepository.java
├── dto/session/
│   ├── SessionCreateRequest.java
│   ├── SessionResponse.java
│   ├── AvailabilityRequest.java
│   └── AvailabilityResponse.java
├── service/
│   ├── SessionService.java          ← 세션 생성/조회/취소/완료
│   ├── AvailabilityService.java     ← 가용 시간 CRUD
│   └── GoogleCalendarService.java   ← Google Calendar 연동 (스텁)
├── controller/
│   ├── SessionController.java       ← /api/sessions
│   └── AvailabilityController.java  ← /api/availability
└── exception/
    ├── SessionNotFoundException.java
    ├── SessionAlreadyExistsException.java
    └── InvalidSessionStateException.java
```

---

## 3. API 엔드포인트 (Phase 4 신규 8개)

### SessionController — `/api/sessions`

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | / | O | 멘토링 세션 생성 (매칭 ACCEPTED 필요) |
| GET | / | O | 내 세션 목록 (멘티+멘토 모두) |
| PUT | /{id}/cancel | O | 세션 취소 |
| PUT | /{id}/complete | O | 세션 완료 처리 |

### AvailabilityController — `/api/availability`

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | / | O | 가용 시간 추가 (멘토용) |
| GET | /me | O | 내 가용 시간 목록 (멘토용) |
| GET | /mentor/{mentorId} | O | 특정 멘토 가용 시간 조회 (멘티용) |
| DELETE | /{id} | O | 가용 시간 삭제 (멘토용) |

---

## 4. 핵심 비즈니스 로직

### 세션 생성 흐름

```
POST /api/sessions { matchingId, sessionDate, startTime, endTime, memo }
  → Matching ACCEPTED 상태 확인
  → 본인 매칭인지 확인
  → 중복 세션 확인
  → MentoringSession 생성 (status: SCHEDULED)
  → GoogleCalendarService.createEvent() 호출
    → 성공: meetLink + calendarEventId 저장
    → 실패: 세션은 유지, meetLink = null
  ← SessionResponse (meetLink 포함)
```

### Google Calendar 연동 설계

- `GoogleCalendarService`는 현재 **스텁(stub) 구현**
- Google Cloud Console 설정 전이므로 로그만 기록하고 null 반환
- 실제 연동 시 메서드 내부만 교체하면 됨 (인터페이스 동일)
- **세션 생성 실패와 분리** — Google API 실패 시에도 세션은 정상 생성

---

## 5. 예외 처리 (Phase 4 추가)

| 예외 | HTTP | 상황 |
|------|------|------|
| `SessionNotFoundException` | 404 | 세션 미존재 |
| `SessionAlreadyExistsException` | 409 | 동일 매칭 세션 중복 |
| `InvalidSessionStateException` | 400 | 이미 취소/완료된 세션 조작 |

---

## 6. DB 스키마

```sql
CREATE TABLE mentoring_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    matching_id BIGINT NOT NULL UNIQUE,
    mentee_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    meet_link VARCHAR(500),
    calendar_event_id VARCHAR(200),
    memo VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE mentor_availabilities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mentor_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
```

---

## 7. 전체 API 엔드포인트 현황 (Phase 2+3+4 = 25개)

| Phase | Controller | 엔드포인트 수 |
|-------|-----------|--------------|
| 2 | AuthController | 4 |
| 2 | UserController | 2 |
| 2 | MentorController | 2 |
| 3 | TestController | 4 |
| 3 | MatchingController | 5 |
| 4 | SessionController | 4 |
| 4 | AvailabilityController | 4 |
| **합계** | | **25** |

---

## 8. 향후 작업

- Google Cloud Console 설정 후 `GoogleCalendarService` 실제 구현 교체
- Phase 5: 결제 (토스페이먼츠) & 커뮤니티 (게시글/댓글/좋아요)
- Phase 6: Admin API + 배포

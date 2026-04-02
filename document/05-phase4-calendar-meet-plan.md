# Phase 4: Google Calendar & Meet 연동 — Backend 구현 계획서

> 작성일: 2026-04-02 | 프로젝트: DevMatch | 기반: ROADMAP.md Phase 4

---

## 1. 현재 상태 분석

### 완료된 항목

| 항목 | 상태 |
|------|------|
| Phase 2: 회원 시스템 (JWT 인증, 마이페이지, 멘토 신청) | ✅ |
| Phase 3: 테스트 & 매칭 (자동 채점, 멘토 추천, 매칭 수락/거절) | ✅ |
| Matching Entity (accept/reject 메서드) | ✅ |
| Google Calendar API 의존성 (build.gradle) | ✅ |
| Google OAuth2 설정 (application.yml) | ✅ |

### Phase 4에서 활용할 기존 코드

| 기존 코드 | 활용 방식 |
|-----------|-----------|
| `Matching` Entity | 매칭 ACCEPTED 시 멘토링 세션 생성 트리거 |
| `User` Entity (email) | Google Calendar attendees 추가 |
| `MatchingService.acceptMatching()` | 수락 시 세션 자동 생성 연동 |

---

## 2. 구현 범위 요약

Phase 4에서 구현할 기능:

1. **멘토링 세션 관리** — 매칭 수락 후 멘토링 일정 등록/조회/취소
2. **일정 관리 API** — 멘토가 가능한 시간대 설정, 멘티가 시간 선택
3. **Google Calendar 연동** — Calendar 이벤트 생성, Meet 링크 자동 생성
4. **매칭-세션 연결** — 매칭 수락 시 세션 생성 흐름 연동

### 설계 방침

Google Calendar 연동은 외부 API에 의존하므로, 핵심 로직(세션 관리)은 Google API 없이도 독립적으로 동작하도록 설계합니다. Google Calendar/Meet 연동은 선택적 기능으로 분리합니다.

---

## 3. Entity 설계

### 3.1 MentoringSession Entity (멘토링 세션)

```
파일: entity/MentoringSession.java (신규)
테이블: mentoring_sessions
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| matching | Matching | FK, @OneToOne | 매칭과 1:1 관계 |
| mentee | User | FK, @ManyToOne | 멘티 |
| mentor | User | FK, @ManyToOne | 멘토 |
| category | String | NOT NULL, max 50 | 멘토링 분야 |
| sessionDate | LocalDate | NOT NULL | 세션 날짜 |
| startTime | LocalTime | NOT NULL | 시작 시간 |
| endTime | LocalTime | NOT NULL | 종료 시간 |
| status | SessionStatus (Enum) | NOT NULL, default SCHEDULED | 세션 상태 |
| meetLink | String | nullable, max 500 | Google Meet 링크 |
| calendarEventId | String | nullable, max 200 | Google Calendar 이벤트 ID |
| memo | String | nullable, max 1000 | 메모 |
| createdAt | LocalDateTime | NOT NULL | 생성일 |
| updatedAt | LocalDateTime | NOT NULL | 수정일 |

**설계 포인트:**
- matching과 1:1 관계 — 하나의 매칭당 하나의 세션
- meetLink와 calendarEventId는 nullable — Google 연동 없이도 세션 관리 가능
- sessionDate + startTime + endTime으로 일정 표현 (LocalDate/LocalTime 분리)

### 3.2 MentorAvailability Entity (멘토 가용 시간)

```
파일: entity/MentorAvailability.java (신규)
테이블: mentor_availabilities
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| mentor | User | FK, @ManyToOne | 멘토 |
| dayOfWeek | DayOfWeek | NOT NULL | 요일 (MONDAY~SUNDAY) |
| startTime | LocalTime | NOT NULL | 시작 시간 |
| endTime | LocalTime | NOT NULL | 종료 시간 |
| isActive | Boolean | NOT NULL, default true | 활성화 여부 |

**설계 포인트:**
- 멘토가 매주 반복되는 가용 시간을 설정 (예: 매주 월요일 19:00~21:00)
- 멘티가 멘토의 가용 시간 중 원하는 시간을 선택하여 세션 예약
- DayOfWeek는 Java의 `java.time.DayOfWeek` 사용 (STRING으로 저장)

### 3.3 SessionStatus Enum

```
파일: entity/SessionStatus.java (신규)
```

| 값 | 설명 |
|----|------|
| SCHEDULED | 예정됨 |
| COMPLETED | 완료 |
| CANCELLED | 취소됨 |

---

## 4. Repository 설계

### 4.1 MentoringSessionRepository

```
파일: repository/MentoringSessionRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByMenteeIdOrderBySessionDateDesc(Long menteeId) | List\<MentoringSession\> | 멘티의 세션 목록 |
| findByMentorIdOrderBySessionDateDesc(Long mentorId) | List\<MentoringSession\> | 멘토의 세션 목록 |
| findByMatchingId(Long matchingId) | Optional\<MentoringSession\> | 매칭별 세션 조회 |
| existsByMatchingId(Long matchingId) | boolean | 해당 매칭의 세션 존재 여부 |

### 4.2 MentorAvailabilityRepository

```
파일: repository/MentorAvailabilityRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByMentorIdAndIsActiveTrue(Long mentorId) | List\<MentorAvailability\> | 멘토의 활성 가용 시간 목록 |
| findByMentorId(Long mentorId) | List\<MentorAvailability\> | 멘토의 전체 가용 시간 목록 |
| deleteByMentorIdAndId(Long mentorId, Long id) | void | 가용 시간 삭제 |

---

## 5. DTO 설계

### 5.1 세션 DTO (dto/session/)

```
파일: dto/session/SessionCreateRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| matchingId | Long | @NotNull | 매칭 ID |
| sessionDate | LocalDate | @NotNull, @Future | 세션 날짜 |
| startTime | LocalTime | @NotNull | 시작 시간 |
| endTime | LocalTime | @NotNull | 종료 시간 |
| memo | String | @Size(max=1000) | 메모 |

```
파일: dto/session/SessionResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 세션 ID |
| matchingId | Long | 매칭 ID |
| menteeId | Long | 멘티 ID |
| menteeName | String | 멘티 이름 |
| mentorId | Long | 멘토 ID |
| mentorName | String | 멘토 이름 |
| category | String | 분야 |
| sessionDate | LocalDate | 세션 날짜 |
| startTime | LocalTime | 시작 시간 |
| endTime | LocalTime | 종료 시간 |
| status | String | 상태 |
| meetLink | String | Meet 링크 |
| memo | String | 메모 |
| createdAt | LocalDateTime | 생성일 |

### 5.2 가용 시간 DTO (dto/session/)

```
파일: dto/session/AvailabilityRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| dayOfWeek | String | @NotBlank | 요일 (MONDAY~SUNDAY) |
| startTime | LocalTime | @NotNull | 시작 시간 |
| endTime | LocalTime | @NotNull | 종료 시간 |

```
파일: dto/session/AvailabilityResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 가용 시간 ID |
| dayOfWeek | String | 요일 |
| startTime | LocalTime | 시작 시간 |
| endTime | LocalTime | 종료 시간 |
| isActive | Boolean | 활성화 여부 |

---

## 6. Service 설계

### 6.1 SessionService

```
파일: service/SessionService.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| createSession(Long userId, SessionCreateRequest) | SessionResponse | 세션 생성 (매칭 ACCEPTED 확인 → 세션 생성 → Google Calendar 연동 시도) |
| getMySessions(Long userId) | List\<SessionResponse\> | 내 세션 목록 (멘티/멘토 모두 조회) |
| cancelSession(Long userId, Long sessionId) | SessionResponse | 세션 취소 (Google Calendar 이벤트 삭제 시도) |
| completeSession(Long userId, Long sessionId) | SessionResponse | 세션 완료 처리 |

### 6.2 AvailabilityService

```
파일: service/AvailabilityService.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| addAvailability(Long mentorId, AvailabilityRequest) | AvailabilityResponse | 가용 시간 추가 |
| getMyAvailabilities(Long mentorId) | List\<AvailabilityResponse\> | 내 가용 시간 목록 |
| getMentorAvailabilities(Long mentorId) | List\<AvailabilityResponse\> | 특정 멘토의 가용 시간 (멘티용) |
| deleteAvailability(Long mentorId, Long availabilityId) | void | 가용 시간 삭제 |

### 6.3 GoogleCalendarService

```
파일: service/GoogleCalendarService.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| createEvent(MentoringSession session) | GoogleEventResult | Calendar 이벤트 + Meet 링크 생성 |
| deleteEvent(String calendarEventId) | void | Calendar 이벤트 삭제 |

**GoogleEventResult (내부 record):**
- calendarEventId (String) — 생성된 이벤트 ID
- meetLink (String) — Google Meet 링크

**설계 포인트:**
- Google API 호출 실패 시 세션 생성은 성공하되, meetLink/calendarEventId만 null로 남김
- 로그로 실패 기록, 사용자에게는 "Google Calendar 연동 실패" 안내
- Google OAuth2 토큰은 서비스 계정(Service Account) 방식 또는 사용자별 OAuth2 토큰 사용

---

## 7. Controller 설계

### 7.1 SessionController

```
파일: controller/SessionController.java (신규)
경로: /api/sessions
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| POST | / | createSession() | 필요 | 멘토링 세션 생성 |
| GET | / | getMySessions() | 필요 | 내 세션 목록 |
| PUT | /{id}/cancel | cancelSession() | 필요 | 세션 취소 |
| PUT | /{id}/complete | completeSession() | 필요 | 세션 완료 |

### 7.2 AvailabilityController

```
파일: controller/AvailabilityController.java (신규)
경로: /api/availability
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| POST | / | addAvailability() | 필요 (MENTOR) | 가용 시간 추가 |
| GET | /me | getMyAvailabilities() | 필요 (MENTOR) | 내 가용 시간 목록 |
| GET | /mentor/{mentorId} | getMentorAvailabilities() | 필요 | 특정 멘토 가용 시간 |
| DELETE | /{id} | deleteAvailability() | 필요 (MENTOR) | 가용 시간 삭제 |

---

## 8. 예외 처리

| 예외 클래스 | HTTP 상태 | 사용 상황 |
|-------------|-----------|-----------|
| SessionNotFoundException | 404 Not Found | 존재하지 않는 세션 조회 |
| SessionAlreadyExistsException | 409 Conflict | 동일 매칭에 세션 중복 생성 |
| InvalidSessionStateException | 400 Bad Request | 이미 취소/완료된 세션 조작 시 |

---

## 9. 신규 생성 파일 목록

| 파일 경로 | 설명 |
|-----------|------|
| `entity/SessionStatus.java` | 세션 상태 Enum |
| `entity/MentoringSession.java` | 멘토링 세션 엔티티 |
| `entity/MentorAvailability.java` | 멘토 가용 시간 엔티티 |
| `repository/MentoringSessionRepository.java` | 세션 Repository |
| `repository/MentorAvailabilityRepository.java` | 가용 시간 Repository |
| `dto/session/SessionCreateRequest.java` | 세션 생성 요청 |
| `dto/session/SessionResponse.java` | 세션 응답 |
| `dto/session/AvailabilityRequest.java` | 가용 시간 요청 |
| `dto/session/AvailabilityResponse.java` | 가용 시간 응답 |
| `service/SessionService.java` | 세션 서비스 |
| `service/AvailabilityService.java` | 가용 시간 서비스 |
| `service/GoogleCalendarService.java` | Google Calendar 연동 |
| `controller/SessionController.java` | 세션 컨트롤러 |
| `controller/AvailabilityController.java` | 가용 시간 컨트롤러 |
| `exception/SessionNotFoundException.java` | 세션 미존재 예외 |
| `exception/SessionAlreadyExistsException.java` | 세션 중복 예외 |
| `exception/InvalidSessionStateException.java` | 세션 상태 예외 |

총 17개 파일 신규 생성, 1개 파일 수정 (GlobalExceptionHandler)

---

## 10. 구현 순서

### Step 1: Enum + Entity

1. `SessionStatus.java`
2. `MentoringSession.java`
3. `MentorAvailability.java`

### Step 2: Repository

1. `MentoringSessionRepository.java`
2. `MentorAvailabilityRepository.java`

### Step 3: DTO + 예외

1. 세션 DTO 4개
2. 커스텀 예외 3개
3. GlobalExceptionHandler 수정

### Step 4: 가용 시간 관리

1. `AvailabilityService.java`
2. `AvailabilityController.java`

### Step 5: 세션 관리

1. `SessionService.java`
2. `SessionController.java`

### Step 6: Google Calendar 연동

1. `GoogleCalendarService.java`
2. SessionService에서 Google Calendar 호출 통합

---

## 11. API 흐름도

### 멘토 가용 시간 설정

```
멘토 → POST /api/availability { dayOfWeek: "MONDAY", startTime: "19:00", endTime: "21:00" }
  ← { id: 1, dayOfWeek: "MONDAY", startTime: "19:00", endTime: "21:00" }
```

### 멘토링 세션 생성 흐름

```
1. 매칭 수락 (Phase 3) → Matching.status = ACCEPTED

2. 멘티 → GET /api/availability/mentor/5
   ← [{dayOfWeek:"MONDAY", startTime:"19:00", endTime:"21:00"}, ...]

3. 멘티 → POST /api/sessions
   { matchingId: 1, sessionDate: "2026-04-10", startTime: "19:00", endTime: "20:00" }
   → SessionService.createSession()
     → Matching ACCEPTED 확인
     → 중복 세션 확인
     → MentoringSession 생성
     → GoogleCalendarService.createEvent() 호출 (선택적)
       → Google Calendar 이벤트 생성 + Meet 링크 생성
       → meetLink, calendarEventId 저장
   ← { id, sessionDate, startTime, endTime, meetLink: "https://meet.google.com/xxx", status: "SCHEDULED" }

4. 멘토/멘티 → GET /api/sessions
   ← 내 세션 목록
```

### 세션 취소 흐름

```
PUT /api/sessions/1/cancel
  → SessionService.cancelSession()
    → SCHEDULED 상태 확인
    → status → CANCELLED
    → GoogleCalendarService.deleteEvent() 호출 (calendarEventId 있으면)
  ← { status: "CANCELLED" }
```

---

## 12. DB 스키마 (예상)

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
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (matching_id) REFERENCES matchings(id),
    FOREIGN KEY (mentee_id) REFERENCES users(id),
    FOREIGN KEY (mentor_id) REFERENCES users(id)
);

CREATE TABLE mentor_availabilities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mentor_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (mentor_id) REFERENCES users(id)
);
```

---

## 13. Google Calendar 연동 참고

### 사전 준비 (Google Cloud Console)

1. Google Cloud Console에서 프로젝트 생성
2. Calendar API 활성화
3. OAuth 2.0 클라이언트 ID 생성 (또는 Service Account)
4. 환경변수 설정: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

### Google Calendar 이벤트 생성 핵심 코드 흐름

```
1. Google Calendar API 클라이언트 초기화
2. Event 객체 생성
   - summary: "DevMatch 멘토링 - {category}"
   - start/end: sessionDate + startTime/endTime
   - attendees: [멘토 이메일, 멘티 이메일]
   - conferenceData: Google Meet 자동 생성 설정
3. calendar.events().insert() 호출
   - conferenceDataVersion=1 파라미터 필수 (Meet 생성)
4. 응답에서 meetLink, eventId 추출
```

### Google API 실패 시 대응

- Google API 호출은 try-catch로 감싸서 실패해도 세션 생성은 성공
- meetLink와 calendarEventId만 null로 남김
- 로그에 오류 기록
- 향후 재시도 또는 수동 링크 입력 지원 가능

---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "LMS 마이페이지 캘린더 CRUD 연동 구현 계획"

---

# LMS Mypage Integration + Calendar + CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable LMS page entry from mypage, add calendar-based session scheduling with time slot booking and change requests, and add CRUD UI to all LMS pages.

**Architecture:** Backend-first for new entities/APIs, then frontend. New `LmsSessionController` at `/api/lms/sessions/{matchingId}` handles calendar slots, session booking, and change requests. Existing `SessionController` remains unchanged. Frontend sessions page gets FullCalendar monthly view with role-based interactions. Other LMS pages get modal-based CRUD forms.

**Tech Stack:** Spring Boot 3.4.4, JPA/Hibernate, Next.js 14 (App Router), FullCalendar 6 (`@fullcalendar/react`, `@fullcalendar/daygrid`, `@fullcalendar/interaction`), Tailwind CSS, Lucide React icons.

---

## File Structure

### Backend — New Files
| File | Responsibility |
|---|---|
| `entity/MentorTimeSlot.java` | Date-based mentor availability slot |
| `entity/SessionChangeRequest.java` | Session reschedule request entity |
| `entity/ChangeRequestStatus.java` | Enum: PENDING, APPROVED, REJECTED |
| `repository/MentorTimeSlotRepository.java` | Time slot queries by matchingId + date range |
| `repository/SessionChangeRequestRepository.java` | Change request queries |
| `dto/lms/TimeSlotCreateRequest.java` | Mentor creates time slot |
| `dto/lms/TimeSlotResponse.java` | Time slot API response |
| `dto/lms/BookSessionRequest.java` | Mentee books a slot |
| `dto/lms/ChangeRequestCreateRequest.java` | Create reschedule request |
| `dto/lms/ChangeRequestResponse.java` | Change request API response |
| `dto/lms/SessionListResponse.java` | Session response for LMS context (with change request info) |
| `service/LmsSessionService.java` | Calendar slots, session booking, change requests |
| `controller/LmsSessionController.java` | `/api/lms/sessions/{matchingId}/*` endpoints |
| `exception/TimeSlotNotFoundException.java` | 404 for time slots |
| `exception/ChangeRequestNotFoundException.java` | 404 for change requests |

### Backend — Modified Files
| File | Change |
|---|---|
| `entity/MentoringSession.java` | Remove `unique=true` on matchingId, add `updateSchedule()` |
| `repository/MentoringSessionRepository.java` | Add `findByMatchingIdOrderBySessionDateDesc()` |
| `exception/GlobalExceptionHandler.java` | Add handlers for new exceptions |

### Frontend — Modified Files
| File | Change |
|---|---|
| `lib/types.ts` | Add `'TRIAL'` to MatchingResponse.status union |
| `lib/lms-types.ts` | Add calendar/session types |
| `lib/lms.ts` | Add calendar/session API functions |
| `app/mypage/page.tsx` | Add TRIAL status config + LMS entry button on matching cards |
| `app/lms/sessions/page.tsx` | Complete rewrite: FullCalendar + slots + booking + change requests |
| `app/lms/curriculum/page.tsx` | Add create/edit modals for mentor, keep toggle for mentee |
| `app/lms/assignments/page.tsx` | Add create/feedback modals (mentor), submit modal (mentee) |
| `app/lms/notes/page.tsx` | Add create modal (mentee), detail view + comment modal (both) |
| `app/lms/career/page.tsx` | Add resume upload (mentee), feedback + interview modals (mentor) |

### SQL
| File | Purpose |
|---|---|
| `backend/src/main/resources/seed-lms.sql` | Seed data for testing |

---

## Task 1: Backend — Entity & Repository Changes

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/MentoringSession.java:23-24`
- Modify: `backend/src/main/java/com/devmatch/repository/MentoringSessionRepository.java`
- Create: `backend/src/main/java/com/devmatch/entity/ChangeRequestStatus.java`
- Create: `backend/src/main/java/com/devmatch/entity/MentorTimeSlot.java`
- Create: `backend/src/main/java/com/devmatch/entity/SessionChangeRequest.java`
- Create: `backend/src/main/java/com/devmatch/repository/MentorTimeSlotRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/SessionChangeRequestRepository.java`

- [ ] **Step 1: Fix MentoringSession unique constraint**

In `MentoringSession.java`, change line 23-24 from:
```java
@Column(name = "matching_id", nullable = false, unique = true)
```
to:
```java
@Column(name = "matching_id", nullable = false)
```

Add `updateSchedule` method after `updateCalendarEventId`:
```java
public void updateSchedule(LocalDate newDate, LocalTime newStart, LocalTime newEnd) {
    this.sessionDate = newDate;
    this.startTime = newStart;
    this.endTime = newEnd;
}
```

- [ ] **Step 2: Add matchingId query to MentoringSessionRepository**

Add to `MentoringSessionRepository.java`:
```java
List<MentoringSession> findByMatchingIdOrderBySessionDateDesc(Long matchingId);
```

- [ ] **Step 3: Create ChangeRequestStatus enum**

Create `backend/src/main/java/com/devmatch/entity/ChangeRequestStatus.java`:
```java
package com.devmatch.entity;

public enum ChangeRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
```

- [ ] **Step 4: Create MentorTimeSlot entity**

Create `backend/src/main/java/com/devmatch/entity/MentorTimeSlot.java`:
```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "mentor_time_slots")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class MentorTimeSlot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_booked", nullable = false)
    @Builder.Default
    private Boolean isBooked = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void book() {
        this.isBooked = true;
    }

    public void unbook() {
        this.isBooked = false;
    }
}
```

- [ ] **Step 5: Create SessionChangeRequest entity**

Create `backend/src/main/java/com/devmatch/entity/SessionChangeRequest.java`:
```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "session_change_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class SessionChangeRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "new_date", nullable = false)
    private LocalDate newDate;

    @Column(name = "new_start_time", nullable = false)
    private LocalTime newStartTime;

    @Column(name = "new_end_time", nullable = false)
    private LocalTime newEndTime;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChangeRequestStatus status = ChangeRequestStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public void approve() {
        this.status = ChangeRequestStatus.APPROVED;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ChangeRequestStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 6: Create MentorTimeSlotRepository**

Create `backend/src/main/java/com/devmatch/repository/MentorTimeSlotRepository.java`:
```java
package com.devmatch.repository;

import com.devmatch.entity.MentorTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MentorTimeSlotRepository extends JpaRepository<MentorTimeSlot, Long> {

    List<MentorTimeSlot> findByMatchingIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            Long matchingId, LocalDate start, LocalDate end);

    List<MentorTimeSlot> findByMatchingIdAndSlotDateAndIsBookedFalseOrderByStartTimeAsc(
            Long matchingId, LocalDate date);

    boolean existsByMatchingIdAndSlotDateAndStartTimeAndEndTime(
            Long matchingId, LocalDate date, LocalTime startTime, LocalTime endTime);
}
```

- [ ] **Step 7: Create SessionChangeRequestRepository**

Create `backend/src/main/java/com/devmatch/repository/SessionChangeRequestRepository.java`:
```java
package com.devmatch.repository;

import com.devmatch.entity.ChangeRequestStatus;
import com.devmatch.entity.SessionChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionChangeRequestRepository extends JpaRepository<SessionChangeRequest, Long> {

    List<SessionChangeRequest> findBySessionIdOrderByCreatedAtDesc(Long sessionId);

    Optional<SessionChangeRequest> findBySessionIdAndStatus(Long sessionId, ChangeRequestStatus status);

    List<SessionChangeRequest> findBySessionIdInOrderByCreatedAtDesc(List<Long> sessionIds);

    boolean existsBySessionIdAndStatus(Long sessionId, ChangeRequestStatus status);
}
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/MentoringSession.java \
       backend/src/main/java/com/devmatch/entity/ChangeRequestStatus.java \
       backend/src/main/java/com/devmatch/entity/MentorTimeSlot.java \
       backend/src/main/java/com/devmatch/entity/SessionChangeRequest.java \
       backend/src/main/java/com/devmatch/repository/MentoringSessionRepository.java \
       backend/src/main/java/com/devmatch/repository/MentorTimeSlotRepository.java \
       backend/src/main/java/com/devmatch/repository/SessionChangeRequestRepository.java
git commit -m "feat(lms): add calendar entities and repositories

Add MentorTimeSlot and SessionChangeRequest entities for calendar-based
session scheduling. Remove unique constraint on MentoringSession.matchingId
to allow multiple sessions per matching. Add matchingId-based query methods."
```

---

## Task 2: Backend — DTOs & Exceptions

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/lms/TimeSlotCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/TimeSlotResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/BookSessionRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/ChangeRequestCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/ChangeRequestResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/SessionListResponse.java`
- Create: `backend/src/main/java/com/devmatch/exception/TimeSlotNotFoundException.java`
- Create: `backend/src/main/java/com/devmatch/exception/ChangeRequestNotFoundException.java`
- Modify: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create TimeSlotCreateRequest**

```java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @NoArgsConstructor @AllArgsConstructor
public class TimeSlotCreateRequest {

    @NotNull(message = "날짜는 필수입니다")
    private LocalDate slotDate;

    @NotNull(message = "시작 시간은 필수입니다")
    private LocalTime startTime;

    @NotNull(message = "종료 시간은 필수입니다")
    private LocalTime endTime;
}
```

- [ ] **Step 2: Create TimeSlotResponse**

```java
package com.devmatch.dto.lms;

import com.devmatch.entity.MentorTimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @AllArgsConstructor @Builder
public class TimeSlotResponse {
    private Long id;
    private Long matchingId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isBooked;

    public static TimeSlotResponse from(MentorTimeSlot slot) {
        return TimeSlotResponse.builder()
                .id(slot.getId())
                .matchingId(slot.getMatchingId())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .isBooked(slot.getIsBooked())
                .build();
    }
}
```

- [ ] **Step 3: Create BookSessionRequest**

```java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class BookSessionRequest {

    @NotNull(message = "슬롯 ID는 필수입니다")
    private Long slotId;

    private String memo;
}
```

- [ ] **Step 4: Create ChangeRequestCreateRequest**

```java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ChangeRequestCreateRequest {

    @NotNull(message = "세션 ID는 필수입니다")
    private Long sessionId;

    @NotNull(message = "변경 희망 날짜는 필수입니다")
    private LocalDate newDate;

    @NotNull(message = "변경 희망 시작 시간은 필수입니다")
    private LocalTime newStartTime;

    @NotNull(message = "변경 희망 종료 시간은 필수입니다")
    private LocalTime newEndTime;

    private String reason;
}
```

- [ ] **Step 5: Create ChangeRequestResponse**

```java
package com.devmatch.dto.lms;

import com.devmatch.entity.ChangeRequestStatus;
import com.devmatch.entity.SessionChangeRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @AllArgsConstructor @Builder
public class ChangeRequestResponse {
    private Long id;
    private Long sessionId;
    private Long requesterId;
    private LocalDate newDate;
    private LocalTime newStartTime;
    private LocalTime newEndTime;
    private String reason;
    private ChangeRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    public static ChangeRequestResponse from(SessionChangeRequest r) {
        return ChangeRequestResponse.builder()
                .id(r.getId())
                .sessionId(r.getSessionId())
                .requesterId(r.getRequesterId())
                .newDate(r.getNewDate())
                .newStartTime(r.getNewStartTime())
                .newEndTime(r.getNewEndTime())
                .reason(r.getReason())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .respondedAt(r.getRespondedAt())
                .build();
    }
}
```

- [ ] **Step 6: Create SessionListResponse**

```java
package com.devmatch.dto.lms;

import com.devmatch.entity.MentoringSession;
import com.devmatch.entity.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @AllArgsConstructor @Builder
public class SessionListResponse {
    private Long id;
    private Long matchingId;
    private Long menteeId;
    private Long mentorId;
    private String category;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private SessionStatus status;
    private String meetLink;
    private String memo;
    private boolean hasPendingChangeRequest;
    private LocalDateTime createdAt;

    public static SessionListResponse from(MentoringSession s, boolean hasPendingChange) {
        return SessionListResponse.builder()
                .id(s.getId())
                .matchingId(s.getMatchingId())
                .menteeId(s.getMenteeId())
                .mentorId(s.getMentorId())
                .category(s.getCategory())
                .sessionDate(s.getSessionDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus())
                .meetLink(s.getMeetLink())
                .memo(s.getMemo())
                .hasPendingChangeRequest(hasPendingChange)
                .createdAt(s.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 7: Create exception classes**

Create `TimeSlotNotFoundException.java`:
```java
package com.devmatch.exception;

public class TimeSlotNotFoundException extends RuntimeException {
    public TimeSlotNotFoundException(String message) { super(message); }
}
```

Create `ChangeRequestNotFoundException.java`:
```java
package com.devmatch.exception;

public class ChangeRequestNotFoundException extends RuntimeException {
    public ChangeRequestNotFoundException(String message) { super(message); }
}
```

- [ ] **Step 8: Update GlobalExceptionHandler**

Add after the existing LMS section (after `handleResumeNotFound`):
```java
@ExceptionHandler(TimeSlotNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleTimeSlotNotFound(TimeSlotNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
}

@ExceptionHandler(ChangeRequestNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleChangeRequestNotFound(ChangeRequestNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
}
```

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devmatch/dto/lms/TimeSlotCreateRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/TimeSlotResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/BookSessionRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/ChangeRequestCreateRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/ChangeRequestResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/SessionListResponse.java \
       backend/src/main/java/com/devmatch/exception/TimeSlotNotFoundException.java \
       backend/src/main/java/com/devmatch/exception/ChangeRequestNotFoundException.java \
       backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java
git commit -m "feat(lms): add calendar DTOs and exception handlers

Add request/response DTOs for time slots, session booking, and change
requests. Add SessionListResponse with pending change request indicator.
Register new exception handlers in GlobalExceptionHandler."
```

---

## Task 3: Backend — LmsSessionService

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/LmsSessionService.java`

- [ ] **Step 1: Create LmsSessionService**

```java
package com.devmatch.service;

import com.devmatch.dto.lms.*;
import com.devmatch.entity.*;
import com.devmatch.exception.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsSessionService {

    private final LmsAccessService lmsAccessService;
    private final MentoringSessionRepository sessionRepository;
    private final MentorTimeSlotRepository timeSlotRepository;
    private final SessionChangeRequestRepository changeRequestRepository;

    // ─── Sessions ───

    public List<SessionListResponse> getSessions(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        List<MentoringSession> sessions = sessionRepository.findByMatchingIdOrderBySessionDateDesc(matchingId);
        List<Long> sessionIds = sessions.stream().map(MentoringSession::getId).toList();
        Set<Long> sessionsWithPendingChange = changeRequestRepository
                .findBySessionIdInOrderByCreatedAtDesc(sessionIds).stream()
                .filter(cr -> cr.getStatus() == ChangeRequestStatus.PENDING)
                .map(SessionChangeRequest::getSessionId)
                .collect(Collectors.toSet());
        return sessions.stream()
                .map(s -> SessionListResponse.from(s, sessionsWithPendingChange.contains(s.getId())))
                .toList();
    }

    @Transactional
    public SessionListResponse completeSession(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateMentorAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정 상태의 세션만 완료 처리할 수 있습니다");
        }
        session.complete();
        return SessionListResponse.from(session, false);
    }

    @Transactional
    public SessionListResponse cancelSession(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정 상태의 세션만 취소할 수 있습니다");
        }
        session.cancel();
        return SessionListResponse.from(session, false);
    }

    // ─── Time Slots ───

    public List<TimeSlotResponse> getSlots(Long userId, Long matchingId, String month) {
        lmsAccessService.validateAccess(userId, matchingId);
        YearMonth ym = YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return timeSlotRepository
                .findByMatchingIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(matchingId, start, end)
                .stream().map(TimeSlotResponse::from).toList();
    }

    public List<TimeSlotResponse> getAvailableSlots(Long userId, Long matchingId, LocalDate date) {
        lmsAccessService.validateAccess(userId, matchingId);
        return timeSlotRepository
                .findByMatchingIdAndSlotDateAndIsBookedFalseOrderByStartTimeAsc(matchingId, date)
                .stream().map(TimeSlotResponse::from).toList();
    }

    @Transactional
    public TimeSlotResponse createSlot(Long userId, Long matchingId, TimeSlotCreateRequest request) {
        Matching matching = lmsAccessService.validateMentorAccess(userId, matchingId);
        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().equals(request.getStartTime())) {
            throw new InvalidSessionStateException("종료 시간은 시작 시간 이후여야 합니다");
        }
        if (timeSlotRepository.existsByMatchingIdAndSlotDateAndStartTimeAndEndTime(
                matchingId, request.getSlotDate(), request.getStartTime(), request.getEndTime())) {
            throw new SessionAlreadyExistsException("동일한 시간대의 슬롯이 이미 존재합니다");
        }
        MentorTimeSlot slot = MentorTimeSlot.builder()
                .mentorId(userId)
                .matchingId(matchingId)
                .slotDate(request.getSlotDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();
        return TimeSlotResponse.from(timeSlotRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(Long userId, Long matchingId, Long slotId) {
        lmsAccessService.validateMentorAccess(userId, matchingId);
        MentorTimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new TimeSlotNotFoundException("슬롯을 찾을 수 없습니다: " + slotId));
        if (!slot.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 슬롯이 아닙니다");
        }
        if (slot.getIsBooked()) {
            throw new InvalidSessionStateException("이미 예약된 슬롯은 삭제할 수 없습니다");
        }
        timeSlotRepository.delete(slot);
    }

    // ─── Booking ───

    @Transactional
    public SessionListResponse bookSession(Long userId, Long matchingId, BookSessionRequest request) {
        Matching matching = lmsAccessService.validateMenteeAccess(userId, matchingId);
        MentorTimeSlot slot = timeSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException("슬롯을 찾을 수 없습니다: " + request.getSlotId()));
        if (!slot.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 슬롯이 아닙니다");
        }
        if (slot.getIsBooked()) {
            throw new InvalidSessionStateException("이미 예약된 슬롯입니다");
        }
        slot.book();
        MentoringSession session = MentoringSession.builder()
                .matchingId(matchingId)
                .menteeId(userId)
                .mentorId(matching.getMentor().getId())
                .category(matching.getCategory())
                .sessionDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .memo(request.getMemo())
                .build();
        sessionRepository.save(session);
        return SessionListResponse.from(session, false);
    }

    // ─── Change Requests ───

    public List<ChangeRequestResponse> getChangeRequests(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return changeRequestRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
                .stream().map(ChangeRequestResponse::from).toList();
    }

    @Transactional
    public ChangeRequestResponse createChangeRequest(Long userId, Long matchingId, ChangeRequestCreateRequest request) {
        lmsAccessService.validateAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + request.getSessionId()));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정 상태의 세션만 변경 요청할 수 있습니다");
        }
        if (changeRequestRepository.existsBySessionIdAndStatus(session.getId(), ChangeRequestStatus.PENDING)) {
            throw new SessionAlreadyExistsException("이미 대기 중인 변경 요청이 있습니다");
        }
        SessionChangeRequest cr = SessionChangeRequest.builder()
                .sessionId(session.getId())
                .requesterId(userId)
                .newDate(request.getNewDate())
                .newStartTime(request.getNewStartTime())
                .newEndTime(request.getNewEndTime())
                .reason(request.getReason())
                .build();
        return ChangeRequestResponse.from(changeRequestRepository.save(cr));
    }

    @Transactional
    public ChangeRequestResponse approveChangeRequest(Long userId, Long matchingId, Long requestId) {
        lmsAccessService.validateAccess(userId, matchingId);
        SessionChangeRequest cr = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ChangeRequestNotFoundException("변경 요청을 찾을 수 없습니다: " + requestId));
        if (cr.getRequesterId().equals(userId)) {
            throw new LmsAccessDeniedException("본인이 요청한 변경은 본인이 승인할 수 없습니다");
        }
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new InvalidSessionStateException("대기 상태의 요청만 승인할 수 있습니다");
        }
        MentoringSession session = sessionRepository.findById(cr.getSessionId())
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다"));
        session.updateSchedule(cr.getNewDate(), cr.getNewStartTime(), cr.getNewEndTime());
        cr.approve();
        return ChangeRequestResponse.from(cr);
    }

    @Transactional
    public ChangeRequestResponse rejectChangeRequest(Long userId, Long matchingId, Long requestId) {
        lmsAccessService.validateAccess(userId, matchingId);
        SessionChangeRequest cr = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ChangeRequestNotFoundException("변경 요청을 찾을 수 없습니다: " + requestId));
        if (cr.getRequesterId().equals(userId)) {
            throw new LmsAccessDeniedException("본인이 요청한 변경은 본인이 거절할 수 없습니다");
        }
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new InvalidSessionStateException("대기 상태의 요청만 거절할 수 있습니다");
        }
        cr.reject();
        return ChangeRequestResponse.from(cr);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/LmsSessionService.java
git commit -m "feat(lms): add LmsSessionService for calendar scheduling

Implements time slot CRUD, session booking from slots, session status
changes, and change request create/approve/reject workflow with
LmsAccessService validation on all operations."
```

---

## Task 4: Backend — LmsSessionController

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/LmsSessionController.java`

- [ ] **Step 1: Create LmsSessionController**

```java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LmsSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "LMS Session", description = "LMS 캘린더 기반 세션 관리 API")
@RestController
@RequestMapping("/api/lms/sessions/{matchingId}")
@RequiredArgsConstructor
public class LmsSessionController {

    private final LmsSessionService lmsSessionService;

    // ─── Sessions ───

    @Operation(summary = "세션 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionListResponse>>> getSessions(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getSessions(user.getUserId(), matchingId)));
    }

    @Operation(summary = "세션 완료 처리")
    @PutMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<SessionListResponse>> completeSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success("세션이 완료 처리되었습니다",
                lmsSessionService.completeSession(user.getUserId(), matchingId, sessionId)));
    }

    @Operation(summary = "세션 취소")
    @PutMapping("/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<SessionListResponse>> cancelSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success("세션이 취소되었습니다",
                lmsSessionService.cancelSession(user.getUserId(), matchingId, sessionId)));
    }

    // ─── Time Slots ───

    @Operation(summary = "월별 가용시간 슬롯 조회")
    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getSlots(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getSlots(user.getUserId(), matchingId, month)));
    }

    @Operation(summary = "특정 날짜 예약 가능 슬롯 조회")
    @GetMapping("/slots/available")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getAvailableSlots(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getAvailableSlots(user.getUserId(), matchingId, date)));
    }

    @Operation(summary = "가용시간 슬롯 등록 (멘토)")
    @PostMapping("/slots")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> createSlot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @Valid @RequestBody TimeSlotCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "가용시간이 등록되었습니다",
                lmsSessionService.createSlot(user.getUserId(), matchingId, request)));
    }

    @Operation(summary = "가용시간 슬롯 삭제 (멘토)")
    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long slotId) {
        lmsSessionService.deleteSlot(user.getUserId(), matchingId, slotId);
        return ResponseEntity.ok(ApiResponse.success("가용시간이 삭제되었습니다", null));
    }

    // ─── Booking ───

    @Operation(summary = "세션 예약 (멘티가 슬롯 선택)")
    @PostMapping("/book")
    public ResponseEntity<ApiResponse<SessionListResponse>> bookSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @Valid @RequestBody BookSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "세션이 예약되었습니다",
                lmsSessionService.bookSession(user.getUserId(), matchingId, request)));
    }

    // ─── Change Requests ───

    @Operation(summary = "변경 요청 목록 조회")
    @GetMapping("/change-requests")
    public ResponseEntity<ApiResponse<List<ChangeRequestResponse>>> getChangeRequests(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @RequestParam Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getChangeRequests(user.getUserId(), matchingId, sessionId)));
    }

    @Operation(summary = "세션 변경 요청 생성")
    @PostMapping("/change-request")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> createChangeRequest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @Valid @RequestBody ChangeRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "변경 요청이 생성되었습니다",
                lmsSessionService.createChangeRequest(user.getUserId(), matchingId, request)));
    }

    @Operation(summary = "변경 요청 승인")
    @PutMapping("/change-request/{requestId}/approve")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> approveChangeRequest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("변경 요청이 승인되었습니다",
                lmsSessionService.approveChangeRequest(user.getUserId(), matchingId, requestId)));
    }

    @Operation(summary = "변경 요청 거절")
    @PutMapping("/change-request/{requestId}/reject")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> rejectChangeRequest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("변경 요청이 거절되었습니다",
                lmsSessionService.rejectChangeRequest(user.getUserId(), matchingId, requestId)));
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devmatch/controller/LmsSessionController.java
git commit -m "feat(lms): add LmsSessionController for calendar API

REST endpoints for session list, time slot CRUD, session booking from
slots, and change request create/approve/reject. All endpoints scoped
under /api/lms/sessions/{matchingId} with LmsAccessService validation."
```

---

## Task 5: Seed Data SQL

**Files:**
- Create: `backend/src/main/resources/seed-lms.sql`

- [ ] **Step 1: Write seed data SQL**

Create `backend/src/main/resources/seed-lms.sql`:
```sql
-- LMS Seed Data for matching_id=1 (가나다 mentee id=10 ↔ 김자바 mentor id=1)
-- Run AFTER backend starts with new entities (Hibernate will create tables)
-- Execute: docker exec -i devmatch-mysql mysql -u root -proot devmatch < seed-lms.sql

-- 1. Curriculum
INSERT INTO curriculums (matching_id, title, description, total_weeks, start_date, end_date, discord_url, created_at, updated_at)
VALUES (1, 'Java Backend 마스터 과정', 'Spring Boot와 JPA를 활용한 백엔드 개발 심화 과정', 8, '2026-03-24', '2026-05-18', NULL, NOW(), NOW());

SET @curriculum_id = LAST_INSERT_ID();

-- 2. Curriculum Weeks
INSERT INTO curriculum_weeks (curriculum_id, week_number, title, description, topics, resources, is_completed, completed_at) VALUES
(@curriculum_id, 1, 'Java 기초 복습', '객체지향 핵심 개념과 Java 17 기능', 'OOP 4대 원칙,Java 17 Records,Sealed Classes', 'https://docs.oracle.com/en/java/', true, '2026-03-28 18:00:00'),
(@curriculum_id, 2, 'Spring Boot 기초', 'Spring Boot 프로젝트 구성과 DI/IoC', 'Spring IoC,의존성 주입,Bean 생명주기', 'https://spring.io/guides', true, '2026-04-04 18:00:00'),
(@curriculum_id, 3, 'Spring MVC & REST API', 'RESTful API 설계와 구현', 'REST 설계 원칙,Controller 패턴,예외 처리', 'https://spring.io/guides/gs/rest-service/', true, '2026-04-11 18:00:00'),
(@curriculum_id, 4, 'JPA & 데이터 접근', 'JPA 엔티티 매핑과 쿼리 작성', 'Entity 매핑,연관관계,JPQL,QueryDSL', 'https://docs.spring.io/spring-data/jpa/', false, NULL),
(@curriculum_id, 5, '인증 & 보안', 'Spring Security와 JWT 인증', 'Spring Security,JWT,OAuth2', '', false, NULL),
(@curriculum_id, 6, '테스트 & CI/CD', '단위 테스트와 통합 테스트', 'JUnit 5,Mockito,Testcontainers', '', false, NULL),
(@curriculum_id, 7, '성능 최적화', '캐싱, 인덱싱, 비동기 처리', 'Redis 캐싱,DB 인덱스,@Async', '', false, NULL),
(@curriculum_id, 8, '배포 & 운영', 'Docker, AWS 배포', 'Docker Compose,AWS EC2,모니터링', '', false, NULL);

-- 3. Mentoring Sessions (remove unique index first if exists)
ALTER TABLE mentoring_sessions DROP INDEX IF EXISTS UK_mentoring_sessions_matching_id;
ALTER TABLE mentoring_sessions DROP INDEX IF EXISTS UKq7y8l8mw0cqx5;

INSERT INTO mentoring_sessions (matching_id, mentee_id, mentor_id, category, session_date, start_time, end_time, status, meet_link, memo, created_at, updated_at) VALUES
(1, 10, 1, 'Backend', '2026-03-25', '19:00:00', '20:00:00', 'COMPLETED', NULL, '첫 세션 - OOP 개념 리뷰', NOW(), NOW()),
(1, 10, 1, 'Backend', '2026-04-01', '19:00:00', '20:00:00', 'COMPLETED', NULL, 'Spring Boot 프로젝트 셋업 실습', NOW(), NOW()),
(1, 10, 1, 'Backend', '2026-04-08', '19:00:00', '20:00:00', 'COMPLETED', NULL, 'REST API 설계 리뷰', NOW(), NOW()),
(1, 10, 1, 'Backend', '2026-04-14', '19:00:00', '20:00:00', 'SCHEDULED', NULL, 'JPA 엔티티 매핑 실습', NOW(), NOW()),
(1, 10, 1, 'Backend', '2026-04-21', '19:00:00', '20:00:00', 'SCHEDULED', NULL, 'QueryDSL 심화', NOW(), NOW());

-- 4. Assignments
INSERT INTO assignments (matching_id, mentor_id, type, title, description, due_date, reference_urls, status, created_at, updated_at) VALUES
(1, 1, 'TASK', 'REST API 설계 과제', 'Todo 앱의 RESTful API를 설계하고 Swagger 문서를 작성하세요', '2026-04-01', 'https://swagger.io/docs/', 'REVIEWED', NOW(), NOW()),
(1, 1, 'CODE_REVIEW', 'Spring Boot CRUD 구현', 'Todo 앱의 CRUD API를 Spring Boot로 구현하세요', '2026-04-08', 'https://github.com/example/spring-todo', 'SUBMITTED', NOW(), NOW()),
(1, 1, 'TASK', 'JPA 연관관계 매핑', '1:N, N:M 연관관계를 매핑하고 테스트를 작성하세요', '2026-04-15', '', 'ASSIGNED', NOW(), NOW()),
(1, 1, 'TASK', 'Spring Security JWT 인증', 'JWT 기반 인증/인가를 구현하세요', '2026-04-22', '', 'ASSIGNED', NOW(), NOW());

-- 5. Assignment Submissions
SET @assign1_id = (SELECT id FROM assignments WHERE title = 'REST API 설계 과제' AND matching_id = 1 LIMIT 1);
SET @assign2_id = (SELECT id FROM assignments WHERE title = 'Spring Boot CRUD 구현' AND matching_id = 1 LIMIT 1);

INSERT INTO assignment_submissions (assignment_id, mentee_id, submission_url, submission_note, submitted_at, feedback_content, grade, feedback_at) VALUES
(@assign1_id, 10, 'https://github.com/ganada/todo-api-design', 'Swagger UI 포함하여 작성했습니다', '2026-03-31 23:00:00', 'API 설계가 깔끔합니다. HATEOAS도 고려해보세요.', 'A', '2026-04-02 10:00:00'),
(@assign2_id, 10, 'https://github.com/ganada/spring-todo-crud', '기본 CRUD 완성, 예외 처리 추가 예정', '2026-04-07 22:00:00', NULL, NULL, NULL);

-- 6. Learning Notes
INSERT INTO learning_notes (matching_id, author_id, type, week_number, title, content, self_rating, created_at, updated_at) VALUES
(1, 10, 'SESSION_REVIEW', 1, '1주차 세션 정리 - OOP 핵심', '오늘 세션에서 SOLID 원칙에 대해 깊이 있게 배웠다. 특히 LSP와 ISP의 실제 적용 사례가 인상적이었다. 다음에는 실제 코드에서 이 원칙들이 어떻게 적용되는지 더 연습해봐야겠다.', 4, NOW(), NOW()),
(1, 10, 'WEEKLY_JOURNAL', 2, '2주차 학습일지 - Spring Boot', 'Spring Boot의 자동 설정 메커니즘을 이해하게 되었다. @SpringBootApplication 어노테이션이 @Configuration, @EnableAutoConfiguration, @ComponentScan을 포함하고 있다는 것을 알게 됐다.', 3, NOW(), NOW()),
(1, 10, 'SESSION_REVIEW', 3, '3주차 세션 정리 - REST API', 'REST API 설계 원칙과 HTTP 메서드 사용법을 정리했다. Richardson Maturity Model Level 2까지는 확실히 이해했고, Level 3 HATEOAS는 추가 학습이 필요하다.', 5, NOW(), NOW());

-- 7. Note Comments
SET @note1_id = (SELECT id FROM learning_notes WHERE title = '1주차 세션 정리 - OOP 핵심' LIMIT 1);
SET @note3_id = (SELECT id FROM learning_notes WHERE title = '3주차 세션 정리 - REST API' LIMIT 1);

INSERT INTO note_comments (note_id, author_id, content, created_at) VALUES
(@note1_id, 1, 'SOLID 원칙 정리를 잘 하셨네요. DIP(의존성 역전)도 Spring에서 어떻게 적용되는지 다음 세션에서 함께 보겠습니다.', NOW()),
(@note3_id, 1, 'Richardson Maturity Model 정리가 좋습니다. Level 3는 실무에서는 잘 안 쓰이니 Level 2까지 확실히 익히는 게 좋습니다.', NOW());

-- 8. Mentor Time Slots (for calendar feature)
INSERT INTO mentor_time_slots (mentor_id, matching_id, slot_date, start_time, end_time, is_booked, created_at) VALUES
(1, 1, '2026-04-14', '19:00:00', '20:00:00', true, NOW()),
(1, 1, '2026-04-16', '20:00:00', '21:00:00', false, NOW()),
(1, 1, '2026-04-18', '19:00:00', '20:00:00', false, NOW()),
(1, 1, '2026-04-21', '19:00:00', '20:00:00', true, NOW()),
(1, 1, '2026-04-23', '19:00:00', '20:30:00', false, NOW()),
(1, 1, '2026-04-25', '20:00:00', '21:00:00', false, NOW());

-- 9. Session Change Request (PENDING for 04-21 session)
SET @session_0421 = (SELECT id FROM mentoring_sessions WHERE session_date = '2026-04-21' AND matching_id = 1 LIMIT 1);

INSERT INTO session_change_requests (session_id, requester_id, new_date, new_start_time, new_end_time, reason, status, created_at)
VALUES (@session_0421, 10, '2026-04-22', '20:00:00', '21:00:00', '개인 일정이 생겨서 하루 뒤로 변경 부탁드립니다', 'PENDING', NOW());
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/seed-lms.sql
git commit -m "chore: add LMS seed data SQL script

Realistic test data for matching_id=1: curriculum with 8 weeks (3 completed),
5 sessions (3 completed, 2 scheduled), 4 assignments, 3 notes with comments,
6 mentor time slots, and 1 pending change request."
```

- [ ] **Step 3: Execute seed data (after backend restart)**

After restarting the backend so Hibernate creates the new tables:
```bash
docker exec -i devmatch-mysql mysql -u root -proot devmatch < backend/src/main/resources/seed-lms.sql
```

---

## Task 6: Frontend — Mypage LMS Entry Button

**Files:**
- Modify: `frontend/src/lib/types.ts:141`
- Modify: `frontend/src/app/mypage/page.tsx:119-124,427-449`

- [ ] **Step 1: Add TRIAL to MatchingResponse status union**

In `frontend/src/lib/types.ts`, change line 141:
```typescript
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
```
to:
```typescript
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'TRIAL';
```

- [ ] **Step 2: Add TRIAL to statusConfig and LMS button to mypage**

In `frontend/src/app/mypage/page.tsx`:

1. Add `GraduationCap` to the lucide-react import (line 9):
```typescript
import { User, Mail, Shield, Calendar, Edit3, Save, X, Loader2, ArrowLeft, FileText, Users, Trophy, ArrowRight, GraduationCap } from 'lucide-react';
```

2. Add TRIAL to statusConfig (after line 123, before the closing `};`):
```typescript
    TRIAL: { label: '체험 중', color: 'text-cyan-400 bg-cyan-500/10' },
```

3. Replace the matching card div (the entire `<div key={matching.id}` block from line 431 to line 449) with:
```tsx
                      <div
                        key={matching.id}
                        className="flex items-center gap-4 p-4 rounded-xl bg-white/3 border border-white/5"
                      >
                        <div className="flex-1 min-w-0">
                          <p className="text-white text-sm font-medium truncate">{matching.mentorName}</p>
                          <p className="text-gray-500 text-xs mt-0.5">
                            {matching.category} · {new Date(matching.createdAt).toLocaleDateString('ko-KR', {
                              year: 'numeric',
                              month: 'long',
                              day: 'numeric',
                            })}
                          </p>
                        </div>
                        {(matching.status === 'ACCEPTED' || matching.status === 'TRIAL') ? (
                          <button
                            onClick={() => router.push(`/lms/dashboard?matchingId=${matching.id}`)}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                                     bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors shrink-0"
                          >
                            <GraduationCap size={14} />
                            LMS
                          </button>
                        ) : (
                          <span className={`px-2.5 py-0.5 rounded-md text-xs font-medium shrink-0 ${status.color}`}>
                            {status.label}
                          </span>
                        )}
                      </div>
```

- [ ] **Step 3: Verify in browser**

Navigate to `http://localhost:3000/mypage`, log in as 가나다 (mentee). Confirm:
- Matching card shows "LMS" button (blue, with graduation cap icon) instead of status badge
- Clicking button navigates to `/lms/dashboard?matchingId=1`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/types.ts frontend/src/app/mypage/page.tsx
git commit -m "feat(mypage): add LMS entry button on matching cards

Show LMS button with GraduationCap icon for ACCEPTED and TRIAL matchings.
Clicking navigates to /lms/dashboard?matchingId={id}. Add TRIAL status
to MatchingResponse type and statusConfig."
```

---

## Task 7: Frontend — Calendar Types & API Functions

**Files:**
- Modify: `frontend/src/lib/lms-types.ts`
- Modify: `frontend/src/lib/lms.ts`

- [ ] **Step 1: Install @fullcalendar/interaction**

```bash
cd frontend && npm install @fullcalendar/interaction@^6.1.20
```

- [ ] **Step 2: Add calendar types to lms-types.ts**

Append to `frontend/src/lib/lms-types.ts`:
```typescript

// ─── Session (LMS context) ───
export type SessionStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export interface SessionListResponse {
  id: number; matchingId: number; menteeId: number; mentorId: number;
  category: string; sessionDate: string; startTime: string; endTime: string;
  status: SessionStatus; meetLink: string | null; memo: string | null;
  hasPendingChangeRequest: boolean; createdAt: string;
}

// ─── Time Slot ───
export interface TimeSlotResponse {
  id: number; matchingId: number; slotDate: string;
  startTime: string; endTime: string; isBooked: boolean;
}
export interface TimeSlotCreateRequest {
  slotDate: string; startTime: string; endTime: string;
}

// ─── Booking ───
export interface BookSessionRequest { slotId: number; memo?: string; }

// ─── Change Request ───
export type ChangeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface ChangeRequestResponse {
  id: number; sessionId: number; requesterId: number;
  newDate: string; newStartTime: string; newEndTime: string;
  reason: string | null; status: ChangeRequestStatus;
  createdAt: string; respondedAt: string | null;
}
export interface ChangeRequestCreateRequest {
  sessionId: number; newDate: string; newStartTime: string;
  newEndTime: string; reason?: string;
}
```

- [ ] **Step 3: Add calendar API functions to lms.ts**

Add imports to the existing import block in `frontend/src/lib/lms.ts`:
```typescript
import type {
  DashboardResponse, EnrollmentResponse, CurriculumResponse, CurriculumCreateRequest,
  AssignmentResponse, AssignmentCreateRequest, SubmissionRequest, FeedbackRequest,
  NoteResponse, NoteCreateRequest, NoteCommentRequest, ResumeResponse,
  ResumeFeedbackRequest, MockInterviewResponse, MockInterviewCreateRequest,
  CertificateEligibilityResponse,
  SessionListResponse, TimeSlotResponse, TimeSlotCreateRequest,
  BookSessionRequest, ChangeRequestResponse, ChangeRequestCreateRequest,
} from './lms-types';
```

Append these functions at the end of `frontend/src/lib/lms.ts`:
```typescript

// ─── LMS Sessions ───
export const getLmsSessions = (matchingId: number) =>
  apiClient.get<ApiResponse<SessionListResponse[]>>(`/lms/sessions/${matchingId}`);
export const completeLmsSession = (matchingId: number, sessionId: number) =>
  apiClient.put<ApiResponse<SessionListResponse>>(`/lms/sessions/${matchingId}/${sessionId}/complete`);
export const cancelLmsSession = (matchingId: number, sessionId: number) =>
  apiClient.put<ApiResponse<SessionListResponse>>(`/lms/sessions/${matchingId}/${sessionId}/cancel`);

// ─── Time Slots ───
export const getTimeSlots = (matchingId: number, month: string) =>
  apiClient.get<ApiResponse<TimeSlotResponse[]>>(`/lms/sessions/${matchingId}/slots`, { params: { month } });
export const getAvailableSlots = (matchingId: number, date: string) =>
  apiClient.get<ApiResponse<TimeSlotResponse[]>>(`/lms/sessions/${matchingId}/slots/available`, { params: { date } });
export const createTimeSlot = (matchingId: number, data: TimeSlotCreateRequest) =>
  apiClient.post<ApiResponse<TimeSlotResponse>>(`/lms/sessions/${matchingId}/slots`, data);
export const deleteTimeSlot = (matchingId: number, slotId: number) =>
  apiClient.delete<ApiResponse<void>>(`/lms/sessions/${matchingId}/slots/${slotId}`);

// ─── Booking ───
export const bookSession = (matchingId: number, data: BookSessionRequest) =>
  apiClient.post<ApiResponse<SessionListResponse>>(`/lms/sessions/${matchingId}/book`, data);

// ─── Change Requests ───
export const getChangeRequests = (matchingId: number, sessionId: number) =>
  apiClient.get<ApiResponse<ChangeRequestResponse[]>>(`/lms/sessions/${matchingId}/change-requests`, { params: { sessionId } });
export const createChangeRequest = (matchingId: number, data: ChangeRequestCreateRequest) =>
  apiClient.post<ApiResponse<ChangeRequestResponse>>(`/lms/sessions/${matchingId}/change-request`, data);
export const approveChangeRequest = (matchingId: number, requestId: number) =>
  apiClient.put<ApiResponse<ChangeRequestResponse>>(`/lms/sessions/${matchingId}/change-request/${requestId}/approve`);
export const rejectChangeRequest = (matchingId: number, requestId: number) =>
  apiClient.put<ApiResponse<ChangeRequestResponse>>(`/lms/sessions/${matchingId}/change-request/${requestId}/reject`);
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/lms-types.ts frontend/src/lib/lms.ts frontend/package.json frontend/package-lock.json
git commit -m "feat(lms): add calendar types, API functions, and fullcalendar interaction

Add TypeScript types for sessions, time slots, booking, and change requests.
Add API client functions for all LMS session endpoints.
Install @fullcalendar/interaction for date click handling."
```

---

## Task 8: Frontend — Sessions Page with Calendar

**Files:**
- Modify: `frontend/src/app/lms/sessions/page.tsx` (complete rewrite)

- [ ] **Step 1: Rewrite sessions page with FullCalendar**

Replace the entire contents of `frontend/src/app/lms/sessions/page.tsx`:

```tsx
'use client';

import { useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { Video, Clock, Plus, Check, X, AlertTriangle, ChevronRight, Loader2, Calendar as CalendarIcon } from 'lucide-react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import type { DateClickArg } from '@fullcalendar/interaction';
import type { EventInput } from '@fullcalendar/core';
import {
  getLmsSessions, completeLmsSession, cancelLmsSession,
  getTimeSlots, createTimeSlot, deleteTimeSlot,
  getAvailableSlots, bookSession,
  getChangeRequests, createChangeRequest, approveChangeRequest, rejectChangeRequest,
} from '@/lib/lms';
import type {
  SessionListResponse, TimeSlotResponse, ChangeRequestResponse,
} from '@/lib/lms-types';

export default function SessionsPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [sessions, setSessions] = useState<SessionListResponse[]>([]);
  const [slots, setSlots] = useState<TimeSlotResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  });

  // Modals
  const [slotModal, setSlotModal] = useState<{ date: string } | null>(null);
  const [bookingModal, setBookingModal] = useState<{ date: string; available: TimeSlotResponse[] } | null>(null);
  const [changeModal, setChangeModal] = useState<{ session: SessionListResponse } | null>(null);
  const [changeDetailModal, setChangeDetailModal] = useState<{ session: SessionListResponse; requests: ChangeRequestResponse[] } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Form state
  const [slotForm, setSlotForm] = useState({ startTime: '19:00', endTime: '20:00' });
  const [bookingMemo, setBookingMemo] = useState('');
  const [changeForm, setChangeForm] = useState({ newDate: '', newStartTime: '19:00', newEndTime: '20:00', reason: '' });

  const fetchData = useCallback(async () => {
    if (!matchingId) return;
    try {
      const [sessRes, slotsRes] = await Promise.all([
        getLmsSessions(matchingId),
        getTimeSlots(matchingId, currentMonth),
      ]);
      setSessions(sessRes.data.data || []);
      setSlots(slotsRes.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [matchingId, currentMonth]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Calendar events
  const calendarEvents: EventInput[] = [
    ...slots.map(s => ({
      id: `slot-${s.id}`,
      title: s.isBooked ? '예약됨' : '가능',
      start: s.slotDate,
      allDay: true,
      color: s.isBooked ? '#6366f1' : '#3b82f6',
      extendedProps: { type: 'slot' as const, ...s },
    })),
    ...sessions.map(s => ({
      id: `session-${s.id}`,
      title: s.status === 'COMPLETED' ? '완료' : s.status === 'CANCELLED' ? '취소' : '세션',
      start: s.sessionDate,
      allDay: true,
      color: s.status === 'COMPLETED' ? '#22c55e' : s.status === 'CANCELLED' ? '#ef4444' : '#8b5cf6',
      extendedProps: { type: 'session' as const, ...s },
    })),
  ];

  const handleDateClick = async (info: DateClickArg) => {
    setError('');
    if (isMentor) {
      setSlotForm({ startTime: '19:00', endTime: '20:00' });
      setSlotModal({ date: info.dateStr });
    } else {
      try {
        const res = await getAvailableSlots(matchingId, info.dateStr);
        const available = res.data.data || [];
        if (available.length === 0) {
          setError('해당 날짜에 예약 가능한 슬롯이 없습니다');
          return;
        }
        setBookingMemo('');
        setBookingModal({ date: info.dateStr, available });
      } catch { setError('슬롯 조회에 실패했습니다'); }
    }
  };

  const handleCreateSlot = async () => {
    if (!slotModal) return;
    setSubmitting(true); setError('');
    try {
      await createTimeSlot(matchingId, {
        slotDate: slotModal.date,
        startTime: slotForm.startTime,
        endTime: slotForm.endTime,
      });
      setSlotModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '슬롯 등록에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleDeleteSlot = async (slotId: number) => {
    try {
      await deleteTimeSlot(matchingId, slotId);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '슬롯 삭제에 실패했습니다');
    }
  };

  const handleBook = async (slotId: number) => {
    setSubmitting(true); setError('');
    try {
      await bookSession(matchingId, { slotId, memo: bookingMemo || undefined });
      setBookingModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '예약에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleComplete = async (sessionId: number) => {
    try {
      await completeLmsSession(matchingId, sessionId);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '완료 처리에 실패했습니다'); }
  };

  const handleCancel = async (sessionId: number) => {
    try {
      await cancelLmsSession(matchingId, sessionId);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '취소에 실패했습니다'); }
  };

  const handleCreateChange = async () => {
    if (!changeModal) return;
    setSubmitting(true); setError('');
    try {
      await createChangeRequest(matchingId, {
        sessionId: changeModal.session.id,
        newDate: changeForm.newDate,
        newStartTime: changeForm.newStartTime,
        newEndTime: changeForm.newEndTime,
        reason: changeForm.reason || undefined,
      });
      setChangeModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '변경 요청에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleViewChanges = async (session: SessionListResponse) => {
    try {
      const res = await getChangeRequests(matchingId, session.id);
      setChangeDetailModal({ session, requests: res.data.data || [] });
    } catch { setError('변경 요청 조회에 실패했습니다'); }
  };

  const handleApprove = async (requestId: number) => {
    try {
      await approveChangeRequest(matchingId, requestId);
      setChangeDetailModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '승인에 실패했습니다'); }
  };

  const handleReject = async (requestId: number) => {
    try {
      await rejectChangeRequest(matchingId, requestId);
      setChangeDetailModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '거절에 실패했습니다'); }
  };

  const statusLabel: Record<string, string> = { SCHEDULED: '예정', COMPLETED: '완료', CANCELLED: '취소' };
  const statusColor: Record<string, string> = {
    SCHEDULED: 'bg-blue-500/10 text-blue-400',
    COMPLETED: 'bg-green-500/10 text-green-400',
    CANCELLED: 'bg-red-500/10 text-red-400',
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">멘토링 세션</h1>
        <p className="text-gray-400 mt-1">
          {isMentor ? '날짜를 클릭하여 가용 시간을 등록하세요' : '파란 날짜를 클릭하여 세션을 예약하세요'}
        </p>
      </div>

      {error && (
        <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">{error}</div>
      )}

      {/* Calendar */}
      <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
        <style>{`
          .fc { --fc-border-color: rgba(255,255,255,0.05); --fc-today-bg-color: rgba(59,130,246,0.1); }
          .fc .fc-daygrid-day-number { color: #e5e7eb; font-size: 0.875rem; }
          .fc .fc-col-header-cell-cushion { color: #9ca3af; font-size: 0.75rem; text-transform: uppercase; }
          .fc .fc-button { background: rgba(59,130,246,0.2); border: 1px solid rgba(59,130,246,0.3); color: #93c5fd; font-size: 0.75rem; }
          .fc .fc-button:hover { background: rgba(59,130,246,0.3); }
          .fc .fc-button-active { background: rgba(59,130,246,0.4) !important; }
          .fc .fc-toolbar-title { color: white; font-size: 1.125rem; }
          .fc .fc-event { font-size: 0.625rem; padding: 1px 4px; border-radius: 4px; border: none; cursor: default; }
          .fc .fc-daygrid-day:hover { background: rgba(255,255,255,0.02); cursor: pointer; }
          .fc .fc-day-other .fc-daygrid-day-number { color: #4b5563; }
        `}</style>
        <FullCalendar
          plugins={[dayGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          locale="ko"
          headerToolbar={{ left: 'prev', center: 'title', right: 'next' }}
          events={calendarEvents}
          dateClick={handleDateClick}
          datesSet={(info) => {
            const mid = new Date((info.start.getTime() + info.end.getTime()) / 2);
            const m = `${mid.getFullYear()}-${String(mid.getMonth() + 1).padStart(2, '0')}`;
            if (m !== currentMonth) setCurrentMonth(m);
          }}
          height="auto"
        />
      </div>

      {/* Session List */}
      <div>
        <h2 className="text-lg font-semibold text-white mb-4">세션 목록</h2>
        {sessions.length === 0 ? (
          <div className="text-center py-12 text-gray-400">아직 예약된 세션이 없습니다.</div>
        ) : (
          <div className="space-y-4">
            {sessions.map((session) => (
              <div key={session.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-3">
                      <h3 className="text-white font-semibold">{session.category}</h3>
                      <span className={`px-2.5 py-0.5 rounded-md text-xs font-medium ${statusColor[session.status] || ''}`}>
                        {statusLabel[session.status] || session.status}
                      </span>
                      {session.hasPendingChangeRequest && (
                        <button
                          onClick={() => handleViewChanges(session)}
                          className="flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-colors"
                        >
                          <AlertTriangle size={12} />
                          변경 요청
                        </button>
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-2 text-gray-400 text-sm">
                      <Clock size={14} />
                      <span>{session.sessionDate} {session.startTime} ~ {session.endTime}</span>
                    </div>
                    {session.memo && <p className="text-gray-500 text-sm mt-2">{session.memo}</p>}
                  </div>
                  {session.status === 'SCHEDULED' && (
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => {
                          setChangeForm({ newDate: session.sessionDate, newStartTime: session.startTime?.slice(0,5) || '19:00', newEndTime: session.endTime?.slice(0,5) || '20:00', reason: '' });
                          setChangeModal({ session });
                        }}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-colors"
                      >
                        변경 요청
                      </button>
                      {isMentor && (
                        <button onClick={() => handleComplete(session.id)}
                          className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">
                          완료
                        </button>
                      )}
                      <button onClick={() => handleCancel(session.id)}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors">
                        취소
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ═══ Slot Create Modal (Mentor) ═══ */}
      {slotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">가용시간 등록 — {slotModal.date}</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">시작 시간</label>
                <input type="time" value={slotForm.startTime} onChange={e => setSlotForm({ ...slotForm, startTime: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">종료 시간</label>
                <input type="time" value={slotForm.endTime} onChange={e => setSlotForm({ ...slotForm, endTime: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setSlotModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">취소</button>
              <button onClick={handleCreateSlot} disabled={submitting}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ═══ Booking Modal (Mentee) ═══ */}
      {bookingModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">세션 예약 — {bookingModal.date}</h3>
            <p className="text-gray-400 text-sm mb-4">원하는 시간대를 선택하세요</p>
            <div className="space-y-2 mb-4">
              {bookingModal.available.map(slot => (
                <button key={slot.id} onClick={() => handleBook(slot.id)} disabled={submitting}
                  className="w-full flex items-center justify-between p-3 rounded-xl bg-white/3 border border-white/5 hover:border-blue-500/30 hover:bg-blue-500/5 transition-all text-left">
                  <div className="flex items-center gap-2 text-white text-sm">
                    <Clock size={14} className="text-blue-400" />
                    {slot.startTime} ~ {slot.endTime}
                  </div>
                  <ChevronRight size={16} className="text-gray-500" />
                </button>
              ))}
            </div>
            <div className="mb-4">
              <label className="text-gray-400 text-sm">메모 (선택)</label>
              <input type="text" value={bookingMemo} onChange={e => setBookingMemo(e.target.value)} placeholder="세션에 대한 메모"
                className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50" />
            </div>
            <button onClick={() => setBookingModal(null)} className="w-full py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">닫기</button>
          </div>
        </div>
      )}

      {/* ═══ Change Request Create Modal ═══ */}
      {changeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">세션 변경 요청</h3>
            <p className="text-gray-400 text-sm mb-4">현재: {changeModal.session.sessionDate} {changeModal.session.startTime} ~ {changeModal.session.endTime}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">변경 희망 날짜</label>
                <input type="date" value={changeForm.newDate} onChange={e => setChangeForm({ ...changeForm, newDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-gray-400 text-sm">시작 시간</label>
                  <input type="time" value={changeForm.newStartTime} onChange={e => setChangeForm({ ...changeForm, newStartTime: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div>
                  <label className="text-gray-400 text-sm">종료 시간</label>
                  <input type="time" value={changeForm.newEndTime} onChange={e => setChangeForm({ ...changeForm, newEndTime: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">변경 사유</label>
                <textarea value={changeForm.reason} onChange={e => setChangeForm({ ...changeForm, reason: e.target.value })} rows={2} placeholder="변경 사유를 입력하세요"
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setChangeModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">취소</button>
              <button onClick={handleCreateChange} disabled={submitting}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-amber-600 to-amber-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '변경 요청'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ═══ Change Request Detail Modal ═══ */}
      {changeDetailModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">변경 요청 내역</h3>
            <div className="space-y-4">
              {changeDetailModal.requests.map(cr => (
                <div key={cr.id} className="p-4 rounded-xl bg-white/3 border border-white/5">
                  <div className="flex items-center justify-between mb-2">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      cr.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400' :
                      cr.status === 'APPROVED' ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
                    }`}>{cr.status === 'PENDING' ? '대기' : cr.status === 'APPROVED' ? '승인' : '거절'}</span>
                    <span className="text-gray-500 text-xs">{new Date(cr.createdAt).toLocaleDateString('ko-KR')}</span>
                  </div>
                  <p className="text-white text-sm">{cr.newDate} {cr.newStartTime} ~ {cr.newEndTime}</p>
                  {cr.reason && <p className="text-gray-400 text-xs mt-1">{cr.reason}</p>}
                  {cr.status === 'PENDING' && cr.requesterId !== user?.id && (
                    <div className="flex gap-2 mt-3">
                      <button onClick={() => handleApprove(cr.id)}
                        className="flex-1 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">승인</button>
                      <button onClick={() => handleReject(cr.id)}
                        className="flex-1 py-1.5 rounded-lg text-xs font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors">거절</button>
                    </div>
                  )}
                  {cr.status === 'PENDING' && cr.requesterId === user?.id && (
                    <p className="text-gray-500 text-xs mt-2">상대방의 응답을 기다리고 있습니다</p>
                  )}
                </div>
              ))}
            </div>
            <button onClick={() => setChangeDetailModal(null)} className="w-full mt-4 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">닫기</button>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Verify in browser**

Navigate to `http://localhost:3000/lms/sessions?matchingId=1`. Confirm:
- Monthly calendar renders with FullCalendar
- Slot dots and session dots appear on calendar
- Date click opens appropriate modal (slot creation for mentor, booking for mentee)
- Session list appears below calendar with status badges and action buttons

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/lms/sessions/page.tsx
git commit -m "feat(lms): rewrite sessions page with FullCalendar calendar

Monthly calendar with mentor time slot registration and mentee booking.
Session list with complete/cancel actions and change request workflow.
Modals for slot creation, slot booking, change request, and change
request approval/rejection."
```

---

## Task 9: Frontend — Curriculum CRUD

**Files:**
- Modify: `frontend/src/app/lms/curriculum/page.tsx`

- [ ] **Step 1: Add CRUD modals to curriculum page**

Replace the entire contents of `frontend/src/app/lms/curriculum/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { BookOpen, CheckCircle, Circle, Plus, Loader2, ExternalLink } from 'lucide-react';
import { getCurriculum, createCurriculum, updateCurriculum, toggleWeekComplete } from '@/lib/lms';
import type { CurriculumResponse, CurriculumWeekResponse, CurriculumWeekRequest } from '@/lib/lms-types';

export default function CurriculumPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [curriculum, setCurriculum] = useState<CurriculumResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [createModal, setCreateModal] = useState(false);
  const [weekModal, setWeekModal] = useState<{ editing?: CurriculumWeekResponse } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Create form
  const [createForm, setCreateForm] = useState({ title: '', description: '', totalWeeks: 8, startDate: '', endDate: '' });
  // Week form
  const [weekForm, setWeekForm] = useState<CurriculumWeekRequest>({ weekNumber: 1, title: '', description: '', topics: [], resources: [] });
  const [topicInput, setTopicInput] = useState('');
  const [resourceInput, setResourceInput] = useState('');

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const res = await getCurriculum(matchingId);
      setCurriculum(res.data.data);
    } catch { setCurriculum(null); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId]);

  const handleToggle = async (weekId: number) => {
    try {
      await toggleWeekComplete(weekId);
      fetchData();
    } catch { setError('상태 변경에 실패했습니다'); }
  };

  const handleCreate = async () => {
    setSubmitting(true); setError('');
    try {
      await createCurriculum({
        matchingId, title: createForm.title, description: createForm.description,
        totalWeeks: createForm.totalWeeks, startDate: createForm.startDate, endDate: createForm.endDate, weeks: [],
      });
      setCreateModal(false);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '생성에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleUpdateCurriculum = async () => {
    if (!curriculum || !weekModal) return;
    setSubmitting(true); setError('');
    try {
      const existingWeeks: CurriculumWeekRequest[] = curriculum.weeks.map(w => ({
        weekNumber: w.weekNumber, title: w.title, description: w.description || '', topics: w.topics, resources: w.resources,
      }));
      if (weekModal.editing) {
        const idx = existingWeeks.findIndex(w => w.weekNumber === weekModal.editing!.weekNumber);
        if (idx >= 0) existingWeeks[idx] = weekForm;
      } else {
        existingWeeks.push(weekForm);
      }
      await updateCurriculum(curriculum.id, {
        matchingId, title: curriculum.title, description: curriculum.description || '',
        totalWeeks: Math.max(curriculum.totalWeeks, existingWeeks.length),
        startDate: curriculum.startDate, endDate: curriculum.endDate, weeks: existingWeeks,
      });
      setWeekModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '수정에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const completedCount = curriculum?.weeks.filter(w => w.isCompleted).length || 0;
  const totalCount = curriculum?.weeks.length || 0;
  const progressPct = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  if (!curriculum) {
    return (
      <div className="space-y-8">
        <h1 className="text-2xl font-bold text-white">커리큘럼</h1>
        <div className="text-center py-20">
          <BookOpen size={48} className="mx-auto text-gray-600 mb-4" />
          <p className="text-gray-400 mb-4">아직 등록된 커리큘럼이 없습니다.</p>
          {isMentor && (
            <button onClick={() => setCreateModal(true)}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold text-white bg-gradient-to-r from-blue-600 to-blue-500">
              <Plus size={16} />커리큘럼 만들기
            </button>
          )}
        </div>
        {createModal && renderCreateModal()}
      </div>
    );
  }

  function renderCreateModal() {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="glass-card rounded-2xl w-full max-w-md p-6">
          <h3 className="text-lg font-semibold text-white mb-4">커리큘럼 생성</h3>
          {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
          <div className="space-y-4">
            <div>
              <label className="text-gray-400 text-sm">제목</label>
              <input type="text" value={createForm.title} onChange={e => setCreateForm({ ...createForm, title: e.target.value })} placeholder="예: Java Backend 마스터 과정"
                className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
            </div>
            <div>
              <label className="text-gray-400 text-sm">설명</label>
              <textarea value={createForm.description} onChange={e => setCreateForm({ ...createForm, description: e.target.value })} rows={2}
                className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="text-gray-400 text-sm">총 주차</label>
                <input type="number" value={createForm.totalWeeks} onChange={e => setCreateForm({ ...createForm, totalWeeks: Number(e.target.value) })} min={1}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">시작일</label>
                <input type="date" value={createForm.startDate} onChange={e => setCreateForm({ ...createForm, startDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">종료일</label>
                <input type="date" value={createForm.endDate} onChange={e => setCreateForm({ ...createForm, endDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
            </div>
          </div>
          <div className="flex gap-3 mt-6">
            <button onClick={() => setCreateModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
            <button onClick={handleCreate} disabled={submitting || !createForm.title || !createForm.startDate || !createForm.endDate}
              className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
              {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '생성'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">{curriculum.title}</h1>
          {curriculum.description && <p className="text-gray-400 mt-1">{curriculum.description}</p>}
        </div>
        {isMentor && (
          <button onClick={() => {
            setWeekForm({ weekNumber: (curriculum.weeks.length || 0) + 1, title: '', description: '', topics: [], resources: [] });
            setTopicInput(''); setResourceInput('');
            setWeekModal({});
          }} className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
            <Plus size={16} />주차 추가
          </button>
        )}
      </div>

      {/* Progress Bar */}
      <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
        <div className="flex items-center justify-between mb-3">
          <span className="text-gray-400 text-sm">전체 진도</span>
          <span className="text-white text-sm font-semibold">{completedCount}/{totalCount} 주차 ({progressPct}%)</span>
        </div>
        <div className="w-full h-2 bg-white/5 rounded-full overflow-hidden">
          <div className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full transition-all duration-500" style={{ width: `${progressPct}%` }} />
        </div>
      </div>

      {/* Week List */}
      <div className="space-y-4">
        {curriculum.weeks.sort((a, b) => a.weekNumber - b.weekNumber).map(week => (
          <div key={week.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
            <div className="flex items-start gap-4">
              <button onClick={() => handleToggle(week.id)} className="mt-0.5 shrink-0">
                {week.isCompleted ? <CheckCircle size={22} className="text-green-400" /> : <Circle size={22} className="text-gray-600 hover:text-blue-400 transition-colors" />}
              </button>
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <span className="text-blue-400 text-xs font-semibold">{week.weekNumber}주차</span>
                  <h3 className={`text-white font-semibold ${week.isCompleted ? 'line-through opacity-60' : ''}`}>{week.title}</h3>
                  {isMentor && (
                    <button onClick={() => {
                      setWeekForm({ weekNumber: week.weekNumber, title: week.title, description: week.description || '', topics: [...week.topics], resources: [...week.resources] });
                      setTopicInput(''); setResourceInput('');
                      setWeekModal({ editing: week });
                    }} className="text-gray-500 hover:text-blue-400 text-xs transition-colors">수정</button>
                  )}
                </div>
                {week.description && <p className="text-gray-400 text-sm mt-1">{week.description}</p>}
                {week.topics.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-3">
                    {week.topics.map((t, i) => <span key={i} className="px-2 py-0.5 rounded-md text-xs bg-blue-500/10 text-blue-400">{t}</span>)}
                  </div>
                )}
                {week.resources.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-2">
                    {week.resources.map((r, i) => (
                      <a key={i} href={r} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 text-xs text-cyan-400 hover:text-cyan-300">
                        <ExternalLink size={12} />{new URL(r).hostname}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Week Add/Edit Modal */}
      {weekModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">{weekModal.editing ? `${weekModal.editing.weekNumber}주차 수정` : '주차 추가'}</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              {!weekModal.editing && (
                <div>
                  <label className="text-gray-400 text-sm">주차 번호</label>
                  <input type="number" value={weekForm.weekNumber} onChange={e => setWeekForm({ ...weekForm, weekNumber: Number(e.target.value) })} min={1}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              )}
              <div>
                <label className="text-gray-400 text-sm">제목</label>
                <input type="text" value={weekForm.title} onChange={e => setWeekForm({ ...weekForm, title: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">설명</label>
                <textarea value={weekForm.description} onChange={e => setWeekForm({ ...weekForm, description: e.target.value })} rows={2}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">주제 (Enter로 추가)</label>
                <div className="flex gap-2 mt-1">
                  <input type="text" value={topicInput} onChange={e => setTopicInput(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && topicInput.trim()) { e.preventDefault(); setWeekForm({ ...weekForm, topics: [...weekForm.topics, topicInput.trim()] }); setTopicInput(''); } }}
                    className="flex-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {weekForm.topics.map((t, i) => (
                    <span key={i} className="flex items-center gap-1 px-2 py-0.5 rounded-md text-xs bg-blue-500/10 text-blue-400">
                      {t} <button onClick={() => setWeekForm({ ...weekForm, topics: weekForm.topics.filter((_, j) => j !== i) })} className="hover:text-red-400">×</button>
                    </span>
                  ))}
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">학습자료 URL (Enter로 추가)</label>
                <div className="flex gap-2 mt-1">
                  <input type="text" value={resourceInput} onChange={e => setResourceInput(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && resourceInput.trim()) { e.preventDefault(); setWeekForm({ ...weekForm, resources: [...weekForm.resources, resourceInput.trim()] }); setResourceInput(''); } }}
                    className="flex-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {weekForm.resources.map((r, i) => (
                    <span key={i} className="flex items-center gap-1 px-2 py-0.5 rounded-md text-xs bg-cyan-500/10 text-cyan-400 max-w-[200px] truncate">
                      {r} <button onClick={() => setWeekForm({ ...weekForm, resources: weekForm.resources.filter((_, j) => j !== i) })} className="hover:text-red-400 shrink-0">×</button>
                    </span>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setWeekModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleUpdateCurriculum} disabled={submitting || !weekForm.title}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : (weekModal.editing ? '수정' : '추가')}
              </button>
            </div>
          </div>
        </div>
      )}
      {createModal && renderCreateModal()}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/lms/curriculum/page.tsx
git commit -m "feat(lms): add curriculum CRUD with create and week management

Mentor can create curriculum and add/edit weeks with topics and resources.
Mentee can toggle week completion. Progress bar shows overall progress.
Modal-based forms with tag input for topics and resource URLs."
```

---

## Task 10: Frontend — Assignments CRUD

**Files:**
- Modify: `frontend/src/app/lms/assignments/page.tsx`

- [ ] **Step 1: Add CRUD modals to assignments page**

Replace the entire contents of `frontend/src/app/lms/assignments/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { ClipboardList, Plus, Loader2, Clock, Star, ExternalLink } from 'lucide-react';
import { getAssignments, createAssignment, submitAssignment, feedbackAssignment } from '@/lib/lms';
import type { AssignmentResponse, AssignmentType, AssignmentStatus } from '@/lib/lms-types';

export default function AssignmentsPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');
  const [createModal, setCreateModal] = useState(false);
  const [submitModal, setSubmitModal] = useState<AssignmentResponse | null>(null);
  const [feedbackModal, setFeedbackModal] = useState<AssignmentResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [createForm, setCreateForm] = useState({ type: 'TASK' as AssignmentType, title: '', description: '', dueDate: '' });
  const [submitForm, setSubmitForm] = useState({ submissionUrl: '', submissionNote: '' });
  const [fbForm, setFbForm] = useState({ feedbackContent: '', grade: '' });

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const res = await getAssignments(matchingId, filter || undefined);
      setAssignments(res.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId, filter]);

  const handleCreate = async () => {
    setSubmitting(true); setError('');
    try {
      await createAssignment({ matchingId, ...createForm, dueDate: createForm.dueDate || undefined });
      setCreateModal(false);
      setCreateForm({ type: 'TASK', title: '', description: '', dueDate: '' });
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '생성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleSubmit = async () => {
    if (!submitModal) return;
    setSubmitting(true); setError('');
    try {
      await submitAssignment(submitModal.id, { submissionUrl: submitForm.submissionUrl, submissionNote: submitForm.submissionNote || undefined });
      setSubmitModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '제출에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleFeedback = async () => {
    if (!feedbackModal) return;
    setSubmitting(true); setError('');
    try {
      await feedbackAssignment(feedbackModal.id, { feedbackContent: fbForm.feedbackContent, grade: fbForm.grade || undefined });
      setFeedbackModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '피드백 작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const statusLabel: Record<string, string> = { ASSIGNED: '미제출', SUBMITTED: '제출됨', REVIEWED: '리뷰 완료' };
  const statusColor: Record<string, string> = {
    ASSIGNED: 'bg-amber-500/10 text-amber-400',
    SUBMITTED: 'bg-blue-500/10 text-blue-400',
    REVIEWED: 'bg-green-500/10 text-green-400',
  };
  const typeLabel: Record<string, string> = { TASK: '과제', CODE_REVIEW: '코드리뷰' };

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">과제 / 코드리뷰</h1>
          <p className="text-gray-400 mt-1">과제를 확인하고 제출하세요</p>
        </div>
        {isMentor && (
          <button onClick={() => setCreateModal(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
            <Plus size={16} />과제 만들기
          </button>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2">
        {[{ label: '전체', value: '' }, { label: '과제', value: 'TASK' }, { label: '코드리뷰', value: 'CODE_REVIEW' }].map(tab => (
          <button key={tab.value} onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${filter === tab.value ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
            {tab.label}
          </button>
        ))}
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {assignments.length === 0 ? (
        <div className="text-center py-20">
          <ClipboardList size={48} className="mx-auto text-gray-600 mb-4" />
          <p className="text-gray-400">아직 등록된 과제가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {assignments.map(a => (
            <div key={a.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    <span className="px-2 py-0.5 rounded text-xs bg-violet-500/10 text-violet-400">{typeLabel[a.type]}</span>
                    <span className={`px-2 py-0.5 rounded text-xs ${statusColor[a.status]}`}>{statusLabel[a.status]}</span>
                  </div>
                  <h3 className="text-white font-semibold">{a.title}</h3>
                  {a.description && <p className="text-gray-400 text-sm mt-1 line-clamp-2">{a.description}</p>}
                  {a.dueDate && (
                    <div className="flex items-center gap-1.5 mt-2 text-gray-500 text-xs">
                      <Clock size={12} />마감: {a.dueDate}
                    </div>
                  )}
                  {/* Submission Info */}
                  {a.submission && (
                    <div className="mt-3 p-3 rounded-xl bg-white/3 border border-white/5">
                      <a href={a.submission.submissionUrl} target="_blank" rel="noopener noreferrer"
                        className="flex items-center gap-1 text-cyan-400 text-sm hover:text-cyan-300">
                        <ExternalLink size={14} />{a.submission.submissionUrl}
                      </a>
                      {a.submission.submissionNote && <p className="text-gray-400 text-xs mt-1">{a.submission.submissionNote}</p>}
                      {a.submission.feedbackContent && (
                        <div className="mt-2 pt-2 border-t border-white/5">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-green-400 text-xs font-medium">멘토 피드백</span>
                            {a.submission.grade && <span className="flex items-center gap-0.5 text-amber-400 text-xs"><Star size={10} />{a.submission.grade}</span>}
                          </div>
                          <p className="text-gray-300 text-sm">{a.submission.feedbackContent}</p>
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <div className="flex gap-2 shrink-0 ml-4">
                  {!isMentor && a.status === 'ASSIGNED' && (
                    <button onClick={() => { setSubmitForm({ submissionUrl: '', submissionNote: '' }); setSubmitModal(a); }}
                      className="px-3 py-1.5 rounded-lg text-xs font-medium bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors">제출</button>
                  )}
                  {isMentor && a.status === 'SUBMITTED' && (
                    <button onClick={() => { setFbForm({ feedbackContent: '', grade: '' }); setFeedbackModal(a); }}
                      className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">피드백</button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {createModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">과제 만들기</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">타입</label>
                <select value={createForm.type} onChange={e => setCreateForm({ ...createForm, type: e.target.value as AssignmentType })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50">
                  <option value="TASK">과제</option>
                  <option value="CODE_REVIEW">코드리뷰</option>
                </select>
              </div>
              <div>
                <label className="text-gray-400 text-sm">제목</label>
                <input type="text" value={createForm.title} onChange={e => setCreateForm({ ...createForm, title: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">설명</label>
                <textarea value={createForm.description} onChange={e => setCreateForm({ ...createForm, description: e.target.value })} rows={3}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">마감일</label>
                <input type="date" value={createForm.dueDate} onChange={e => setCreateForm({ ...createForm, dueDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setCreateModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleCreate} disabled={submitting || !createForm.title}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Submit Modal */}
      {submitModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-2">과제 제출</h3>
            <p className="text-gray-400 text-sm mb-4">{submitModal.title}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">GitHub URL</label>
                <input type="url" value={submitForm.submissionUrl} onChange={e => setSubmitForm({ ...submitForm, submissionUrl: e.target.value })} placeholder="https://github.com/..."
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">메모</label>
                <textarea value={submitForm.submissionNote} onChange={e => setSubmitForm({ ...submitForm, submissionNote: e.target.value })} rows={2} placeholder="제출 관련 메모"
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setSubmitModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleSubmit} disabled={submitting || !submitForm.submissionUrl}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '제출'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Feedback Modal */}
      {feedbackModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-2">피드백 작성</h3>
            <p className="text-gray-400 text-sm mb-4">{feedbackModal.title}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">피드백 내용</label>
                <textarea value={fbForm.feedbackContent} onChange={e => setFbForm({ ...fbForm, feedbackContent: e.target.value })} rows={4}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">등급</label>
                <select value={fbForm.grade} onChange={e => setFbForm({ ...fbForm, grade: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50">
                  <option value="">선택 안함</option>
                  <option value="A">A</option><option value="B">B</option><option value="C">C</option><option value="D">D</option><option value="F">F</option>
                </select>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setFeedbackModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleFeedback} disabled={submitting || !fbForm.feedbackContent}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-green-600 to-green-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '피드백 등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/lms/assignments/page.tsx
git commit -m "feat(lms): add assignment CRUD with create, submit, and feedback

Mentor can create assignments (TASK/CODE_REVIEW) and write feedback with grades.
Mentee can submit assignments with GitHub URL. Filter tabs for type filtering.
Submission details and mentor feedback shown inline on assignment cards."
```

---

## Task 11: Frontend — Notes CRUD

**Files:**
- Modify: `frontend/src/app/lms/notes/page.tsx`

- [ ] **Step 1: Add CRUD modals to notes page**

Replace the entire contents of `frontend/src/app/lms/notes/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { NotebookPen, Plus, Loader2, Star, MessageSquare, ChevronDown, ChevronUp, Send } from 'lucide-react';
import { getNotes, getNote, createNote, addNoteComment } from '@/lib/lms';
import type { NoteResponse, NoteType, NoteCommentResponse } from '@/lib/lms-types';

export default function NotesPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentee = user?.role === 'MENTEE';

  const [notes, setNotes] = useState<NoteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');
  const [createModal, setCreateModal] = useState(false);
  const [expandedNote, setExpandedNote] = useState<number | null>(null);
  const [detailNote, setDetailNote] = useState<NoteResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [createForm, setCreateForm] = useState({ type: 'SESSION_REVIEW' as NoteType, title: '', content: '', weekNumber: 1, selfRating: 3 });
  const [commentInput, setCommentInput] = useState('');

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const res = await getNotes(matchingId, filter || undefined);
      setNotes(res.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId, filter]);

  const handleCreate = async () => {
    setSubmitting(true); setError('');
    try {
      await createNote({ matchingId, type: createForm.type, title: createForm.title, content: createForm.content, weekNumber: createForm.weekNumber, selfRating: createForm.selfRating });
      setCreateModal(false);
      setCreateForm({ type: 'SESSION_REVIEW', title: '', content: '', weekNumber: 1, selfRating: 3 });
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleExpand = async (noteId: number) => {
    if (expandedNote === noteId) { setExpandedNote(null); setDetailNote(null); return; }
    try {
      const res = await getNote(noteId);
      setDetailNote(res.data.data);
      setExpandedNote(noteId);
      setCommentInput('');
    } catch { setError('노트 조회에 실패했습니다'); }
  };

  const handleComment = async () => {
    if (!detailNote || !commentInput.trim()) return;
    setSubmitting(true);
    try {
      await addNoteComment(detailNote.id, { content: commentInput });
      const res = await getNote(detailNote.id);
      setDetailNote(res.data.data);
      setCommentInput('');
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '코멘트 작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const typeLabel: Record<string, string> = { SESSION_REVIEW: '세션 리뷰', WEEKLY_JOURNAL: '주간 학습일지' };
  const typeColor: Record<string, string> = { SESSION_REVIEW: 'bg-blue-500/10 text-blue-400', WEEKLY_JOURNAL: 'bg-violet-500/10 text-violet-400' };

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">학습 노트</h1>
          <p className="text-gray-400 mt-1">학습 내용을 기록하고 피드백을 받으세요</p>
        </div>
        {isMentee && (
          <button onClick={() => setCreateModal(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
            <Plus size={16} />노트 작성
          </button>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2">
        {[{ label: '전체', value: '' }, { label: '세션 리뷰', value: 'SESSION_REVIEW' }, { label: '주간 학습일지', value: 'WEEKLY_JOURNAL' }].map(tab => (
          <button key={tab.value} onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${filter === tab.value ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
            {tab.label}
          </button>
        ))}
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {notes.length === 0 ? (
        <div className="text-center py-20">
          <NotebookPen size={48} className="mx-auto text-gray-600 mb-4" />
          <p className="text-gray-400">아직 작성된 노트가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {notes.map(note => (
            <div key={note.id} className="bg-[#0f1420] border border-white/5 rounded-2xl overflow-hidden">
              <button onClick={() => handleExpand(note.id)} className="w-full p-6 text-left">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <span className={`px-2 py-0.5 rounded text-xs ${typeColor[note.type]}`}>{typeLabel[note.type]}</span>
                      {note.weekNumber && <span className="text-gray-500 text-xs">{note.weekNumber}주차</span>}
                      {note.selfRating && (
                        <span className="flex items-center gap-0.5 text-amber-400 text-xs">
                          <Star size={10} fill="currentColor" />{note.selfRating}
                        </span>
                      )}
                    </div>
                    <h3 className="text-white font-semibold">{note.title}</h3>
                    <p className="text-gray-400 text-sm mt-1 line-clamp-2">{note.content}</p>
                    <div className="flex items-center gap-3 mt-2 text-gray-500 text-xs">
                      <span>{note.authorName}</span>
                      <span>{new Date(note.createdAt).toLocaleDateString('ko-KR')}</span>
                      <span className="flex items-center gap-1"><MessageSquare size={10} />{note.comments?.length || 0}</span>
                    </div>
                  </div>
                  <div className="shrink-0 ml-4 mt-1">
                    {expandedNote === note.id ? <ChevronUp size={18} className="text-gray-500" /> : <ChevronDown size={18} className="text-gray-500" />}
                  </div>
                </div>
              </button>

              {/* Expanded Detail */}
              {expandedNote === note.id && detailNote && (
                <div className="px-6 pb-6 border-t border-white/5 pt-4">
                  <div className="text-gray-300 text-sm whitespace-pre-wrap mb-6">{detailNote.content}</div>

                  {/* Comments */}
                  <div className="space-y-3">
                    <h4 className="text-gray-400 text-sm font-medium">코멘트 ({detailNote.comments?.length || 0})</h4>
                    {detailNote.comments?.map(c => (
                      <div key={c.id} className="p-3 rounded-xl bg-white/3 border border-white/5">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-white text-xs font-medium">{c.authorName}</span>
                          <span className="text-gray-600 text-xs">{new Date(c.createdAt).toLocaleDateString('ko-KR')}</span>
                        </div>
                        <p className="text-gray-300 text-sm">{c.content}</p>
                      </div>
                    ))}

                    {/* Comment Input */}
                    <div className="flex gap-2">
                      <input type="text" value={commentInput} onChange={e => setCommentInput(e.target.value)} placeholder="코멘트를 입력하세요"
                        onKeyDown={e => { if (e.key === 'Enter') handleComment(); }}
                        className="flex-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50" />
                      <button onClick={handleComment} disabled={submitting || !commentInput.trim()}
                        className="px-3 py-2 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors disabled:opacity-60">
                        <Send size={16} />
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {createModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-lg p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">학습 노트 작성</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-gray-400 text-sm">타입</label>
                  <select value={createForm.type} onChange={e => setCreateForm({ ...createForm, type: e.target.value as NoteType })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50">
                    <option value="SESSION_REVIEW">세션 리뷰</option>
                    <option value="WEEKLY_JOURNAL">주간 학습일지</option>
                  </select>
                </div>
                <div>
                  <label className="text-gray-400 text-sm">주차</label>
                  <input type="number" value={createForm.weekNumber} onChange={e => setCreateForm({ ...createForm, weekNumber: Number(e.target.value) })} min={1}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">제목</label>
                <input type="text" value={createForm.title} onChange={e => setCreateForm({ ...createForm, title: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">내용</label>
                <textarea value={createForm.content} onChange={e => setCreateForm({ ...createForm, content: e.target.value })} rows={8}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">자기 평가 ({createForm.selfRating}/5)</label>
                <div className="flex gap-1 mt-1">
                  {[1, 2, 3, 4, 5].map(n => (
                    <button key={n} onClick={() => setCreateForm({ ...createForm, selfRating: n })}>
                      <Star size={20} className={n <= createForm.selfRating ? 'text-amber-400 fill-amber-400' : 'text-gray-600'} />
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setCreateModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleCreate} disabled={submitting || !createForm.title || !createForm.content}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '작성'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/lms/notes/page.tsx
git commit -m "feat(lms): add notes CRUD with create, detail view, and comments

Mentee can create notes (SESSION_REVIEW/WEEKLY_JOURNAL) with self-rating.
Expandable note cards show full content and comments inline.
Both mentor and mentee can add comments with Enter-to-send."
```

---

## Task 12: Frontend — Career CRUD

**Files:**
- Modify: `frontend/src/app/lms/career/page.tsx`

- [ ] **Step 1: Add CRUD modals to career page**

Replace the entire contents of `frontend/src/app/lms/career/page.tsx`:

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { Briefcase, FileUp, Plus, Loader2, Star, MessageSquare, ExternalLink } from 'lucide-react';
import { getResumes, uploadResume, feedbackResume, getMockInterviews, createMockInterview } from '@/lib/lms';
import type { ResumeResponse, MockInterviewResponse } from '@/lib/lms-types';

export default function CareerPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';
  const isMentee = user?.role === 'MENTEE';

  const [tab, setTab] = useState<'resume' | 'interview'>('resume');
  const [resumes, setResumes] = useState<ResumeResponse[]>([]);
  const [interviews, setInterviews] = useState<MockInterviewResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Modals
  const [uploadModal, setUploadModal] = useState(false);
  const [fbModal, setFbModal] = useState<ResumeResponse | null>(null);
  const [interviewModal, setInterviewModal] = useState(false);

  // Forms
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fbContent, setFbContent] = useState('');
  const [interviewForm, setInterviewForm] = useState({ interviewDate: '', topic: '', questionsAndAnswers: '', mentorFeedback: '', rating: 3 });

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const [rRes, iRes] = await Promise.all([getResumes(matchingId), getMockInterviews(matchingId)]);
      setResumes(rRes.data.data || []);
      setInterviews(iRes.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId]);

  const handleUpload = async () => {
    if (!selectedFile) return;
    setSubmitting(true); setError('');
    try {
      await uploadResume(matchingId, selectedFile);
      setUploadModal(false); setSelectedFile(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '업로드에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleFeedback = async () => {
    if (!fbModal) return;
    setSubmitting(true); setError('');
    try {
      await feedbackResume(fbModal.id, { mentorFeedback: fbContent });
      setFbModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '피드백 작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleCreateInterview = async () => {
    setSubmitting(true); setError('');
    try {
      await createMockInterview({
        matchingId, interviewDate: interviewForm.interviewDate, topic: interviewForm.topic,
        questionsAndAnswers: interviewForm.questionsAndAnswers || undefined,
        mentorFeedback: interviewForm.mentorFeedback || undefined, rating: interviewForm.rating,
      });
      setInterviewModal(false);
      setInterviewForm({ interviewDate: '', topic: '', questionsAndAnswers: '', mentorFeedback: '', rating: 3 });
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '생성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">취업 지원</h1>
        <p className="text-gray-400 mt-1">이력서 관리와 모의면접 기록</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2">
        <button onClick={() => setTab('resume')}
          className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${tab === 'resume' ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
          이력서
        </button>
        <button onClick={() => setTab('interview')}
          className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${tab === 'interview' ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
          모의면접
        </button>
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {/* Resume Tab */}
      {tab === 'resume' && (
        <div className="space-y-4">
          {isMentee && (
            <button onClick={() => setUploadModal(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
              <FileUp size={16} />이력서 업로드
            </button>
          )}
          {resumes.length === 0 ? (
            <div className="text-center py-20">
              <Briefcase size={48} className="mx-auto text-gray-600 mb-4" />
              <p className="text-gray-400">등록된 이력서가 없습니다.</p>
            </div>
          ) : (
            resumes.map(r => (
              <div key={r.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-3 mb-1">
                      <h3 className="text-white font-semibold">{r.fileName}</h3>
                      <span className="px-2 py-0.5 rounded text-xs bg-blue-500/10 text-blue-400">v{r.version}</span>
                    </div>
                    <p className="text-gray-500 text-xs">{new Date(r.uploadedAt).toLocaleDateString('ko-KR')}</p>
                    {r.mentorFeedback && (
                      <div className="mt-3 p-3 rounded-xl bg-white/3 border border-white/5">
                        <span className="text-green-400 text-xs font-medium">멘토 피드백</span>
                        <p className="text-gray-300 text-sm mt-1">{r.mentorFeedback}</p>
                      </div>
                    )}
                  </div>
                  <div className="flex gap-2 shrink-0 ml-4">
                    <a href={r.fileUrl} target="_blank" rel="noopener noreferrer"
                      className="px-3 py-1.5 rounded-lg text-xs font-medium bg-cyan-500/10 text-cyan-400 hover:bg-cyan-500/20 transition-colors">
                      <ExternalLink size={14} />
                    </a>
                    {isMentor && !r.mentorFeedback && (
                      <button onClick={() => { setFbContent(''); setFbModal(r); }}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">피드백</button>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Interview Tab */}
      {tab === 'interview' && (
        <div className="space-y-4">
          {isMentor && (
            <button onClick={() => setInterviewModal(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
              <Plus size={16} />모의면접 기록
            </button>
          )}
          {interviews.length === 0 ? (
            <div className="text-center py-20">
              <MessageSquare size={48} className="mx-auto text-gray-600 mb-4" />
              <p className="text-gray-400">모의면접 기록이 없습니다.</p>
            </div>
          ) : (
            interviews.map(iv => (
              <div key={iv.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-center gap-3 mb-2">
                  <h3 className="text-white font-semibold">{iv.topic}</h3>
                  {iv.rating && (
                    <span className="flex items-center gap-0.5 text-amber-400 text-xs">
                      <Star size={10} fill="currentColor" />{iv.rating}/5
                    </span>
                  )}
                </div>
                <p className="text-gray-500 text-xs mb-3">{iv.interviewDate}</p>
                {iv.questionsAndAnswers && <p className="text-gray-400 text-sm whitespace-pre-wrap mb-3">{iv.questionsAndAnswers}</p>}
                {iv.mentorFeedback && (
                  <div className="p-3 rounded-xl bg-white/3 border border-white/5">
                    <span className="text-green-400 text-xs font-medium">피드백</span>
                    <p className="text-gray-300 text-sm mt-1">{iv.mentorFeedback}</p>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* Upload Modal */}
      {uploadModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">이력서 업로드</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="mb-4">
              <input type="file" accept=".pdf,.doc,.docx" onChange={e => setSelectedFile(e.target.files?.[0] || null)}
                className="w-full text-sm text-gray-400 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-blue-500/10 file:text-blue-400 hover:file:bg-blue-500/20" />
            </div>
            <div className="flex gap-3">
              <button onClick={() => setUploadModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleUpload} disabled={submitting || !selectedFile}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '업로드'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Resume Feedback Modal */}
      {fbModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-2">이력서 피드백</h3>
            <p className="text-gray-400 text-sm mb-4">{fbModal.fileName} v{fbModal.version}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <textarea value={fbContent} onChange={e => setFbContent(e.target.value)} rows={5} placeholder="이력서에 대한 피드백을 작성하세요"
              className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
            <div className="flex gap-3 mt-4">
              <button onClick={() => setFbModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleFeedback} disabled={submitting || !fbContent.trim()}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-green-600 to-green-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '피드백 등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Interview Create Modal */}
      {interviewModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-lg p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">모의면접 기록</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-gray-400 text-sm">면접 날짜</label>
                  <input type="date" value={interviewForm.interviewDate} onChange={e => setInterviewForm({ ...interviewForm, interviewDate: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div>
                  <label className="text-gray-400 text-sm">주제</label>
                  <input type="text" value={interviewForm.topic} onChange={e => setInterviewForm({ ...interviewForm, topic: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">질문 & 답변</label>
                <textarea value={interviewForm.questionsAndAnswers} onChange={e => setInterviewForm({ ...interviewForm, questionsAndAnswers: e.target.value })} rows={4}
                  placeholder="Q: 질문 내용&#10;A: 답변 내용"
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">피드백</label>
                <textarea value={interviewForm.mentorFeedback} onChange={e => setInterviewForm({ ...interviewForm, mentorFeedback: e.target.value })} rows={3}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">평가 ({interviewForm.rating}/5)</label>
                <div className="flex gap-1 mt-1">
                  {[1, 2, 3, 4, 5].map(n => (
                    <button key={n} onClick={() => setInterviewForm({ ...interviewForm, rating: n })}>
                      <Star size={20} className={n <= interviewForm.rating ? 'text-amber-400 fill-amber-400' : 'text-gray-600'} />
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setInterviewModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleCreateInterview} disabled={submitting || !interviewForm.interviewDate || !interviewForm.topic}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '기록 저장'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/lms/career/page.tsx
git commit -m "feat(lms): add career page CRUD with resume upload and mock interviews

Mentee can upload resumes (file upload). Mentor can write resume feedback
and create mock interview records with Q&A and ratings. Tab-based UI
for resume vs interview sections."
```

---

## Task 13: E2E Verification

- [ ] **Step 1: Restart backend**

Restart the Spring Boot application in IntelliJ to trigger Hibernate table creation for new entities (MentorTimeSlot, SessionChangeRequest).

- [ ] **Step 2: Drop and recreate unique index on mentoring_sessions**

```bash
docker exec -i devmatch-mysql mysql -u root -proot devmatch -e "
  SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_NAME = 'mentoring_sessions' AND CONSTRAINT_TYPE = 'UNIQUE';
"
```

If a unique constraint exists on matching_id, drop it:
```bash
docker exec -i devmatch-mysql mysql -u root -proot devmatch -e "
  ALTER TABLE mentoring_sessions DROP INDEX <constraint_name>;
"
```

- [ ] **Step 3: Run seed data**

```bash
docker exec -i devmatch-mysql mysql -u root -proot devmatch < backend/src/main/resources/seed-lms.sql
```

- [ ] **Step 4: Verify frontend**

Test the following flows:
1. Login as 가나다 (mentee) → Mypage → LMS button visible → Click → LMS Dashboard loads
2. Sessions page: Calendar renders, available slots shown, click date to book
3. Curriculum page: View weeks with progress bar, toggle completion
4. Assignments page: View assignments with status badges, submit for ASSIGNED assignments
5. Notes page: View notes, expand for detail/comments, create new note
6. Career page: Upload resume, view mock interviews

6. Login as 김자바 (mentor) → LMS:
7. Sessions page: Click date to add time slot, complete sessions
8. Curriculum page: Create curriculum, add/edit weeks
9. Assignments page: Create assignment, write feedback for SUBMITTED
10. Notes page: Add comments
11. Career page: Write resume feedback, create mock interview record

- [ ] **Step 5: Final commit**

```bash
git add -A
git status
# If any remaining changes, commit:
git commit -m "chore: final adjustments after E2E verification"
```

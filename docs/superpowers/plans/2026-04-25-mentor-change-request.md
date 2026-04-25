# 멘토 교체 신청·심사 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 멘티가 사유와 함께 멘토 교체를 신청하면, 관리자가 새 멘토를 선택해 승인하거나 반려할 수 있는 워크플로우를 추가한다.

**Architecture:** 신규 `MentorChangeRequest` 엔티티 + 두 개의 서비스(멘티용, 관리자용)를 추가하고, 승인 시 기존 `MentorSwapService.swap()` 을 재사용한다. 관리자 프론트는 `admin/posts` 패턴(목록 + 상세)을 따른다. 멘티 프론트 UI는 본 계획 범위 밖(다른 담당자 작업).

**Tech Stack:** Spring Boot 3.4.4 (Java 17, Spring Data JPA, Spring Security), JUnit 5 + Mockito + AssertJ, Next.js 14 (TypeScript) + Tailwind/shadcn-ui

**Spec:** [docs/superpowers/specs/2026-04-25-mentor-change-request-design.md](../specs/2026-04-25-mentor-change-request-design.md)

---

## File Structure

**Backend (신규)**
- `backend/src/main/java/com/devmatch/entity/MentorChangeRequest.java` — 엔티티
- `backend/src/main/java/com/devmatch/entity/MentorChangeRequestStatus.java` — 상태 enum
- `backend/src/main/java/com/devmatch/repository/MentorChangeRequestRepository.java`
- `backend/src/main/java/com/devmatch/service/MentorChangeRequestService.java` — 멘티용
- `backend/src/main/java/com/devmatch/service/AdminMentorChangeRequestService.java` — 관리자용
- `backend/src/main/java/com/devmatch/controller/MentorChangeRequestController.java` — 멘티 REST
- `backend/src/main/java/com/devmatch/controller/AdminMentorChangeRequestController.java` — 관리자 REST
- `backend/src/main/java/com/devmatch/exception/NoActiveMatchingException.java`
- `backend/src/main/java/com/devmatch/exception/DuplicatePendingMentorChangeRequestException.java`
- `backend/src/main/java/com/devmatch/exception/MentorChangeRequestNotFoundException.java`
- `backend/src/main/java/com/devmatch/dto/menteechange/...` (요청/응답 DTO)
- `backend/src/main/java/com/devmatch/dto/admin/menteechange/...` (요청/응답 DTO)
- `backend/src/test/java/com/devmatch/entity/MentorChangeRequestTest.java`
- `backend/src/test/java/com/devmatch/service/MentorChangeRequestServiceTest.java`
- `backend/src/test/java/com/devmatch/service/AdminMentorChangeRequestServiceTest.java`

**Backend (수정)**
- `backend/src/main/java/com/devmatch/entity/AdminActionType.java` — enum 값 2개 추가
- `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java` — 핸들러 3개 추가
- `backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java` — 후보 조회 쿼리
- `backend/data/devmatch-schema.sql` + `backend/src/main/resources/seed-lms.sql` — CREATE TABLE
- (필요 시) `backend/src/main/java/com/devmatch/config/SecurityConfig.java` — 경로 권한

**Frontend (신규)**
- `frontend/src/lib/admin/mentor-change-requests.ts`
- `frontend/src/app/admin/mentor-change-requests/page.tsx`
- `frontend/src/app/admin/mentor-change-requests/[id]/page.tsx`

**Frontend (수정)**
- `frontend/src/components/admin/AdminSidebar.tsx` — 메뉴 항목 1줄

---

## Phase A — 도메인 & 영속성

### Task 1: `AdminActionType` 에 액션 2종 추가

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/AdminActionType.java`

- [ ] **Step 1: enum 값 2개 추가**

기존 enum 마지막에 다음 두 값을 추가:
```java
    // Phase II Feature 5 (멘토 교체 신청)
    MENTOR_CHANGE_APPROVE,
    MENTOR_CHANGE_REJECT
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./mvnw -q -DskipTests compile` (Windows: `mvnw.cmd` — 이하 동일)
Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/AdminActionType.java
git commit -m "feat(audit): MENTOR_CHANGE_APPROVE/REJECT 액션 추가"
```

---

### Task 2: `MentorChangeRequestStatus` enum

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/MentorChangeRequestStatus.java`

- [ ] **Step 1: 파일 생성**

```java
package com.devmatch.entity;

public enum MentorChangeRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
```

- [ ] **Step 2: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/MentorChangeRequestStatus.java
git commit -m "feat(mentor-change): MentorChangeRequestStatus enum 추가"
```

---

### Task 3: `MentorChangeRequest` 엔티티 + 상태 전이 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/MentorChangeRequest.java`
- Create: `backend/src/test/java/com/devmatch/entity/MentorChangeRequestTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.devmatch.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentorChangeRequestTest {

    private MentorChangeRequest pending() {
        return MentorChangeRequest.builder()
                .menteeId(1L)
                .currentMatchingId(10L)
                .currentMentorId(20L)
                .reason("멘토 스타일이 맞지 않습니다")
                .status(MentorChangeRequestStatus.PENDING)
                .build();
    }

    @Test
    void approve_정상_상태와_새멘토ID_세팅() {
        MentorChangeRequest r = pending();
        r.approve(99L, 33L);
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.APPROVED);
        assertThat(r.getDecidedByAdminId()).isEqualTo(99L);
        assertThat(r.getNewMentorId()).isEqualTo(33L);
        assertThat(r.getRespondedAt()).isNotNull();
    }

    @Test
    void reject_정상_사유_저장() {
        MentorChangeRequest r = pending();
        r.reject(99L, "객관적 사유가 부족합니다");
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.REJECTED);
        assertThat(r.getRejectReason()).isEqualTo("객관적 사유가 부족합니다");
        assertThat(r.getRespondedAt()).isNotNull();
    }

    @Test
    void reject_사유_빈값_예외() {
        MentorChangeRequest r = pending();
        assertThatThrownBy(() -> r.reject(99L, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_정상() {
        MentorChangeRequest r = pending();
        r.cancel();
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.CANCELLED);
        assertThat(r.getRespondedAt()).isNotNull();
    }

    @Test
    void approve_PENDING_아니면_예외() {
        MentorChangeRequest r = pending();
        r.cancel();
        assertThatThrownBy(() -> r.approve(99L, 33L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reject_PENDING_아니면_예외() {
        MentorChangeRequest r = pending();
        r.approve(99L, 33L);
        assertThatThrownBy(() -> r.reject(99L, "사유"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_PENDING_아니면_예외() {
        MentorChangeRequest r = pending();
        r.reject(99L, "사유");
        assertThatThrownBy(r::cancel).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `cd backend && ./mvnw -q test -Dtest=MentorChangeRequestTest`
Expected: 컴파일 에러 — `MentorChangeRequest cannot be resolved`

- [ ] **Step 3: 엔티티 작성**

```java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentor_change_requests", indexes = {
        @Index(name = "idx_mentor_change_status_created", columnList = "status, created_at"),
        @Index(name = "idx_mentor_change_mentee_status", columnList = "mentee_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentorChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(name = "current_matching_id", nullable = false)
    private Long currentMatchingId;

    @Column(name = "current_mentor_id", nullable = false)
    private Long currentMentorId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MentorChangeRequestStatus status = MentorChangeRequestStatus.PENDING;

    @Column(name = "decided_by_admin_id")
    private Long decidedByAdminId;

    @Column(name = "new_mentor_id")
    private Long newMentorId;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public void approve(Long adminId, Long newMentorUserId) {
        requirePending();
        this.status = MentorChangeRequestStatus.APPROVED;
        this.decidedByAdminId = adminId;
        this.newMentorId = newMentorUserId;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject(Long adminId, String rejectReason) {
        requirePending();
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 비어 있을 수 없습니다");
        }
        this.status = MentorChangeRequestStatus.REJECTED;
        this.decidedByAdminId = adminId;
        this.rejectReason = rejectReason;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        requirePending();
        this.status = MentorChangeRequestStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }

    private void requirePending() {
        if (this.status != MentorChangeRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 처리할 수 있습니다 (현재: " + this.status + ")");
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./mvnw -q test -Dtest=MentorChangeRequestTest`
Expected: 7 tests passed

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/entity/MentorChangeRequest.java \
        backend/src/test/java/com/devmatch/entity/MentorChangeRequestTest.java
git commit -m "feat(mentor-change): MentorChangeRequest 엔티티 + 상태 전이 테스트"
```

---

### Task 4: 신규 예외 3종 + GlobalExceptionHandler 등록

**Files:**
- Create: `backend/src/main/java/com/devmatch/exception/NoActiveMatchingException.java`
- Create: `backend/src/main/java/com/devmatch/exception/DuplicatePendingMentorChangeRequestException.java`
- Create: `backend/src/main/java/com/devmatch/exception/MentorChangeRequestNotFoundException.java`
- Modify: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 예외 3종 작성**

`NoActiveMatchingException.java`:
```java
package com.devmatch.exception;

public class NoActiveMatchingException extends RuntimeException {
    public NoActiveMatchingException(String message) { super(message); }
}
```

`DuplicatePendingMentorChangeRequestException.java`:
```java
package com.devmatch.exception;

public class DuplicatePendingMentorChangeRequestException extends RuntimeException {
    public DuplicatePendingMentorChangeRequestException(String message) { super(message); }
}
```

`MentorChangeRequestNotFoundException.java`:
```java
package com.devmatch.exception;

public class MentorChangeRequestNotFoundException extends RuntimeException {
    public MentorChangeRequestNotFoundException(String message) { super(message); }
}
```

- [ ] **Step 2: GlobalExceptionHandler 에 핸들러 추가**

`GlobalExceptionHandler.java` 의 `handleGeneral` 위(파일 거의 끝 부분)에 다음 핸들러 3개 추가:

```java
    @ExceptionHandler(NoActiveMatchingException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoActiveMatching(NoActiveMatchingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(DuplicatePendingMentorChangeRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicatePendingMentorChangeRequest(
            DuplicatePendingMentorChangeRequestException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MentorChangeRequestNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMentorChangeRequestNotFound(
            MentorChangeRequestNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/devmatch/exception/
git commit -m "feat(mentor-change): 신규 예외 3종 + GlobalExceptionHandler 등록"
```

---

### Task 5: `MentorChangeRequestRepository`

**Files:**
- Create: `backend/src/main/java/com/devmatch/repository/MentorChangeRequestRepository.java`

- [ ] **Step 1: 레포지토리 작성**

```java
package com.devmatch.repository;

import com.devmatch.entity.MentorChangeRequest;
import com.devmatch.entity.MentorChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MentorChangeRequestRepository extends JpaRepository<MentorChangeRequest, Long> {

    boolean existsByMenteeIdAndStatus(Long menteeId, MentorChangeRequestStatus status);

    Optional<MentorChangeRequest> findFirstByMenteeIdOrderByCreatedAtDesc(Long menteeId);

    Page<MentorChangeRequest> findByStatus(MentorChangeRequestStatus status, Pageable pageable);

    @Query("""
        SELECT r FROM MentorChangeRequest r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:menteeId IS NULL OR r.menteeId = :menteeId)
        """)
    Page<MentorChangeRequest> search(@Param("status") MentorChangeRequestStatus status,
                                     @Param("menteeId") Long menteeId,
                                     Pageable pageable);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/MentorChangeRequestRepository.java
git commit -m "feat(mentor-change): Repository 추가 (search/existsPending)"
```

---

## Phase B — 서비스

### Task 6: `MentorChangeRequestService` (멘티) + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/MentorChangeRequestService.java`
- Create: `backend/src/main/java/com/devmatch/dto/menteechange/MentorChangeRequestSubmitRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/menteechange/MentorChangeRequestResponse.java`
- Create: `backend/src/test/java/com/devmatch/service/MentorChangeRequestServiceTest.java`

- [ ] **Step 1: 요청/응답 DTO 작성**

`MentorChangeRequestSubmitRequest.java`:
```java
package com.devmatch.dto.menteechange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MentorChangeRequestSubmitRequest(
        @NotBlank(message = "사유는 필수입니다")
        @Size(min = 1, max = 500, message = "사유는 1~500자여야 합니다")
        String reason
) {}
```

`MentorChangeRequestResponse.java`:
```java
package com.devmatch.dto.menteechange;

import com.devmatch.entity.MentorChangeRequest;
import com.devmatch.entity.MentorChangeRequestStatus;

import java.time.LocalDateTime;

public record MentorChangeRequestResponse(
        Long id,
        Long menteeId,
        Long currentMatchingId,
        Long currentMentorId,
        String reason,
        MentorChangeRequestStatus status,
        Long newMentorId,
        String rejectReason,
        Long decidedByAdminId,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
    public static MentorChangeRequestResponse from(MentorChangeRequest e) {
        return new MentorChangeRequestResponse(
                e.getId(), e.getMenteeId(), e.getCurrentMatchingId(), e.getCurrentMentorId(),
                e.getReason(), e.getStatus(), e.getNewMentorId(), e.getRejectReason(),
                e.getDecidedByAdminId(), e.getCreatedAt(), e.getRespondedAt());
    }
}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
package com.devmatch.service;

import com.devmatch.entity.*;
import com.devmatch.exception.DuplicatePendingMentorChangeRequestException;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.MentorChangeRequestNotFoundException;
import com.devmatch.exception.NoActiveMatchingException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorChangeRequestServiceTest {

    @Mock MatchingRepository matchingRepository;
    @Mock MentorChangeRequestRepository requestRepository;

    @InjectMocks MentorChangeRequestService service;

    private User userOf(Long id) {
        User u = User.builder().email("u").name("u").password("p")
                .role(Role.MENTEE).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Matching activeMatching(Long id, Long menteeId, Long mentorId) {
        Matching m = Matching.builder()
                .mentee(userOf(menteeId)).mentor(userOf(mentorId))
                .category("Java BE").status(MatchingStatus.ACCEPTED).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void submit_정상_PENDING_으로_저장() {
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                eq(7L), eq(List.of(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL))))
                .thenReturn(Optional.of(activeMatching(10L, 7L, 20L)));
        when(requestRepository.existsByMenteeIdAndStatus(7L, MentorChangeRequestStatus.PENDING))
                .thenReturn(false);
        when(requestRepository.save(any(MentorChangeRequest.class)))
                .thenAnswer(inv -> {
                    MentorChangeRequest r = inv.getArgument(0);
                    ReflectionTestUtils.setField(r, "id", 100L);
                    return r;
                });

        var res = service.submit(7L, "스타일이 맞지 않습니다");

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.status()).isEqualTo(MentorChangeRequestStatus.PENDING);

        ArgumentCaptor<MentorChangeRequest> cap = ArgumentCaptor.forClass(MentorChangeRequest.class);
        verify(requestRepository).save(cap.capture());
        assertThat(cap.getValue().getCurrentMatchingId()).isEqualTo(10L);
        assertThat(cap.getValue().getCurrentMentorId()).isEqualTo(20L);
        assertThat(cap.getValue().getReason()).isEqualTo("스타일이 맞지 않습니다");
    }

    @Test
    void submit_활성매칭_없음_예외() {
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(7L, "사유"))
                .isInstanceOf(NoActiveMatchingException.class);
    }

    @Test
    void submit_PENDING_중복_예외() {
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(activeMatching(10L, 7L, 20L)));
        when(requestRepository.existsByMenteeIdAndStatus(7L, MentorChangeRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(7L, "사유"))
                .isInstanceOf(DuplicatePendingMentorChangeRequestException.class);
    }

    @Test
    void cancel_정상_엔티티_상태_변경() {
        MentorChangeRequest r = MentorChangeRequest.builder()
                .menteeId(7L).currentMatchingId(10L).currentMentorId(20L)
                .reason("사유").status(MentorChangeRequestStatus.PENDING).build();
        ReflectionTestUtils.setField(r, "id", 100L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        service.cancel(7L, 100L);

        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.CANCELLED);
    }

    @Test
    void cancel_타인_신청_403() {
        MentorChangeRequest r = MentorChangeRequest.builder()
                .menteeId(99L).currentMatchingId(10L).currentMentorId(20L)
                .reason("사유").status(MentorChangeRequestStatus.PENDING).build();
        ReflectionTestUtils.setField(r, "id", 100L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancel(7L, 100L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void cancel_존재하지_않으면_404() {
        when(requestRepository.findById(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(7L, 100L))
                .isInstanceOf(MentorChangeRequestNotFoundException.class);
    }

    @Test
    void getLatest_없으면_null_반환() {
        when(requestRepository.findFirstByMenteeIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.empty());
        assertThat(service.getLatest(7L)).isNull();
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `cd backend && ./mvnw -q test -Dtest=MentorChangeRequestServiceTest`
Expected: `MentorChangeRequestService cannot be resolved`

- [ ] **Step 4: 서비스 구현**

```java
package com.devmatch.service;

import com.devmatch.dto.menteechange.MentorChangeRequestResponse;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.MentorChangeRequest;
import com.devmatch.entity.MentorChangeRequestStatus;
import com.devmatch.exception.DuplicatePendingMentorChangeRequestException;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.MentorChangeRequestNotFoundException;
import com.devmatch.exception.NoActiveMatchingException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorChangeRequestService {

    private final MatchingRepository matchingRepository;
    private final MentorChangeRequestRepository requestRepository;

    @Transactional
    public MentorChangeRequestResponse submit(Long menteeUserId, String reason) {
        Matching active = matchingRepository
                .findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                        menteeUserId,
                        List.of(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL))
                .orElseThrow(() -> new NoActiveMatchingException(
                        "활성 매칭이 없어 멘토 교체를 신청할 수 없습니다"));

        if (requestRepository.existsByMenteeIdAndStatus(
                menteeUserId, MentorChangeRequestStatus.PENDING)) {
            throw new DuplicatePendingMentorChangeRequestException(
                    "이미 심사 중인 멘토 교체 신청이 있습니다");
        }

        MentorChangeRequest saved = requestRepository.save(
                MentorChangeRequest.builder()
                        .menteeId(menteeUserId)
                        .currentMatchingId(active.getId())
                        .currentMentorId(active.getMentor().getId())
                        .reason(reason)
                        .status(MentorChangeRequestStatus.PENDING)
                        .build());

        return MentorChangeRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MentorChangeRequestResponse getLatest(Long menteeUserId) {
        return requestRepository.findFirstByMenteeIdOrderByCreatedAtDesc(menteeUserId)
                .map(MentorChangeRequestResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MentorChangeRequestResponse getOwn(Long menteeUserId, Long requestId) {
        MentorChangeRequest r = requestRepository.findById(requestId)
                .orElseThrow(() -> new MentorChangeRequestNotFoundException(
                        "신청을 찾을 수 없습니다: " + requestId));
        if (!r.getMenteeId().equals(menteeUserId)) {
            throw new ForbiddenOperationException("본인 신청만 조회할 수 있습니다");
        }
        return MentorChangeRequestResponse.from(r);
    }

    @Transactional
    public void cancel(Long menteeUserId, Long requestId) {
        MentorChangeRequest r = requestRepository.findById(requestId)
                .orElseThrow(() -> new MentorChangeRequestNotFoundException(
                        "신청을 찾을 수 없습니다: " + requestId));
        if (!r.getMenteeId().equals(menteeUserId)) {
            throw new ForbiddenOperationException("본인 신청만 취소할 수 있습니다");
        }
        r.cancel();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./mvnw -q test -Dtest=MentorChangeRequestServiceTest`
Expected: 7 tests passed

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/MentorChangeRequestService.java \
        backend/src/main/java/com/devmatch/dto/menteechange/ \
        backend/src/test/java/com/devmatch/service/MentorChangeRequestServiceTest.java
git commit -m "feat(mentor-change): 멘티용 신청/조회/취소 서비스 + 테스트"
```

---

### Task 7: `MentorProfileRepository.findApprovedByCategoryAndKeyword`

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java`

`Matching.category` 는 String 이므로 `MentoringCourse.title` 또는 `courseKey` 와 동일한 값으로 매칭한다고 가정한다. 키워드는 멘토의 `User.name` 부분일치.

- [ ] **Step 1: 메서드 추가**

기존 인터페이스 안에 다음 메서드 추가:

```java
    @Query("""
        SELECT DISTINCT mp FROM MentorProfile mp
        JOIN mp.courses c
        WHERE mp.status = com.devmatch.entity.MentorStatus.APPROVED
          AND mp.user.id <> :excludeUserId
          AND (c.courseKey = :category OR c.title = :category)
          AND (:keyword = '' OR LOWER(mp.user.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    org.springframework.data.domain.Page<MentorProfile> findApprovedByCategoryAndKeyword(
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("excludeUserId") Long excludeUserId,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
```

(import 추가가 필요하면 줄 맨 위에)

- [ ] **Step 2: 컴파일 + 부트 컨텍스트 검증**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/repository/MentorProfileRepository.java
git commit -m "feat(mentor): 카테고리+키워드 기반 후보 멘토 조회 추가"
```

---

### Task 8: `AdminMentorChangeRequestService` + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/AdminMentorChangeRequestService.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/menteechange/AdminMentorChangeListItemResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/menteechange/AdminMentorChangeDetailResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/menteechange/CandidateMentorResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/menteechange/AdminMentorChangeApproveRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/menteechange/AdminMentorChangeRejectRequest.java`
- Create: `backend/src/test/java/com/devmatch/service/AdminMentorChangeRequestServiceTest.java`

- [ ] **Step 1: DTO 5종 작성**

`AdminMentorChangeListItemResponse.java`:
```java
package com.devmatch.dto.admin.menteechange;

import com.devmatch.entity.MentorChangeRequestStatus;

import java.time.LocalDateTime;

public record AdminMentorChangeListItemResponse(
        Long id,
        Long menteeId,
        String menteeName,
        String menteeEmail,
        Long currentMentorId,
        String currentMentorName,
        String reasonPreview,
        MentorChangeRequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {}
```

`AdminMentorChangeDetailResponse.java`:
```java
package com.devmatch.dto.admin.menteechange;

import com.devmatch.entity.MentorChangeRequestStatus;

import java.time.LocalDateTime;

public record AdminMentorChangeDetailResponse(
        Long id,
        Long menteeId,
        String menteeName,
        String menteeEmail,
        Long currentMatchingId,
        String currentCategory,
        Long currentMentorId,
        String currentMentorName,
        String reason,
        MentorChangeRequestStatus status,
        Long newMentorId,
        String newMentorName,
        String rejectReason,
        Long decidedByAdminId,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {}
```

`CandidateMentorResponse.java`:
```java
package com.devmatch.dto.admin.menteechange;

public record CandidateMentorResponse(
        Long userId,
        String name,
        String email,
        Integer activeMenteeCount
) {}
```

`AdminMentorChangeApproveRequest.java`:
```java
package com.devmatch.dto.admin.menteechange;

import jakarta.validation.constraints.NotNull;

public record AdminMentorChangeApproveRequest(
        @NotNull(message = "newMentorUserId 는 필수입니다") Long newMentorUserId
) {}
```

`AdminMentorChangeRejectRequest.java`:
```java
package com.devmatch.dto.admin.menteechange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMentorChangeRejectRequest(
        @NotBlank(message = "반려 사유는 필수입니다")
        @Size(min = 1, max = 500, message = "반려 사유는 1~500자여야 합니다")
        String rejectReason
) {}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
package com.devmatch.service;

import com.devmatch.dto.admin.menteechange.AdminMentorChangeApproveRequest;
import com.devmatch.entity.*;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMentorChangeRequestServiceTest {

    @Mock MentorChangeRequestRepository requestRepository;
    @Mock UserRepository userRepository;
    @Mock MatchingRepository matchingRepository;
    @Mock MentorSwapService mentorSwapService;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks AdminMentorChangeRequestService service;

    private MentorChangeRequest pending(Long id, Long menteeId, Long mentorId) {
        MentorChangeRequest r = MentorChangeRequest.builder()
                .menteeId(menteeId).currentMatchingId(10L).currentMentorId(mentorId)
                .reason("스타일 안 맞음").status(MentorChangeRequestStatus.PENDING).build();
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    void approve_정상_swap_호출_엔티티_APPROVED_감사로그() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        service.approve(99L, 100L, new AdminMentorChangeApproveRequest(33L));

        verify(mentorSwapService).swap(99L, 7L, 33L, "스타일 안 맞음");
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.APPROVED);
        assertThat(r.getNewMentorId()).isEqualTo(33L);
        verify(auditLogService).record(
                eq(99L), eq(AdminActionType.MENTOR_CHANGE_APPROVE),
                eq("MENTOR_CHANGE_REQUEST"), eq(100L), eq("스타일 안 맞음"),
                eq(Map.of("newMentorUserId", 33L, "oldMentorUserId", 20L)));
    }

    @Test
    void approve_PENDING_아니면_예외() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        r.cancel();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(99L, 100L,
                new AdminMentorChangeApproveRequest(33L)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mentorSwapService);
    }

    @Test
    void approve_swap_실패시_엔티티_변경_없음() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));
        doThrow(new com.devmatch.exception.ForbiddenOperationException("매칭 없음"))
                .when(mentorSwapService).swap(any(), any(), any(), any());

        assertThatThrownBy(() -> service.approve(99L, 100L,
                new AdminMentorChangeApproveRequest(33L)))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.PENDING);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reject_정상_엔티티_REJECTED_감사로그() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        service.reject(99L, 100L,
                new com.devmatch.dto.admin.menteechange.AdminMentorChangeRejectRequest("객관적 사유 부족"));

        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.REJECTED);
        assertThat(r.getRejectReason()).isEqualTo("객관적 사유 부족");
        verify(auditLogService).record(
                eq(99L), eq(AdminActionType.MENTOR_CHANGE_REJECT),
                eq("MENTOR_CHANGE_REQUEST"), eq(100L), eq("객관적 사유 부족"),
                eq(Map.of("menteeReason", "스타일 안 맞음")));
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `cd backend && ./mvnw -q test -Dtest=AdminMentorChangeRequestServiceTest`
Expected: `AdminMentorChangeRequestService cannot be resolved`

- [ ] **Step 4: 서비스 구현**

```java
package com.devmatch.service;

import com.devmatch.dto.admin.menteechange.*;
import com.devmatch.entity.*;
import com.devmatch.exception.MentorChangeRequestNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminMentorChangeRequestService {

    private final MentorChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final MatchingRepository matchingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorSwapService mentorSwapService;
    private final AdminAuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminMentorChangeListItemResponse> list(
            MentorChangeRequestStatus status, Pageable pageable) {
        Page<MentorChangeRequest> page = (status == null)
                ? requestRepository.findAll(pageable)
                : requestRepository.findByStatus(status, pageable);
        return page.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public AdminMentorChangeDetailResponse get(Long requestId) {
        MentorChangeRequest r = findRequiring(requestId);
        return toDetail(r);
    }

    @Transactional(readOnly = true)
    public Page<CandidateMentorResponse> listCandidateMentors(
            Long requestId, String keyword, Pageable pageable) {
        MentorChangeRequest r = findRequiring(requestId);
        Matching m = matchingRepository.findById(r.getCurrentMatchingId())
                .orElseThrow(() -> new IllegalStateException(
                        "매칭이 존재하지 않습니다: " + r.getCurrentMatchingId()));
        String kw = (keyword == null) ? "" : keyword.trim();
        Page<MentorProfile> profiles = mentorProfileRepository.findApprovedByCategoryAndKeyword(
                m.getCategory(), r.getCurrentMentorId(), kw, pageable);
        return profiles.map(p -> {
            int active = matchingRepository.countByMentorIdAndStatusIn(
                    p.getUser().getId(),
                    List.of(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL));
            return new CandidateMentorResponse(
                    p.getUser().getId(),
                    p.getUser().getName(),
                    p.getUser().getEmail(),
                    active);
        });
    }

    @Transactional
    public AdminMentorChangeDetailResponse approve(
            Long adminId, Long requestId, AdminMentorChangeApproveRequest req) {
        MentorChangeRequest r = findRequiring(requestId);
        // PENDING 검증은 r.approve() 가 수행 — 여기서는 swap 먼저 호출
        // (PENDING 아니면 어차피 r.approve() 에서 IllegalStateException)
        if (r.getStatus() != MentorChangeRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 처리할 수 있습니다 (현재: " + r.getStatus() + ")");
        }
        Long oldMentorId = r.getCurrentMentorId();
        mentorSwapService.swap(adminId, r.getMenteeId(), req.newMentorUserId(), r.getReason());
        r.approve(adminId, req.newMentorUserId());
        auditLogService.record(adminId, AdminActionType.MENTOR_CHANGE_APPROVE,
                "MENTOR_CHANGE_REQUEST", r.getId(), r.getReason(),
                Map.of("newMentorUserId", req.newMentorUserId(), "oldMentorUserId", oldMentorId));
        return toDetail(r);
    }

    @Transactional
    public AdminMentorChangeDetailResponse reject(
            Long adminId, Long requestId, AdminMentorChangeRejectRequest req) {
        MentorChangeRequest r = findRequiring(requestId);
        String menteeReason = r.getReason();
        r.reject(adminId, req.rejectReason());
        auditLogService.record(adminId, AdminActionType.MENTOR_CHANGE_REJECT,
                "MENTOR_CHANGE_REQUEST", r.getId(), req.rejectReason(),
                Map.of("menteeReason", menteeReason));
        return toDetail(r);
    }

    private MentorChangeRequest findRequiring(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new MentorChangeRequestNotFoundException(
                        "신청을 찾을 수 없습니다: " + requestId));
    }

    private AdminMentorChangeListItemResponse toListItem(MentorChangeRequest r) {
        Optional<User> mentee = userRepository.findById(r.getMenteeId());
        Optional<User> mentor = userRepository.findById(r.getCurrentMentorId());
        String preview = r.getReason() == null ? ""
                : r.getReason().length() > 40 ? r.getReason().substring(0, 40) + "…" : r.getReason();
        return new AdminMentorChangeListItemResponse(
                r.getId(),
                r.getMenteeId(),
                mentee.map(User::getName).orElse("(삭제됨)"),
                mentee.map(User::getEmail).orElse(null),
                r.getCurrentMentorId(),
                mentor.map(User::getName).orElse("(삭제됨)"),
                preview,
                r.getStatus(),
                r.getCreatedAt(),
                r.getRespondedAt());
    }

    private AdminMentorChangeDetailResponse toDetail(MentorChangeRequest r) {
        Optional<User> mentee = userRepository.findById(r.getMenteeId());
        Optional<User> mentor = userRepository.findById(r.getCurrentMentorId());
        Optional<User> newMentor = (r.getNewMentorId() == null)
                ? Optional.empty() : userRepository.findById(r.getNewMentorId());
        Optional<Matching> matching = matchingRepository.findById(r.getCurrentMatchingId());
        return new AdminMentorChangeDetailResponse(
                r.getId(),
                r.getMenteeId(),
                mentee.map(User::getName).orElse("(삭제됨)"),
                mentee.map(User::getEmail).orElse(null),
                r.getCurrentMatchingId(),
                matching.map(Matching::getCategory).orElse(null),
                r.getCurrentMentorId(),
                mentor.map(User::getName).orElse("(삭제됨)"),
                r.getReason(),
                r.getStatus(),
                r.getNewMentorId(),
                newMentor.map(User::getName).orElse(null),
                r.getRejectReason(),
                r.getDecidedByAdminId(),
                r.getCreatedAt(),
                r.getRespondedAt());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && ./mvnw -q test -Dtest=AdminMentorChangeRequestServiceTest`
Expected: 4 tests passed

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/devmatch/service/AdminMentorChangeRequestService.java \
        backend/src/main/java/com/devmatch/dto/admin/menteechange/ \
        backend/src/test/java/com/devmatch/service/AdminMentorChangeRequestServiceTest.java
git commit -m "feat(mentor-change): 관리자 신청 심사 서비스 + 테스트 (승인=swap+감사로그, 반려)"
```

---

## Phase C — REST 컨트롤러

### Task 9: 멘티 컨트롤러

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/MentorChangeRequestController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.menteechange.MentorChangeRequestResponse;
import com.devmatch.dto.menteechange.MentorChangeRequestSubmitRequest;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.MentorChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mentor Change (Mentee)", description = "멘티의 멘토 교체 신청 API")
@RestController
@RequestMapping("/api/mentee/mentor-change-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTEE')")
public class MentorChangeRequestController {

    private final MentorChangeRequestService service;

    @Operation(summary = "멘토 교체 신청 제출")
    @PostMapping
    public ResponseEntity<ApiResponse<MentorChangeRequestResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MentorChangeRequestSubmitRequest request
    ) {
        MentorChangeRequestResponse res = service.submit(user.getUserId(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("멘토 교체 신청이 접수되었습니다", res));
    }

    @Operation(summary = "본인 최근 신청 조회 (없으면 null)")
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<MentorChangeRequestResponse>> latest(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getLatest(user.getUserId())));
    }

    @Operation(summary = "본인 신청 상세")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MentorChangeRequestResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getOwn(user.getUserId(), id)));
    }

    @Operation(summary = "본인 PENDING 신청 취소")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        service.cancel(user.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/controller/MentorChangeRequestController.java
git commit -m "feat(mentor-change): 멘티용 REST 컨트롤러"
```

---

### Task 10: 관리자 컨트롤러

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/AdminMentorChangeRequestController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.menteechange.*;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.MentorChangeRequestStatus;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminMentorChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Mentor Change", description = "관리자 멘토 교체 신청 심사 API")
@RestController
@RequestMapping("/api/admin/mentor-change-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMentorChangeRequestController {

    private final AdminMentorChangeRequestService service;

    @Operation(summary = "신청 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminMentorChangeListItemResponse>>> list(
            @RequestParam(required = false) MentorChangeRequestStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @Operation(summary = "신청 상세")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminMentorChangeDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @Operation(summary = "후보 멘토 조회 (현재 카테고리, 현재 멘토 제외)")
    @GetMapping("/{id}/candidate-mentors")
    public ResponseEntity<ApiResponse<Page<CandidateMentorResponse>>> candidates(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String keyword,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                service.listCandidateMentors(id, keyword, pageable)));
    }

    @Operation(summary = "승인 (멘토 교체 실행)")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminMentorChangeDetailResponse>> approve(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long id,
            @Valid @RequestBody AdminMentorChangeApproveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "멘토 교체가 완료되었습니다",
                service.approve(admin.getUserId(), id, request)));
    }

    @Operation(summary = "반려")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminMentorChangeDetailResponse>> reject(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long id,
            @Valid @RequestBody AdminMentorChangeRejectRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "신청이 반려되었습니다",
                service.reject(admin.getUserId(), id, request)));
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/devmatch/controller/AdminMentorChangeRequestController.java
git commit -m "feat(mentor-change): 관리자 REST 컨트롤러 (목록/상세/후보/승인/반려)"
```

---

### Task 11: SecurityConfig 경로 권한 확인

**Files:**
- Modify (조건부): `backend/src/main/java/com/devmatch/config/SecurityConfig.java`

- [ ] **Step 1: SecurityConfig 확인**

Run: `grep -n "mentee\|admin" backend/src/main/java/com/devmatch/config/SecurityConfig.java`
Expected: 기존 `/api/admin/**` 와 `/api/mentee/**` 패턴이 이미 권한 매핑돼 있는지 확인.

- [ ] **Step 2: 필요 시 추가**

기존 `requestMatchers("/api/admin/**").hasRole("ADMIN")` 가 있으면 본 컨트롤러는 자동 보호됨 (`@PreAuthorize` 만으로도 충분). 신규 추가 불필요.

`/api/mentee/**` 경로 매핑이 없거나 다른 메서드별 매핑이라면 다음 한 줄 추가 (filterChain 내부 적절한 위치):
```java
.requestMatchers("/api/mentee/mentor-change-requests/**").hasRole("MENTEE")
```

- [ ] **Step 3: 부트 컨텍스트 검증**

Run: `cd backend && ./mvnw -q test -Dtest=DevMatchApplicationTests`
Expected: PASS — 컨텍스트 로드 성공

- [ ] **Step 4: 변경 있으면 커밋 (없으면 건너뛰기)**

```bash
git add backend/src/main/java/com/devmatch/config/SecurityConfig.java
git commit -m "chore(security): /api/mentee/mentor-change-requests 권한 매핑"
```

---

## Phase D — 스키마 + 통합 테스트

### Task 12: prod 스키마 파일에 CREATE TABLE 추가

**Files:**
- Modify: `backend/data/devmatch-schema.sql`
- Modify: `backend/src/main/resources/seed-lms.sql`

- [ ] **Step 1: `devmatch-schema.sql` 에 추가**

라인 494(`session_change_requests` 테이블 정의 끝) 다음, `survey_responses` 정의 시작(`-- Table structure for table 'survey_responses'`) 직전에 다음 블록을 삽입:

```sql

--
-- Table structure for table `mentor_change_requests`
--

DROP TABLE IF EXISTS `mentor_change_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mentor_change_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mentee_id` bigint NOT NULL,
  `current_matching_id` bigint NOT NULL,
  `current_mentor_id` bigint NOT NULL,
  `reason` varchar(500) NOT NULL,
  `status` enum('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL,
  `decided_by_admin_id` bigint DEFAULT NULL,
  `new_mentor_id` bigint DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mentor_change_status_created` (`status`,`created_at`),
  KEY `idx_mentor_change_mentee_status` (`mentee_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
```

- [ ] **Step 2: `seed-lms.sql` 도 동일 시점에 같은 DDL 추가 (테이블 생성만, 시드 데이터는 없음)**

`seed-lms.sql` 에서 `session_change_requests` 와 같은 인근 위치에 위와 동일한 `CREATE TABLE` 블록 삽입. 파일 구조가 다르면(데이터 INSERT 만 있고 DDL 없으면) 이 단계는 건너뛰고 dev 환경의 `ddl-auto: update` 로 자동 생성에 의존.

Run: `grep -n "CREATE TABLE.*session_change_requests" backend/src/main/resources/seed-lms.sql`
없으면 — `seed-lms.sql` 은 데이터 전용. 건너뛰기.

- [ ] **Step 3: dev 부트로 자동 생성 확인 (선택)**

Run: `cd backend && ./mvnw -q test -Dtest=DevMatchApplicationTests`
Expected: PASS — H2 또는 dev DB 에서 컨텍스트 로드 성공 (테이블 자동 생성)

- [ ] **Step 4: 커밋**

```bash
git add backend/data/devmatch-schema.sql backend/src/main/resources/seed-lms.sql
git commit -m "chore(schema): mentor_change_requests 테이블 DDL"
```

---

### Task 13: 통합 테스트 (관리자 happy path)

**Files:**
- Create: `backend/src/test/java/com/devmatch/controller/AdminMentorChangeRequestControllerIT.java`

- [ ] **Step 1: 통합 테스트 작성**

```java
package com.devmatch.controller;

import com.devmatch.entity.*;
import com.devmatch.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminMentorChangeRequestControllerIT {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired MatchingRepository matchingRepository;
    @Autowired MentorChangeRequestRepository requestRepository;
    @Autowired MentorProfileRepository mentorProfileRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reject_정상_REJECTED_로_변경() throws Exception {
        User mentee = userRepository.save(User.builder()
                .email("m@x.com").name("멘티").password(passwordEncoder.encode("p"))
                .role(Role.MENTEE).status(UserStatus.ACTIVE).build());
        User mentor = userRepository.save(User.builder()
                .email("t@x.com").name("멘토").password(passwordEncoder.encode("p"))
                .role(Role.MENTOR).status(UserStatus.ACTIVE).build());
        Matching matching = matchingRepository.save(Matching.builder()
                .mentee(mentee).mentor(mentor).category("Java BE")
                .status(MatchingStatus.ACCEPTED).build());
        MentorChangeRequest req = requestRepository.save(MentorChangeRequest.builder()
                .menteeId(mentee.getId())
                .currentMatchingId(matching.getId())
                .currentMentorId(mentor.getId())
                .reason("스타일 안 맞음")
                .status(MentorChangeRequestStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/admin/mentor-change-requests/" + req.getId() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("rejectReason", "객관적 사유 부족"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("객관적 사유 부족"));

        MentorChangeRequest updated = requestRepository.findById(req.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MentorChangeRequestStatus.REJECTED);
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./mvnw -q test -Dtest=AdminMentorChangeRequestControllerIT`
Expected: 1 test passed

- [ ] **Step 3: 전체 테스트 회귀 검사**

Run: `cd backend && ./mvnw -q test`
Expected: 전체 PASS — 기존 테스트가 깨지면 안 됨

- [ ] **Step 4: 커밋**

```bash
git add backend/src/test/java/com/devmatch/controller/AdminMentorChangeRequestControllerIT.java
git commit -m "test(mentor-change): 관리자 반려 happy path 통합 테스트"
```

---

## Phase E — 관리자 프론트엔드

### Task 14: API 클라이언트

**Files:**
- Create: `frontend/src/lib/admin/mentor-change-requests.ts`

- [ ] **Step 1: 클라이언트 작성**

```ts
import apiClient from '../api';
import type { ApiResponse, PageResponse } from '../types';

export type { PageResponse };

export type MentorChangeRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export interface AdminMentorChangeListItem {
  id: number;
  menteeId: number;
  menteeName: string;
  menteeEmail: string | null;
  currentMentorId: number;
  currentMentorName: string;
  reasonPreview: string;
  status: MentorChangeRequestStatus;
  createdAt: string;
  respondedAt: string | null;
}

export interface AdminMentorChangeDetail {
  id: number;
  menteeId: number;
  menteeName: string;
  menteeEmail: string | null;
  currentMatchingId: number;
  currentCategory: string | null;
  currentMentorId: number;
  currentMentorName: string;
  reason: string;
  status: MentorChangeRequestStatus;
  newMentorId: number | null;
  newMentorName: string | null;
  rejectReason: string | null;
  decidedByAdminId: number | null;
  createdAt: string;
  respondedAt: string | null;
}

export interface CandidateMentor {
  userId: number;
  name: string;
  email: string | null;
  activeMenteeCount: number;
}

export interface ListAdminMentorChangeParams {
  page?: number;
  size?: number;
  status?: MentorChangeRequestStatus;
}

export async function listAdminMentorChangeRequests(
  params: ListAdminMentorChangeParams,
): Promise<PageResponse<AdminMentorChangeListItem>> {
  const res = await apiClient.get<ApiResponse<PageResponse<AdminMentorChangeListItem>>>(
    '/admin/mentor-change-requests',
    { params },
  );
  return res.data.data!;
}

export async function getAdminMentorChangeRequest(
  id: number,
): Promise<AdminMentorChangeDetail> {
  const res = await apiClient.get<ApiResponse<AdminMentorChangeDetail>>(
    `/admin/mentor-change-requests/${id}`,
  );
  return res.data.data!;
}

export async function listCandidateMentors(
  id: number,
  keyword: string,
  page: number,
  size = 10,
): Promise<PageResponse<CandidateMentor>> {
  const res = await apiClient.get<ApiResponse<PageResponse<CandidateMentor>>>(
    `/admin/mentor-change-requests/${id}/candidate-mentors`,
    { params: { keyword, page, size } },
  );
  return res.data.data!;
}

export async function approveMentorChangeRequest(
  id: number,
  newMentorUserId: number,
): Promise<AdminMentorChangeDetail> {
  const res = await apiClient.post<ApiResponse<AdminMentorChangeDetail>>(
    `/admin/mentor-change-requests/${id}/approve`,
    { newMentorUserId },
  );
  return res.data.data!;
}

export async function rejectMentorChangeRequest(
  id: number,
  rejectReason: string,
): Promise<AdminMentorChangeDetail> {
  const res = await apiClient.post<ApiResponse<AdminMentorChangeDetail>>(
    `/admin/mentor-change-requests/${id}/reject`,
    { rejectReason },
  );
  return res.data.data!;
}
```

- [ ] **Step 2: TS 타입 검사**

Run: `cd frontend && npx tsc --noEmit`
Expected: 새 파일 관련 에러 없음 (기존 에러는 무시)

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/lib/admin/mentor-change-requests.ts
git commit -m "feat(admin-fe): 멘토 교체 신청 API 클라이언트"
```

---

### Task 15: 관리자 목록 페이지

**Files:**
- Create: `frontend/src/app/admin/mentor-change-requests/page.tsx`

- [ ] **Step 1: 페이지 작성**

기존 `frontend/src/app/admin/posts/page.tsx` 의 헤더/탭/페이지네이션 사용 패턴을 그대로 따른다. 다음 코드를 그대로 사용:

```tsx
'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import AdminListHeader from '@/components/admin/AdminListHeader';
import Pagination from '@/components/admin/Pagination';
import {
  listAdminMentorChangeRequests,
  type AdminMentorChangeListItem,
  type MentorChangeRequestStatus,
} from '@/lib/admin/mentor-change-requests';

const TABS: Array<{ key: MentorChangeRequestStatus | 'ALL'; label: string }> = [
  { key: 'PENDING', label: '대기' },
  { key: 'APPROVED', label: '승인됨' },
  { key: 'REJECTED', label: '반려됨' },
  { key: 'CANCELLED', label: '취소됨' },
  { key: 'ALL', label: '전체' },
];

const STATUS_BADGE: Record<MentorChangeRequestStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-rose-100 text-rose-800',
  CANCELLED: 'bg-slate-200 text-slate-700',
};

export default function AdminMentorChangeRequestsPage() {
  const [tab, setTab] = useState<MentorChangeRequestStatus | 'ALL'>('PENDING');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<AdminMentorChangeListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listAdminMentorChangeRequests({
      page,
      size: 20,
      status: tab === 'ALL' ? undefined : tab,
    })
      .then((res) => {
        if (cancelled) return;
        setItems(res.content);
        setTotalPages(res.totalPages);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다');
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [tab, page]);

  return (
    <div className="space-y-6">
      <AdminListHeader title="멘토 교체 신청 관리" />

      <div className="flex gap-1 border-b border-slate-200">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => {
              setTab(t.key);
              setPage(0);
            }}
            className={
              'px-4 py-2 text-sm font-medium border-b-2 transition-colors ' +
              (tab === t.key
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-900')
            }
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase text-slate-500">
            <tr>
              <th className="px-4 py-3">신청일</th>
              <th className="px-4 py-3">멘티</th>
              <th className="px-4 py-3">현재 멘토</th>
              <th className="px-4 py-3">사유</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3">처리일</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  불러오는 중…
                </td>
              </tr>
            )}
            {!loading && error && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-rose-600">
                  {error}
                </td>
              </tr>
            )}
            {!loading && !error && items.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  신청이 없습니다
                </td>
              </tr>
            )}
            {!loading &&
              !error &&
              items.map((it) => (
                <tr key={it.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-600">
                    {new Date(it.createdAt).toLocaleString('ko-KR')}
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      href={`/admin/mentor-change-requests/${it.id}`}
                      className="font-medium text-slate-900 hover:underline"
                    >
                      {it.menteeName}
                    </Link>
                    <div className="text-xs text-slate-500">{it.menteeEmail}</div>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{it.currentMentorName}</td>
                  <td className="px-4 py-3 text-slate-700">{it.reasonPreview}</td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium ' +
                        STATUS_BADGE[it.status]
                      }
                    >
                      {it.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {it.respondedAt
                      ? new Date(it.respondedAt).toLocaleString('ko-KR')
                      : '-'}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onChange={setPage}
        />
      )}
    </div>
  );
}
```

> **참고:** `Pagination` props 시그니처가 다르면 (`AdminListHeader` 도) 기존 admin 페이지의 사용법을 보고 동일하게 맞춘다. 기준 파일: `frontend/src/app/admin/posts/page.tsx`.

- [ ] **Step 2: dev 서버에서 페이지 로드 확인**

Run: `cd frontend && npm run dev` (백그라운드)
브라우저: `http://localhost:3000/admin/mentor-change-requests` (관리자 로그인 후)
Expected: 빈 목록 또는 더미 신청이 보이면 OK

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/admin/mentor-change-requests/page.tsx
git commit -m "feat(admin-fe): 멘토 교체 신청 목록 페이지"
```

---

### Task 16: 관리자 상세 페이지 (승인/반려)

**Files:**
- Create: `frontend/src/app/admin/mentor-change-requests/[id]/page.tsx`

- [ ] **Step 1: 페이지 작성**

```tsx
'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  getAdminMentorChangeRequest,
  listCandidateMentors,
  approveMentorChangeRequest,
  rejectMentorChangeRequest,
  type AdminMentorChangeDetail,
  type CandidateMentor,
} from '@/lib/admin/mentor-change-requests';

export default function AdminMentorChangeRequestDetailPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Number(params.id);

  const [detail, setDetail] = useState<AdminMentorChangeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [keyword, setKeyword] = useState('');
  const [candidates, setCandidates] = useState<CandidateMentor[]>([]);
  const [candidatePage, setCandidatePage] = useState(0);
  const [candidateTotalPages, setCandidateTotalPages] = useState(0);
  const [selectedMentorId, setSelectedMentorId] = useState<number | null>(null);

  const [rejectReason, setRejectReason] = useState('');
  const [submitting, setSubmitting] = useState<'approve' | 'reject' | null>(null);

  const reloadDetail = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await getAdminMentorChangeRequest(id));
    } catch (e) {
      setError(e instanceof Error ? e.message : '상세 조회 실패');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    reloadDetail();
  }, [reloadDetail]);

  useEffect(() => {
    if (!detail || detail.status !== 'PENDING') return;
    let cancelled = false;
    listCandidateMentors(id, keyword, candidatePage, 10)
      .then((res) => {
        if (cancelled) return;
        setCandidates(res.content);
        setCandidateTotalPages(res.totalPages);
      })
      .catch(() => {
        if (cancelled) return;
        setCandidates([]);
        setCandidateTotalPages(0);
      });
    return () => {
      cancelled = true;
    };
  }, [id, detail, keyword, candidatePage]);

  if (loading) return <div className="text-slate-500">불러오는 중…</div>;
  if (error) return <div className="text-rose-600">{error}</div>;
  if (!detail) return null;

  const isPending = detail.status === 'PENDING';

  const handleApprove = async () => {
    if (!selectedMentorId) return;
    if (!confirm('이 멘토로 교체합니다. 되돌릴 수 없습니다. 진행할까요?')) return;
    setSubmitting('approve');
    try {
      await approveMentorChangeRequest(id, selectedMentorId);
      alert('멘토 교체가 완료되었습니다');
      router.push('/admin/mentor-change-requests');
    } catch (e) {
      alert(e instanceof Error ? e.message : '승인 실패');
    } finally {
      setSubmitting(null);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    if (!confirm('이 신청을 반려합니다. 진행할까요?')) return;
    setSubmitting('reject');
    try {
      await rejectMentorChangeRequest(id, rejectReason);
      alert('신청을 반려했습니다');
      router.push('/admin/mentor-change-requests');
    } catch (e) {
      alert(e instanceof Error ? e.message : '반려 실패');
    } finally {
      setSubmitting(null);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
        <h2 className="text-lg font-semibold">신청 정보</h2>
        <dl className="grid grid-cols-3 gap-y-3 text-sm">
          <dt className="text-slate-500">신청일</dt>
          <dd className="col-span-2">
            {new Date(detail.createdAt).toLocaleString('ko-KR')}
          </dd>
          <dt className="text-slate-500">멘티</dt>
          <dd className="col-span-2">
            {detail.menteeName}{' '}
            <span className="text-slate-400">({detail.menteeEmail})</span>
          </dd>
          <dt className="text-slate-500">분야</dt>
          <dd className="col-span-2">{detail.currentCategory ?? '-'}</dd>
          <dt className="text-slate-500">현재 멘토</dt>
          <dd className="col-span-2">{detail.currentMentorName}</dd>
          <dt className="text-slate-500">상태</dt>
          <dd className="col-span-2">{detail.status}</dd>
        </dl>
        <div>
          <h3 className="text-sm font-medium text-slate-700">멘티 사유</h3>
          <p className="mt-2 whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm text-slate-700">
            {detail.reason}
          </p>
        </div>
      </section>

      {isPending ? (
        <div className="space-y-6">
          <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
            <h2 className="text-lg font-semibold">승인하고 멘토 교체</h2>
            <input
              type="text"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setCandidatePage(0);
              }}
              placeholder="멘토 이름 검색"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <div className="max-h-72 overflow-y-auto rounded-md border border-slate-200">
              {candidates.length === 0 ? (
                <p className="p-4 text-center text-sm text-slate-400">
                  후보 멘토가 없습니다
                </p>
              ) : (
                <table className="w-full text-sm">
                  <tbody className="divide-y divide-slate-100">
                    {candidates.map((c) => (
                      <tr
                        key={c.userId}
                        className="cursor-pointer hover:bg-slate-50"
                        onClick={() => setSelectedMentorId(c.userId)}
                      >
                        <td className="w-10 px-3 py-2">
                          <input
                            type="radio"
                            name="candidate"
                            checked={selectedMentorId === c.userId}
                            onChange={() => setSelectedMentorId(c.userId)}
                          />
                        </td>
                        <td className="px-3 py-2">
                          <div className="font-medium">{c.name}</div>
                          <div className="text-xs text-slate-500">{c.email}</div>
                        </td>
                        <td className="px-3 py-2 text-right text-xs text-slate-500">
                          진행중 {c.activeMenteeCount}명
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {candidateTotalPages > 1 && (
              <div className="flex items-center justify-between text-sm">
                <button
                  disabled={candidatePage === 0}
                  onClick={() => setCandidatePage((p) => p - 1)}
                  className="rounded-md border border-slate-300 px-2 py-1 disabled:opacity-50"
                >
                  이전
                </button>
                <span className="text-slate-500">
                  {candidatePage + 1} / {candidateTotalPages}
                </span>
                <button
                  disabled={candidatePage >= candidateTotalPages - 1}
                  onClick={() => setCandidatePage((p) => p + 1)}
                  className="rounded-md border border-slate-300 px-2 py-1 disabled:opacity-50"
                >
                  다음
                </button>
              </div>
            )}
            <button
              disabled={!selectedMentorId || submitting === 'approve'}
              onClick={handleApprove}
              className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {submitting === 'approve' ? '처리 중…' : '교체 승인'}
            </button>
          </section>

          <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
            <h2 className="text-lg font-semibold">반려</h2>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="반려 사유 (1~500자)"
              maxLength={500}
              rows={4}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <div className="text-right text-xs text-slate-400">
              {rejectReason.length} / 500
            </div>
            <button
              disabled={!rejectReason.trim() || submitting === 'reject'}
              onClick={handleReject}
              className="w-full rounded-md border border-rose-600 px-4 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50 disabled:opacity-50"
            >
              {submitting === 'reject' ? '처리 중…' : '반려'}
            </button>
          </section>
        </div>
      ) : (
        <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="text-lg font-semibold">처리 결과</h2>
          {detail.status === 'APPROVED' && (
            <p className="text-sm text-slate-700">
              <strong>{detail.newMentorName}</strong> (id: {detail.newMentorId}) 로
              교체됨 · 처리일{' '}
              {detail.respondedAt
                ? new Date(detail.respondedAt).toLocaleString('ko-KR')
                : '-'}
            </p>
          )}
          {detail.status === 'REJECTED' && (
            <>
              <p className="text-sm text-slate-700">
                반려 · 처리일{' '}
                {detail.respondedAt
                  ? new Date(detail.respondedAt).toLocaleString('ko-KR')
                  : '-'}
              </p>
              <p className="whitespace-pre-wrap rounded-md bg-rose-50 p-3 text-sm text-rose-800">
                {detail.rejectReason}
              </p>
            </>
          )}
          {detail.status === 'CANCELLED' && (
            <p className="text-sm text-slate-700">
              멘티 본인이 취소함 ·{' '}
              {detail.respondedAt
                ? new Date(detail.respondedAt).toLocaleString('ko-KR')
                : '-'}
            </p>
          )}
        </section>
      )}
    </div>
  );
}
```

- [ ] **Step 2: dev 서버에서 수동 시나리오 확인**
  - PENDING 신청 1건을 DB 에 직접 INSERT (또는 멘티 API 로 제출)
  - `/admin/mentor-change-requests/{id}` 진입 → 후보 멘토 목록 노출 → 1명 선택 → "교체 승인" → 토스트 + 목록 복귀 + 상태 APPROVED
  - 새 PENDING → 반려 사유 입력 → 반려 → REJECTED 확인

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/admin/mentor-change-requests/[id]/page.tsx
git commit -m "feat(admin-fe): 멘토 교체 신청 상세 페이지 (승인/반려)"
```

---

### Task 17: 사이드바 메뉴 추가

**Files:**
- Modify: `frontend/src/components/admin/AdminSidebar.tsx`

- [ ] **Step 1: 메뉴 항목 1개 추가**

`NAV_ITEMS` 배열에서 `'/admin/users'` 다음 위치에 다음 항목을 삽입. 그리고 import 라인에서 `Repeat` 아이콘을 추가:

```tsx
import { UserCheck, Users, CreditCard, FileText, ShieldCheck, Repeat } from 'lucide-react';
```

`NAV_ITEMS` 안:
```tsx
  {
    href: '/admin/mentor-change-requests',
    label: '멘토 교체 신청',
    icon: Repeat,
    match: (p) =>
      p === '/admin/mentor-change-requests' ||
      p.startsWith('/admin/mentor-change-requests/'),
  },
```

- [ ] **Step 2: dev 서버에서 사이드바 표시 확인**

브라우저: `/admin` 진입 → 사이드바에 "멘토 교체 신청" 메뉴 보이고 클릭 시 목록 페이지로 이동

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/admin/AdminSidebar.tsx
git commit -m "feat(admin-fe): 사이드바에 멘토 교체 신청 메뉴 추가"
```

---

## 마무리

### Task 18: 전체 회귀 + 스펙·에러 로깅 정리

- [ ] **Step 1: 백엔드 전체 테스트 회귀**

Run: `cd backend && ./mvnw -q test`
Expected: 전체 PASS

- [ ] **Step 2: 프론트 빌드/타입 검사**

Run: `cd frontend && npx tsc --noEmit`
Expected: 신규 파일 관련 에러 없음

- [ ] **Step 3: PR 본문용 수동 시나리오 체크리스트 작성 — README 또는 PR 본문에 첨부**

다음 항목을 포함:
- [ ] 멘티 API: POST 신청 (사유 1자 미만 → 400)
- [ ] 멘티 API: 활성 매칭 없는 멘티의 POST → 400 NoActiveMatching
- [ ] 멘티 API: PENDING 있는 상태에서 POST → 409 DuplicatePending
- [ ] 관리자: 목록 탭 PENDING/APPROVED/REJECTED/CANCELLED/전체 정상
- [ ] 관리자: PENDING 상세에서 후보 멘토 검색·선택·승인 → 토스트 + 목록 복귀, DB `matchings` 의 옛 매칭 status=SWAPPED, 새 매칭 status=ACCEPTED 확인
- [ ] 관리자: PENDING 상세에서 반려 사유 빈값 → 버튼 disabled
- [ ] 관리자: 비-PENDING 상세에서 액션 카드 미노출 + 결과 패널 노출
- [ ] 감사 로그: `admin_audit_log` 에 `MENTOR_CHANGE_APPROVE` / `MENTOR_CHANGE_REJECT` / (승인 시) `USER_MENTOR_SWAP` 모두 기록 확인

- [ ] **Step 4: 에러 로깅 — 구현 중 발생한 실 이슈만 `error/YYYY-MM-DD-...md` 로 기록**

(원인을 특정해 해결한 이슈가 없으면 이 단계는 건너뛴다 — CLAUDE.md 의 에러 로깅 규칙)

- [ ] **Step 5: 마지막 정리 커밋 (필요 시)**

```bash
# 변경이 있다면 커밋
git status
```

---

## Self-Review 체크리스트 (계획 작성자 본인용 — 실행자 무시)

- [x] 스펙의 모든 In Scope 항목이 어떤 Task 에 매핑되는지 확인됨
  - 엔티티/enum/마이그레이션 → Task 1~5, 12
  - 멘티 API 4개 → Task 6, 9
  - 관리자 API 5개 → Task 7, 8, 10
  - 관리자 페이지 2개 → Task 14~16
  - 사이드바 메뉴 → Task 17
  - 감사 로그 액션 2종 → Task 1, 8
  - 백엔드 테스트 → Task 3, 6, 8, 13
- [x] 모든 Step 에 실제 코드가 있고, 플레이스홀더 없음
- [x] 타입/메서드 시그니처 일관 — `approve(adminId, newMentorUserId)`, `reject(adminId, rejectReason)`, `cancel()` 가 엔티티/서비스/컨트롤러 전반에서 동일

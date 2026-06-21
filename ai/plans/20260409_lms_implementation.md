---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "LMS 도입 계획 및 마이그레이션 방향"

---

# LMS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** DevMatch 멘토 매칭 플랫폼에 LMS(학습 관리 시스템)를 추가하여 매칭 후 커리큘럼 관리, 과제/코드리뷰, 학습 노트, 취업 지원, 수료증 기능을 제공한다.

**Architecture:** Spring Boot 3.4 백엔드에 6개의 신규 Entity + 8개 Repository + 7개 Service + 6개 Controller를 추가한다. 기존 SessionService는 GoogleCalendar 대신 JitsiMeetService를 사용하도록 수정한다. Next.js 14 프론트엔드에는 /lms 하위 7개 페이지와 사이드바 레이아웃을 추가한다.

**Tech Stack:** Spring Boot 3.4, JPA, Java 17, Next.js 14, Tailwind CSS, TypeScript, shadcn/ui, Recharts, FullCalendar, OpenPDF

**Spec:** `docs/superpowers/specs/2026-04-08-lms-implementation-design.md`

---

## File Structure

### Backend 신규 파일

```
backend/src/main/java/com/devmatch/
├── entity/
│   ├── AssignmentType.java          — enum (TASK, CODE_REVIEW)
│   ├── AssignmentStatus.java        — enum (ASSIGNED, SUBMITTED, REVIEWED)
│   ├── NoteType.java                — enum (SESSION_REVIEW, WEEKLY_JOURNAL)
│   ├── Curriculum.java              — 커리큘럼 entity
│   ├── CurriculumWeek.java          — 주차별 상세 entity
│   ├── Assignment.java              — 과제+코드리뷰 통합 entity
│   ├── AssignmentSubmission.java    — 과제 제출 entity
│   ├── LearningNote.java            — 학습노트 통합 entity
│   ├── NoteComment.java             — 노트 코멘트 entity
│   ├── Resume.java                  — 이력서 entity
│   └── MockInterview.java           — 모의면접 entity
├── repository/
│   ├── CurriculumRepository.java
│   ├── CurriculumWeekRepository.java
│   ├── AssignmentRepository.java
│   ├── AssignmentSubmissionRepository.java
│   ├── LearningNoteRepository.java
│   ├── NoteCommentRepository.java
│   ├── ResumeRepository.java
│   └── MockInterviewRepository.java
├── dto/lms/
│   ├── DashboardResponse.java
│   ├── EnrollmentResponse.java
│   ├── CurriculumCreateRequest.java
│   ├── CurriculumResponse.java
│   ├── AssignmentCreateRequest.java
│   ├── AssignmentResponse.java
│   ├── SubmissionRequest.java
│   ├── FeedbackRequest.java
│   ├── NoteCreateRequest.java
│   ├── NoteResponse.java
│   ├── NoteCommentRequest.java
│   ├── ResumeResponse.java
│   ├── ResumeFeedbackRequest.java
│   ├── MockInterviewCreateRequest.java
│   ├── MockInterviewResponse.java
│   └── CertificateEligibilityResponse.java
├── service/
│   ├── JitsiMeetService.java
│   ├── LmsAccessService.java         — LMS 접근 권한 검증 공통 서비스
│   ├── LmsDashboardService.java
│   ├── CurriculumService.java
│   ├── LmsAssignmentService.java
│   ├── LearningNoteService.java
│   ├── CareerService.java
│   └── CertificateService.java
├── controller/
│   ├── LmsDashboardController.java
│   ├── CurriculumController.java
│   ├── LmsAssignmentController.java
│   ├── LearningNoteController.java
│   ├── CareerController.java
│   └── CertificateController.java
└── exception/
    ├── LmsAccessDeniedException.java
    ├── CurriculumNotFoundException.java
    ├── AssignmentNotFoundException.java
    ├── NoteNotFoundException.java
    └── ResumeNotFoundException.java
```

### Backend 수정 파일

```
backend/src/main/java/com/devmatch/
├── service/SessionService.java              — GoogleCalendar → Jitsi 전환
├── exception/GlobalExceptionHandler.java    — LMS 예외 핸들러 추가
└── build.gradle                             — OpenPDF 의존성 추가
```

### Frontend 신규 파일

```
frontend/src/
├── app/lms/
│   ├── layout.tsx                  — 사이드바 레이아웃
│   ├── dashboard/page.tsx          — 대시보드
│   ├── curriculum/page.tsx         — 커리큘럼
│   ├── sessions/page.tsx           — 세션 목록 + Jitsi 링크
│   ├── assignments/page.tsx        — 과제/코드리뷰
│   ├── notes/page.tsx              — 학습 노트
│   ├── career/page.tsx             — 취업 지원
│   └── certificate/page.tsx        — 수료증
├── components/lms/
│   ├── LmsSidebar.tsx              — 사이드바 네비게이션
│   ├── StatCard.tsx                — 통계 카드 컴포넌트
│   ├── ProgressCircle.tsx          — 원형 프로그레스
│   ├── ActivityFeed.tsx            — 최근 활동 피드
│   └── SessionCalendar.tsx         — FullCalendar 래퍼
├── lib/
│   └── lms.ts                      — LMS API 호출 함수
└── lib/lms-types.ts                — LMS 타입 정의
```

### Frontend 수정 파일

```
frontend/src/
├── components/layout/Header.tsx    — LMS 메뉴 항목 추가
└── package.json                    — shadcn/ui, recharts, fullcalendar 의존성 추가
```

---

## 공통 작업 (Day 1)

### Task 1: Enum 및 LMS 접근 권한 서비스 (공통 기반)

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/AssignmentType.java`
- Create: `backend/src/main/java/com/devmatch/entity/AssignmentStatus.java`
- Create: `backend/src/main/java/com/devmatch/entity/NoteType.java`
- Create: `backend/src/main/java/com/devmatch/service/LmsAccessService.java`
- Create: `backend/src/main/java/com/devmatch/exception/LmsAccessDeniedException.java`
- Modify: `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java:86-139`

- [ ] **Step 1: Create AssignmentType enum**

```java
// backend/src/main/java/com/devmatch/entity/AssignmentType.java
package com.devmatch.entity;

public enum AssignmentType {
    TASK,
    CODE_REVIEW
}
```

- [ ] **Step 2: Create AssignmentStatus enum**

```java
// backend/src/main/java/com/devmatch/entity/AssignmentStatus.java
package com.devmatch.entity;

public enum AssignmentStatus {
    ASSIGNED,
    SUBMITTED,
    REVIEWED
}
```

- [ ] **Step 3: Create NoteType enum**

```java
// backend/src/main/java/com/devmatch/entity/NoteType.java
package com.devmatch.entity;

public enum NoteType {
    SESSION_REVIEW,
    WEEKLY_JOURNAL
}
```

- [ ] **Step 4: Create LmsAccessDeniedException**

```java
// backend/src/main/java/com/devmatch/exception/LmsAccessDeniedException.java
package com.devmatch.exception;

public class LmsAccessDeniedException extends RuntimeException {
    public LmsAccessDeniedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Create LmsAccessService**

이 서비스는 모든 LMS 컨트롤러에서 공유하는 접근 권한 검증 로직을 담는다. `Matching.status`가 TRIAL 또는 ACCEPTED인지, 요청자가 해당 매칭의 멘토 또는 멘티인지를 확인한다.

```java
// backend/src/main/java/com/devmatch/service/LmsAccessService.java
package com.devmatch.service;

import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.exception.LmsAccessDeniedException;
import com.devmatch.exception.MatchingNotFoundException;
import com.devmatch.repository.MatchingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsAccessService {

    private final MatchingRepository matchingRepository;

    /**
     * LMS 접근 권한 검증.
     * 1. matchingId로 Matching 조회
     * 2. 요청자가 멘토 또는 멘티인지 확인
     * 3. Matching.status가 TRIAL 또는 ACCEPTED인지 확인
     *
     * @return 검증된 Matching 엔티티
     */
    public Matching validateAccess(Long userId, Long matchingId) {
        Matching matching = matchingRepository.findById(matchingId)
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다: " + matchingId));

        boolean isParticipant = matching.getMentee().getId().equals(userId)
                || matching.getMentor().getId().equals(userId);

        if (!isParticipant) {
            throw new LmsAccessDeniedException("해당 매칭에 대한 접근 권한이 없습니다");
        }

        boolean isActiveMatching = matching.getStatus() == MatchingStatus.TRIAL
                || matching.getStatus() == MatchingStatus.ACCEPTED;

        if (!isActiveMatching) {
            throw new LmsAccessDeniedException("활성 상태의 매칭만 LMS에 접근할 수 있습니다. 현재 상태: " + matching.getStatus());
        }

        return matching;
    }

    /**
     * 멘토 전용 접근 권한 검증
     */
    public Matching validateMentorAccess(Long userId, Long matchingId) {
        Matching matching = validateAccess(userId, matchingId);
        if (!matching.getMentor().getId().equals(userId)) {
            throw new LmsAccessDeniedException("멘토만 수행할 수 있는 작업입니다");
        }
        return matching;
    }

    /**
     * 멘티 전용 접근 권한 검증
     */
    public Matching validateMenteeAccess(Long userId, Long matchingId) {
        Matching matching = validateAccess(userId, matchingId);
        if (!matching.getMentee().getId().equals(userId)) {
            throw new LmsAccessDeniedException("멘티만 수행할 수 있는 작업입니다");
        }
        return matching;
    }
}
```

- [ ] **Step 6: Add LMS exception handlers to GlobalExceptionHandler**

`GlobalExceptionHandler.java`의 `// ===== Phase 5` 주석 위에 LMS 예외 핸들러를 추가한다.

기존 파일의 87행 `// ===== Phase 5: 결제 & 커뮤니티 =====` 바로 위에 삽입:

```java
    // ===== LMS =====

    @ExceptionHandler(LmsAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLmsAccessDenied(LmsAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(CurriculumNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCurriculumNotFound(CurriculumNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AssignmentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAssignmentNotFound(AssignmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(NoteNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoteNotFound(NoteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(ResumeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResumeNotFound(ResumeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
```

- [ ] **Step 7: Create remaining exception classes**

```java
// backend/src/main/java/com/devmatch/exception/CurriculumNotFoundException.java
package com.devmatch.exception;

public class CurriculumNotFoundException extends RuntimeException {
    public CurriculumNotFoundException(String message) {
        super(message);
    }
}
```

```java
// backend/src/main/java/com/devmatch/exception/AssignmentNotFoundException.java
package com.devmatch.exception;

public class AssignmentNotFoundException extends RuntimeException {
    public AssignmentNotFoundException(String message) {
        super(message);
    }
}
```

```java
// backend/src/main/java/com/devmatch/exception/NoteNotFoundException.java
package com.devmatch.exception;

public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(String message) {
        super(message);
    }
}
```

```java
// backend/src/main/java/com/devmatch/exception/ResumeNotFoundException.java
package com.devmatch.exception;

public class ResumeNotFoundException extends RuntimeException {
    public ResumeNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 8: Build to verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/AssignmentType.java \
       backend/src/main/java/com/devmatch/entity/AssignmentStatus.java \
       backend/src/main/java/com/devmatch/entity/NoteType.java \
       backend/src/main/java/com/devmatch/service/LmsAccessService.java \
       backend/src/main/java/com/devmatch/exception/LmsAccessDeniedException.java \
       backend/src/main/java/com/devmatch/exception/CurriculumNotFoundException.java \
       backend/src/main/java/com/devmatch/exception/AssignmentNotFoundException.java \
       backend/src/main/java/com/devmatch/exception/NoteNotFoundException.java \
       backend/src/main/java/com/devmatch/exception/ResumeNotFoundException.java \
       backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java
git commit -m "feat(lms): add enums, access service, and exception handlers for LMS"
```

---

### Task 2: 프론트엔드 공통 — LMS 레이아웃 + 사이드바

**Files:**
- Create: `frontend/src/app/lms/layout.tsx`
- Create: `frontend/src/components/lms/LmsSidebar.tsx`
- Create: `frontend/src/lib/lms-types.ts`
- Create: `frontend/src/lib/lms.ts`
- Modify: `frontend/src/components/layout/Header.tsx:9-15`

- [ ] **Step 1: Install frontend dependencies**

Run: `cd frontend && npm install recharts @fullcalendar/react @fullcalendar/daygrid @fullcalendar/core`
Expected: added N packages

- [ ] **Step 2: Create LMS TypeScript types**

```typescript
// frontend/src/lib/lms-types.ts

// ─── Enums ───
export type AssignmentType = 'TASK' | 'CODE_REVIEW';
export type AssignmentStatus = 'ASSIGNED' | 'SUBMITTED' | 'REVIEWED';
export type NoteType = 'SESSION_REVIEW' | 'WEEKLY_JOURNAL';

// ─── Dashboard ───
export interface DashboardResponse {
  progressRate: number;
  attendanceRate: number;
  dDay: number;
  assignmentStats: {
    total: number;
    submitted: number;
    reviewed: number;
  };
  nextSession: {
    id: number;
    date: string;
    startTime: string;
    endTime: string;
    meetLink: string;
    category: string;
  } | null;
  recentActivities: {
    type: string;
    title: string;
    createdAt: string;
  }[];
  mentorInfo: {
    name: string;
    specialty: string[];
    email: string;
  };
  communicationLinks: {
    discord: string | null;
    jitsiMeet: string | null;
  };
}

export interface EnrollmentResponse {
  matchingId: number;
  menteeName: string;
  mentorName: string;
  category: string;
  status: string;
  startDate: string;
  trialEndDate: string | null;
}

// ─── Curriculum ───
export interface CurriculumWeekResponse {
  id: number;
  weekNumber: number;
  title: string;
  description: string | null;
  topics: string[];
  resources: string[];
  isCompleted: boolean;
  completedAt: string | null;
}

export interface CurriculumResponse {
  id: number;
  matchingId: number;
  title: string;
  description: string | null;
  totalWeeks: number;
  startDate: string;
  endDate: string;
  discordUrl: string | null;
  weeks: CurriculumWeekResponse[];
}

export interface CurriculumWeekRequest {
  weekNumber: number;
  title: string;
  description?: string;
  topics: string[];
  resources: string[];
}

export interface CurriculumCreateRequest {
  matchingId: number;
  title: string;
  description?: string;
  totalWeeks: number;
  startDate: string;
  endDate: string;
  discordUrl?: string;
  weeks: CurriculumWeekRequest[];
}

// ─── Assignment ───
export interface SubmissionResponse {
  id: number;
  submissionUrl: string;
  submissionNote: string | null;
  submittedAt: string;
  feedbackContent: string | null;
  grade: string | null;
  feedbackAt: string | null;
}

export interface AssignmentResponse {
  id: number;
  matchingId: number;
  mentorId: number;
  type: AssignmentType;
  title: string;
  description: string | null;
  dueDate: string | null;
  referenceUrls: string[];
  status: AssignmentStatus;
  submission: SubmissionResponse | null;
  createdAt: string;
}

export interface AssignmentCreateRequest {
  matchingId: number;
  type: AssignmentType;
  title: string;
  description?: string;
  dueDate?: string;
  referenceUrls?: string[];
}

export interface SubmissionRequest {
  submissionUrl: string;
  submissionNote?: string;
}

export interface FeedbackRequest {
  feedbackContent: string;
  grade?: string;
}

// ─── Learning Note ───
export interface NoteCommentResponse {
  id: number;
  authorId: number;
  authorName: string;
  content: string;
  createdAt: string;
}

export interface NoteResponse {
  id: number;
  matchingId: number;
  authorId: number;
  authorName: string;
  type: NoteType;
  sessionId: number | null;
  weekNumber: number | null;
  title: string;
  content: string;
  selfRating: number | null;
  comments: NoteCommentResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface NoteCreateRequest {
  matchingId: number;
  type: NoteType;
  sessionId?: number;
  weekNumber?: number;
  title: string;
  content: string;
  selfRating?: number;
}

export interface NoteCommentRequest {
  content: string;
}

// ─── Career ───
export interface ResumeResponse {
  id: number;
  menteeId: number;
  matchingId: number;
  version: number;
  fileUrl: string;
  fileName: string;
  mentorFeedback: string | null;
  feedbackAt: string | null;
  uploadedAt: string;
}

export interface ResumeFeedbackRequest {
  mentorFeedback: string;
}

export interface MockInterviewResponse {
  id: number;
  matchingId: number;
  interviewDate: string;
  topic: string;
  questionsAndAnswers: string | null;
  mentorFeedback: string | null;
  rating: number | null;
  createdAt: string;
}

export interface MockInterviewCreateRequest {
  matchingId: number;
  interviewDate: string;
  topic: string;
  questionsAndAnswers?: string;
  mentorFeedback?: string;
  rating?: number;
}

// ─── Certificate ───
export interface CertificateEligibilityResponse {
  eligible: boolean;
  progressRate: number;
  attendanceRate: number;
  assignmentSubmitRate: number;
  requiredProgress: number;
  requiredAttendance: number;
  requiredAssignmentRate: number;
}
```

- [ ] **Step 3: Create LMS API client**

```typescript
// frontend/src/lib/lms.ts
import apiClient from './api';
import type { ApiResponse } from './types';
import type {
  DashboardResponse,
  EnrollmentResponse,
  CurriculumResponse,
  CurriculumCreateRequest,
  AssignmentResponse,
  AssignmentCreateRequest,
  SubmissionRequest,
  FeedbackRequest,
  NoteResponse,
  NoteCreateRequest,
  NoteCommentRequest,
  ResumeResponse,
  ResumeFeedbackRequest,
  MockInterviewResponse,
  MockInterviewCreateRequest,
  CertificateEligibilityResponse,
} from './lms-types';

// ─── Dashboard ───
export const getDashboard = (matchingId: number) =>
  apiClient.get<ApiResponse<DashboardResponse>>(`/lms/dashboard/${matchingId}`);

export const getEnrollment = (matchingId: number) =>
  apiClient.get<ApiResponse<EnrollmentResponse>>(`/lms/enrollment/${matchingId}`);

// ─── Curriculum ───
export const createCurriculum = (data: CurriculumCreateRequest) =>
  apiClient.post<ApiResponse<CurriculumResponse>>('/lms/curriculum', data);

export const getCurriculum = (matchingId: number) =>
  apiClient.get<ApiResponse<CurriculumResponse>>(`/lms/curriculum/${matchingId}`);

export const updateCurriculum = (id: number, data: Partial<CurriculumCreateRequest>) =>
  apiClient.put<ApiResponse<CurriculumResponse>>(`/lms/curriculum/${id}`, data);

export const toggleWeekComplete = (weekId: number) =>
  apiClient.put<ApiResponse<void>>(`/lms/curriculum/weeks/${weekId}/complete`);

// ─── Assignment ───
export const createAssignment = (data: AssignmentCreateRequest) =>
  apiClient.post<ApiResponse<AssignmentResponse>>('/lms/assignments', data);

export const getAssignments = (matchingId: number, type?: string) =>
  apiClient.get<ApiResponse<AssignmentResponse[]>>('/lms/assignments', {
    params: { matchingId, type },
  });

export const getAssignment = (id: number) =>
  apiClient.get<ApiResponse<AssignmentResponse>>(`/lms/assignments/${id}`);

export const submitAssignment = (id: number, data: SubmissionRequest) =>
  apiClient.post<ApiResponse<AssignmentResponse>>(`/lms/assignments/${id}/submit`, data);

export const feedbackAssignment = (id: number, data: FeedbackRequest) =>
  apiClient.post<ApiResponse<AssignmentResponse>>(`/lms/assignments/${id}/feedback`, data);

// ─── Notes ───
export const createNote = (data: NoteCreateRequest) =>
  apiClient.post<ApiResponse<NoteResponse>>('/lms/notes', data);

export const getNotes = (matchingId: number, type?: string) =>
  apiClient.get<ApiResponse<NoteResponse[]>>('/lms/notes', {
    params: { matchingId, type },
  });

export const getNote = (id: number) =>
  apiClient.get<ApiResponse<NoteResponse>>(`/lms/notes/${id}`);

export const updateNote = (id: number, data: Partial<NoteCreateRequest>) =>
  apiClient.put<ApiResponse<NoteResponse>>(`/lms/notes/${id}`, data);

export const addNoteComment = (noteId: number, data: NoteCommentRequest) =>
  apiClient.post<ApiResponse<NoteResponse>>(`/lms/notes/${noteId}/comments`, data);

// ─── Career ───
export const uploadResume = (matchingId: number, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('matchingId', matchingId.toString());
  return apiClient.post<ApiResponse<ResumeResponse>>('/lms/resumes', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getResumes = (matchingId: number) =>
  apiClient.get<ApiResponse<ResumeResponse[]>>('/lms/resumes', {
    params: { matchingId },
  });

export const feedbackResume = (id: number, data: ResumeFeedbackRequest) =>
  apiClient.post<ApiResponse<ResumeResponse>>(`/lms/resumes/${id}/feedback`, data);

export const createMockInterview = (data: MockInterviewCreateRequest) =>
  apiClient.post<ApiResponse<MockInterviewResponse>>('/lms/mock-interviews', data);

export const getMockInterviews = (matchingId: number) =>
  apiClient.get<ApiResponse<MockInterviewResponse[]>>('/lms/mock-interviews', {
    params: { matchingId },
  });

// ─── Certificate ───
export const checkEligibility = (matchingId: number) =>
  apiClient.get<ApiResponse<CertificateEligibilityResponse>>(`/lms/certificate/eligibility/${matchingId}`);

export const downloadCertificate = (matchingId: number) =>
  apiClient.get(`/lms/certificate/${matchingId}/download`, {
    responseType: 'blob',
  });
```

- [ ] **Step 4: Create LmsSidebar component**

```tsx
// frontend/src/components/lms/LmsSidebar.tsx
'use client';

import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import {
  LayoutDashboard,
  BookOpen,
  Video,
  ClipboardList,
  NotebookPen,
  Briefcase,
  Award,
} from 'lucide-react';

const menuItems = [
  { label: '대시보드', href: '/lms/dashboard', icon: LayoutDashboard },
  { label: '커리큘럼', href: '/lms/curriculum', icon: BookOpen },
  { label: '멘토링 세션', href: '/lms/sessions', icon: Video },
  { label: '과제 / 코드리뷰', href: '/lms/assignments', icon: ClipboardList },
  { label: '학습 노트', href: '/lms/notes', icon: NotebookPen },
  { label: '취업 지원', href: '/lms/career', icon: Briefcase },
  { label: '수료증', href: '/lms/certificate', icon: Award },
];

export default function LmsSidebar() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const matchingId = searchParams.get('matchingId') || '';

  return (
    <aside className="w-64 min-h-screen bg-[#0b101e] border-r border-white/5 flex flex-col">
      {/* Logo area */}
      <div className="px-6 py-5 border-b border-white/5">
        <Link href="/" className="text-white font-bold text-lg tracking-tight font-[Outfit]">
          Dev<span className="text-cyan-400">Match</span>
          <span className="text-gray-500 text-sm font-normal ml-2">LMS</span>
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1">
        {menuItems.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;
          const href = matchingId ? `${item.href}?matchingId=${matchingId}` : item.href;

          return (
            <Link
              key={item.href}
              href={href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 ${
                isActive
                  ? 'bg-blue-500/10 text-blue-400'
                  : 'text-gray-400 hover:text-white hover:bg-white/5'
              }`}
            >
              <Icon size={18} />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
```

- [ ] **Step 5: Create LMS layout**

```tsx
// frontend/src/app/lms/layout.tsx
import LmsSidebar from '@/components/lms/LmsSidebar';

export default function LmsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[#0a0e1a]">
      <LmsSidebar />
      <main className="flex-1 p-8 overflow-auto">
        {children}
      </main>
    </div>
  );
}
```

- [ ] **Step 6: Add LMS menu item to Header.tsx**

`Header.tsx`의 `navItems` 배열(9행)에 LMS 항목을 추가한다.

변경 전:
```typescript
const navItems = [
  { label: '멘토 찾기', href: '/mentors' },
  { label: '실력 테스트', href: '/tests' },
  { label: '수강 신청', href: '/apply' },
  { label: '커뮤니티', href: '/community' },
  { label: 'FAQ', href: '/faq' },
];
```

변경 후:
```typescript
const navItems = [
  { label: '멘토 찾기', href: '/mentors' },
  { label: '실력 테스트', href: '/tests' },
  { label: '수강 신청', href: '/apply' },
  { label: 'LMS', href: '/lms/dashboard' },
  { label: '커뮤니티', href: '/community' },
  { label: 'FAQ', href: '/faq' },
];
```

`lucide-react` import에 `GraduationCap` 추가:
```typescript
import { Menu, X, User, LogOut, ChevronDown, FileText, Users, GraduationCap } from 'lucide-react';
```

- [ ] **Step 7: Verify frontend compiles**

Run: `cd frontend && npm run build`
Expected: BUILD 성공 (or at least `npm run dev` starts without errors)

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/lms/layout.tsx \
       frontend/src/components/lms/LmsSidebar.tsx \
       frontend/src/lib/lms-types.ts \
       frontend/src/lib/lms.ts \
       frontend/src/components/layout/Header.tsx \
       frontend/package.json frontend/package-lock.json
git commit -m "feat(lms): add LMS layout, sidebar, types, and API client"
```

---

## 담당자 A 트랙

### Task 3: Curriculum + CurriculumWeek Entity & Repository (Day 2)

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/Curriculum.java`
- Create: `backend/src/main/java/com/devmatch/entity/CurriculumWeek.java`
- Create: `backend/src/main/java/com/devmatch/repository/CurriculumRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/CurriculumWeekRepository.java`

- [ ] **Step 1: Create Curriculum entity**

```java
// backend/src/main/java/com/devmatch/entity/Curriculum.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "curriculums")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Curriculum {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matching_id", nullable = false, unique = true)
    private Long matchingId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_weeks", nullable = false)
    private Integer totalWeeks;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "discord_url", length = 500)
    private String discordUrl;

    @OneToMany(mappedBy = "curriculum", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("weekNumber ASC")
    @Builder.Default
    private List<CurriculumWeek> weeks = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(String title, String description, Integer totalWeeks,
                       LocalDate startDate, LocalDate endDate, String discordUrl) {
        this.title = title;
        this.description = description;
        this.totalWeeks = totalWeeks;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discordUrl = discordUrl;
    }

    public void addWeek(CurriculumWeek week) {
        this.weeks.add(week);
    }
}
```

- [ ] **Step 2: Create CurriculumWeek entity**

```java
// backend/src/main/java/com/devmatch/entity/CurriculumWeek.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "curriculum_weeks")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class CurriculumWeek {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> topics;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> resources;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public void toggleComplete() {
        if (this.isCompleted) {
            this.isCompleted = false;
            this.completedAt = null;
        } else {
            this.isCompleted = true;
            this.completedAt = LocalDateTime.now();
        }
    }
}
```

- [ ] **Step 3: Create CurriculumRepository**

```java
// backend/src/main/java/com/devmatch/repository/CurriculumRepository.java
package com.devmatch.repository;

import com.devmatch.entity.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    Optional<Curriculum> findByMatchingId(Long matchingId);
    boolean existsByMatchingId(Long matchingId);
}
```

- [ ] **Step 4: Create CurriculumWeekRepository**

```java
// backend/src/main/java/com/devmatch/repository/CurriculumWeekRepository.java
package com.devmatch.repository;

import com.devmatch.entity.CurriculumWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurriculumWeekRepository extends JpaRepository<CurriculumWeek, Long> {
    List<CurriculumWeek> findByCurriculumIdOrderByWeekNumberAsc(Long curriculumId);
    long countByCurriculumIdAndIsCompletedTrue(Long curriculumId);
}
```

- [ ] **Step 5: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/Curriculum.java \
       backend/src/main/java/com/devmatch/entity/CurriculumWeek.java \
       backend/src/main/java/com/devmatch/repository/CurriculumRepository.java \
       backend/src/main/java/com/devmatch/repository/CurriculumWeekRepository.java
git commit -m "feat(lms): add Curriculum and CurriculumWeek entities with repositories"
```

---

### Task 4: Curriculum DTO + Service + Controller (Day 2)

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/lms/CurriculumCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/CurriculumResponse.java`
- Create: `backend/src/main/java/com/devmatch/service/CurriculumService.java`
- Create: `backend/src/main/java/com/devmatch/controller/CurriculumController.java`

- [ ] **Step 1: Create CurriculumCreateRequest DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/CurriculumCreateRequest.java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumCreateRequest {

    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;

    @NotBlank(message = "커리큘럼 제목은 필수입니다")
    private String title;

    private String description;

    @NotNull(message = "총 주차 수는 필수입니다")
    private Integer totalWeeks;

    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다")
    private LocalDate endDate;

    private String discordUrl;

    private List<WeekRequest> weeks;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekRequest {
        private Integer weekNumber;
        private String title;
        private String description;
        private List<String> topics;
        private List<String> resources;
    }
}
```

- [ ] **Step 2: Create CurriculumResponse DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/CurriculumResponse.java
package com.devmatch.dto.lms;

import com.devmatch.entity.Curriculum;
import com.devmatch.entity.CurriculumWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@Builder
public class CurriculumResponse {

    private Long id;
    private Long matchingId;
    private String title;
    private String description;
    private Integer totalWeeks;
    private LocalDate startDate;
    private LocalDate endDate;
    private String discordUrl;
    private List<WeekResponse> weeks;
    private LocalDateTime createdAt;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class WeekResponse {
        private Long id;
        private Integer weekNumber;
        private String title;
        private String description;
        private List<String> topics;
        private List<String> resources;
        private Boolean isCompleted;
        private LocalDateTime completedAt;

        public static WeekResponse from(CurriculumWeek week) {
            return WeekResponse.builder()
                    .id(week.getId())
                    .weekNumber(week.getWeekNumber())
                    .title(week.getTitle())
                    .description(week.getDescription())
                    .topics(week.getTopics())
                    .resources(week.getResources())
                    .isCompleted(week.getIsCompleted())
                    .completedAt(week.getCompletedAt())
                    .build();
        }
    }

    public static CurriculumResponse from(Curriculum curriculum) {
        return CurriculumResponse.builder()
                .id(curriculum.getId())
                .matchingId(curriculum.getMatchingId())
                .title(curriculum.getTitle())
                .description(curriculum.getDescription())
                .totalWeeks(curriculum.getTotalWeeks())
                .startDate(curriculum.getStartDate())
                .endDate(curriculum.getEndDate())
                .discordUrl(curriculum.getDiscordUrl())
                .weeks(curriculum.getWeeks().stream()
                        .map(WeekResponse::from)
                        .collect(Collectors.toList()))
                .createdAt(curriculum.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 3: Create CurriculumService**

```java
// backend/src/main/java/com/devmatch/service/CurriculumService.java
package com.devmatch.service;

import com.devmatch.dto.lms.CurriculumCreateRequest;
import com.devmatch.dto.lms.CurriculumResponse;
import com.devmatch.entity.Curriculum;
import com.devmatch.entity.CurriculumWeek;
import com.devmatch.exception.CurriculumNotFoundException;
import com.devmatch.repository.CurriculumRepository;
import com.devmatch.repository.CurriculumWeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final LmsAccessService lmsAccessService;

    @Transactional
    public CurriculumResponse create(Long userId, CurriculumCreateRequest request) {
        lmsAccessService.validateMentorAccess(userId, request.getMatchingId());

        Curriculum curriculum = Curriculum.builder()
                .matchingId(request.getMatchingId())
                .title(request.getTitle())
                .description(request.getDescription())
                .totalWeeks(request.getTotalWeeks())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .discordUrl(request.getDiscordUrl())
                .build();

        Curriculum saved = curriculumRepository.save(curriculum);

        if (request.getWeeks() != null) {
            for (CurriculumCreateRequest.WeekRequest weekReq : request.getWeeks()) {
                CurriculumWeek week = CurriculumWeek.builder()
                        .curriculum(saved)
                        .weekNumber(weekReq.getWeekNumber())
                        .title(weekReq.getTitle())
                        .description(weekReq.getDescription())
                        .topics(weekReq.getTopics() != null ? weekReq.getTopics() : Collections.emptyList())
                        .resources(weekReq.getResources() != null ? weekReq.getResources() : Collections.emptyList())
                        .build();
                saved.addWeek(week);
            }
            curriculumRepository.save(saved);
        }

        return CurriculumResponse.from(saved);
    }

    public CurriculumResponse getByMatchingId(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);

        Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId)
                .orElseThrow(() -> new CurriculumNotFoundException("커리큘럼을 찾을 수 없습니다. matchingId: " + matchingId));

        return CurriculumResponse.from(curriculum);
    }

    @Transactional
    public CurriculumResponse update(Long userId, Long curriculumId, CurriculumCreateRequest request) {
        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new CurriculumNotFoundException("커리큘럼을 찾을 수 없습니다: " + curriculumId));

        lmsAccessService.validateMentorAccess(userId, curriculum.getMatchingId());

        curriculum.update(
                request.getTitle(),
                request.getDescription(),
                request.getTotalWeeks(),
                request.getStartDate(),
                request.getEndDate(),
                request.getDiscordUrl()
        );

        return CurriculumResponse.from(curriculum);
    }

    @Transactional
    public void toggleWeekComplete(Long userId, Long weekId) {
        CurriculumWeek week = weekRepository.findById(weekId)
                .orElseThrow(() -> new CurriculumNotFoundException("주차를 찾을 수 없습니다: " + weekId));

        lmsAccessService.validateMenteeAccess(userId, week.getCurriculum().getMatchingId());
        week.toggleComplete();
    }
}
```

- [ ] **Step 4: Create CurriculumController**

```java
// backend/src/main/java/com/devmatch/controller/CurriculumController.java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.CurriculumCreateRequest;
import com.devmatch.dto.lms.CurriculumResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.CurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "LMS - Curriculum", description = "커리큘럼 관리 API")
@RestController
@RequestMapping("/api/lms/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;

    @Operation(summary = "커리큘럼 생성", description = "멘토가 커리큘럼과 주차 정보를 생성합니다")
    @PostMapping
    public ResponseEntity<ApiResponse<CurriculumResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CurriculumCreateRequest request
    ) {
        CurriculumResponse response = curriculumService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("커리큘럼이 생성되었습니다", response));
    }

    @Operation(summary = "커리큘럼 조회", description = "매칭 ID로 커리큘럼과 주차 목록을 조회합니다")
    @GetMapping("/{matchingId}")
    public ResponseEntity<ApiResponse<CurriculumResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        CurriculumResponse response = curriculumService.getByMatchingId(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "커리큘럼 수정", description = "멘토가 커리큘럼 정보를 수정합니다")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CurriculumResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CurriculumCreateRequest request
    ) {
        CurriculumResponse response = curriculumService.update(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("커리큘럼이 수정되었습니다", response));
    }

    @Operation(summary = "주차 완료 토글", description = "멘티가 주차 완료 상태를 토글합니다")
    @PutMapping("/weeks/{weekId}/complete")
    public ResponseEntity<ApiResponse<Void>> toggleWeekComplete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long weekId
    ) {
        curriculumService.toggleWeekComplete(user.getUserId(), weekId);
        return ResponseEntity.ok(ApiResponse.success("주차 완료 상태가 변경되었습니다", null));
    }
}
```

- [ ] **Step 5: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/dto/lms/CurriculumCreateRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/CurriculumResponse.java \
       backend/src/main/java/com/devmatch/service/CurriculumService.java \
       backend/src/main/java/com/devmatch/controller/CurriculumController.java
git commit -m "feat(lms): add Curriculum API — create, read, update, toggle week complete"
```

---

### Task 5: JitsiMeetService + SessionService 수정 (Day 3)

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/JitsiMeetService.java`
- Modify: `backend/src/main/java/com/devmatch/service/SessionService.java:27-89`

- [ ] **Step 1: Create JitsiMeetService**

```java
// backend/src/main/java/com/devmatch/service/JitsiMeetService.java
package com.devmatch.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class JitsiMeetService {

    private static final String JITSI_BASE_URL = "https://meet.jit.si";

    public String generateMeetLink(Long matchingId, LocalDate sessionDate) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String roomName = String.format("devmatch-%d-%s-%s",
                matchingId,
                sessionDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                uuid);
        return JITSI_BASE_URL + "/" + roomName;
    }
}
```

- [ ] **Step 2: Modify SessionService to use Jitsi instead of Google Calendar**

`SessionService.java`에서 `googleCalendarService` 의존성을 `jitsiMeetService`로 교체한다.

변경할 부분 — 필드 선언 (29행):

변경 전:
```java
    private final GoogleCalendarService googleCalendarService;
```

변경 후:
```java
    private final GoogleCalendarService googleCalendarService;
    private final JitsiMeetService jitsiMeetService;
```

변경할 부분 — `createSession` 메서드의 Google Calendar 호출 블록 (61~86행):

변경 전:
```java
        // Google Calendar 이벤트 생성 시도
        try {
            // 사용자 이메일 조회
            String menteeEmail = userRepository.findById(userId)
                    .map(u -> u.getEmail())
                    .orElse("mentee@devmatch.kr");

            Map<String, String> calendarResult = googleCalendarService.createMentoringEvent(
                    "mentor@devmatch.kr",  // Matching 연동 시 실제 멘토 이메일로 교체
                    menteeEmail,
                    saved.getCategory(),
                    saved.getSessionDate(),
                    saved.getStartTime(),
                    saved.getEndTime(),
                    saved.getMemo()
            );

            if (calendarResult != null) {
                saved.updateMeetLink(calendarResult.get("meetLink"));
                saved.updateCalendarEventId(calendarResult.get("calendarEventId"));
                log.info("[Session] Google Calendar 연동 성공 — sessionId: {}", saved.getId());
            }
        } catch (Exception e) {
            // Calendar 연동 실패해도 세션은 유지
            log.warn("[Session] Google Calendar 연동 실패 (세션은 유지) — {}", e.getMessage());
        }
```

변경 후:
```java
        // Jitsi Meet 링크 생성
        String meetLink = jitsiMeetService.generateMeetLink(
                saved.getMatchingId(), saved.getSessionDate());
        saved.updateMeetLink(meetLink);
        log.info("[Session] Jitsi Meet 링크 생성 — sessionId: {}, link: {}", saved.getId(), meetLink);
```

import에서 `java.util.Map` 제거 가능 (다른 곳에서 사용하지 않으면).

- [ ] **Step 3: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/JitsiMeetService.java \
       backend/src/main/java/com/devmatch/service/SessionService.java
git commit -m "feat(lms): add JitsiMeetService and replace Google Calendar in SessionService"
```

---

### Task 6: Dashboard API (Day 4)

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/EnrollmentResponse.java`
- Create: `backend/src/main/java/com/devmatch/service/LmsDashboardService.java`
- Create: `backend/src/main/java/com/devmatch/controller/LmsDashboardController.java`

- [ ] **Step 1: Create DashboardResponse DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java
package com.devmatch.dto.lms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private int progressRate;
    private int attendanceRate;
    private long dDay;
    private AssignmentStats assignmentStats;
    private NextSessionInfo nextSession;
    private List<ActivityItem> recentActivities;
    private MentorInfo mentorInfo;
    private CommunicationLinks communicationLinks;

    @Getter @AllArgsConstructor @Builder
    public static class AssignmentStats {
        private long total;
        private long submitted;
        private long reviewed;
    }

    @Getter @AllArgsConstructor @Builder
    public static class NextSessionInfo {
        private Long id;
        private String date;
        private String startTime;
        private String endTime;
        private String meetLink;
        private String category;
    }

    @Getter @AllArgsConstructor @Builder
    public static class ActivityItem {
        private String type;
        private String title;
        private String createdAt;
    }

    @Getter @AllArgsConstructor @Builder
    public static class MentorInfo {
        private String name;
        private List<String> specialty;
        private String email;
    }

    @Getter @AllArgsConstructor @Builder
    public static class CommunicationLinks {
        private String discord;
        private String jitsiMeet;
    }
}
```

- [ ] **Step 2: Create EnrollmentResponse DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/EnrollmentResponse.java
package com.devmatch.dto.lms;

import com.devmatch.entity.Matching;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Builder
public class EnrollmentResponse {

    private Long matchingId;
    private String menteeName;
    private String mentorName;
    private String category;
    private String status;
    private LocalDate startDate;
    private LocalDate trialEndDate;

    public static EnrollmentResponse from(Matching matching) {
        return EnrollmentResponse.builder()
                .matchingId(matching.getId())
                .menteeName(matching.getMentee().getName())
                .mentorName(matching.getMentor().getName())
                .category(matching.getCategory())
                .status(matching.getStatus().name())
                .startDate(matching.getCreatedAt().toLocalDate())
                .trialEndDate(matching.getTrialEndDate())
                .build();
    }
}
```

- [ ] **Step 3: Create LmsDashboardService**

```java
// backend/src/main/java/com/devmatch/service/LmsDashboardService.java
package com.devmatch.service;

import com.devmatch.dto.lms.DashboardResponse;
import com.devmatch.dto.lms.EnrollmentResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsDashboardService {

    private final LmsAccessService lmsAccessService;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final AssignmentRepository assignmentRepository;
    private final MentoringSessionRepository sessionRepository;
    private final LearningNoteRepository noteRepository;

    public DashboardResponse getDashboard(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);

        // 진도율: 완료 주차 / 전체 주차
        int progressRate = 0;
        String discordUrl = null;
        if (curriculumRepository.existsByMatchingId(matchingId)) {
            Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId).orElse(null);
            if (curriculum != null && curriculum.getTotalWeeks() > 0) {
                long completedWeeks = weekRepository.countByCurriculumIdAndIsCompletedTrue(curriculum.getId());
                progressRate = (int) ((completedWeeks * 100) / curriculum.getTotalWeeks());
                discordUrl = curriculum.getDiscordUrl();
            }
        }

        // 출석률: 완료 세션 / 전체 세션 (CANCELLED 제외)
        List<MentoringSession> sessions = sessionRepository
                .findByMenteeIdOrMentorIdOrderBySessionDateDesc(
                        matching.getMentee().getId(), matching.getMentor().getId());
        // matchingId로 필터
        List<MentoringSession> matchingSessions = sessions.stream()
                .filter(s -> s.getMatchingId().equals(matchingId))
                .toList();
        long totalSessions = matchingSessions.stream()
                .filter(s -> s.getStatus() != SessionStatus.CANCELLED)
                .count();
        long completedSessions = matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .count();
        int attendanceRate = totalSessions > 0 ? (int) ((completedSessions * 100) / totalSessions) : 0;

        // D-Day: 커리큘럼 종료일까지 남은 일수
        long dDay = 0;
        if (curriculumRepository.existsByMatchingId(matchingId)) {
            Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId).orElse(null);
            if (curriculum != null) {
                dDay = ChronoUnit.DAYS.between(LocalDate.now(), curriculum.getEndDate());
            }
        }

        // 과제 통계
        List<Assignment> assignments = assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId);
        long totalAssignments = assignments.size();
        long submittedAssignments = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.SUBMITTED || a.getStatus() == AssignmentStatus.REVIEWED)
                .count();
        long reviewedAssignments = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.REVIEWED)
                .count();

        // 다음 세션
        DashboardResponse.NextSessionInfo nextSession = matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED)
                .filter(s -> !s.getSessionDate().isBefore(LocalDate.now()))
                .min(Comparator.comparing(MentoringSession::getSessionDate)
                        .thenComparing(MentoringSession::getStartTime))
                .map(s -> DashboardResponse.NextSessionInfo.builder()
                        .id(s.getId())
                        .date(s.getSessionDate().toString())
                        .startTime(s.getStartTime().toString())
                        .endTime(s.getEndTime().toString())
                        .meetLink(s.getMeetLink())
                        .category(s.getCategory())
                        .build())
                .orElse(null);

        // 최근 활동 — 각 도메인에서 최근 3건 → 합산 후 시간순 5건
        List<DashboardResponse.ActivityItem> activities = new ArrayList<>();

        assignments.stream().limit(3).forEach(a ->
                activities.add(DashboardResponse.ActivityItem.builder()
                        .type("ASSIGNMENT")
                        .title(a.getTitle())
                        .createdAt(a.getCreatedAt().toString())
                        .build()));

        noteRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId).stream().limit(3).forEach(n ->
                activities.add(DashboardResponse.ActivityItem.builder()
                        .type("NOTE")
                        .title(n.getTitle())
                        .createdAt(n.getCreatedAt().toString())
                        .build()));

        matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .limit(3)
                .forEach(s -> activities.add(DashboardResponse.ActivityItem.builder()
                        .type("SESSION")
                        .title(s.getCategory() + " 세션 완료")
                        .createdAt(s.getUpdatedAt().toString())
                        .build()));

        activities.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        List<DashboardResponse.ActivityItem> topActivities = activities.stream().limit(5).toList();

        // 멘토 정보
        User mentor = matching.getMentor();
        DashboardResponse.MentorInfo mentorInfo = DashboardResponse.MentorInfo.builder()
                .name(mentor.getName())
                .specialty(mentor.getMentorProfile() != null ? mentor.getMentorProfile().getSpecialty() : List.of())
                .email(mentor.getEmail())
                .build();

        // Jitsi Meet 링크 — 다음 세션의 meetLink
        String jitsiMeet = nextSession != null ? nextSession.getMeetLink() : null;

        return DashboardResponse.builder()
                .progressRate(progressRate)
                .attendanceRate(attendanceRate)
                .dDay(dDay)
                .assignmentStats(DashboardResponse.AssignmentStats.builder()
                        .total(totalAssignments)
                        .submitted(submittedAssignments)
                        .reviewed(reviewedAssignments)
                        .build())
                .nextSession(nextSession)
                .recentActivities(topActivities)
                .mentorInfo(mentorInfo)
                .communicationLinks(DashboardResponse.CommunicationLinks.builder()
                        .discord(discordUrl)
                        .jitsiMeet(jitsiMeet)
                        .build())
                .build();
    }

    public EnrollmentResponse getEnrollment(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);
        return EnrollmentResponse.from(matching);
    }
}
```

- [ ] **Step 4: Create LmsDashboardController**

```java
// backend/src/main/java/com/devmatch/controller/LmsDashboardController.java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.DashboardResponse;
import com.devmatch.dto.lms.EnrollmentResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LmsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "LMS - Dashboard", description = "LMS 대시보드 API")
@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
public class LmsDashboardController {

    private final LmsDashboardService dashboardService;

    @Operation(summary = "대시보드 조회", description = "진도율, 출석률, 과제 통계, 다음 세션 등 종합 정보")
    @GetMapping("/dashboard/{matchingId}")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        DashboardResponse response = dashboardService.getDashboard(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "수강 정보 조회", description = "매칭 기반 수강 정보")
    @GetMapping("/enrollment/{matchingId}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> getEnrollment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        EnrollmentResponse response = dashboardService.getEnrollment(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

- [ ] **Step 5: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (AssignmentRepository와 LearningNoteRepository가 아직 없으면 담당자B와 동기화 필요 — 아래 Note 참조)

> **Note:** 이 서비스는 `AssignmentRepository`와 `LearningNoteRepository`에 의존한다. 담당자B가 Task 7~8을 먼저 완료해야 컴파일된다. 순서 조정이 필요하면 DashboardService에서 해당 Repository 호출 부분을 임시로 빈 리스트로 처리하고, 담당자B 작업 완료 후 연동한다.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/EnrollmentResponse.java \
       backend/src/main/java/com/devmatch/service/LmsDashboardService.java \
       backend/src/main/java/com/devmatch/controller/LmsDashboardController.java
git commit -m "feat(lms): add Dashboard and Enrollment APIs with statistics aggregation"
```

---

## 담당자 B 트랙

### Task 7: Assignment + AssignmentSubmission Entity & Repository (Day 2)

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/Assignment.java`
- Create: `backend/src/main/java/com/devmatch/entity/AssignmentSubmission.java`
- Create: `backend/src/main/java/com/devmatch/repository/AssignmentRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/AssignmentSubmissionRepository.java`

- [ ] **Step 1: Create Assignment entity**

```java
// backend/src/main/java/com/devmatch/entity/Assignment.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assignments")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Assignment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Convert(converter = StringListConverter.class)
    @Column(name = "reference_urls", columnDefinition = "TEXT")
    private List<String> referenceUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @OneToOne(mappedBy = "assignment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AssignmentSubmission submission;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void submit() {
        this.status = AssignmentStatus.SUBMITTED;
    }

    public void reviewed() {
        this.status = AssignmentStatus.REVIEWED;
    }
}
```

- [ ] **Step 2: Create AssignmentSubmission entity**

```java
// backend/src/main/java/com/devmatch/entity/AssignmentSubmission.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_submissions")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class AssignmentSubmission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    private Assignment assignment;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(name = "submission_url", nullable = false, length = 500)
    private String submissionUrl;

    @Column(name = "submission_note", columnDefinition = "TEXT")
    private String submissionNote;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "feedback_content", columnDefinition = "TEXT")
    private String feedbackContent;

    @Column(length = 10)
    private String grade;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    public void addFeedback(String feedbackContent, String grade) {
        this.feedbackContent = feedbackContent;
        this.grade = grade;
        this.feedbackAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create AssignmentRepository**

```java
// backend/src/main/java/com/devmatch/repository/AssignmentRepository.java
package com.devmatch.repository;

import com.devmatch.entity.Assignment;
import com.devmatch.entity.AssignmentStatus;
import com.devmatch.entity.AssignmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByMatchingIdOrderByCreatedAtDesc(Long matchingId);
    List<Assignment> findByMatchingIdAndTypeOrderByCreatedAtDesc(Long matchingId, AssignmentType type);
    long countByMatchingId(Long matchingId);
    long countByMatchingIdAndStatusIn(Long matchingId, List<AssignmentStatus> statuses);
}
```

- [ ] **Step 4: Create AssignmentSubmissionRepository**

```java
// backend/src/main/java/com/devmatch/repository/AssignmentSubmissionRepository.java
package com.devmatch.repository;

import com.devmatch.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    Optional<AssignmentSubmission> findByAssignmentId(Long assignmentId);
    boolean existsByAssignmentId(Long assignmentId);
}
```

- [ ] **Step 5: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/Assignment.java \
       backend/src/main/java/com/devmatch/entity/AssignmentSubmission.java \
       backend/src/main/java/com/devmatch/repository/AssignmentRepository.java \
       backend/src/main/java/com/devmatch/repository/AssignmentSubmissionRepository.java
git commit -m "feat(lms): add Assignment and AssignmentSubmission entities with repositories"
```

---

### Task 8: Assignment DTO + Service + Controller (Day 2)

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/lms/AssignmentCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/AssignmentResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/SubmissionRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/FeedbackRequest.java`
- Create: `backend/src/main/java/com/devmatch/service/LmsAssignmentService.java`
- Create: `backend/src/main/java/com/devmatch/controller/LmsAssignmentController.java`

- [ ] **Step 1: Create AssignmentCreateRequest DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/AssignmentCreateRequest.java
package com.devmatch.dto.lms;

import com.devmatch.entity.AssignmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentCreateRequest {

    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;

    @NotNull(message = "과제 유형은 필수입니다")
    private AssignmentType type;

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    private String description;
    private LocalDate dueDate;
    private List<String> referenceUrls;
}
```

- [ ] **Step 2: Create AssignmentResponse DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/AssignmentResponse.java
package com.devmatch.dto.lms;

import com.devmatch.entity.Assignment;
import com.devmatch.entity.AssignmentStatus;
import com.devmatch.entity.AssignmentSubmission;
import com.devmatch.entity.AssignmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class AssignmentResponse {

    private Long id;
    private Long matchingId;
    private Long mentorId;
    private AssignmentType type;
    private String title;
    private String description;
    private LocalDate dueDate;
    private List<String> referenceUrls;
    private AssignmentStatus status;
    private SubmissionInfo submission;
    private LocalDateTime createdAt;

    @Getter @AllArgsConstructor @Builder
    public static class SubmissionInfo {
        private Long id;
        private String submissionUrl;
        private String submissionNote;
        private LocalDateTime submittedAt;
        private String feedbackContent;
        private String grade;
        private LocalDateTime feedbackAt;

        public static SubmissionInfo from(AssignmentSubmission sub) {
            if (sub == null) return null;
            return SubmissionInfo.builder()
                    .id(sub.getId())
                    .submissionUrl(sub.getSubmissionUrl())
                    .submissionNote(sub.getSubmissionNote())
                    .submittedAt(sub.getSubmittedAt())
                    .feedbackContent(sub.getFeedbackContent())
                    .grade(sub.getGrade())
                    .feedbackAt(sub.getFeedbackAt())
                    .build();
        }
    }

    public static AssignmentResponse from(Assignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .matchingId(assignment.getMatchingId())
                .mentorId(assignment.getMentorId())
                .type(assignment.getType())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .dueDate(assignment.getDueDate())
                .referenceUrls(assignment.getReferenceUrls())
                .status(assignment.getStatus())
                .submission(SubmissionInfo.from(assignment.getSubmission()))
                .createdAt(assignment.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 3: Create SubmissionRequest and FeedbackRequest DTOs**

```java
// backend/src/main/java/com/devmatch/dto/lms/SubmissionRequest.java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequest {

    @NotBlank(message = "제출 URL은 필수입니다")
    private String submissionUrl;

    private String submissionNote;
}
```

```java
// backend/src/main/java/com/devmatch/dto/lms/FeedbackRequest.java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    @NotBlank(message = "피드백 내용은 필수입니다")
    private String feedbackContent;

    private String grade;
}
```

- [ ] **Step 4: Create LmsAssignmentService**

```java
// backend/src/main/java/com/devmatch/service/LmsAssignmentService.java
package com.devmatch.service;

import com.devmatch.dto.lms.AssignmentCreateRequest;
import com.devmatch.dto.lms.AssignmentResponse;
import com.devmatch.dto.lms.FeedbackRequest;
import com.devmatch.dto.lms.SubmissionRequest;
import com.devmatch.entity.*;
import com.devmatch.exception.AssignmentNotFoundException;
import com.devmatch.repository.AssignmentRepository;
import com.devmatch.repository.AssignmentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LmsAccessService lmsAccessService;

    @Transactional
    public AssignmentResponse create(Long userId, AssignmentCreateRequest request) {
        Matching matching = lmsAccessService.validateAccess(userId, request.getMatchingId());

        Assignment assignment = Assignment.builder()
                .matchingId(request.getMatchingId())
                .mentorId(matching.getMentor().getId())
                .type(request.getType())
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .referenceUrls(request.getReferenceUrls() != null ? request.getReferenceUrls() : Collections.emptyList())
                .build();

        return AssignmentResponse.from(assignmentRepository.save(assignment));
    }

    public List<AssignmentResponse> getList(Long userId, Long matchingId, String type) {
        lmsAccessService.validateAccess(userId, matchingId);

        List<Assignment> assignments;
        if (type != null && !type.isBlank()) {
            assignments = assignmentRepository.findByMatchingIdAndTypeOrderByCreatedAtDesc(
                    matchingId, AssignmentType.valueOf(type));
        } else {
            assignments = assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId);
        }

        return assignments.stream().map(AssignmentResponse::from).collect(Collectors.toList());
    }

    public AssignmentResponse getDetail(Long userId, Long assignmentId) {
        Assignment assignment = findAssignment(assignmentId);
        lmsAccessService.validateAccess(userId, assignment.getMatchingId());
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse submit(Long userId, Long assignmentId, SubmissionRequest request) {
        Assignment assignment = findAssignment(assignmentId);
        Matching matching = lmsAccessService.validateMenteeAccess(userId, assignment.getMatchingId());

        AssignmentSubmission submission = AssignmentSubmission.builder()
                .assignment(assignment)
                .menteeId(matching.getMentee().getId())
                .submissionUrl(request.getSubmissionUrl())
                .submissionNote(request.getSubmissionNote())
                .submittedAt(LocalDateTime.now())
                .build();

        submissionRepository.save(submission);
        assignment.submit();

        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse feedback(Long userId, Long assignmentId, FeedbackRequest request) {
        Assignment assignment = findAssignment(assignmentId);
        lmsAccessService.validateMentorAccess(userId, assignment.getMatchingId());

        AssignmentSubmission submission = submissionRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException("제출물을 찾을 수 없습니다. assignmentId: " + assignmentId));

        submission.addFeedback(request.getFeedbackContent(), request.getGrade());
        assignment.reviewed();

        return AssignmentResponse.from(assignment);
    }

    private Assignment findAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new AssignmentNotFoundException("과제를 찾을 수 없습니다: " + id));
    }
}
```

- [ ] **Step 5: Create LmsAssignmentController**

```java
// backend/src/main/java/com/devmatch/controller/LmsAssignmentController.java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LmsAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "LMS - Assignment", description = "과제/코드리뷰 API")
@RestController
@RequestMapping("/api/lms/assignments")
@RequiredArgsConstructor
public class LmsAssignmentController {

    private final LmsAssignmentService assignmentService;

    @Operation(summary = "과제 생성", description = "멘토가 과제를 출제하거나 멘티가 코드리뷰를 요청합니다")
    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody AssignmentCreateRequest request
    ) {
        AssignmentResponse response = assignmentService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("과제가 생성되었습니다", response));
    }

    @Operation(summary = "과제 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId,
            @RequestParam(required = false) String type
    ) {
        List<AssignmentResponse> response = assignmentService.getList(user.getUserId(), matchingId, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "과제 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        AssignmentResponse response = assignmentService.getDetail(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "과제 제출", description = "멘티가 과제를 제출합니다 (GitHub PR/Repo 링크)")
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<AssignmentResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody SubmissionRequest request
    ) {
        AssignmentResponse response = assignmentService.submit(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("과제가 제출되었습니다", response));
    }

    @Operation(summary = "과제 피드백", description = "멘토가 제출된 과제에 피드백을 작성합니다")
    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<AssignmentResponse>> feedback(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request
    ) {
        AssignmentResponse response = assignmentService.feedback(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("피드백이 등록되었습니다", response));
    }
}
```

- [ ] **Step 6: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devmatch/dto/lms/AssignmentCreateRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/AssignmentResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/SubmissionRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/FeedbackRequest.java \
       backend/src/main/java/com/devmatch/service/LmsAssignmentService.java \
       backend/src/main/java/com/devmatch/controller/LmsAssignmentController.java
git commit -m "feat(lms): add Assignment API — create, list, detail, submit, feedback"
```

---

### Task 9: LearningNote + NoteComment Entity, Repository, DTO, Service, Controller (Day 3)

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/LearningNote.java`
- Create: `backend/src/main/java/com/devmatch/entity/NoteComment.java`
- Create: `backend/src/main/java/com/devmatch/repository/LearningNoteRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/NoteCommentRepository.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/NoteCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/NoteResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/NoteCommentRequest.java`
- Create: `backend/src/main/java/com/devmatch/service/LearningNoteService.java`
- Create: `backend/src/main/java/com/devmatch/controller/LearningNoteController.java`

- [ ] **Step 1: Create LearningNote entity**

```java
// backend/src/main/java/com/devmatch/entity/LearningNote.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "learning_notes")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class LearningNote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteType type;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "self_rating")
    private Integer selfRating;

    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<NoteComment> comments = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(String title, String content, Integer selfRating) {
        this.title = title;
        this.content = content;
        this.selfRating = selfRating;
    }
}
```

- [ ] **Step 2: Create NoteComment entity**

```java
// backend/src/main/java/com/devmatch/entity/NoteComment.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "note_comments")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class NoteComment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private LearningNote note;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create LearningNoteRepository and NoteCommentRepository**

```java
// backend/src/main/java/com/devmatch/repository/LearningNoteRepository.java
package com.devmatch.repository;

import com.devmatch.entity.LearningNote;
import com.devmatch.entity.NoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningNoteRepository extends JpaRepository<LearningNote, Long> {
    List<LearningNote> findByMatchingIdOrderByCreatedAtDesc(Long matchingId);
    List<LearningNote> findByMatchingIdAndTypeOrderByCreatedAtDesc(Long matchingId, NoteType type);
}
```

```java
// backend/src/main/java/com/devmatch/repository/NoteCommentRepository.java
package com.devmatch.repository;

import com.devmatch.entity.NoteComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteCommentRepository extends JpaRepository<NoteComment, Long> {
}
```

- [ ] **Step 4: Create NoteCreateRequest, NoteResponse, NoteCommentRequest DTOs**

```java
// backend/src/main/java/com/devmatch/dto/lms/NoteCreateRequest.java
package com.devmatch.dto.lms;

import com.devmatch.entity.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NoteCreateRequest {

    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;

    @NotNull(message = "노트 유형은 필수입니다")
    private NoteType type;

    private Long sessionId;
    private Integer weekNumber;

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    private Integer selfRating;
}
```

```java
// backend/src/main/java/com/devmatch/dto/lms/NoteResponse.java
package com.devmatch.dto.lms;

import com.devmatch.entity.LearningNote;
import com.devmatch.entity.NoteComment;
import com.devmatch.entity.NoteType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@Builder
public class NoteResponse {

    private Long id;
    private Long matchingId;
    private Long authorId;
    private String authorName;
    private NoteType type;
    private Long sessionId;
    private Integer weekNumber;
    private String title;
    private String content;
    private Integer selfRating;
    private List<CommentInfo> comments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter @AllArgsConstructor @Builder
    public static class CommentInfo {
        private Long id;
        private Long authorId;
        private String authorName;
        private String content;
        private LocalDateTime createdAt;

        public static CommentInfo from(NoteComment comment, String authorName) {
            return CommentInfo.builder()
                    .id(comment.getId())
                    .authorId(comment.getAuthorId())
                    .authorName(authorName)
                    .content(comment.getContent())
                    .createdAt(comment.getCreatedAt())
                    .build();
        }
    }

    public static NoteResponse from(LearningNote note, String authorName,
                                     List<CommentInfo> commentInfos) {
        return NoteResponse.builder()
                .id(note.getId())
                .matchingId(note.getMatchingId())
                .authorId(note.getAuthorId())
                .authorName(authorName)
                .type(note.getType())
                .sessionId(note.getSessionId())
                .weekNumber(note.getWeekNumber())
                .title(note.getTitle())
                .content(note.getContent())
                .selfRating(note.getSelfRating())
                .comments(commentInfos)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
```

```java
// backend/src/main/java/com/devmatch/dto/lms/NoteCommentRequest.java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NoteCommentRequest {

    @NotBlank(message = "코멘트 내용은 필수입니다")
    private String content;
}
```

- [ ] **Step 5: Create LearningNoteService**

```java
// backend/src/main/java/com/devmatch/service/LearningNoteService.java
package com.devmatch.service;

import com.devmatch.dto.lms.NoteCommentRequest;
import com.devmatch.dto.lms.NoteCreateRequest;
import com.devmatch.dto.lms.NoteResponse;
import com.devmatch.entity.LearningNote;
import com.devmatch.entity.NoteComment;
import com.devmatch.entity.NoteType;
import com.devmatch.entity.User;
import com.devmatch.exception.NoteNotFoundException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.LearningNoteRepository;
import com.devmatch.repository.NoteCommentRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningNoteService {

    private final LearningNoteRepository noteRepository;
    private final NoteCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final LmsAccessService lmsAccessService;

    @Transactional
    public NoteResponse create(Long userId, NoteCreateRequest request) {
        lmsAccessService.validateAccess(userId, request.getMatchingId());
        User author = findUser(userId);

        LearningNote note = LearningNote.builder()
                .matchingId(request.getMatchingId())
                .authorId(userId)
                .type(request.getType())
                .sessionId(request.getSessionId())
                .weekNumber(request.getWeekNumber())
                .title(request.getTitle())
                .content(request.getContent())
                .selfRating(request.getSelfRating())
                .build();

        LearningNote saved = noteRepository.save(note);
        return NoteResponse.from(saved, author.getName(), Collections.emptyList());
    }

    public List<NoteResponse> getList(Long userId, Long matchingId, String type) {
        lmsAccessService.validateAccess(userId, matchingId);

        List<LearningNote> notes;
        if (type != null && !type.isBlank()) {
            notes = noteRepository.findByMatchingIdAndTypeOrderByCreatedAtDesc(
                    matchingId, NoteType.valueOf(type));
        } else {
            notes = noteRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId);
        }

        return notes.stream().map(note -> {
            String authorName = userRepository.findById(note.getAuthorId())
                    .map(User::getName).orElse("알 수 없음");
            List<NoteResponse.CommentInfo> commentInfos = note.getComments().stream()
                    .map(c -> {
                        String cName = userRepository.findById(c.getAuthorId())
                                .map(User::getName).orElse("알 수 없음");
                        return NoteResponse.CommentInfo.from(c, cName);
                    }).collect(Collectors.toList());
            return NoteResponse.from(note, authorName, commentInfos);
        }).collect(Collectors.toList());
    }

    public NoteResponse getDetail(Long userId, Long noteId) {
        LearningNote note = findNote(noteId);
        lmsAccessService.validateAccess(userId, note.getMatchingId());

        String authorName = userRepository.findById(note.getAuthorId())
                .map(User::getName).orElse("알 수 없음");
        List<NoteResponse.CommentInfo> commentInfos = note.getComments().stream()
                .map(c -> {
                    String cName = userRepository.findById(c.getAuthorId())
                            .map(User::getName).orElse("알 수 없음");
                    return NoteResponse.CommentInfo.from(c, cName);
                }).collect(Collectors.toList());

        return NoteResponse.from(note, authorName, commentInfos);
    }

    @Transactional
    public NoteResponse update(Long userId, Long noteId, NoteCreateRequest request) {
        LearningNote note = findNote(noteId);
        lmsAccessService.validateAccess(userId, note.getMatchingId());

        note.update(request.getTitle(), request.getContent(), request.getSelfRating());

        String authorName = userRepository.findById(note.getAuthorId())
                .map(User::getName).orElse("알 수 없음");
        return NoteResponse.from(note, authorName, Collections.emptyList());
    }

    @Transactional
    public NoteResponse addComment(Long userId, Long noteId, NoteCommentRequest request) {
        LearningNote note = findNote(noteId);
        lmsAccessService.validateAccess(userId, note.getMatchingId());

        NoteComment comment = NoteComment.builder()
                .note(note)
                .authorId(userId)
                .content(request.getContent())
                .build();
        commentRepository.save(comment);

        return getDetail(userId, noteId);
    }

    private LearningNote findNote(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException("학습 노트를 찾을 수 없습니다: " + id));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));
    }
}
```

- [ ] **Step 6: Create LearningNoteController**

```java
// backend/src/main/java/com/devmatch/controller/LearningNoteController.java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.NoteCommentRequest;
import com.devmatch.dto.lms.NoteCreateRequest;
import com.devmatch.dto.lms.NoteResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LearningNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "LMS - Notes", description = "학습 노트 API")
@RestController
@RequestMapping("/api/lms/notes")
@RequiredArgsConstructor
public class LearningNoteController {

    private final LearningNoteService noteService;

    @Operation(summary = "노트 작성")
    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody NoteCreateRequest request
    ) {
        NoteResponse response = noteService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("학습 노트가 작성되었습니다", response));
    }

    @Operation(summary = "노트 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId,
            @RequestParam(required = false) String type
    ) {
        List<NoteResponse> response = noteService.getList(user.getUserId(), matchingId, type);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "노트 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        NoteResponse response = noteService.getDetail(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "노트 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody NoteCreateRequest request
    ) {
        NoteResponse response = noteService.update(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("학습 노트가 수정되었습니다", response));
    }

    @Operation(summary = "코멘트 추가")
    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<NoteResponse>> addComment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody NoteCommentRequest request
    ) {
        NoteResponse response = noteService.addComment(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("코멘트가 등록되었습니다", response));
    }
}
```

- [ ] **Step 7: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/LearningNote.java \
       backend/src/main/java/com/devmatch/entity/NoteComment.java \
       backend/src/main/java/com/devmatch/repository/LearningNoteRepository.java \
       backend/src/main/java/com/devmatch/repository/NoteCommentRepository.java \
       backend/src/main/java/com/devmatch/dto/lms/NoteCreateRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/NoteResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/NoteCommentRequest.java \
       backend/src/main/java/com/devmatch/service/LearningNoteService.java \
       backend/src/main/java/com/devmatch/controller/LearningNoteController.java
git commit -m "feat(lms): add LearningNote and NoteComment — full CRUD with comments"
```

---

### Task 10: Career (Resume + MockInterview) + Certificate (Day 4)

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/Resume.java`
- Create: `backend/src/main/java/com/devmatch/entity/MockInterview.java`
- Create: `backend/src/main/java/com/devmatch/repository/ResumeRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/MockInterviewRepository.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/ResumeResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/ResumeFeedbackRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/MockInterviewCreateRequest.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/MockInterviewResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/lms/CertificateEligibilityResponse.java`
- Create: `backend/src/main/java/com/devmatch/service/CareerService.java`
- Create: `backend/src/main/java/com/devmatch/service/CertificateService.java`
- Create: `backend/src/main/java/com/devmatch/controller/CareerController.java`
- Create: `backend/src/main/java/com/devmatch/controller/CertificateController.java`
- Modify: `backend/build.gradle` — OpenPDF 의존성 추가

- [ ] **Step 1: Add OpenPDF dependency to build.gradle**

`build.gradle`의 dependencies 블록에 추가:

```gradle
    // PDF Generation (Certificate)
    implementation 'com.github.librepdf:openpdf:2.0.3'
```

- [ ] **Step 2: Create Resume entity**

```java
// backend/src/main/java/com/devmatch/entity/Resume.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Resume {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public void addFeedback(String feedback) {
        this.mentorFeedback = feedback;
        this.feedbackAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create MockInterview entity**

```java
// backend/src/main/java/com/devmatch/entity/MockInterview.java
package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mock_interviews")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class MockInterview {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "questions_and_answers", columnDefinition = "TEXT")
    private String questionsAndAnswers;

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @Column
    private Integer rating;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create ResumeRepository and MockInterviewRepository**

```java
// backend/src/main/java/com/devmatch/repository/ResumeRepository.java
package com.devmatch.repository;

import com.devmatch.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByMatchingIdOrderByVersionDesc(Long matchingId);
    long countByMatchingId(Long matchingId);
}
```

```java
// backend/src/main/java/com/devmatch/repository/MockInterviewRepository.java
package com.devmatch.repository;

import com.devmatch.entity.MockInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockInterviewRepository extends JpaRepository<MockInterview, Long> {
    List<MockInterview> findByMatchingIdOrderByInterviewDateDesc(Long matchingId);
}
```

- [ ] **Step 5: Create Resume DTOs**

```java
// backend/src/main/java/com/devmatch/dto/lms/ResumeResponse.java
package com.devmatch.dto.lms;

import com.devmatch.entity.Resume;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @AllArgsConstructor @Builder
public class ResumeResponse {
    private Long id;
    private Long menteeId;
    private Long matchingId;
    private Integer version;
    private String fileUrl;
    private String fileName;
    private String mentorFeedback;
    private LocalDateTime feedbackAt;
    private LocalDateTime uploadedAt;

    public static ResumeResponse from(Resume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .menteeId(resume.getMenteeId())
                .matchingId(resume.getMatchingId())
                .version(resume.getVersion())
                .fileUrl(resume.getFileUrl())
                .fileName(resume.getFileName())
                .mentorFeedback(resume.getMentorFeedback())
                .feedbackAt(resume.getFeedbackAt())
                .uploadedAt(resume.getUploadedAt())
                .build();
    }
}
```

```java
// backend/src/main/java/com/devmatch/dto/lms/ResumeFeedbackRequest.java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ResumeFeedbackRequest {
    @NotBlank(message = "피드백 내용은 필수입니다")
    private String mentorFeedback;
}
```

- [ ] **Step 6: Create MockInterview DTOs**

```java
// backend/src/main/java/com/devmatch/dto/lms/MockInterviewCreateRequest.java
package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter @NoArgsConstructor @AllArgsConstructor
public class MockInterviewCreateRequest {
    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;
    @NotNull(message = "면접 날짜는 필수입니다")
    private LocalDate interviewDate;
    @NotBlank(message = "주제는 필수입니다")
    private String topic;
    private String questionsAndAnswers;
    private String mentorFeedback;
    private Integer rating;
}
```

```java
// backend/src/main/java/com/devmatch/dto/lms/MockInterviewResponse.java
package com.devmatch.dto.lms;

import com.devmatch.entity.MockInterview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @AllArgsConstructor @Builder
public class MockInterviewResponse {
    private Long id;
    private Long matchingId;
    private LocalDate interviewDate;
    private String topic;
    private String questionsAndAnswers;
    private String mentorFeedback;
    private Integer rating;
    private LocalDateTime createdAt;

    public static MockInterviewResponse from(MockInterview mi) {
        return MockInterviewResponse.builder()
                .id(mi.getId())
                .matchingId(mi.getMatchingId())
                .interviewDate(mi.getInterviewDate())
                .topic(mi.getTopic())
                .questionsAndAnswers(mi.getQuestionsAndAnswers())
                .mentorFeedback(mi.getMentorFeedback())
                .rating(mi.getRating())
                .createdAt(mi.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 7: Create CertificateEligibilityResponse DTO**

```java
// backend/src/main/java/com/devmatch/dto/lms/CertificateEligibilityResponse.java
package com.devmatch.dto.lms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter @AllArgsConstructor @Builder
public class CertificateEligibilityResponse {
    private boolean eligible;
    private int progressRate;
    private int attendanceRate;
    private int assignmentSubmitRate;
    private int requiredProgress;
    private int requiredAttendance;
    private int requiredAssignmentRate;
}
```

- [ ] **Step 8: Create CareerService**

```java
// backend/src/main/java/com/devmatch/service/CareerService.java
package com.devmatch.service;

import com.devmatch.dto.lms.*;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MockInterview;
import com.devmatch.entity.Resume;
import com.devmatch.exception.ResumeNotFoundException;
import com.devmatch.repository.MockInterviewRepository;
import com.devmatch.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareerService {

    private final ResumeRepository resumeRepository;
    private final MockInterviewRepository mockInterviewRepository;
    private final LmsAccessService lmsAccessService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public ResumeResponse uploadResume(Long userId, Long matchingId, MultipartFile file) {
        Matching matching = lmsAccessService.validateMenteeAccess(userId, matchingId);

        long currentVersion = resumeRepository.countByMatchingId(matchingId);
        int newVersion = (int) currentVersion + 1;

        String fileName = file.getOriginalFilename();
        String storedName = UUID.randomUUID() + "_" + fileName;
        Path uploadPath = Paths.get(uploadDir, "resumes");

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), uploadPath.resolve(storedName));
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 실패: " + e.getMessage());
        }

        Resume resume = Resume.builder()
                .menteeId(matching.getMentee().getId())
                .matchingId(matchingId)
                .version(newVersion)
                .fileUrl("/uploads/resumes/" + storedName)
                .fileName(fileName)
                .uploadedAt(LocalDateTime.now())
                .build();

        return ResumeResponse.from(resumeRepository.save(resume));
    }

    public List<ResumeResponse> getResumes(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return resumeRepository.findByMatchingIdOrderByVersionDesc(matchingId)
                .stream().map(ResumeResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public ResumeResponse feedbackResume(Long userId, Long resumeId, ResumeFeedbackRequest request) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResumeNotFoundException("이력서를 찾을 수 없습니다: " + resumeId));
        lmsAccessService.validateMentorAccess(userId, resume.getMatchingId());

        resume.addFeedback(request.getMentorFeedback());
        return ResumeResponse.from(resume);
    }

    @Transactional
    public MockInterviewResponse createMockInterview(Long userId, MockInterviewCreateRequest request) {
        lmsAccessService.validateAccess(userId, request.getMatchingId());

        MockInterview mockInterview = MockInterview.builder()
                .matchingId(request.getMatchingId())
                .interviewDate(request.getInterviewDate())
                .topic(request.getTopic())
                .questionsAndAnswers(request.getQuestionsAndAnswers())
                .mentorFeedback(request.getMentorFeedback())
                .rating(request.getRating())
                .build();

        return MockInterviewResponse.from(mockInterviewRepository.save(mockInterview));
    }

    public List<MockInterviewResponse> getMockInterviews(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return mockInterviewRepository.findByMatchingIdOrderByInterviewDateDesc(matchingId)
                .stream().map(MockInterviewResponse::from).collect(Collectors.toList());
    }
}
```

- [ ] **Step 9: Create CertificateService**

```java
// backend/src/main/java/com/devmatch/service/CertificateService.java
package com.devmatch.service;

import com.devmatch.dto.lms.CertificateEligibilityResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private static final int REQUIRED_PROGRESS = 80;
    private static final int REQUIRED_ATTENDANCE = 80;
    private static final int REQUIRED_ASSIGNMENT_RATE = 70;

    private final LmsAccessService lmsAccessService;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final AssignmentRepository assignmentRepository;
    private final MentoringSessionRepository sessionRepository;

    public CertificateEligibilityResponse checkEligibility(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);

        int progressRate = calculateProgressRate(matchingId);
        int attendanceRate = calculateAttendanceRate(matching);
        int assignmentSubmitRate = calculateAssignmentSubmitRate(matchingId);

        boolean eligible = progressRate >= REQUIRED_PROGRESS
                && attendanceRate >= REQUIRED_ATTENDANCE
                && assignmentSubmitRate >= REQUIRED_ASSIGNMENT_RATE;

        return CertificateEligibilityResponse.builder()
                .eligible(eligible)
                .progressRate(progressRate)
                .attendanceRate(attendanceRate)
                .assignmentSubmitRate(assignmentSubmitRate)
                .requiredProgress(REQUIRED_PROGRESS)
                .requiredAttendance(REQUIRED_ATTENDANCE)
                .requiredAssignmentRate(REQUIRED_ASSIGNMENT_RATE)
                .build();
    }

    public byte[] generatePdf(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);

        CertificateEligibilityResponse eligibility = checkEligibility(userId, matchingId);
        if (!eligibility.isEligible()) {
            throw new RuntimeException("수료 자격 미달: 진도율 " + eligibility.getProgressRate()
                    + "%, 출석률 " + eligibility.getAttendanceRate()
                    + "%, 과제제출률 " + eligibility.getAssignmentSubmitRate() + "%");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 36, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, 18, Font.NORMAL);
            Font smallFont = new Font(Font.HELVETICA, 12, Font.NORMAL);

            document.add(new Paragraph("\n\n"));
            Paragraph title = new Paragraph("Certificate of Completion", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph("\n\n"));
            Paragraph name = new Paragraph(matching.getMentee().getName(), bodyFont);
            name.setAlignment(Element.ALIGN_CENTER);
            document.add(name);

            document.add(new Paragraph("\n"));
            Paragraph desc = new Paragraph(
                    "has successfully completed the mentoring program in "
                            + matching.getCategory() + " with mentor " + matching.getMentor().getName(),
                    bodyFont);
            desc.setAlignment(Element.ALIGN_CENTER);
            document.add(desc);

            document.add(new Paragraph("\n\n"));
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Paragraph date = new Paragraph("Date: " + dateStr, smallFont);
            date.setAlignment(Element.ALIGN_CENTER);
            document.add(date);

            Paragraph stats = new Paragraph(
                    String.format("Progress: %d%% | Attendance: %d%% | Assignments: %d%%",
                            eligibility.getProgressRate(),
                            eligibility.getAttendanceRate(),
                            eligibility.getAssignmentSubmitRate()),
                    smallFont);
            stats.setAlignment(Element.ALIGN_CENTER);
            document.add(stats);

            document.add(new Paragraph("\n"));
            Paragraph brand = new Paragraph("DevMatch - Skill-Based Mentor Matching Platform", smallFont);
            brand.setAlignment(Element.ALIGN_CENTER);
            document.add(brand);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 생성 실패: " + e.getMessage());
        }
    }

    private int calculateProgressRate(Long matchingId) {
        return curriculumRepository.findByMatchingId(matchingId)
                .map(curriculum -> {
                    if (curriculum.getTotalWeeks() == 0) return 0;
                    long completed = weekRepository.countByCurriculumIdAndIsCompletedTrue(curriculum.getId());
                    return (int) ((completed * 100) / curriculum.getTotalWeeks());
                })
                .orElse(0);
    }

    private int calculateAttendanceRate(Matching matching) {
        List<MentoringSession> sessions = sessionRepository
                .findByMenteeIdOrMentorIdOrderBySessionDateDesc(
                        matching.getMentee().getId(), matching.getMentor().getId());
        List<MentoringSession> matchingSessions = sessions.stream()
                .filter(s -> s.getMatchingId().equals(matching.getId()))
                .toList();
        long total = matchingSessions.stream()
                .filter(s -> s.getStatus() != SessionStatus.CANCELLED)
                .count();
        long completed = matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .count();
        return total > 0 ? (int) ((completed * 100) / total) : 0;
    }

    private int calculateAssignmentSubmitRate(Long matchingId) {
        long total = assignmentRepository.countByMatchingId(matchingId);
        if (total == 0) return 0;
        long submitted = assignmentRepository.countByMatchingIdAndStatusIn(
                matchingId, List.of(AssignmentStatus.SUBMITTED, AssignmentStatus.REVIEWED));
        return (int) ((submitted * 100) / total);
    }
}
```

- [ ] **Step 10: Create CareerController**

```java
// backend/src/main/java/com/devmatch/controller/CareerController.java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "LMS - Career", description = "취업 지원 API (이력서/모의면접)")
@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;

    @Operation(summary = "이력서 업로드")
    @PostMapping("/resumes")
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId,
            @RequestParam("file") MultipartFile file
    ) {
        ResumeResponse response = careerService.uploadResume(user.getUserId(), matchingId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("이력서가 업로드되었습니다", response));
    }

    @Operation(summary = "이력서 목록 조회")
    @GetMapping("/resumes")
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> getResumes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId
    ) {
        List<ResumeResponse> response = careerService.getResumes(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "이력서 피드백")
    @PostMapping("/resumes/{id}/feedback")
    public ResponseEntity<ApiResponse<ResumeResponse>> feedbackResume(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ResumeFeedbackRequest request
    ) {
        ResumeResponse response = careerService.feedbackResume(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("피드백이 등록되었습니다", response));
    }

    @Operation(summary = "모의 면접 기록")
    @PostMapping("/mock-interviews")
    public ResponseEntity<ApiResponse<MockInterviewResponse>> createMockInterview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MockInterviewCreateRequest request
    ) {
        MockInterviewResponse response = careerService.createMockInterview(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("모의 면접이 기록되었습니다", response));
    }

    @Operation(summary = "모의 면접 목록 조회")
    @GetMapping("/mock-interviews")
    public ResponseEntity<ApiResponse<List<MockInterviewResponse>>> getMockInterviews(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId
    ) {
        List<MockInterviewResponse> response = careerService.getMockInterviews(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

- [ ] **Step 11: Create CertificateController**

```java
// backend/src/main/java/com/devmatch/controller/CertificateController.java
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.CertificateEligibilityResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "LMS - Certificate", description = "수료증 API")
@RestController
@RequestMapping("/api/lms/certificate")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "수료 자격 확인")
    @GetMapping("/eligibility/{matchingId}")
    public ResponseEntity<ApiResponse<CertificateEligibilityResponse>> checkEligibility(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        CertificateEligibilityResponse response = certificateService.checkEligibility(
                user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "수료증 PDF 다운로드")
    @GetMapping("/{matchingId}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        byte[] pdfBytes = certificateService.generatePdf(user.getUserId(), matchingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificate.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
```

- [ ] **Step 12: Build to verify**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 13: Commit**

```bash
git add backend/build.gradle \
       backend/src/main/java/com/devmatch/entity/Resume.java \
       backend/src/main/java/com/devmatch/entity/MockInterview.java \
       backend/src/main/java/com/devmatch/repository/ResumeRepository.java \
       backend/src/main/java/com/devmatch/repository/MockInterviewRepository.java \
       backend/src/main/java/com/devmatch/dto/lms/ResumeResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/ResumeFeedbackRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/MockInterviewCreateRequest.java \
       backend/src/main/java/com/devmatch/dto/lms/MockInterviewResponse.java \
       backend/src/main/java/com/devmatch/dto/lms/CertificateEligibilityResponse.java \
       backend/src/main/java/com/devmatch/service/CareerService.java \
       backend/src/main/java/com/devmatch/service/CertificateService.java \
       backend/src/main/java/com/devmatch/controller/CareerController.java \
       backend/src/main/java/com/devmatch/controller/CertificateController.java
git commit -m "feat(lms): add Career (Resume/MockInterview) and Certificate APIs with PDF generation"
```

---

## 프론트엔드 페이지 (Day 5~6)

### Task 11: 대시보드 페이지 + 공통 컴포넌트 (담당자 A — Day 5)

**Files:**
- Create: `frontend/src/components/lms/StatCard.tsx`
- Create: `frontend/src/components/lms/ProgressCircle.tsx`
- Create: `frontend/src/components/lms/ActivityFeed.tsx`
- Create: `frontend/src/app/lms/dashboard/page.tsx`

- [ ] **Step 1: Create StatCard component**

```tsx
// frontend/src/components/lms/StatCard.tsx
import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: string | number;
  icon: LucideIcon;
  color?: string;
}

export default function StatCard({ label, value, icon: Icon, color = 'blue' }: StatCardProps) {
  const colorMap: Record<string, string> = {
    blue: 'from-blue-500 to-cyan-400',
    green: 'from-green-500 to-emerald-400',
    purple: 'from-purple-500 to-violet-400',
    orange: 'from-orange-500 to-amber-400',
  };

  return (
    <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
      <div className="flex items-center justify-between mb-4">
        <span className="text-gray-400 text-sm">{label}</span>
        <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${colorMap[color] || colorMap.blue} flex items-center justify-center`}>
          <Icon size={20} className="text-white" />
        </div>
      </div>
      <p className="text-white text-3xl font-bold">{value}</p>
    </div>
  );
}
```

- [ ] **Step 2: Create ProgressCircle component**

```tsx
// frontend/src/components/lms/ProgressCircle.tsx
interface ProgressCircleProps {
  value: number;
  label: string;
  size?: number;
}

export default function ProgressCircle({ value, label, size = 120 }: ProgressCircleProps) {
  const radius = (size - 12) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (value / 100) * circumference;

  return (
    <div className="flex flex-col items-center gap-2">
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none"
          stroke="rgba(255,255,255,0.05)" strokeWidth="10" />
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none"
          stroke="url(#gradient)" strokeWidth="10" strokeLinecap="round"
          strokeDasharray={circumference} strokeDashoffset={offset}
          className="transition-all duration-700" />
        <defs>
          <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#3b82f6" />
            <stop offset="100%" stopColor="#22d3ee" />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="text-white text-2xl font-bold">{value}%</span>
      </div>
      <span className="text-gray-400 text-sm">{label}</span>
    </div>
  );
}
```

- [ ] **Step 3: Create ActivityFeed component**

```tsx
// frontend/src/components/lms/ActivityFeed.tsx
interface Activity {
  type: string;
  title: string;
  createdAt: string;
}

interface ActivityFeedProps {
  activities: Activity[];
}

const typeLabels: Record<string, string> = {
  ASSIGNMENT: '과제',
  NOTE: '노트',
  SESSION: '세션',
  ASSIGNMENT_FEEDBACK: '피드백',
};

export default function ActivityFeed({ activities }: ActivityFeedProps) {
  if (activities.length === 0) {
    return <p className="text-gray-500 text-sm">최근 활동이 없습니다</p>;
  }

  return (
    <div className="space-y-3">
      {activities.map((activity, i) => (
        <div key={i} className="flex items-center gap-3 py-2">
          <div className="w-2 h-2 rounded-full bg-blue-400 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm truncate">{activity.title}</p>
            <p className="text-gray-500 text-xs">
              {typeLabels[activity.type] || activity.type} · {new Date(activity.createdAt).toLocaleDateString('ko-KR')}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: Create Dashboard page**

```tsx
// frontend/src/app/lms/dashboard/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { TrendingUp, Calendar, ClipboardList, Clock, MessageCircle, Video } from 'lucide-react';
import StatCard from '@/components/lms/StatCard';
import ActivityFeed from '@/components/lms/ActivityFeed';
import { getDashboard } from '@/lib/lms';
import type { DashboardResponse } from '@/lib/lms-types';

export default function DashboardPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    getDashboard(matchingId)
      .then((res) => setData(res.data.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId]);

  if (!matchingId) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-gray-400">매칭 ID가 필요합니다. 매칭 내역에서 LMS에 접근해주세요.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">대시보드</h1>
        <p className="text-gray-400 mt-1">멘토링 진행 현황을 한눈에 확인하세요</p>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="진도율" value={`${data.progressRate}%`} icon={TrendingUp} color="blue" />
        <StatCard label="출석률" value={`${data.attendanceRate}%`} icon={Calendar} color="green" />
        <StatCard label="과제" value={`${data.assignmentStats.submitted}/${data.assignmentStats.total}`} icon={ClipboardList} color="purple" />
        <StatCard label="D-Day" value={`D-${data.dDay}`} icon={Clock} color="orange" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 다음 세션 */}
        <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4">다음 멘토링</h3>
          {data.nextSession ? (
            <div className="space-y-3">
              <p className="text-gray-300">{data.nextSession.category}</p>
              <p className="text-gray-400 text-sm">
                {data.nextSession.date} {data.nextSession.startTime} ~ {data.nextSession.endTime}
              </p>
              <a
                href={data.nextSession.meetLink}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors text-sm"
              >
                <Video size={16} />
                화상 회의 참여
              </a>
            </div>
          ) : (
            <p className="text-gray-500 text-sm">예정된 세션이 없습니다</p>
          )}
        </div>

        {/* 최근 활동 */}
        <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4">최근 활동</h3>
          <ActivityFeed activities={data.recentActivities} />
        </div>

        {/* 소통 링크 */}
        <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4">소통</h3>
          <div className="space-y-3">
            <div>
              <p className="text-gray-400 text-xs mb-1">멘토</p>
              <p className="text-white">{data.mentorInfo.name}</p>
              <p className="text-gray-500 text-sm">{data.mentorInfo.email}</p>
            </div>
            {data.communicationLinks.discord && (
              <a
                href={data.communicationLinks.discord}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-[#5865F2]/10 text-[#5865F2] hover:bg-[#5865F2]/20 transition-colors text-sm"
              >
                <MessageCircle size={16} />
                Discord 참여
              </a>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Verify frontend compiles**

Run: `cd frontend && npm run build`
Expected: BUILD 성공

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/lms/StatCard.tsx \
       frontend/src/components/lms/ProgressCircle.tsx \
       frontend/src/components/lms/ActivityFeed.tsx \
       frontend/src/app/lms/dashboard/page.tsx
git commit -m "feat(lms): add Dashboard page with stat cards, activity feed, and communication links"
```

---

### Task 12: 커리큘럼 + 세션 페이지 (담당자 A — Day 5~6)

**Files:**
- Create: `frontend/src/app/lms/curriculum/page.tsx`
- Create: `frontend/src/app/lms/sessions/page.tsx`

- [ ] **Step 1: Create Curriculum page**

```tsx
// frontend/src/app/lms/curriculum/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { CheckCircle2, Circle, ExternalLink } from 'lucide-react';
import { getCurriculum, toggleWeekComplete } from '@/lib/lms';
import type { CurriculumResponse } from '@/lib/lms-types';

export default function CurriculumPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [curriculum, setCurriculum] = useState<CurriculumResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchCurriculum = () => {
    if (!matchingId) return;
    getCurriculum(matchingId)
      .then((res) => setCurriculum(res.data.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchCurriculum(); }, [matchingId]);

  const handleToggle = async (weekId: number) => {
    await toggleWeekComplete(weekId);
    fetchCurriculum();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!curriculum) {
    return (
      <div className="text-center py-20">
        <p className="text-gray-400">아직 커리큘럼이 등록되지 않았습니다.</p>
        <p className="text-gray-500 text-sm mt-1">멘토가 커리큘럼을 설정하면 여기에 표시됩니다.</p>
      </div>
    );
  }

  const completedCount = curriculum.weeks.filter(w => w.isCompleted).length;
  const progressPercent = curriculum.totalWeeks > 0
    ? Math.round((completedCount / curriculum.totalWeeks) * 100) : 0;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">{curriculum.title}</h1>
        {curriculum.description && (
          <p className="text-gray-400 mt-1">{curriculum.description}</p>
        )}
        <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
          <span>{curriculum.startDate} ~ {curriculum.endDate}</span>
          <span>{completedCount}/{curriculum.totalWeeks}주 완료</span>
        </div>
      </div>

      {/* 프로그레스 바 */}
      <div className="bg-white/5 rounded-full h-3 overflow-hidden">
        <div
          className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full transition-all duration-500"
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      {/* 주차 목록 */}
      <div className="space-y-4">
        {curriculum.weeks.map((week) => (
          <div
            key={week.id}
            className={`bg-[#0f1420] border rounded-2xl p-6 transition-all ${
              week.isCompleted ? 'border-blue-500/30' : 'border-white/5'
            }`}
          >
            <div className="flex items-start gap-4">
              <button
                onClick={() => handleToggle(week.id)}
                className="mt-0.5 shrink-0"
              >
                {week.isCompleted ? (
                  <CheckCircle2 size={22} className="text-blue-400" />
                ) : (
                  <Circle size={22} className="text-gray-600 hover:text-gray-400 transition-colors" />
                )}
              </button>
              <div className="flex-1">
                <h3 className="text-white font-semibold">
                  Week {week.weekNumber}: {week.title}
                </h3>
                {week.description && (
                  <p className="text-gray-400 text-sm mt-1">{week.description}</p>
                )}
                {week.topics.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-3">
                    {week.topics.map((topic, i) => (
                      <span key={i} className="px-2.5 py-0.5 rounded-md text-xs font-medium bg-blue-500/10 text-blue-400">
                        {topic}
                      </span>
                    ))}
                  </div>
                )}
                {week.resources.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-2">
                    {week.resources.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-blue-400 transition-colors">
                        <ExternalLink size={12} /> 참고 자료 {i + 1}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create Sessions page**

```tsx
// frontend/src/app/lms/sessions/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { Video, Clock } from 'lucide-react';
import apiClient from '@/lib/api';
import type { ApiResponse } from '@/lib/types';

interface Session {
  id: number;
  matchingId: number;
  category: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  status: string;
  meetLink: string | null;
  memo: string | null;
}

export default function SessionsPage() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiClient.get<ApiResponse<Session[]>>('/sessions')
      .then((res) => setSessions(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  const statusLabel: Record<string, string> = {
    SCHEDULED: '예정',
    COMPLETED: '완료',
    CANCELLED: '취소',
  };

  const statusColor: Record<string, string> = {
    SCHEDULED: 'bg-blue-500/10 text-blue-400',
    COMPLETED: 'bg-green-500/10 text-green-400',
    CANCELLED: 'bg-red-500/10 text-red-400',
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">멘토링 세션</h1>
        <p className="text-gray-400 mt-1">예정된 세션과 지난 세션을 확인하세요</p>
      </div>

      {sessions.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">아직 예정된 세션이 없습니다.</p>
        </div>
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
                  </div>
                  <div className="flex items-center gap-2 mt-2 text-gray-400 text-sm">
                    <Clock size={14} />
                    <span>{session.sessionDate} {session.startTime} ~ {session.endTime}</span>
                  </div>
                  {session.memo && (
                    <p className="text-gray-500 text-sm mt-2">{session.memo}</p>
                  )}
                </div>
                {session.meetLink && session.status === 'SCHEDULED' && (
                  <a
                    href={session.meetLink}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors text-sm font-medium"
                  >
                    <Video size={16} />
                    참여
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/lms/curriculum/page.tsx \
       frontend/src/app/lms/sessions/page.tsx
git commit -m "feat(lms): add Curriculum and Sessions pages"
```

---

### Task 13: 과제 + 학습노트 페이지 (담당자 B — Day 5)

**Files:**
- Create: `frontend/src/app/lms/assignments/page.tsx`
- Create: `frontend/src/app/lms/notes/page.tsx`

- [ ] **Step 1: Create Assignments page**

```tsx
// frontend/src/app/lms/assignments/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ClipboardList, GitPullRequest, ExternalLink } from 'lucide-react';
import { getAssignments } from '@/lib/lms';
import type { AssignmentResponse } from '@/lib/lms-types';

export default function AssignmentsPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [filter, setFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    getAssignments(matchingId, filter || undefined)
      .then((res) => setAssignments(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId, filter]);

  const statusLabel: Record<string, string> = {
    ASSIGNED: '진행중',
    SUBMITTED: '제출완료',
    REVIEWED: '리뷰완료',
  };

  const statusColor: Record<string, string> = {
    ASSIGNED: 'bg-yellow-500/10 text-yellow-400',
    SUBMITTED: 'bg-blue-500/10 text-blue-400',
    REVIEWED: 'bg-green-500/10 text-green-400',
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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">과제 / 코드리뷰</h1>
          <p className="text-gray-400 mt-1">과제 제출과 코드리뷰 현황을 관리하세요</p>
        </div>
      </div>

      {/* 필터 탭 */}
      <div className="flex gap-2">
        {[
          { label: '전체', value: '' },
          { label: '과제', value: 'TASK' },
          { label: '코드리뷰', value: 'CODE_REVIEW' },
        ].map((tab) => (
          <button
            key={tab.value}
            onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filter === tab.value
                ? 'bg-blue-500/10 text-blue-400'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {assignments.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">등록된 과제가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {assignments.map((a) => (
            <div key={a.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  {a.type === 'TASK' ? (
                    <ClipboardList size={20} className="text-blue-400 mt-0.5" />
                  ) : (
                    <GitPullRequest size={20} className="text-purple-400 mt-0.5" />
                  )}
                  <div>
                    <h3 className="text-white font-semibold">{a.title}</h3>
                    {a.description && (
                      <p className="text-gray-400 text-sm mt-1 line-clamp-2">{a.description}</p>
                    )}
                    <div className="flex items-center gap-3 mt-2 text-xs text-gray-500">
                      {a.dueDate && <span>마감: {a.dueDate}</span>}
                      <span>{new Date(a.createdAt).toLocaleDateString('ko-KR')}</span>
                    </div>
                    {a.referenceUrls && a.referenceUrls.length > 0 && (
                      <div className="flex gap-2 mt-2">
                        {a.referenceUrls.map((url, i) => (
                          <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-blue-400">
                            <ExternalLink size={12} /> 링크 {i + 1}
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
                <span className={`px-2.5 py-0.5 rounded-md text-xs font-medium shrink-0 ${statusColor[a.status] || ''}`}>
                  {statusLabel[a.status] || a.status}
                </span>
              </div>
              {a.submission && (
                <div className="mt-4 pt-4 border-t border-white/5">
                  <p className="text-gray-400 text-xs mb-1">제출물</p>
                  <a href={a.submission.submissionUrl} target="_blank" rel="noopener noreferrer"
                    className="text-blue-400 text-sm hover:underline">{a.submission.submissionUrl}</a>
                  {a.submission.feedbackContent && (
                    <div className="mt-2 p-3 rounded-lg bg-white/3">
                      <p className="text-gray-400 text-xs mb-1">멘토 피드백 {a.submission.grade && `(${a.submission.grade})`}</p>
                      <p className="text-gray-300 text-sm">{a.submission.feedbackContent}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Create Notes page**

```tsx
// frontend/src/app/lms/notes/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { NotebookPen, Star } from 'lucide-react';
import { getNotes } from '@/lib/lms';
import type { NoteResponse } from '@/lib/lms-types';

export default function NotesPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [notes, setNotes] = useState<NoteResponse[]>([]);
  const [filter, setFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    getNotes(matchingId, filter || undefined)
      .then((res) => setNotes(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId, filter]);

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
        <h1 className="text-2xl font-bold text-white">학습 노트</h1>
        <p className="text-gray-400 mt-1">세션 회고와 주간 일지를 기록하세요</p>
      </div>

      <div className="flex gap-2">
        {[
          { label: '전체', value: '' },
          { label: '세션 회고', value: 'SESSION_REVIEW' },
          { label: '주간 일지', value: 'WEEKLY_JOURNAL' },
        ].map((tab) => (
          <button
            key={tab.value}
            onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filter === tab.value
                ? 'bg-blue-500/10 text-blue-400'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {notes.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">작성된 노트가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {notes.map((note) => (
            <div key={note.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-start gap-3">
                <NotebookPen size={20} className="text-cyan-400 mt-0.5 shrink-0" />
                <div className="flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="text-white font-semibold">{note.title}</h3>
                    <span className="px-2.5 py-0.5 rounded-md text-xs font-medium bg-white/5 text-gray-400">
                      {note.type === 'SESSION_REVIEW' ? '세션 회고' : '주간 일지'}
                    </span>
                    {note.selfRating && (
                      <span className="inline-flex items-center gap-1 text-xs text-yellow-400">
                        <Star size={12} fill="currentColor" /> {note.selfRating}
                      </span>
                    )}
                  </div>
                  <p className="text-gray-400 text-sm mt-2 line-clamp-3">{note.content}</p>
                  <div className="flex items-center gap-3 mt-3 text-xs text-gray-500">
                    <span>{note.authorName}</span>
                    <span>{new Date(note.createdAt).toLocaleDateString('ko-KR')}</span>
                    {note.comments.length > 0 && <span>코멘트 {note.comments.length}개</span>}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/lms/assignments/page.tsx \
       frontend/src/app/lms/notes/page.tsx
git commit -m "feat(lms): add Assignments and Notes pages"
```

---

### Task 14: 취업지원 + 수료증 페이지 (담당자 B — Day 6)

**Files:**
- Create: `frontend/src/app/lms/career/page.tsx`
- Create: `frontend/src/app/lms/certificate/page.tsx`

- [ ] **Step 1: Create Career page**

```tsx
// frontend/src/app/lms/career/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { FileText, MessageSquare } from 'lucide-react';
import { getResumes, getMockInterviews } from '@/lib/lms';
import type { ResumeResponse, MockInterviewResponse } from '@/lib/lms-types';

export default function CareerPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [resumes, setResumes] = useState<ResumeResponse[]>([]);
  const [interviews, setInterviews] = useState<MockInterviewResponse[]>([]);
  const [tab, setTab] = useState<'resume' | 'interview'>('resume');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    Promise.all([
      getResumes(matchingId).then((res) => setResumes(res.data.data || [])),
      getMockInterviews(matchingId).then((res) => setInterviews(res.data.data || [])),
    ])
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId]);

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
        <h1 className="text-2xl font-bold text-white">취업 지원</h1>
        <p className="text-gray-400 mt-1">이력서 관리와 모의 면접 기록</p>
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => setTab('resume')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            tab === 'resume' ? 'bg-blue-500/10 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'
          }`}
        >
          이력서 ({resumes.length})
        </button>
        <button
          onClick={() => setTab('interview')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            tab === 'interview' ? 'bg-blue-500/10 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'
          }`}
        >
          모의 면접 ({interviews.length})
        </button>
      </div>

      {tab === 'resume' ? (
        resumes.length === 0 ? (
          <div className="text-center py-20"><p className="text-gray-400">등록된 이력서가 없습니다.</p></div>
        ) : (
          <div className="space-y-4">
            {resumes.map((r) => (
              <div key={r.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-start gap-3">
                  <FileText size={20} className="text-blue-400 mt-0.5" />
                  <div className="flex-1">
                    <div className="flex items-center gap-3">
                      <h3 className="text-white font-semibold">{r.fileName}</h3>
                      <span className="text-gray-500 text-xs">v{r.version}</span>
                    </div>
                    <p className="text-gray-500 text-xs mt-1">
                      {new Date(r.uploadedAt).toLocaleDateString('ko-KR')} 업로드
                    </p>
                    {r.mentorFeedback && (
                      <div className="mt-3 p-3 rounded-lg bg-white/3">
                        <p className="text-gray-400 text-xs mb-1">멘토 피드백</p>
                        <p className="text-gray-300 text-sm">{r.mentorFeedback}</p>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      ) : (
        interviews.length === 0 ? (
          <div className="text-center py-20"><p className="text-gray-400">기록된 모의 면접이 없습니다.</p></div>
        ) : (
          <div className="space-y-4">
            {interviews.map((mi) => (
              <div key={mi.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-start gap-3">
                  <MessageSquare size={20} className="text-purple-400 mt-0.5" />
                  <div className="flex-1">
                    <h3 className="text-white font-semibold">{mi.topic}</h3>
                    <p className="text-gray-500 text-xs mt-1">{mi.interviewDate}</p>
                    {mi.rating && (
                      <span className="text-yellow-400 text-xs">평가: {mi.rating}/5</span>
                    )}
                    {mi.mentorFeedback && (
                      <div className="mt-3 p-3 rounded-lg bg-white/3">
                        <p className="text-gray-400 text-xs mb-1">멘토 피드백</p>
                        <p className="text-gray-300 text-sm">{mi.mentorFeedback}</p>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      )}
    </div>
  );
}
```

- [ ] **Step 2: Create Certificate page**

```tsx
// frontend/src/app/lms/certificate/page.tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Award, Download, CheckCircle2, XCircle } from 'lucide-react';
import { checkEligibility, downloadCertificate } from '@/lib/lms';
import type { CertificateEligibilityResponse } from '@/lib/lms-types';

export default function CertificatePage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [eligibility, setEligibility] = useState<CertificateEligibilityResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    if (!matchingId) return;
    checkEligibility(matchingId)
      .then((res) => setEligibility(res.data.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId]);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      const res = await downloadCertificate(matchingId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = 'certificate.pdf';
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      alert('수료증 다운로드에 실패했습니다.');
    } finally {
      setDownloading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!eligibility) return null;

  const criteria = [
    { label: '진도율', value: eligibility.progressRate, required: eligibility.requiredProgress },
    { label: '출석률', value: eligibility.attendanceRate, required: eligibility.requiredAttendance },
    { label: '과제 제출률', value: eligibility.assignmentSubmitRate, required: eligibility.requiredAssignmentRate },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">수료증</h1>
        <p className="text-gray-400 mt-1">멘토링 수료 자격을 확인하고 수료증을 발급받으세요</p>
      </div>

      <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-8">
        <div className="flex items-center gap-4 mb-6">
          <div className={`w-14 h-14 rounded-2xl flex items-center justify-center ${
            eligibility.eligible
              ? 'bg-gradient-to-br from-blue-500 to-cyan-400'
              : 'bg-white/5'
          }`}>
            <Award size={28} className="text-white" />
          </div>
          <div>
            <h2 className="text-white text-xl font-bold">
              {eligibility.eligible ? '수료 자격 충족!' : '수료 자격 미달'}
            </h2>
            <p className="text-gray-400 text-sm">
              {eligibility.eligible
                ? '축하합니다! 수료증을 다운로드할 수 있습니다.'
                : '아래 기준을 충족하면 수료증을 발급받을 수 있습니다.'}
            </p>
          </div>
        </div>

        <div className="space-y-4">
          {criteria.map((c) => {
            const passed = c.value >= c.required;
            return (
              <div key={c.label} className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  {passed ? (
                    <CheckCircle2 size={18} className="text-green-400" />
                  ) : (
                    <XCircle size={18} className="text-red-400" />
                  )}
                  <span className="text-gray-300">{c.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`font-semibold ${passed ? 'text-green-400' : 'text-red-400'}`}>
                    {c.value}%
                  </span>
                  <span className="text-gray-500 text-sm">/ {c.required}% 이상</span>
                </div>
              </div>
            );
          })}
        </div>

        {eligibility.eligible && (
          <button
            onClick={handleDownload}
            disabled={downloading}
            className="mt-8 w-full py-3 rounded-xl bg-gradient-to-r from-blue-600 to-blue-500 text-white font-semibold
                     hover:from-blue-500 hover:to-blue-400 transition-all duration-300
                     shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30
                     flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <Download size={18} />
            {downloading ? '다운로드 중...' : '수료증 다운로드 (PDF)'}
          </button>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/lms/career/page.tsx \
       frontend/src/app/lms/certificate/page.tsx
git commit -m "feat(lms): add Career and Certificate pages"
```

---

## Day 7: 통합 및 마무리

### Task 15: 통합 빌드 검증 + 버그 수정

- [ ] **Step 1: Backend 전체 빌드**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Frontend 전체 빌드**

Run: `cd frontend && npm run build`
Expected: BUILD 성공

- [ ] **Step 3: 통합 테스트 — 서버 기동 확인**

Run: `cd backend && ./gradlew bootRun`
Expected: 서버가 정상 기동되며, `/swagger-ui.html`에서 새로운 LMS API가 표시됨

- [ ] **Step 4: Fix any compilation or runtime errors found**

발견된 에러를 수정한다. 일반적으로 발생 가능한 이슈:
- Entity 간 관계 매핑 오류 (FK 칼럼명 불일치)
- DTO의 from() 메서드에서 null 처리 누락
- Repository 쿼리 메서드 이름 오타

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "fix(lms): resolve integration issues and finalize LMS implementation"
```

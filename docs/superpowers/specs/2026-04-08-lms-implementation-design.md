# DevMatch LMS 구현 설계서

> **작성일:** 2026-04-08
> **상태:** 확정
> **구현 기간:** 1주일 (2명 분업)
> **이전 문서:** document/teamwork/lms-feature-spec.md, document/teamwork/lms-design-guide.md

---

## 1. 설계 결정 요약

### 기존 스펙 대비 변경 사항

| 항목 | 기존 스펙 | 변경 후 | 이유 |
|------|----------|---------|------|
| 페이지 수 | 9개 | **7개** | Communication은 대시보드에 통합, CodeReview/Journal은 각각 Assignment/Note에 통합 |
| Entity 수 | 9개+ | **8개** (신규 6 + 취업 2) | Assignment+CodeReview 통합, SessionNote+Journal 통합 |
| API 수 | 30개+ | **23개** (신규) + 기존 세션 4개 수정 | 통합으로 중복 제거 |
| 화상 회의 | Google Meet (스텁) | **Jitsi Meet** | 무료, API 키 불필요, 항상 동작 |
| 실시간 소통 | Slack | **Discord** | 완전 무료, 메시지 보관 무제한 |
| Google Calendar | 연동 (스텁 모드) | **미사용** (코드 유지) | Jitsi로 대체, 기존 코드는 포트폴리오용 보존 |
| LMS 진입 조건 | 결제 확인 후 | **TRIAL 또는 ACCEPTED** | Matching에 TRIAL 상태 존재 |
| 수료증 | Entity + DB 저장 | **Entity 없이 즉시 PDF 생성** | 오버엔지니어링 방지 |

### 핵심 설계 원칙

1. **중복 제거** — 유사 도메인은 type 필드로 통합 (면접에서 설계 판단력 어필)
2. **기존 코드 최대 재활용** — Phase 4 세션/가용시간, Phase 5 결제/커뮤니티 그대로 활용
3. **외부 의존 최소화** — Jitsi는 URL 생성만, GitHub/Discord는 링크만 (MVP)
4. **백엔드 먼저** — Day 2~3 백엔드 집중, Day 4~6 프론트 집중

---

## 2. 페이지 구조

```
/lms
├── /dashboard          ← 대시보드 (소통 링크 포함)      [담당자A]
├── /curriculum         ← 커리큘럼 / 진도 관리           [담당자A]
├── /sessions           ← 멘토링 세션 + Jitsi Meet      [담당자A]
├── /assignments        ← 과제 + 코드리뷰 (통합)         [담당자B]
├── /notes              ← 학습 노트 (세션회고+일지 통합)   [담당자B]
├── /career             ← 취업 지원 (이력서/모의면접)      [담당자B]
└── /certificate        ← 수료증                        [담당자B]
```

---

## 3. LMS 접근 권한

```
모든 /api/lms/** 요청 시:
  1. JWT에서 userId 추출
  2. matchingId 파라미터로 Matching 조회
  3. Matching.mentee.id == userId || Matching.mentor.id == userId 확인
  4. Matching.status == TRIAL || ACCEPTED 확인
  5. 실패 시 403 Forbidden
```

구현: Service 레벨 공통 메서드 `validateLmsAccess(Long userId, Long matchingId)`

---

## 4. Entity 설계

### 4.1 Curriculum

```java
@Entity
@Table(name = "curriculums")
Curriculum {
    id: Long (PK, AUTO_INCREMENT)
    matchingId: Long (FK → matchings, UNIQUE)
    title: String (NOT NULL, max 200)
    description: String (TEXT, nullable)
    totalWeeks: Integer (NOT NULL)
    startDate: LocalDate (NOT NULL)
    endDate: LocalDate (NOT NULL)
    discordUrl: String (nullable, max 500)  // Discord 서버 초대 링크
    createdAt: LocalDateTime
    updatedAt: LocalDateTime
}
```

### 4.2 CurriculumWeek

```java
@Entity
@Table(name = "curriculum_weeks")
CurriculumWeek {
    id: Long (PK, AUTO_INCREMENT)
    curriculumId: Long (FK → curriculums)
    weekNumber: Integer (NOT NULL)
    title: String (NOT NULL, max 200)
    description: String (TEXT, nullable)
    topics: List<String> (JSON, StringListConverter)
    resources: List<String> (JSON, StringListConverter)
    isCompleted: Boolean (NOT NULL, default false)
    completedAt: LocalDateTime (nullable)
}
```

**참고:** `StringListConverter`는 기존 프로젝트에 이미 존재함 (`entity/StringListConverter.java`)

### 4.3 Assignment (과제 + 코드리뷰 통합)

```java
@Entity
@Table(name = "assignments")
Assignment {
    id: Long (PK, AUTO_INCREMENT)
    matchingId: Long (FK → matchings)
    mentorId: Long (FK → users)
    type: AssignmentType (ENUM: TASK / CODE_REVIEW)
    title: String (NOT NULL, max 200)
    description: String (TEXT, nullable)
    dueDate: LocalDate (nullable)
    referenceUrls: List<String> (JSON, StringListConverter)
    status: AssignmentStatus (ENUM: ASSIGNED / SUBMITTED / REVIEWED, default ASSIGNED)
    createdAt: LocalDateTime
    updatedAt: LocalDateTime
}
```

- `type=TASK`: 멘토가 출제, 마감일 있음
- `type=CODE_REVIEW`: 멘티가 PR URL 등록 요청, 마감일 선택적

### 4.4 AssignmentSubmission

```java
@Entity
@Table(name = "assignment_submissions")
AssignmentSubmission {
    id: Long (PK, AUTO_INCREMENT)
    assignmentId: Long (FK → assignments)
    menteeId: Long (FK → users)
    submissionUrl: String (NOT NULL, max 500)   // GitHub PR/Repo 링크
    submissionNote: String (TEXT, nullable)
    submittedAt: LocalDateTime (NOT NULL)
    feedbackContent: String (TEXT, nullable)
    grade: String (nullable, max 10)             // PASS/FAIL 또는 A/B/C
    feedbackAt: LocalDateTime (nullable)
}
```

### 4.5 LearningNote (세션 회고 + 주간 일지 통합)

```java
@Entity
@Table(name = "learning_notes")
LearningNote {
    id: Long (PK, AUTO_INCREMENT)
    matchingId: Long (FK → matchings)
    authorId: Long (FK → users)
    type: NoteType (ENUM: SESSION_REVIEW / WEEKLY_JOURNAL)
    sessionId: Long (nullable, FK → mentoring_sessions)  // SESSION_REVIEW일 때만
    weekNumber: Integer (nullable)                        // WEEKLY_JOURNAL일 때만
    title: String (NOT NULL, max 200)
    content: String (TEXT, NOT NULL)
    selfRating: Integer (nullable, 1~5)
    createdAt: LocalDateTime
    updatedAt: LocalDateTime
}
```

### 4.6 NoteComment

```java
@Entity
@Table(name = "note_comments")
NoteComment {
    id: Long (PK, AUTO_INCREMENT)
    noteId: Long (FK → learning_notes)
    authorId: Long (FK → users)
    content: String (TEXT, NOT NULL)
    createdAt: LocalDateTime
}
```

### 4.7 Resume

```java
@Entity
@Table(name = "resumes")
Resume {
    id: Long (PK, AUTO_INCREMENT)
    menteeId: Long (FK → users)
    matchingId: Long (FK → matchings)
    version: Integer (NOT NULL)
    fileUrl: String (NOT NULL, max 500)
    fileName: String (NOT NULL, max 200)
    mentorFeedback: String (TEXT, nullable)
    feedbackAt: LocalDateTime (nullable)
    uploadedAt: LocalDateTime (NOT NULL)
}
```

### 4.8 MockInterview

```java
@Entity
@Table(name = "mock_interviews")
MockInterview {
    id: Long (PK, AUTO_INCREMENT)
    matchingId: Long (FK → matchings)
    interviewDate: LocalDate (NOT NULL)
    topic: String (NOT NULL, max 200)
    questionsAndAnswers: String (TEXT)
    mentorFeedback: String (TEXT, nullable)
    rating: Integer (nullable, 1~5)
    createdAt: LocalDateTime
}
```

### Enum 정의

```java
public enum AssignmentType { TASK, CODE_REVIEW }
public enum AssignmentStatus { ASSIGNED, SUBMITTED, REVIEWED }
public enum NoteType { SESSION_REVIEW, WEEKLY_JOURNAL }
```

---

## 5. API 설계

### 5.1 대시보드 (2개) — 담당자 A

| Method | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/lms/dashboard/{matchingId}` | O | 종합 통계 |
| GET | `/api/lms/enrollment/{matchingId}` | O | 수강 정보 |

**대시보드 응답 구조:**
```json
{
  "progressRate": 68,
  "attendanceRate": 85,
  "dDay": 42,
  "assignmentStats": { "total": 5, "submitted": 3, "reviewed": 2 },
  "nextSession": { "id": 1, "date": "2026-04-10", "startTime": "19:00", "endTime": "20:00", "meetLink": "https://meet.jit.si/devmatch-...", "category": "Spring Security" },
  "recentActivities": [
    { "type": "ASSIGNMENT_FEEDBACK", "title": "JPA N+1 해결", "createdAt": "2026-04-08T10:00:00" }
  ],
  "mentorInfo": { "name": "김재현", "specialty": ["Java", "Spring"], "email": "mentor@example.com" },
  "communicationLinks": { "discord": "https://discord.gg/...", "jitsiMeet": "https://meet.jit.si/devmatch-..." }
  // ※ discord 링크는 Curriculum.description에 멘토가 기입하거나, 대시보드 응답 시 Matching 메모에서 추출
}
```

**recentActivities 집계:** Assignment, LearningNote, MentoringSession 각각 최근 3건 조회 → Java에서 합산 후 시간순 상위 5건 반환. 별도 Activity Entity 없음.

### 5.2 커리큘럼 (4개) — 담당자 A

| Method | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/lms/curriculum` | O (멘토) | 커리큘럼 생성 (weeks 배열 포함) |
| GET | `/api/lms/curriculum/{matchingId}` | O | 커리큘럼 + 주차 목록 |
| PUT | `/api/lms/curriculum/{id}` | O (멘토) | 커리큘럼 수정 |
| PUT | `/api/lms/curriculum/weeks/{weekId}/complete` | O (멘티) | 주차 완료 토글 |

### 5.3 세션 — 담당자 A (기존 수정)

기존 Phase 4 API 4개(`/api/sessions`) 그대로 재사용. 변경 사항:
- `SessionService.createSession()`: GoogleCalendarService → **JitsiMeetService** 호출
- meetLink에 Jitsi URL 저장

### 5.4 과제/코드리뷰 통합 (5개) — 담당자 B

| Method | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/lms/assignments` | O | 과제 출제(멘토) 또는 코드리뷰 등록(멘티) |
| GET | `/api/lms/assignments` | O | 목록 (`?matchingId=&type=TASK/CODE_REVIEW`) |
| GET | `/api/lms/assignments/{id}` | O | 상세 (제출+피드백 포함) |
| POST | `/api/lms/assignments/{id}/submit` | O (멘티) | 제출 |
| POST | `/api/lms/assignments/{id}/feedback` | O (멘토) | 피드백 |

### 5.5 학습 노트 통합 (5개) — 담당자 B

| Method | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/lms/notes` | O | 노트 작성 (`type: SESSION_REVIEW/WEEKLY_JOURNAL`) |
| GET | `/api/lms/notes` | O | 목록 (`?matchingId=&type=`) |
| GET | `/api/lms/notes/{id}` | O | 상세 (코멘트 포함) |
| PUT | `/api/lms/notes/{id}` | O | 수정 |
| POST | `/api/lms/notes/{id}/comments` | O (멘토) | 코멘트 |

### 5.6 취업 지원 (5개) — 담당자 B

| Method | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/lms/resumes` | O (멘티) | 이력서 업로드 (multipart/form-data) |
| GET | `/api/lms/resumes` | O | 이력서 목록 (`?matchingId=`) |
| POST | `/api/lms/resumes/{id}/feedback` | O (멘토) | 이력서 피드백 |
| POST | `/api/lms/mock-interviews` | O | 모의 면접 기록 |
| GET | `/api/lms/mock-interviews` | O | 모의 면접 목록 (`?matchingId=`) |

### 5.7 수료증 (2개) — 담당자 B

| Method | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/lms/certificate/eligibility/{matchingId}` | O | 수료 자격 확인 |
| GET | `/api/lms/certificate/{matchingId}/download` | O | PDF 다운로드 |

**수료 자격 기준:**
```
진도율 ≥ 80% AND 출석률 ≥ 80% AND 과제 제출률 ≥ 70%
```

---

## 6. Jitsi Meet 연동

### JitsiMeetService

```java
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

### SessionService 변경

```
기존: googleCalendarService.createMentoringEvent(...) → null 반환 (스텁)
변경: jitsiMeetService.generateMeetLink(matchingId, sessionDate) → 항상 URL 반환
     → session.updateMeetLink(meetLink) 저장
     → GoogleCalendarService 호출 제거
```

### 프론트엔드 세션 페이지

```
옵션 A (단순): <a href={meetLink} target="_blank">화상 회의 참여</a>
옵션 B (임베딩): <iframe src={meetLink} allow="camera;microphone" />
```

MVP에서는 옵션 A로 시작. 시간 여유가 있으면 옵션 B로 전환.

---

## 7. 기존 코드 변경 범위

| 파일 | 변경 | 담당 |
|------|------|------|
| `SessionService.java` | GoogleCalendar 호출 → Jitsi 호출 | A |
| `GoogleCalendarService.java` | 변경 없음 (유지) | - |
| `GoogleCalendarConfig.java` | 변경 없음 (유지) | - |
| `Header.tsx` | LMS 메뉴 추가 (`/lms/dashboard`) | 공통 |
| **신규** `JitsiMeetService.java` | Jitsi URL 생성 | A |
| **신규** `frontend/src/app/lms/layout.tsx` | 사이드바 레이아웃 | 공통 |

---

## 8. 프론트엔드 구조

### 레이아웃

```
/lms/layout.tsx          ← 사이드바 + 메인 콘텐츠 영역
  ├── LmsSidebar.tsx     ← 메뉴 + 하단 멘토 카드
  └── children           ← 각 페이지 콘텐츠
```

### 디자인 토큰 (기존 시스템 유지)

```
배경:          bg-[#0a0e1a] (메인), bg-[#0f1420] (카드), bg-[#0b101e] (사이드바)
카드:          border border-white/5 rounded-2xl
활성 메뉴:     bg-blue-500/10 text-blue-400
프로그레스 바:  bg-white/5 (트랙), bg-gradient-to-r from-blue-500 to-cyan-400 (바)
배지:          px-2.5 py-0.5 rounded-md text-xs font-medium
아이콘:        lucide-react
```

### 추가 라이브러리

```
shadcn/ui: card, progress, badge, tabs, avatar, tooltip
recharts: 대시보드 차트
@fullcalendar/react: 세션 캘린더
```

---

## 9. 분업 및 일정

### 담당 범위

| 담당자 | Backend Entity | Backend API | Frontend 페이지 |
|--------|---------------|-------------|----------------|
| **A** | Curriculum, CurriculumWeek, JitsiMeetService | dashboard 2 + curriculum 4 = 6개 (신규) + session 4개 수정 | dashboard, curriculum, sessions |
| **B** | Assignment, AssignmentSubmission, LearningNote, NoteComment, Resume, MockInterview | assignment 5 + note 5 + career 5 + certificate 2 = 17개 (신규) | assignments, notes, career, certificate |
| **공통** | Enum(AssignmentType, NoteType, AssignmentStatus), LMS 접근 권한 | - | LmsLayout, LmsSidebar |

### 일별 계획

| 일차 | 담당자 A | 담당자 B |
|------|---------|---------|
| **Day 1** | 공통: LmsLayout.tsx, 사이드바, Enum, LMS 접근 권한 체크 | 공통: 같이 작업 + Assignment/LearningNote Entity 생성 |
| **Day 2** | Curriculum + CurriculumWeek Entity, Repository, DTO, API 4개 | Assignment + AssignmentSubmission Entity, Repository, DTO, API 5개 |
| **Day 3** | JitsiMeetService + SessionService 수정 + 세션 관련 DTO 수정 | LearningNote + NoteComment Entity, Repository, DTO, API 5개 |
| **Day 4** | 대시보드 API (통계 집계 로직) + 대시보드 UI 시작 | Resume + MockInterview Entity, API 5개 + 수료증 API 2개 |
| **Day 5** | 대시보드 UI 완성 (차트, 프로그레스) + 커리큘럼 페이지 | assignments 페이지 + notes 페이지 |
| **Day 6** | 세션 페이지 (FullCalendar + Jitsi 링크) | career 페이지 + certificate 페이지 |
| **Day 7** | 통합 테스트 + 버그 수정 | 통합 테스트 + 버그 수정 |

### Day 1 합의 필수 사항

1. **API 요청/응답 형태** — DTO 구조를 문서화하여 공유
2. **matchingId 전달 방식** — URL param vs query param 통일
3. **에러 응답 형태** — 기존 `ApiResponse` 재사용 확인
4. **프론트 상태 관리** — 각 페이지에서 matchingId를 어떻게 가져올지 (URL param 또는 context)

---

## 10. 파일 생성 목록

### Backend 신규 파일

```
entity/
  AssignmentType.java
  AssignmentStatus.java
  NoteType.java
  Curriculum.java
  CurriculumWeek.java
  Assignment.java
  AssignmentSubmission.java
  LearningNote.java
  NoteComment.java
  Resume.java
  MockInterview.java

repository/
  CurriculumRepository.java
  CurriculumWeekRepository.java
  AssignmentRepository.java
  AssignmentSubmissionRepository.java
  LearningNoteRepository.java
  NoteCommentRepository.java
  ResumeRepository.java
  MockInterviewRepository.java

dto/lms/
  DashboardResponse.java
  EnrollmentResponse.java
  CurriculumCreateRequest.java
  CurriculumResponse.java
  AssignmentCreateRequest.java
  AssignmentResponse.java
  SubmissionRequest.java
  FeedbackRequest.java
  NoteCreateRequest.java
  NoteResponse.java
  NoteCommentRequest.java
  ResumeResponse.java
  ResumeFeedbackRequest.java
  MockInterviewCreateRequest.java
  MockInterviewResponse.java
  CertificateEligibilityResponse.java

service/
  JitsiMeetService.java
  LmsDashboardService.java
  CurriculumService.java
  LmsAssignmentService.java
  LearningNoteService.java
  CareerService.java
  CertificateService.java

controller/
  LmsDashboardController.java
  CurriculumController.java
  LmsAssignmentController.java
  LearningNoteController.java
  CareerController.java
  CertificateController.java
```

### Frontend 신규 파일

```
src/app/lms/
  layout.tsx                    ← 사이드바 레이아웃
  dashboard/page.tsx
  curriculum/page.tsx
  sessions/page.tsx
  assignments/page.tsx
  notes/page.tsx
  career/page.tsx
  certificate/page.tsx

src/components/lms/
  LmsSidebar.tsx
  StatCard.tsx
  ProgressCircle.tsx
  ActivityFeed.tsx
  SessionCalendar.tsx

src/lib/
  lms.ts                        ← LMS API 호출 함수
```

---

## 11. 기술 스택 (무료)

| 영역 | 기술 | 비고 |
|------|------|------|
| Backend | Spring Boot 3.4 + JPA + Java 17 | 기존 유지 |
| Frontend | Next.js 14 + Tailwind CSS | 기존 유지 |
| 화상 회의 | Jitsi Meet (meet.jit.si) | URL 생성만, API 키 불필요 |
| 소통 | Discord | 링크 제공만 (MVP) |
| 차트 | Recharts | shadcn/ui Chart 호환 |
| 캘린더 | FullCalendar React | MIT 라이센스 |
| UI 컴포넌트 | shadcn/ui | card, progress, badge, tabs |
| PDF 생성 | OpenPDF 또는 Apache PDFBox | 수료증용 |
| 파일 저장 | 로컬 (`/uploads`) | MVP. 배포 시 Cloudflare R2 전환 |

# DevMatch LMS(학습 관리 시스템) 기능 명세서

> **참고 페이지:** https://f-lab.kr/
> **작성일:** 2026-04-07
> **상태:** 기획 초안 (팀 리뷰 필요)
> **진입 조건:** 멘토 매칭 완료 + 결제 확인 후 LMS 페이지 접근 가능

---

## 1. 개요

멘토 매칭이 완료된 멘티에게 체계적인 학습 환경을 제공하는 LMS(Learning Management System) 페이지를 구현합니다.

### 진입 흐름

```
[지원서 제출] → [합격] → [결제 완료] → [멘토 매칭] → 🎯 [LMS 페이지 진입]
```

### 기능 분류

| 구분 | 사용자 제안 기능 | F-Lab 참고 추가 기능 |
|------|-----------------|---------------------|
| 학습 관리 | 진도율, 출석률 | 커리큘럼/로드맵, 주간 학습 일지 |
| 산출물 | 산출물 관리 | 과제 제출/피드백, 프로젝트 관리 |
| 일정 | 일정 관리 | 세션 기록/회고, 멘토 가용시간 연동 (Phase 4 활용) |
| 코드 리뷰 | GitHub 연동 | PR 기반 리뷰 추적, 리뷰 통계 |
| 소통 | Slack 연동 | 멘토링 노트, 질문/답변 게시판 |
| 화상 회의 | Zoom (또는 대안) | Google Meet 연동 (Phase 4 활용), 세션 녹화 링크 |
| **추가 제안** | - | 멘토 피드백 리포트, 취업 지원(이력서/모의면접), 대시보드, 수료증 |

---

## 2. LMS 전체 구조

```
/lms
├── /dashboard              ← 학습 대시보드 (메인)
├── /curriculum              ← 커리큘럼 / 학습 로드맵
├── /sessions                ← 멘토링 세션 (일정 + 기록)
├── /assignments             ← 과제 / 산출물 관리
├── /code-review             ← 코드 리뷰 (GitHub 연동)
├── /journal                 ← 학습 일지 / 회고록
├── /communication           ← 소통 (Slack/화상회의 연동)
├── /career                  ← 취업 지원 (이력서/모의면접)
└── /certificate             ← 수료증
```

---

## 3. 기능 상세

### 3.1 학습 대시보드 (`/lms/dashboard`)

멘티가 LMS 진입 시 가장 먼저 보는 메인 화면입니다.

#### 표시 항목

| 카드 | 내용 | 데이터 소스 |
|------|------|------------|
| 진도율 | 전체 커리큘럼 대비 완료율 (%) + 프로그레스 바 | Curriculum 완료 체크 |
| 출석률 | 전체 세션 대비 참석율 (%) | Session 상태 (COMPLETED/CANCELLED) |
| 다음 세션 | 가장 가까운 예정 세션 날짜/시간 + Meet 링크 | MentoringSession (Phase 4) |
| 최근 과제 | 최근 과제 상태 (제출/미제출/피드백 완료) | Assignment |
| 최근 코드 리뷰 | GitHub PR 최근 리뷰 현황 | GitHub API |
| 멘토 정보 | 매칭된 멘토 이름, 전문 분야, 연락처 | Matching + MentorProfile |
| 학습 기간 | 시작일 ~ 종료 예정일 + D-Day | Enrollment |

#### 와이어프레임

```
┌─────────────────────────────────────────────────────────────┐
│  DevMatch LMS                           [멘토: 홍길동]  👤  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │ 진도율    │ │ 출석률    │ │ D-Day    │ │ 과제     │      │
│  │ ████░ 68%│ │ ████░ 85%│ │ D-42     │ │ 2/5 완료 │      │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
│                                                             │
│  ── 다음 멘토링 세션 ──────────────────────────────────      │
│  📅 2026-04-10 (목) 19:00 ~ 20:00                          │
│  📌 주제: Spring Security 심화                              │
│  🔗 [Google Meet 참여하기]                                  │
│                                                             │
│  ── 최근 활동 ─────────────────────────────────────         │
│  • [과제] "JPA N+1 해결 과제" 피드백 도착 (2h 전)            │
│  • [코드리뷰] PR #23 멘토 코멘트 3개 (어제)                  │
│  • [학습일지] 4월 1주차 회고 작성 완료                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

### 3.2 커리큘럼 / 학습 로드맵 (`/lms/curriculum`)

> **F-Lab 참고:** F-Lab은 코스별 커리큘럼을 제공하며, 멘토의 꼬리질문을 통해 깊이 있는 학습을 유도합니다.

멘토가 설정한 주차별 학습 목표와 토픽을 관리합니다.

#### 기능

| 기능 | 역할 | 설명 |
|------|------|------|
| 커리큘럼 조회 | 멘티/멘토 | 주차별 학습 토픽, 목표, 참고 자료 확인 |
| 커리큘럼 생성/수정 | 멘토 | 주차별 학습 계획 작성, 토픽 추가/수정 |
| 진도 체크 | 멘티 | 각 토픽의 학습 완료 체크 |
| 진도 확인 | 멘토 | 멘티의 학습 진도 현황 확인 |

#### 데이터 모델

```
Curriculum {
  id: Long (PK)
  matchingId: Long (FK → Matching)
  title: String                    // "Java Backend 심화 과정"
  description: String              // 과정 설명
  totalWeeks: Integer              // 총 주차 수 (예: 16주)
  startDate: LocalDate             // 시작일
  endDate: LocalDate               // 종료 예정일
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}

CurriculumWeek {
  id: Long (PK)
  curriculumId: Long (FK → Curriculum)
  weekNumber: Integer              // 주차 번호 (1, 2, 3...)
  title: String                    // "Spring Core 원리"
  description: String              // 주차별 학습 목표
  topics: List<String>             // ["IoC/DI", "AOP", "Bean Lifecycle"]
  resources: List<String>          // 참고 자료 URL 목록
  isCompleted: Boolean             // 멘티 완료 체크
  completedAt: LocalDateTime       // 완료 시점
}
```

#### 와이어프레임

```
┌─────────────────────────────────────────────────────┐
│  커리큘럼: Java Backend 심화 과정 (16주)              │
│  진도: ████████░░░░░░░░ 50% (8/16주 완료)            │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ✅ Week 1 — Java 기초 심화                          │
│     └ 토픽: JVM 구조, GC, 메모리 관리                 │
│                                                     │
│  ✅ Week 2 — 객체지향 설계                            │
│     └ 토픽: SOLID 원칙, 디자인 패턴                   │
│                                                     │
│  ...                                                │
│                                                     │
│  🔵 Week 8 — Spring Security 심화      ← 현재 주차   │
│     └ 토픽: JWT, OAuth2, CORS                       │
│     └ 참고자료: [Spring Docs] [Baeldung]             │
│     └ [✅ 완료 체크]                                 │
│                                                     │
│  ⬜ Week 9 — JPA 심화                                │
│     └ 토픽: N+1 문제, QueryDSL, 성능 최적화           │
│                                                     │
│  ⬜ Week 10 — 테스트 전략                             │
│     └ ...                                           │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

### 3.3 멘토링 세션 관리 (`/lms/sessions`)

> **기존 Phase 4 (Calendar & Meet 연동)를 LMS UI로 통합합니다.**

#### 기능

| 기능 | 역할 | 설명 |
|------|------|------|
| 세션 캘린더 | 멘티/멘토 | 월간/주간 캘린더 뷰로 세션 일정 확인 |
| 세션 예약 | 멘티 | 멘토 가용 시간 기반 세션 예약 (기존 API 활용) |
| 세션 기록 작성 | 멘토/멘티 | 세션 후 요약, 피드백, 다음 목표 기록 |
| 출석 관리 | 자동 | 세션 COMPLETED → 출석, CANCELLED → 결석 처리 |
| 화상 회의 참여 | 멘티/멘토 | Google Meet 링크로 바로 참여 |

#### 세션 기록 데이터 모델 (신규)

```
SessionNote {
  id: Long (PK)
  sessionId: Long (FK → MentoringSession)
  authorId: Long (FK → User)          // 작성자 (멘토 또는 멘티)
  summary: String                      // 세션 요약
  mentorFeedback: String               // 멘토 피드백 (멘토만 작성)
  nextGoals: String                    // 다음 세션까지 목표
  menteeSelfReview: String             // 멘티 자기 평가 (멘티만 작성)
  rating: Integer                      // 세션 만족도 (1~5, 멘티만)
  createdAt: LocalDateTime
}
```

#### 출석률 계산

```
출석률 = (COMPLETED 세션 수 / 전체 세션 수) × 100

전체 세션 = COMPLETED + CANCELLED (SCHEDULED 제외)
```

---

### 3.4 과제 / 산출물 관리 (`/lms/assignments`)

> **F-Lab 참고:** F-Lab은 프로젝트 기반 학습을 강조하며, 멘토가 과제를 통해 실력을 검증합니다.

#### 기능

| 기능 | 역할 | 설명 |
|------|------|------|
| 과제 출제 | 멘토 | 과제 제목, 설명, 마감일, 참고자료 설정 |
| 과제 제출 | 멘티 | GitHub 링크 또는 파일 업로드로 제출 |
| 과제 피드백 | 멘토 | 과제에 대한 피드백 작성 + 점수/등급 |
| 과제 목록 | 멘티/멘토 | 전체 과제 목록 + 상태 필터 |
| 산출물 아카이브 | 멘티 | 전체 과제/프로젝트 결과물 모아보기 |

#### 데이터 모델

```
Assignment {
  id: Long (PK)
  matchingId: Long (FK → Matching)
  mentorId: Long (FK → User)
  title: String                        // "Spring Security JWT 구현"
  description: String                  // 과제 상세 설명
  dueDate: LocalDate                   // 마감일
  referenceUrls: List<String>          // 참고 자료 URL
  status: AssignmentStatus             // ASSIGNED / SUBMITTED / REVIEWED
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}

AssignmentSubmission {
  id: Long (PK)
  assignmentId: Long (FK → Assignment)
  menteeId: Long (FK → User)
  submissionUrl: String                // GitHub PR/Repo 링크
  submissionNote: String               // 제출 시 코멘트
  submittedAt: LocalDateTime

  // 멘토 피드백
  feedbackContent: String              // 피드백 내용
  grade: String                        // "A" / "B" / "C" 또는 "PASS" / "FAIL"
  feedbackAt: LocalDateTime
}

AssignmentStatus: ASSIGNED → SUBMITTED → REVIEWED
```

---

### 3.5 코드 리뷰 (`/lms/code-review`) — GitHub 연동

> **사용자 제안:** GitHub 기반 코드 리뷰
> **F-Lab 참고:** F-Lab은 코드 리뷰와 피드백을 핵심 멘토링 방식으로 활용합니다.

#### 기능

| 기능 | 역할 | 설명 |
|------|------|------|
| GitHub 연결 | 멘티 | 본인 GitHub 계정 연동 (OAuth) |
| 리뷰 대상 PR 등록 | 멘티 | 리뷰 받을 PR URL 등록 |
| PR 목록 조회 | 멘티/멘토 | 등록된 PR 목록 + 리뷰 상태 |
| 리뷰 통계 | 멘티/멘토 | 총 PR 수, 리뷰 완료율, 평균 코멘트 수 |
| GitHub 바로가기 | 멘티/멘토 | 클릭 시 해당 PR 페이지로 이동 |

#### 데이터 모델

```
CodeReview {
  id: Long (PK)
  matchingId: Long (FK → Matching)
  menteeId: Long (FK → User)
  prUrl: String                        // "https://github.com/user/repo/pull/1"
  repoName: String                     // "user/repo"
  prTitle: String                      // PR 제목
  prNumber: Integer                    // PR 번호
  status: ReviewStatus                 // PENDING / IN_REVIEW / REVIEWED
  reviewCommentCount: Integer          // 리뷰 코멘트 수
  submittedAt: LocalDateTime
  reviewedAt: LocalDateTime
}

ReviewStatus: PENDING → IN_REVIEW → REVIEWED
```

#### 연동 방식

```
옵션 A (MVP): 수동 등록
  멘티가 PR URL을 직접 입력 → 멘토가 GitHub에서 리뷰 → 멘티가 상태 업데이트

옵션 B (고도화): GitHub Webhook
  GitHub Webhook으로 PR 이벤트 수신 → 자동 PR 목록 갱신
  → 리뷰 코멘트 수 자동 카운트
```

---

### 3.6 학습 일지 / 회고록 (`/lms/journal`)

> **F-Lab 참고:** F-Lab은 기술 블로그 작성을 권장하며, 학습 내용을 정리하는 습관을 강조합니다.

#### 기능

| 기능 | 역할 | 설명 |
|------|------|------|
| 주간 학습 일지 작성 | 멘티 | 이번 주 학습 내용, 어려웠던 점, 목표 달성 여부 |
| 일지 조회 | 멘토 | 멘티의 학습 일지 확인 + 코멘트 |
| 일지 코멘트 | 멘토 | 멘티 일지에 피드백/격려 작성 |
| 일지 목록 | 멘티/멘토 | 주차별 학습 일지 목록 |

#### 데이터 모델

```
LearningJournal {
  id: Long (PK)
  matchingId: Long (FK → Matching)
  menteeId: Long (FK → User)
  weekNumber: Integer                  // 몇 주차
  weekStartDate: LocalDate             // 해당 주 시작일
  title: String                        // "4월 1주차 회고"
  whatILearned: String                 // 이번 주 배운 것
  difficulties: String                 // 어려웠던 점
  nextWeekGoal: String                 // 다음 주 목표
  selfRating: Integer                  // 자기 평가 (1~5)
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}

JournalComment {
  id: Long (PK)
  journalId: Long (FK → LearningJournal)
  authorId: Long (FK → User)          // 멘토
  content: String
  createdAt: LocalDateTime
}
```

---

### 3.7 소통 도구 연동 (`/lms/communication`)

> **사용자 제안:** Slack (실시간 소통), Zoom (화상 회의)

#### 3.7.1 Slack 연동

| 기능 | 설명 |
|------|------|
| Slack 워크스페이스 링크 | 멘토-멘티 전용 Slack 채널 바로가기 |
| 채널 자동 생성 | 매칭 완료 시 `#mentoring-{멘티이름}-{멘토이름}` 채널 자동 생성 |
| 알림 연동 | 과제 마감, 세션 리마인더 등을 Slack으로 알림 |

#### 3.7.2 화상 회의

| 옵션 | 장점 | 단점 | 권장 |
|------|------|------|------|
| **Google Meet** | Phase 4에서 이미 연동 완료, 추가 비용 없음 | 녹화 기능 유료 | **MVP 권장** |
| Zoom | 녹화 기능 우수, 브레이크아웃 룸 | 별도 API 연동 필요, 유료 플랜 | 고도화 시 |
| Discord | 무료, 화면 공유 | 기업용 이미지 약함 | 대안 |

**권장:** Phase 4에서 구현한 Google Meet을 기본으로 사용하고, 추후 필요 시 Zoom 연동을 추가합니다.

#### 연동 UI

```
┌─────────────────────────────────────────────────────┐
│  소통 채널                                           │
├─────────────────────────────────────────────────────┤
│                                                     │
│  💬 Slack                                           │
│  ┌─────────────────────────────────────────┐       │
│  │ #mentoring-김철수-홍길동                   │       │
│  │ [Slack 채널 열기 →]                       │       │
│  └─────────────────────────────────────────┘       │
│                                                     │
│  📹 화상 회의 (Google Meet)                          │
│  ┌─────────────────────────────────────────┐       │
│  │ 다음 세션: 2026-04-10 19:00              │       │
│  │ [Meet 참여하기 →]                        │       │
│  └─────────────────────────────────────────┘       │
│                                                     │
│  📧 멘토 연락처                                      │
│  ┌─────────────────────────────────────────┐       │
│  │ 이메일: mentor@example.com               │       │
│  │ 응답 가능 시간: 평일 18:00~22:00          │       │
│  └─────────────────────────────────────────┘       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

### 3.8 취업 지원 (`/lms/career`) — F-Lab 참고 추가 기능

> **F-Lab 참고:** F-Lab은 이력서 피드백, 모의 면접, 어필 전략 수립 등 취업 준비를 핵심 서비스로 제공합니다.

#### 기능

| 기능 | 역할 | 설명 |
|------|------|------|
| 이력서 업로드/관리 | 멘티 | 이력서 파일 업로드 (PDF), 버전 관리 |
| 이력서 피드백 | 멘토 | 이력서에 대한 상세 피드백 작성 |
| 모의 면접 기록 | 멘토/멘티 | 모의 면접 질문/답변 기록 + 피드백 |
| 포트폴리오 관리 | 멘티 | 프로젝트, GitHub, 블로그 링크 모아보기 |
| 취업 활동 트래킹 | 멘티 | 지원 회사, 면접 일정, 결과 기록 |

#### 데이터 모델

```
Resume {
  id: Long (PK)
  menteeId: Long (FK → User)
  version: Integer                     // 버전 (1, 2, 3...)
  fileUrl: String                      // 업로드된 파일 URL
  fileName: String                     // 원본 파일명
  mentorFeedback: String               // 멘토 피드백
  feedbackAt: LocalDateTime
  uploadedAt: LocalDateTime
}

MockInterview {
  id: Long (PK)
  matchingId: Long (FK → Matching)
  interviewDate: LocalDate
  topic: String                        // "Java 기술 면접" / "시스템 설계"
  questions: List<String>              // 질문 목록
  mentorFeedback: String               // 종합 피드백
  rating: Integer                      // 수행 점수 (1~5)
  createdAt: LocalDateTime
}
```

---

### 3.9 수료증 (`/lms/certificate`)

#### 기능

| 기능 | 조건 | 설명 |
|------|------|------|
| 수료증 발급 | 진도율 80% 이상 + 출석률 80% 이상 | PDF 형식 수료증 자동 생성 |
| 수료증 조회/다운로드 | 수료 완료 | 수료증 PDF 다운로드 |

#### 수료 조건

```
수료 판정 기준:
  1. 커리큘럼 진도율 ≥ 80%
  2. 멘토링 세션 출석률 ≥ 80%
  3. 필수 과제 제출률 ≥ 70%
  4. 멘토 최종 승인
```

---

## 4. F-Lab 참고 추가 기능 요약

아래는 F-Lab의 멘토링 모델을 분석하여 DevMatch에 추가를 제안하는 기능입니다.

### 사용자 기존 제안 vs 추가 제안 비교

| # | 기능 | 사용자 제안 | F-Lab 참고 추가 | 우선순위 | 근거 |
|---|------|:---------:|:-------------:|:-------:|------|
| 1 | 진도율 | O | - | **MVP** | 학습 동기부여 핵심 지표 |
| 2 | 출석률 | O | - | **MVP** | 멘토링 참여도 측정 |
| 3 | 산출물 관리 | O | - | **MVP** | 과제 제출/피드백 필수 |
| 4 | 일정 관리 | O | - | **MVP** | Phase 4 캘린더 연동 활용 |
| 5 | 코드 리뷰 (GitHub) | O | - | **MVP** | 개발 멘토링 핵심 |
| 6 | 실시간 소통 (Slack) | O | - | **MVP** | 비동기 커뮤니케이션 |
| 7 | 화상 회의 | O | Google Meet 활용 | **MVP** | Phase 4 Meet 연동 재활용 |
| 8 | 커리큘럼/로드맵 | - | **O** | **MVP** | 체계적 학습 가이드, F-Lab 핵심 |
| 9 | 학습 대시보드 | - | **O** | **MVP** | 학습 현황 한눈에 파악 |
| 10 | 세션 기록/피드백 | - | **O** | **2차** | 멘토링 품질 관리 |
| 11 | 학습 일지/회고록 | - | **O** | **2차** | 자기주도 학습 습관, F-Lab 블로그 문화 |
| 12 | 이력서 피드백 | - | **O** | **2차** | F-Lab 취업 지원 핵심 기능 |
| 13 | 모의 면접 | - | **O** | **2차** | F-Lab 취업 지원 핵심 기능 |
| 14 | 멘토 피드백 리포트 | - | **O** | **3차** | 정기적 역량 평가 |
| 15 | 코드 리뷰 통계 | - | **O** | **3차** | 리뷰 활동 데이터 시각화 |
| 16 | 수료증 발급 | - | **O** | **3차** | 과정 완료 인증 |
| 17 | 포트폴리오 관리 | - | **O** | **3차** | 취업 준비 종합 관리 |
| 18 | Slack 채널 자동생성 | - | **O** | **3차** | Slack API 연동 고도화 |

---

## 5. 기존 Phase 4/5 코드 재활용 매핑

| LMS 기능 | 기존 코드 | 활용 방식 |
|----------|----------|-----------|
| 세션 일정 | `MentoringSession` (Phase 4) | 세션 캘린더 데이터 소스 |
| 화상 회의 | `GoogleCalendarService` (Phase 4) | Meet 링크 자동 생성 |
| 멘토 가용시간 | `MentorAvailability` (Phase 4) | 세션 예약 시 시간 선택 |
| 출석률 | `SessionStatus` (Phase 4) | COMPLETED/CANCELLED 기반 계산 |
| 결제 확인 | `Payment` (Phase 5) | LMS 접근 권한 확인 |
| 커뮤니티 | `Post` / `Comment` (Phase 5) | 학습 커뮤니티 확장 가능 |

---

## 6. API 엔드포인트 설계

### LMS 메인

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| GET | `/api/lms/dashboard` | O | 대시보드 종합 데이터 |
| GET | `/api/lms/enrollment` | O | 내 수강 정보 (멘토, 기간, 상태) |

### 커리큘럼

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | `/api/lms/curriculum` | O (멘토) | 커리큘럼 생성 |
| GET | `/api/lms/curriculum/{matchingId}` | O | 커리큘럼 조회 |
| PUT | `/api/lms/curriculum/{id}` | O (멘토) | 커리큘럼 수정 |
| PUT | `/api/lms/curriculum/weeks/{weekId}/complete` | O (멘티) | 주차 완료 체크 |

### 과제

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | `/api/lms/assignments` | O (멘토) | 과제 출제 |
| GET | `/api/lms/assignments` | O | 내 과제 목록 |
| GET | `/api/lms/assignments/{id}` | O | 과제 상세 |
| POST | `/api/lms/assignments/{id}/submit` | O (멘티) | 과제 제출 |
| POST | `/api/lms/assignments/{id}/feedback` | O (멘토) | 과제 피드백 |

### 코드 리뷰

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | `/api/lms/code-reviews` | O (멘티) | PR 등록 |
| GET | `/api/lms/code-reviews` | O | PR 목록 |
| PUT | `/api/lms/code-reviews/{id}/status` | O (멘토) | 리뷰 상태 변경 |

### 학습 일지

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | `/api/lms/journals` | O (멘티) | 일지 작성 |
| GET | `/api/lms/journals` | O | 일지 목록 |
| GET | `/api/lms/journals/{id}` | O | 일지 상세 |
| POST | `/api/lms/journals/{id}/comments` | O (멘토) | 일지 코멘트 |

### 취업 지원

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | `/api/lms/resumes` | O (멘티) | 이력서 업로드 |
| GET | `/api/lms/resumes` | O | 이력서 목록 |
| POST | `/api/lms/resumes/{id}/feedback` | O (멘토) | 이력서 피드백 |
| POST | `/api/lms/mock-interviews` | O (멘토) | 모의 면접 기록 |
| GET | `/api/lms/mock-interviews` | O | 모의 면접 목록 |

### 수료증

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| GET | `/api/lms/certificate/eligibility` | O | 수료 자격 확인 |
| POST | `/api/lms/certificate/issue` | O (멘토) | 수료증 발급 |
| GET | `/api/lms/certificate/{id}/download` | O | 수료증 다운로드 |

---

## 7. 구현 우선순위

### MVP (Phase 6-1)

- [ ] LMS 접근 권한 체크 (매칭 완료 + 결제 확인)
- [ ] 학습 대시보드 UI + API
- [ ] 커리큘럼 CRUD + 진도율 계산
- [ ] 과제 출제/제출/피드백
- [ ] 코드 리뷰 PR 등록 (수동)
- [ ] 기존 세션 관리 LMS UI 통합
- [ ] Slack/Meet 바로가기 링크

### 2차 (Phase 6-2)

- [ ] 학습 일지/회고록
- [ ] 세션 기록/피드백 노트
- [ ] 이력서 업로드/피드백
- [ ] 모의 면접 기록
- [ ] 출석률 자동 계산 + 통계

### 3차 (Phase 6-3)

- [ ] 수료증 발급 (PDF 생성)
- [ ] GitHub Webhook 연동 (PR 자동 감지)
- [ ] Slack 채널 자동 생성 (Slack API)
- [ ] 코드 리뷰 통계 대시보드
- [ ] 포트폴리오 종합 관리
- [ ] 멘토 피드백 리포트 (월간)

---

## 8. 팀 논의 필요 사항

1. **화상 회의 도구:** Google Meet (기존 연동)을 유지할지, Zoom을 추가 연동할지
2. **Slack 연동 범위:** MVP에서는 링크만 제공하고, 3차에서 API 연동할지
3. **GitHub 연동 방식:** OAuth 연동으로 PR 자동 감지할지, 수동 URL 입력으로 시작할지
4. **커리큘럼 템플릿:** 코스별 기본 커리큘럼 템플릿을 제공할지, 멘토가 자유롭게 구성할지
5. **수료 기준:** 진도율/출석률 기준을 어떻게 설정할지 (80%? 70%?)
6. **이력서 관리:** 파일 스토리지를 어디에 둘지 (S3, 로컬 등)
7. **LMS URL 구조:** `/lms` 하위에 둘지, 별도 서브도메인(lms.devmatch.com)으로 분리할지

---

## 9. 기술 스택 참고 (무료 구성)

| 영역 | 기술 | 비용 | 비고 |
|------|------|------|------|
| Frontend | Next.js + Tailwind CSS | 무료 | 기존 프로젝트 스택 유지 |
| Backend | Spring Boot + JPA | 무료 | 기존 프로젝트 스택 유지 |
| 화상 회의 | **Jitsi Meet** (meet.jit.si) | 무료 | API 키 불필요, iframe 임베딩 가능 |
| 일정 관리 | Google Calendar API | 무료 | Phase 4 코드 유지 (스텁 모드 대응) |
| 실시간 소통 | **Discord** | 무료 | Slack 무료 플랜 90일 제한 회피 |
| 코드 리뷰 | GitHub API (REST) | 무료 | MVP: 수동 URL 등록, 고도화: Webhook |
| 파일 스토리지 | **Cloudflare R2** (10GB) 또는 로컬 | 무료 | MVP: 로컬 → 배포 시 R2 이전 |
| PDF 생성 | **OpenPDF** (LGPL) 또는 Apache PDFBox | 무료 | 수료증 PDF 생성 |
| 캘린더 UI | FullCalendar (React) | 무료 (MIT) | 세션 캘린더 뷰 |
| 배포 (Frontend) | Vercel | 무료 | Git push 자동 배포 |
| 배포 (Backend) | Railway / Render | 무료 플랜 | Docker 컨테이너 배포 |

> 상세 비교: [video-conferencing-analysis.md](video-conferencing-analysis.md)

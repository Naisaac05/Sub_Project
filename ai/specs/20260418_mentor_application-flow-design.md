---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "멘토 등록 신청서 + 멘토링 코스 DB 승격 설계 상세 요구사항 및 기능 동작 명세서"

---

# 멘토 등록 신청서 + 멘토링 코스 DB 승격 설계

- 작성일: 2026-04-18
- 서브시스템: #1 (멘토 신청서 + 멘토 쪽 PENDING UX)
- 관련 후속 서브시스템: #0 어드민 토대, #2 멘토 승인 관리, #3 회원 관리, #4 커뮤니티 게시글 관리, #5 결제/환불 관리

## 배경

### 해결하려는 문제

1. **멘토 역할 가입 흐름이 불완전함.** 회원가입에서 MENTOR 를 선택해도 멘토링을 시작하기 위해 필요한 전문 프로필 정보(경력·분야·포트폴리오 등)를 입력할 경로가 없음. 멘토 신청 백엔드 API (`POST /api/mentor/apply`) 는 이미 존재하지만 대응되는 프론트엔드 페이지가 없음.
2. **멘토 승인 전 UX 부재.** 승인 전 멘토의 헤더 표시, 메뉴 차단, 상태 확인 페이지가 없음. 검증되지 않은 멘토가 LMS·매칭 등 멘토 전용 기능에 노출될 위험이 있음.
3. **멘토링 코스 카탈로그가 프론트엔드에 하드코딩되어 있음.** `frontend/src/app/mentors/[id]/page.tsx` 의 `COURSE_DATA` 상수(12개 코스)가 유일한 출처. 멘토 신청서가 이 목록과 연동되려면 공용 데이터 소스가 필요하며, 장기적으로 관리자가 코스를 관리하려면 DB 엔티티화가 선행되어야 함.

### 이번 스펙의 범위

- MENTOR 로 회원가입한 사용자가 로그인 후 자동으로 신청서 페이지로 유도되는 흐름
- 멘토 신청서 페이지 (`/mentor/apply`) — 10개 필드, 코스 다중 선택 + 자유 태그 세부 기술 스택
- 멘토 상태 확인 페이지 (`/mentor/status`) — PENDING / REJECTED / APPROVED 상태별 UI
- 백엔드: `MentorProfile` 엔티티 확장, `MentorProfileHistory` 신규 엔티티, `MentoringCourse` 신규 엔티티 및 12개 시드
- 멘토 쪽 상태별 UX: 헤더 뱃지, 라우트 가드, REJECTED 재신청 흐름

### 비범위 (후속 서브시스템으로 이관)

- **관리자 승인 UI** — 서브시스템 #2 에서 구현. 이번 스펙은 PENDING 상태 생성까지. 승인 전환은 임시로 DB 수동 UPDATE.
- **관리자 코스 CRUD UI** — 후속 서브시스템에서. 이번엔 DB 시드만 제공.
- **회원가입 시 MENTOR 역할이 DB 에 저장되는 백엔드 재빌드 이슈** — 설계 대상이 아닌 운영 조치. 전제조건으로 가정 (`error/2026-04-18-signup-role-always-mentee-stale-build.md` 참고).
- **멘토 프로필을 멘티에게 공개하는 `/mentors/{id}` 상세 페이지** — 이미 존재하는 페이지의 데이터 소스를 API 로 교체하는 범위까지만 포함, 디자인/기능 개선은 범위 밖.

## 설계

### 데이터 모델

#### 신규 엔티티 — `MentoringCourse`

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | 내부 FK |
| `courseKey` | `VARCHAR(50)` | UNIQUE NOT NULL | 외부 식별자. 예: `java-backend`, `kafka` |
| `title` | `VARCHAR(200)` | NOT NULL | "AI+ Java 백엔드" |
| `subtitle` | `VARCHAR(500)` | NULL | |
| `iconString` | `VARCHAR(10)` | NULL | 이모지 등 |
| `descriptionTitle` | `TEXT` | NULL | |
| `descriptionText` | `TEXT` | NULL | |
| `boxesJson` | `TEXT` | NULL | 기존 `boxes` 배열을 JSON 직렬화 |
| `displayOrder` | `INT` | DEFAULT 0 | 목록 정렬 |
| `active` | `BOOLEAN` | DEFAULT TRUE | 소프트 삭제 |
| `createdAt`, `updatedAt` | `DATETIME` | `@EntityListeners(AuditingEntityListener)` | |

#### `MentorProfile` 변경

기존 필드에 다음 변경:
- `specialty: List<String>` **제거**
- `courses: Set<MentoringCourse>` **추가** — `@ManyToMany`, junction table `mentor_profile_courses(mentor_profile_id, course_id)`
- `techStack: List<String>` **추가** — `@Convert(converter = StringListConverter.class)`, 자유 태그 세부 기술 스택
- `jobTitle: String` **추가** — `VARCHAR(100) NULL`, 현재 직무
- `portfolioUrl: String` **추가** — `VARCHAR(500) NULL`
- `education: String` **추가** — `VARCHAR(200) NULL`
- `certifications: List<String>` **추가** — `@Convert`, 자격증 태그
- `preferredMenteeLevel: String` **추가** — `VARCHAR(20) NULL`, 값: `BEGINNER | INTERMEDIATE | ADVANCED | ANY`

기존 `company`, `bio`, `careerYears`, `status`, `user`, `createdAt`, `updatedAt` 는 유지.

**역할**: 멘토의 **최신/활성 프로필**. 멘티 추천·매칭은 이 테이블만 참조.

#### 신규 엔티티 — `MentorProfileHistory`

매 제출 및 심사 결정마다 한 행씩 insert. 스키마:

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK` | |
| `userId` | `BIGINT FK(users)` | `@ManyToOne`, 한 유저에 여러 이력 허용 |
| `courseKeys` | `TEXT` (via `StringListConverter`) | 제출 당시 선택 코스 키 배열 (FK 아님 — 코스 삭제 대비 스냅샷) |
| `techStack` | `TEXT` | |
| `careerYears`, `company`, `jobTitle`, `portfolioUrl`, `education`, `bio`, `preferredMenteeLevel` | (해당 타입) | 모두 스냅샷 |
| `certifications` | `TEXT` | |
| `status` | `ENUM(PENDING, APPROVED, REJECTED)` | 제출 시 PENDING. 심사 후 해당 행을 업데이트. |
| `rejectedReason` | `VARCHAR(500) NULL` | REJECTED 전환 시 입력 |
| `submittedAt` | `DATETIME NOT NULL` | |
| `reviewedAt` | `DATETIME NULL` | |
| `reviewedBy` | `BIGINT NULL FK(users)` | 관리자 userId (#2 에서 채움) |

#### 시드 데이터 — `DataInitializer` 확장

서버 최초 구동 시 `mentoring_courses` 테이블이 비어있으면 현재 `COURSE_DATA` 의 12개 코스를 insert:
`java-backend`, `node-backend`, `python-backend`, `frontend`, `android`, `ios`, `flutter`, `react-native`, `devops`, `firststep`, `distributed-lock`, `kafka`

멱등: 기존 행이 있으면 건너뜀.

#### DDL 적용 방식

`spring.jpa.hibernate.ddl-auto=update` 사용 중. 재시작 시 자동으로 컬럼/테이블 추가됨. 학생 프로젝트 범위에선 별도 마이그레이션 스크립트 불필요.

### API 엔드포인트

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| `GET` | `/api/courses` | public | 활성(`active=true`) 코스 목록. `displayOrder` 정렬 |
| `GET` | `/api/courses/{courseKey}` | public | 단일 코스 상세 (`/mentors/[id]` 에서 사용) |
| `POST` | `/api/mentor/apply` | 로그인 | 멘토 신청/재신청. body 에 `courseKeys: List<String>` 포함 (기존 `specialty` 대체) |
| `GET` | `/api/mentor/me` | 로그인 + MENTOR | 응답에 `rejectedReason` 필드 추가 |

#### Response DTO — `MentorProfileResponse` 변경

`GET /api/mentor/me` 응답에 반영:
- `specialty: List<String>` **제거**
- `courses: List<CourseSummary>` **추가** — `{courseKey, title, iconString}` 최소 정보
- `techStack: List<String>` **추가**
- `jobTitle, portfolioUrl, education, preferredMenteeLevel` **추가**
- `certifications: List<String>` **추가**
- `rejectedReason: String` **추가** — 최신 `MentorProfileHistory` 에서 조회 (REJECTED 일 때만 non-null)

#### `POST /api/mentor/apply` 동작 분기

- `MentorProfile` 없음 → insert + `MentorProfileHistory` insert (둘 다 PENDING)
- `MentorProfile` 존재 + `status=REJECTED` → `MentorProfile` 필드 덮어쓰기 + `status=PENDING` 으로 복귀 + 새 `MentorProfileHistory` insert
- `MentorProfile` 존재 + `status=PENDING` → `409 Conflict` 반환 (중복 신청)
- `MentorProfile` 존재 + `status=APPROVED` → `409 Conflict` 반환 (이미 승인됨)

#### Request DTO — `MentorApplyRequest` 변경

| 필드 | 타입 | 제약 |
|---|---|---|
| `courseKeys` | `List<String>` | `@NotEmpty` 최소 1개 |
| `careerYears` | `Integer` | `@NotNull @Min(1)` |
| `company` | `String` | 선택, `@Size(max=100)` |
| `jobTitle` | `String` | 선택, `@Size(max=100)` |
| `portfolioUrl` | `String` | 선택, `@Size(max=500)` |
| `education` | `String` | 선택, `@Size(max=200)` |
| `certifications` | `List<String>` | 선택 |
| `techStack` | `List<String>` | 선택 |
| `preferredMenteeLevel` | `String` | 선택, enum 검증 |
| `bio` | `String` | 선택, `@Size(max=1000)` |

### 가입 후 흐름 & 라우팅

```
[회원가입]
  role=MENTOR → /auth/login?signup=success

[로그인 성공]
  AuthContext 가 user.role 파악 (+ 필요 시 /api/mentor/me 호출)
  → role=MENTOR & 프로필 없음/REJECTED  → /mentor/apply
  → role=MENTOR & PENDING               → /mentor/status
  → role=MENTOR & APPROVED              → /
  → role=MENTEE/ADMIN                   → 기존 흐름

[/mentor/apply]
  제출 성공 → /mentor/status (PENDING 안내)

[/mentor/status]
  GET /api/mentor/me
  → PENDING   : 승인 대기 카드 + 제출 요약
  → REJECTED  : 반려 사유 + "수정 후 재신청" → /mentor/apply (prefill)
  → APPROVED  : 승인 완료 안내 + 홈 이동
```

#### 신규 프론트엔드 라우트

| 경로 | 접근 조건 |
|---|---|
| `/mentor/apply` | 로그인 + role=MENTOR + (프로필 없음 \|\| status=REJECTED) |
| `/mentor/status` | 로그인 + role=MENTOR + 프로필 있음 |

조건 불충족 시:
- 비로그인 → `/auth/login?redirect=...`
- role=MENTEE 가 `/mentor/apply` 접근 → `/` + toast "멘토 계정만 접근 가능합니다"
- status=PENDING 이 `/mentor/apply` 접근 → `/mentor/status`
- status=APPROVED 가 `/mentor/apply` 접근 → `/mentor/status`

### 신청서 폼 UI (`/mentor/apply`)

단일 스크롤 페이지 + 섹션 구분. 기존 `/apply` 페이지 패턴 준수.

```
[상단 안내]  "검토 후 승인까지 영업일 기준 2-3일 소요됩니다"

[섹션 A — 기본 정보]
  • jobTitle  : Input
  • company   : Input
  • education : Input

[섹션 B — 멘토링 코스 & 경력]
  • courseKeys * : Checkbox grid (GET /api/courses 에서 렌더링, 최소 1개 필수)
  • techStack    : 태그 입력 (Enter 로 추가)
  • careerYears *: Input type=number (min=1)
  • preferredMenteeLevel : RadioGroup (입문/중급/고급/무관)

[섹션 C — 포트폴리오 & 자격증]
  • portfolioUrl  : Input type=url
  • certifications: 태그 입력

[섹션 D — 자기소개]
  • bio : Textarea (최대 1000자, 글자수 표시)

[하단]
  [ 취소 ]    [ 신청서 제출 ]
```

#### shadcn 컴포넌트 매핑 (구현 참고)

| UI 요소 | 컴포넌트 |
|---|---|
| 섹션 | `Card` + `CardHeader`/`CardContent` |
| 텍스트 입력 | `Input`, `Textarea` |
| 코스 체크박스 | `Checkbox` (grid) |
| 태그 입력 | `Badge` + `Input` 커스텀 조합 (기존 프로젝트 유사 컴포넌트 있는지 구현 단계 초반 확인) |
| 선호 멘티 수준 | `RadioGroup` |
| 버튼 | `Button` (primary / outline) |

**프론트 구현 직전 Pencil MCP 로 섹션별 목업을 만들어 사용자 확인을 받는다.** (프로젝트 메모리 `feedback_frontend_preview.md` 규칙)

#### 유효성 검증 (프론트)

- `react-hook-form` + `zod` 스키마 (프로젝트 기존 패턴 따라감 — 구현 초기에 점검)
- 필수 미입력 → 해당 섹션으로 스크롤 + 에러 메시지
- 제출 중 `Button disabled` + spinner
- REJECTED 재신청 진입 시 `GET /api/mentor/me` 로 이전 값 prefill + 상단 배너에 반려 사유 노출

### 상태별 UX

#### 접근 권한 매트릭스

| 기능 | 없음 | PENDING | REJECTED | APPROVED |
|---|---|---|---|---|
| `/mentor/apply` | ✅ | ❌ → `/mentor/status` | ✅ (prefill) | ❌ → `/mentor/status` |
| `/mentor/status` | ❌ → `/mentor/apply` | ✅ | ✅ | ✅ |
| `/lms/assignments` | ❌ | ❌ | ❌ | ✅ |
| `/lms/(dashboard)/*` | ❌ | ❌ | ❌ | ✅ |
| `/matching` (멘토 수락 기능) | ❌ | ❌ | ❌ | ✅ |
| `/mypage`, `/community`, 공개 페이지 | ✅ | ✅ | ✅ | ✅ |

차단 경로 직접 접근 시 → `/mentor/status` 로 리다이렉트 + toast "멘토 승인 후 이용 가능합니다".

#### 헤더 변경 (`Header.tsx`)

`user.role === 'MENTOR'` 분기에 `mentorStatus` 기준 서브라벨 추가:
- PENDING → amber 뱃지 "MENTOR · 승인 대기중"
- REJECTED → red 뱃지 "MENTOR · 반려됨"
- APPROVED → 기본 "멘토" 표기만
- 멘토 전용 메뉴(`LMS 배정 목록`, `매칭 관리` 등)는 APPROVED 에서만 렌더

#### `/mentor/status` 콘텐츠

**PENDING**: "승인 대기 중" 카드 + 제출 내용 요약 (코스·경력·회사·학력 등)

**REJECTED**: "반려되었습니다" 카드 + `rejectedReason` 인용 + 이전 제출 요약 + "수정 후 재신청" 버튼 (`/mentor/apply`)

**APPROVED**: "승인 완료" 카드 + "홈으로" · "멘토 대시보드" 버튼

### 프론트엔드 코드 정리

- `frontend/src/app/mentors/[id]/page.tsx` 의 `COURSE_DATA` 삭제 → `GET /api/courses/{courseKey}` 호출로 교체
- 신규 `frontend/src/lib/courses.ts`:
  - `export interface MentoringCourse { courseKey, title, subtitle, iconString, descriptionTitle, descriptionText, boxes, displayOrder, active }`
  - `export async function fetchCourses(): Promise<MentoringCourse[]>`
  - `export async function fetchCourse(key: string): Promise<MentoringCourse>`
- `frontend/src/app/apply/page.tsx` 의 `category: 'backend'` 하드코딩도 `fetchCourses()` 기반 드롭다운으로 교체 (범위 내 변경)
- `/api/courses` 응답은 `boxesJson` 문자열을 서버에서 파싱해 `boxes: object[]` 로 직렬화하여 반환

### 에러/엣지 케이스

- 로그인 직후 `GET /api/mentor/me` 실패(네트워크) → 홈으로 fallback + toast "상태 조회 실패, 다시 로그인해주세요"
- 제출 시 400 (validation) → 폼 상단 에러 배너 + 필드별 메시지
- 제출 시 409 (중복/이미 승인) → toast 후 `/mentor/status` 로 강제 이동
- `courseKeys` 에 존재하지 않는 키 포함 → 백엔드 400 "존재하지 않는 코스"
- PENDING 상태로 서버가 잠시 죽었다가 살아나도 `/mentor/status` 는 항상 최신 상태 재조회

### 테스트 범위

플랜 작성 시 상세화할 항목:
- **백엔드 유닛**: `MentorService.apply()` 4가지 분기 (신규/REJECTED 재신청/PENDING 중복/APPROVED 중복)
- **백엔드 유닛**: `MentorProfileHistory` insert 검증, 반려 후 재신청 시 새 이력 행 생성 확인
- **백엔드 유닛**: `CourseService.findAllActive()` 정렬/활성 필터
- **백엔드 유닛**: `DataInitializer` 멱등성 (재실행 시 중복 insert 없음)
- **프론트 유닛**: `/mentor/apply` 폼 유효성, `courseKeys` 최소 1개 강제, `/mentor/status` 3개 상태 렌더
- **프론트 유닛**: 라우트 가드 (role/status 조합별 리다이렉트)
- **E2E 수동**: 회원가입 → 로그인 → 자동 유도 → 제출 → status 확인 → (DB 수동 REJECTED + rejectedReason 세팅) → 재진입 시 사유 노출 → 재신청 → 재제출 성공

## 마이그레이션 / 후속 작업

- 이번 스펙이 머지된 뒤 기존 DB 에 남아있는 `mentor_profiles.specialty` 컬럼(있다면) 은 `ddl-auto=update` 로 자동 삭제되지 않음. 운영 환경이라면 별도 DROP 마이그레이션 필요. 학생 프로젝트에서는 `ddl-auto=create-drop` 으로 1회 리셋하거나 수동 SQL 실행.
- 기존 `mentor_profiles` 에 저장된 데이터가 있다면 `specialty` 문자열 → `mentor_profile_courses` 행으로 변환하는 데이터 마이그레이션 스크립트 필요. 스케일이 작으면 수동 SQL.
- 관리자 코스 관리 UI 와 승인 UI 는 서브시스템 #2 및 후속에서 추가.

## 참고

- 에러 로그: `error/2026-04-18-signup-role-always-mentee-stale-build.md` — 회원가입 role 저장 이슈 (본 스펙 전제조건)
- 프론트 기존 코스 데이터 원본: `frontend/src/app/mentors/[id]/page.tsx` `COURSE_DATA`
- 기존 신청서 패턴 참고: `frontend/src/app/apply/page.tsx` (멘티용)

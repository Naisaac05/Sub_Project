---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "LMS 마이페이지 진입 + 실사용 가능화 설계 상세 요구사항 및 기능 동작 명세서"

---

# LMS 마이페이지 진입 + 실사용 가능화 설계

## 1. 개요

멘티와 멘토가 매칭되면 마이페이지에서 LMS 페이지로 진입할 수 있도록 하고, 현재 조회만 가능한 LMS 페이지들에 CRUD 기능을 추가하여 실제 사용 가능한 상태로 만든다.

### 범위
- 마이페이지 매칭 카드에 LMS 진입 버튼 추가
- LMS 7개 페이지 중 5개에 CRUD UI 추가 (대시보드, 수료증은 변경 없음)
- 세션 페이지 백엔드 matchingId 기반 필터링 수정
- 캘린더 기반 세션 예약 시스템 (멘토 가용시간 등록 → 멘티 선택 → 변경 요청/승인)
- 현실적 수준의 시드 데이터 투입
- Jitsi 화상회의 연동은 제외

### 대상 사용자
- 멘티 (가나다, id=10) — 과제 제출, 노트 작성, 이력서 업로드
- 멘토 (김자바, id=1) — 커리큘럼/과제/세션 생성, 피드백 작성

## 2. 마이페이지 → LMS 진입

### 변경 파일
- `frontend/src/app/mypage/page.tsx`
- `frontend/src/lib/types.ts`

### 변경 내용
1. `MatchingResponse` 타입의 `status` 유니온에 `'TRIAL'` 추가
2. `statusConfig`에 `TRIAL: { label: '체험 중', color: 'text-cyan-400 bg-cyan-500/10' }` 추가
3. 매칭 카드 우측에 조건부 LMS 버튼:
   - `status === 'ACCEPTED' || status === 'TRIAL'` → `GraduationCap` 아이콘 + "LMS" 텍스트 버튼
   - 클릭 시 `router.push('/lms/dashboard?matchingId=${matching.id}')` 이동
   - 그 외 상태 → 기존 상태 배지만 표시
4. 멘토 마이페이지는 이번 범위에서 제외 (멘티 기준)

## 3. 시드 데이터

### 방식
SQL 스크립트를 Docker MySQL 컨테이너에서 실행.

### 대상 매칭
가나다(mentee, id=10) ↔ 김자바(mentor, id=1), matching_id=1

### 데이터 구성

| 테이블 | 건수 | 상세 |
|---|---|---|
| `curriculums` | 1건 | "Java Backend 마스터 과정", 총 8주 |
| `curriculum_weeks` | 8건 | 1~3주차 completed, 4주차 진행중, 5~8주차 미완료 |
| `mentoring_sessions` | 5건 | 과거 3건(COMPLETED), 예정 2건(SCHEDULED). meet_link null |
| `assignments` | 4건 | REVIEWED 1건, SUBMITTED 1건, ASSIGNED 2건 |
| `assignment_submissions` | 2건 | REVIEWED/SUBMITTED 과제 각 1건 |
| `learning_notes` | 3건 | 1~3주차 학습 노트, 타입: SUMMARY, TIL, QUESTION |
| `note_comments` | 2건 | 멘토가 노트에 남긴 코멘트 |

| `mentor_time_slots` | 6건 | 04-14 ~ 04-25 중 가용 슬롯, 2건은 is_booked=true (예정 세션에 대응) |
| `session_change_requests` | 1건 | 04-21 세션에 대한 변경 요청 (PENDING 상태) |

### 날짜 기준
- 과거 세션/과제: 2026-03-25 ~ 04-08
- 예정 세션: 04-14, 04-21
- 멘토 가용 슬롯: 04-14, 04-16, 04-18, 04-21, 04-23, 04-25

## 4. LMS 페이지별 CRUD 기능

### 4-1. 커리큘럼 (`/lms/curriculum`)
- **기존**: 주차 목록 조회 + 완료 토글
- **추가 (멘토 전용)**:
  - 커리큘럼 생성 모달 (제목, 설명)
  - 주차 추가/수정 모달 (주제, 학습자료 URL, 설명)
- **멘티**: 기존 주차 완료 체크 토글 유지

### 4-2. 멘토링 세션 (`/lms/sessions`) — 캘린더 기반 예약 시스템
- **기존**: 전체 세션 목록 조회 (`/api/sessions`, matchingId 미사용)
- **수정**: `matchingId` 기반 조회로 변경 (다른 LMS 페이지와 동일 패턴)

#### 캘린더 UI 구조
- 페이지 상단: 월간 캘린더 (FullCalendar `dayGridMonth` — `@fullcalendar/react` 이미 설치됨)
- 페이지 하단: 확정된 세션 목록 (기존 목록 유지)
- 캘린더 날짜 셀에 가용 슬롯/예약된 세션 도트 표시

#### 멘토 흐름
1. 캘린더에서 날짜 클릭 → 가용시간 등록 모달
2. 모달: 시작시간, 종료시간 입력 (같은 날짜에 여러 슬롯 등록 가능)
3. 등록된 가용시간은 캘린더에 파란 도트로 표시
4. 세션 상태 변경: SCHEDULED → COMPLETED

#### 멘티 흐름
1. 캘린더에서 가용 슬롯이 있는 날짜(파란 도트) 클릭
2. 해당 날짜의 가용 시간 슬롯 목록 표시
3. 원하는 슬롯 선택 → 세션 예약 확정 (즉시 SCHEDULED)

#### 세션 변경 요청/승인 흐름
- 확정된 세션의 날짜/시간 변경을 원하는 경우:
  - 멘토 또는 멘티가 "변경 요청" 버튼 클릭
  - 새로운 날짜, 시작시간, 종료시간 입력 → 변경 요청 생성
  - 상대방에게 요청 표시 (세션 카드에 "변경 요청 있음" 배지)
  - 상대방이 승인 또는 거절
  - 승인 시 세션 날짜/시간 업데이트, 거절 시 기존 유지

- **백엔드 수정**: SessionController에 matchingId 기반 필터링 확인/추가, LmsAccessService 접근 검증 적용

### 4-3. 과제 (`/lms/assignments`)
- **기존**: 과제 목록 + 상태 필터 조회
- **추가 (멘토 전용)**:
  - 과제 생성 모달 (제목, 설명, 타입, 마감일)
  - 피드백 작성 모달 (점수, 코멘트)
- **추가 (멘티 전용)**:
  - 과제 제출 모달 (GitHub URL 입력)

### 4-4. 학습 노트 (`/lms/notes`)
- **기존**: 노트 목록 + 타입 필터 조회
- **추가 (멘티 전용)**:
  - 노트 작성 모달 (제목, 내용, 타입, 주차 번호)
- **추가 (공통)**:
  - 노트 상세 보기 (내용 + 코멘트 목록)
  - 코멘트 작성

### 4-5. 취업 지원 (`/lms/career`)
- **기존**: 이력서/모의면접 탭 전환 + 목록 조회
- **추가 (멘티 전용)**:
  - 이력서 등록 모달 (파일 URL, 파일명 입력)
- **추가 (멘토 전용)**:
  - 이력서 피드백 작성 모달
  - 모의면접 기록 생성 모달 (날짜, 주제, Q&A, 피드백, 점수)

### 4-6. 수료증 (`/lms/certificate`)
- 변경 없음 — 기존 수료 자격 확인 + PDF 다운로드 유지

### 4-7. 대시보드 (`/lms/dashboard`)
- 변경 없음 — 다른 페이지 데이터가 채워지면 자연스럽게 통계 반영

## 5. 세션 페이지 백엔드 수정

### 5-1. matchingId 기반 필터링

#### 문제
프론트 세션 페이지가 `GET /api/sessions` (전체 조회)를 호출하며 matchingId 기반 필터가 없음.

#### 수정
- **백엔드**: SessionController에 `GET /api/sessions?matchingId={id}` 파라미터 지원 또는 `GET /api/lms/sessions/{matchingId}` 엔드포인트 확인/추가. LmsAccessService로 접근 검증 적용
- **프론트**: useSearchParams에서 matchingId 읽고 해당 엔드포인트 호출 (다른 LMS 페이지와 동일 패턴)
- 세션 생성 POST, 상태 변경 PUT API가 SessionController에 있는지 확인. 없으면 추가

### 5-2. MentoringSession unique 제약 수정

#### 문제
`MentoringSession.matchingId`에 `unique = true` 설정되어 있어 한 매칭당 세션 1개만 생성 가능. 주차별 여러 세션이 필요하므로 제거 필요.

#### 수정
- `MentoringSession.java`에서 `unique = true` 제거
- DB 마이그레이션: `ALTER TABLE mentoring_sessions DROP INDEX` (unique index 제거)

### 5-3. 멘토 가용시간 API

#### 현재 상태
`MentorAvailability` 엔티티는 요일(dayOfWeek) 기반. 캘린더 기능을 위해 날짜(date) 기반 슬롯이 필요.

#### 새 엔티티: `MentorTimeSlot`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| mentor_id | Long | 멘토 ID |
| matching_id | Long | 매칭 ID |
| slot_date | LocalDate | 가용 날짜 |
| start_time | LocalTime | 시작 시간 |
| end_time | LocalTime | 종료 시간 |
| is_booked | Boolean | 예약 여부 (default: false) |
| created_at | LocalDateTime | 생성일 |

기존 `MentorAvailability`는 그대로 두고 (다른 곳에서 사용 가능), 새 엔티티로 날짜 기반 슬롯 관리.

#### API 엔드포인트

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/lms/sessions/{matchingId}/slots` | 가용시간 슬롯 등록 | 멘토 |
| GET | `/api/lms/sessions/{matchingId}/slots?month=2026-04` | 월별 가용시간 조회 | 멘토/멘티 |
| DELETE | `/api/lms/sessions/{matchingId}/slots/{slotId}` | 가용시간 삭제 | 멘토 |

### 5-4. 세션 예약 API (멘티가 슬롯 선택)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/lms/sessions/{matchingId}/book` | 슬롯 선택하여 세션 예약 | 멘티 |

요청 본문: `{ slotId: Long, memo: String }`
- 해당 슬롯의 `is_booked`를 true로 변경
- `MentoringSession` 생성 (status: SCHEDULED)
- 슬롯의 날짜/시간 정보를 세션에 복사

### 5-5. 세션 변경 요청 API

#### 새 엔티티: `SessionChangeRequest`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| session_id | Long | 대상 세션 ID |
| requester_id | Long | 요청자 ID |
| new_date | LocalDate | 변경 희망 날짜 |
| new_start_time | LocalTime | 변경 희망 시작시간 |
| new_end_time | LocalTime | 변경 희망 종료시간 |
| reason | String | 변경 사유 |
| status | Enum | PENDING / APPROVED / REJECTED |
| created_at | LocalDateTime | 요청일 |
| responded_at | LocalDateTime | 응답일 (nullable) |

#### API 엔드포인트

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/lms/sessions/{matchingId}/change-request` | 변경 요청 생성 | 멘토/멘티 |
| GET | `/api/lms/sessions/{matchingId}/change-requests` | 변경 요청 목록 조회 | 멘토/멘티 |
| PUT | `/api/lms/sessions/{matchingId}/change-request/{id}/approve` | 변경 승인 | 상대방 |
| PUT | `/api/lms/sessions/{matchingId}/change-request/{id}/reject` | 변경 거절 | 상대방 |

승인 시: 세션의 날짜/시간을 변경 요청 값으로 업데이트, 요청 상태를 APPROVED로 변경
거절 시: 요청 상태만 REJECTED로 변경, 세션 유지

#### MentoringSession 변경사항
- `updateSchedule(LocalDate newDate, LocalTime newStart, LocalTime newEnd)` 메서드 추가
- `SessionStatus`는 기존 SCHEDULED/COMPLETED/CANCELLED 유지 (변경 요청은 별도 엔티티로 관리하므로 세션 상태에 추가 불필요)
- 변경 요청이 PENDING 상태인지 여부는 `SessionChangeRequest` 조회로 판단

## 6. 에러 처리 및 UX 패턴

### 폼 패턴
- 모달(Modal) 방식 — 목록 위에 오버레이로 생성/수정 폼 표시
- 폼 제출 시 로딩 스피너 + 버튼 비활성화
- 성공 시 모달 닫기 + 목록 자동 새로고침
- 실패 시 모달 내부에 에러 메시지 표시

### 권한 구분
- `AuthContext`의 `user.role`로 멘토/멘티 구분
- 멘토 전용 버튼/폼: `user?.role === 'MENTOR'` 조건부 렌더링
- 멘티 전용도 마찬가지
- 백엔드 403 응답 시 모달/화면에 "권한이 없습니다" 에러 표시

### 빈 상태 (Empty State)
- 데이터 없을 때 안내 문구 + 역할에 따른 생성 유도 버튼
- 예: "아직 등록된 과제가 없습니다" + 멘토에게 "과제 만들기" 버튼

### 스타일 일관성
- 기존 LMS 다크 테마 유지: `bg-[#0f1420]`, `border-white/5`, `text-white`/`gray-400`
- 버튼: `blue-500` 계열 그라데이션
- 모달: `bg-black/50` 오버레이 + `glass-card` 스타일 카드

## 7. 구현 순서 (접근 A: 프론트엔드 우선)

1. 시드 데이터 SQL 작성 및 실행
2. 마이페이지 LMS 진입 버튼 추가
3. 세션 백엔드 수정 (matchingId 필터링 + unique 제약 제거)
4. 캘린더 백엔드 구축 (MentorTimeSlot 엔티티 + 가용시간/예약/변경요청 API)
5. 세션 페이지 캘린더 UI (FullCalendar + 멘토 가용시간 등록 + 멘티 슬롯 선택)
6. 세션 변경 요청/승인 UI
7. 커리큘럼 페이지 CRUD
8. 과제 페이지 CRUD
9. 학습 노트 페이지 CRUD
10. 취업 지원 페이지 CRUD
11. 전체 E2E 검증

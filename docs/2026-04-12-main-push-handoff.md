# 2026-04-12 main 푸시 인수인계 문서

> **대상 독자**: 프로젝트 팀원
> **목적**: 2026-04-12에 `main`에 푸시된 변경사항을 리뷰·검증하고, 필요 시 수정·롤백할 수 있게 한다.
> **작성 배경**: 하루치 작업이 여러 주제로 누적되어 있어, 각 커밋이 어떤 맥락에서 만들어졌고 어디에 영향이 있는지 한 문서에서 확인할 수 있도록 정리.

---

## TL;DR

2026-04-12에 다음 6개 커밋이 `origin/main`에 올라갔습니다.

| 커밋 | 유형 | 주제 | 규모 |
|---|---|---|---|
| `326657c` | feat | 커리큘럼 주차 제한 + 세션 PENDING 플로우 + 버그 픽스 | +1,309 / −99 (30 files) |
| `9c2fad3` | refactor | Refresh token → HttpOnly 쿠키 기반 서버 세션 전환 | +373 / −128 (14 files) |
| `9a43b91` | feat | LMS 대시보드 D-Day + 카드 네비게이션 | +37 / −6 |
| `dbe85f3` | feat | DDayStatCard 컴포넌트 신규 | +128 |
| `6321c1b` | feat | StatCard에 optional `href` prop 추가 | +21 / −4 |
| `a3329e6` | feat | D-day 계산 유틸 `lms-dday.ts` 신규 | +106 / −1 |

위 커밋 이전에 `ba6cc76`(plan), `95f001b`(spec) 문서 커밋 2건이 먼저 올라갔습니다.

**⚠️ 운영 배포 전 필수 확인**: `REFRESH_COOKIE_SECURE=true` 환경변수 설정 (HTTPS 필수). 자세한 체크리스트는 [§4. 배포 체크리스트](#4-배포-체크리스트-중요) 참조.

---

## 1. LMS 대시보드 D-Day 표시 + 카드 네비게이션

### 배경
대시보드 상단 4개 통계 카드(`진도율`, `출석률`, `과제`, `D-Day`) 중 D-Day가 서버에서 계산된 단순 숫자였고, 카드 클릭 시 상세 페이지로 이동하는 기능이 없었다. 이번에 D-Day를 "다음 세션"과 "멘토링 종료"의 2행으로 확장하고, 모든 카드를 클릭 가능하게 만들었다.

### 관련 문서
- `docs/superpowers/specs/2026-04-12-lms-dashboard-dday-navigation-design.md` — 설계 스펙
- `docs/superpowers/plans/2026-04-12-lms-dashboard-dday-navigation.md` — 구현 플랜 (단계별 코드 포함)

### 변경 내역

**백엔드 (`f8e1d90`)**
- `DashboardResponse.dDay: long` → `mentoringEndDate: String` (nullable)
  - 파일: `backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java`
  - 서버가 D-day 숫자를 계산하지 않고 절대 종료일만 내려주는 방향으로 전환 → 자정 경계에서 stale해지지 않음
- `LmsDashboardService.getDashboard`
  - 파일: `backend/src/main/java/com/devmatch/service/LmsDashboardService.java`
  - 커리큘럼을 2회 조회하던 부분을 1회로 통합 (진도율 + discordUrl + 종료일 공통 사용)
  - `ChronoUnit` import 제거

**프론트엔드 타입 & 유틸 (`a3329e6`)**
- `frontend/src/lib/lms-types.ts` — `DashboardResponse.dDay` → `mentoringEndDate: string | null`
- 🆕 `frontend/src/lib/lms-dday.ts` — 순수함수 D-day 계산 유틸
  - `type DdayState = { kind: 'none' | 'upcoming' | 'today' | 'inProgress' | 'past'; ... }` (discriminated union)
  - `computeSessionDday(date, startTime, endTime, now?)` — 세션 기준 D-day
  - `computeEndDateDday(endDate, now?)` — 종료일 기준 D-day
  - `now` 파라미터 주입 가능 → 수동 검증용. 파일 상단 주석에 검증 케이스 표 포함
  - 색상 분기: `none`/`past`=회색, `today`=amber, `inProgress`=green+펄스, `upcoming`=white

**프론트엔드 UI (`6321c1b`, `dbe85f3`, `9a43b91`)**
- `frontend/src/components/lms/StatCard.tsx` — optional `href` prop 추가
  - `href`가 주어지면 `next/link`로 래핑, `group`/`focus-visible`/`hover:border-white/20` 스타일
  - `href` 없으면 기존 non-interactive `div` 렌더 (하위 호환)
  - `h-full` 추가로 grid `items-stretch` 시 높이 맞춤
- 🆕 `frontend/src/components/lms/DDayStatCard.tsx` — 2행 구조 전용 카드
  - 외곽 `<div>`는 non-interactive, 내부 2개의 `<Link>`(다음 세션 / 멘토링 종료)가 각각 독립적인 클릭 타겟
  - `group/row`로 행별 hover 효과 분리
  - `sessionAbsolute`/`endAbsolute`는 `upcoming` 상태일 때만 절대 날짜(`MM/DD (요일) HH:mm`) 보조 렌더
- `frontend/src/app/lms/dashboard/page.tsx` — 상단 StatCard 섹션 재구성
  - 진도율 → `/lms/curriculum`
  - 출석률 → `/lms/sessions`
  - 과제 → `/lms/assignments`
  - D-Day 슬롯 → `DDayStatCard` (내부 2개 Link: sessions + curriculum)
  - 안 쓰는 `Clock` import 제거

### 검증 포인트 (팀원이 직접 확인)
1. `/lms/dashboard?matchingId=<id>` 접속 시 4개 카드가 `lg` 이상에서 한 줄로 정렬, 높이 동일
2. 각 카드 클릭 시 올바른 상세 페이지로 이동
3. D-Day 카드의 두 행을 각각 클릭했을 때 `sessions`/`curriculum`으로 분리되어 이동
4. Tab 키로 네비게이션 시 focus ring이 카드마다 보이는지
5. 엣지 케이스:
   - 예정 세션 없음 → "예정된 세션 없음", 회색
   - 커리큘럼 미설정 → "커리큘럼 미설정", 회색
   - 오늘 세션(시작 전) → amber + "D-0 · 오늘 HH:mm"
   - 세션 진행 중 → green + 펄스 점 + "진행 중"
   - 과거 세션 → 회색 + "D+N"

### 알려진 이슈
- **타임존 미세 버그**: `lms-dday.ts::parseDate`가 `"YYYY-MM-DD"`를 `new Date()`로 파싱하면 UTC 자정으로 해석됨 → 로컬 자정 경계에서 D-day가 1일 어긋날 수 있음. 후속 수정 권장.

---

## 2. 인증 리프레시 토큰 → HttpOnly 쿠키 기반 서버 세션 전환 (`9c2fad3`)

### 배경
기존에는 `accessToken`과 `refreshToken`을 모두 클라이언트가 localStorage에 보관했다. XSS 노출·재사용 탐지 부재·로그아웃 시 서버에서 세션 무효화 불가 등 보안 이슈가 있어, refresh token을 **서버 측 Redis 세션**으로 이동하고 브라우저에는 **HttpOnly 쿠키**로만 전달하도록 재설계.

### 관련 문서
- `error/2026-04-12-auth-refresh-token-session-redesign.md` — 트러블슈팅 기록

### 변경 내역

**백엔드**
- 🆕 `backend/src/main/java/com/devmatch/config/AuthProperties.java`
  - 쿠키 설정(`name`, `path`, `secure`, `same-site`)과 재사용 탐지 윈도우(`reuse-window-seconds`) 설정을 `@ConfigurationProperties`로 묶음
- 🆕 `backend/src/main/java/com/devmatch/service/RefreshSessionService.java`
  - Redis에 `{userId, deviceInfo, ip, issuedAt}` 형태의 세션 저장
  - `InvalidRefreshSessionException` (중첩 클래스)로 예외 통일 → GlobalExceptionHandler에서 401로 응답
  - 재사용 윈도우 내 동일 세션 재시도는 허용(네트워크 재시도 감안), 윈도우 벗어난 재사용은 세션 폐기 후 401
- `backend/src/main/java/com/devmatch/controller/AuthController.java`
  - `/login`, `/refresh`: `Set-Cookie` 헤더로 refresh 쿠키 발급, 응답 바디엔 `accessToken`만
  - `/refresh`는 요청 바디 없이 쿠키에서 직접 읽음
  - `deviceInfo` = `User-Agent` 헤더, `ip` = `X-Forwarded-For` 우선 → remote addr fallback
- ❌ `backend/src/main/java/com/devmatch/dto/auth/TokenRefreshRequest.java` **삭제**
- `backend/src/main/java/com/devmatch/dto/auth/TokenResponse.java` — `refreshToken` 필드 제거, `of(String accessToken)` 팩토리만 남김
- `backend/src/main/java/com/devmatch/service/AuthService.java` — `AuthTokens` 내부 레코드(`accessToken`, `refreshToken`) 반환
- `backend/src/main/java/com/devmatch/security/JwtTokenProvider.java` — refresh 서명/검증 로직 정리
- `backend/src/main/java/com/devmatch/config/SecurityConfig.java` — CORS credentials 허용
- `backend/src/main/resources/application.yml`
  ```yaml
  app:
    auth:
      refresh-cookie:
        name: refresh_token
        path: /api/auth
        secure: ${REFRESH_COOKIE_SECURE:false}
        same-site: Strict
      reuse-window-seconds: 300
  ```
- `docker-compose.yml` — Redis AOF 영속화 활성화
  ```
  command: redis-server --appendonly yes --appendfsync everysec --save 900 1 --save 300 10 --save 60 10000
  ```
  세션이 Redis 재시작 후에도 유지되도록 함.

**프론트엔드**
- `frontend/src/lib/token.ts`
  - `getRefreshToken`/`setTokens` 제거
  - `getAccessToken`/`setAccessToken`/`clearAccessToken`만 남김
- `frontend/src/lib/api.ts`
  - `axios` 인스턴스에 `withCredentials: true` 추가
  - 401 응답 시 `/api/auth/refresh`를 **바디 없이** 호출 (쿠키 자동 전송)
  - 응답에서 `accessToken`만 추출해 `setAccessToken`
- `frontend/src/lib/auth.ts` — `setTokens` → `setAccessToken`
- `frontend/src/lib/types.ts` — `TokenResponse`에서 `refreshToken` 제거

### 검증 포인트
1. 로그인 성공 시 브라우저 개발자도구 > Application > Cookies에 `refresh_token`이 HttpOnly로 세팅되는지
2. localStorage에 refresh 관련 키가 **없는지** (access만 있어야 함)
3. access 토큰 만료 후 API 호출 시 자동 refresh 후 원 요청 재시도되는지
4. 다른 기기에서 로그인 → 기존 기기에서 refresh 호출 시 재사용 탐지되어 로그아웃되는지
5. Redis 재시작 후에도 세션 유지되는지 (AOF 영속화)

### 🚨 **마이그레이션 주의**
- 기존 사용자의 localStorage에 남아있는 `refreshToken`은 더 이상 사용되지 않음
- 배포 직후 기존 사용자는 **전원 재로그인 필요**
- 로그인 페이지에 "인증 방식이 개선되어 다시 로그인이 필요합니다" 안내 배너 추가 검토

---

## 3. LMS 커리큘럼 주차 수 제한 (결제 연동)

### 배경
커리큘럼의 총 주차(`totalWeeks`)를 무제한으로 설정할 수 있어, 결제 없이 또는 1개월 결제로 10주짜리 커리큘럼을 등록하는 이슈가 있었다. 결제 확인된 개월 수에 `WEEKS_PER_MONTH = 4`를 곱한 값을 상한으로 적용.

### 관련 문서
- `error/2026-04-12-lms-curriculum-week-add-fails.md`
- `error/2026-04-12-lms-curriculum-weeks-persist-restart.md`

### 변경 내역
- 🆕 `backend/src/main/java/com/devmatch/dto/lms/CurriculumLimitResponse.java`
  ```java
  { maxWeeks, monthsBundled, paymentDate, hasConfirmedPayment }
  ```
- 🆕 `backend/src/main/java/com/devmatch/exception/CurriculumWeekLimitException.java` → 400
- `backend/src/main/java/com/devmatch/service/CurriculumService.java`
  - 상수: `WEEKS_PER_MONTH = 4`, `FALLBACK_MAX_WEEKS = 4`
  - `resolveMaxWeeks(matchingId)` — `PaymentRepository`에서 확인된 결제 집계 → `monthsBundled × 4`, 없으면 `FALLBACK_MAX_WEEKS`
  - `enforceWeekLimit(totalWeeks, weeksListSize, maxWeeks)` — create/update 시 검증
- `backend/src/main/java/com/devmatch/controller/CurriculumController.java` — `GET /api/lms/curriculum/{matchingId}/limit` 엔드포인트 추가
- `backend/src/main/java/com/devmatch/entity/CurriculumWeek.java` — 필드/메서드 정리
- `frontend/src/lib/lms-types.ts` — `CurriculumLimitResponse` 인터페이스 추가
- `frontend/src/lib/lms.ts` — `getCurriculumLimit(matchingId)` API 래퍼
- `frontend/src/app/lms/curriculum/page.tsx` — 주차 추가 시 `maxWeeks` 초과 여부 검증, 결제 정보 안내 UI

### 검증 포인트
1. 결제 없는 매칭에서 주차 5개 시도 → 400 + "주차 제한" 메시지
2. 1개월 결제 매칭 → 최대 4주
3. 3개월 결제 매칭 → 최대 12주
4. `GET /api/lms/curriculum/{matchingId}/limit` 응답 필드 확인

---

## 4. LMS 세션 예약 PENDING 플로우 + 멘티 슬롯 제안 + 멘토 직접 생성

### 배경
기존 플로우는 "멘토가 슬롯 오픈 → 멘티가 예약 즉시 확정"이라 멘토가 일방적으로 확정된 일정에 묶였다. 아래 3가지 개선:
1. 멘티 예약 시 **`PENDING`** 상태로 시작, 멘토 승인/거절 필요
2. 멘티가 원하는 시간대를 **슬롯 제안** 형태로 올릴 수 있음 (멘토가 확정 전까지 `proposedByMentee=true`)
3. 멘토가 슬롯 없이 **직접 세션 생성** 가능 (자유 시간대)

### 관련 문서
- `error/2026-04-12-fullcalendar-event-click-no-dateclick.md`

### 변경 내역

**백엔드**
- `backend/src/main/java/com/devmatch/entity/SessionStatus.java` — `PENDING` enum 추가
- `backend/src/main/java/com/devmatch/entity/MentorTimeSlot.java` — `proposedByMentee: Boolean` 필드
- `backend/src/main/java/com/devmatch/entity/MentoringSession.java` — 상태 전이 메서드(`approve`, 등)
- 🆕 `backend/src/main/java/com/devmatch/dto/lms/DirectSessionCreateRequest.java`
- `backend/src/main/java/com/devmatch/dto/lms/TimeSlotResponse.java` — `proposedByMentee` 노출
- `backend/src/main/java/com/devmatch/repository/MentorTimeSlotRepository.java`
  - `findByMatchingIdAndSlotDateAndIsBookedFalseAndProposedByMenteeFalse...`
  - `findByMatchingIdAndProposedByMenteeTrue...`
- `backend/src/main/java/com/devmatch/service/LmsSessionService.java`
  - `proposeSlot(userId, matchingId, request)` — 멘티용, `proposedByMentee=true`
  - `createSessionDirect(userId, matchingId, request)` — 멘토 전용
  - `bookSession` — 예약 시 `PENDING` 상태로 시작
  - `approveSession` — `PENDING → SCHEDULED`
  - `rejectSession` — `PENDING → CANCELLED` + 슬롯 unbook
  - `cancelSession` — `PENDING`/`SCHEDULED` 둘 다 취소 허용
- `backend/src/main/java/com/devmatch/controller/LmsSessionController.java` — 위 메서드에 대응하는 엔드포인트

**프론트엔드**
- `frontend/src/lib/lms-types.ts`
  - `SessionStatus = 'PENDING' | 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'`
  - `TimeSlotResponse.proposedByMentee`
  - `DirectSessionCreateRequest` 인터페이스
- `frontend/src/lib/lms.ts`
  - `approveLmsSession`, `rejectLmsSession`
  - `proposeTimeSlot`, `getProposedSlots`
  - `createLmsSessionDirect`
- `frontend/src/app/lms/sessions/page.tsx` **(+409)** — 가장 큰 변경
  - FullCalendar 이벤트 색상 매핑:
    - 멘티 제안(`#f59e0b` amber) / 예약됨(`#6366f1` indigo) / 가용(`#3b82f6` blue)
    - 완료(`#22c55e` green) / 취소(`#ef4444` red) / PENDING(`#f59e0b` amber) / SCHEDULED(`#8b5cf6` violet)
  - PENDING 세션에만 승인/거절 버튼 노출
  - 멘티 제안 슬롯 모달 (날짜 클릭 시 해당 날짜 제안 목록)

### 검증 포인트
1. 멘티로 로그인 → 가용 슬롯 예약 시 상태가 `PENDING`으로 시작
2. 멘토로 로그인 → PENDING 세션에 승인/거절 버튼 표시, 승인 시 `SCHEDULED` 전이
3. 멘티가 슬롯 제안 → 멘토 캘린더에 amber 색상으로 표시
4. 멘토가 직접 세션 생성 (슬롯 없이) → 정상 예약
5. 본인이 요청한 변경은 본인이 승인 불가 (기존 로직 유지)

---

## 5. UI 버그 픽스 (`326657c` 일부)

| 파일 | 문제 | 관련 에러 로그 |
|---|---|---|
| `frontend/src/app/lms/assignments/page.tsx` | `<select>` 옵션이 어두운 배경에서 안 보임 | `error/2026-04-12-lms-assignments-select-option-invisible.md` |
| `frontend/src/app/mypage/page.tsx` | 멘토 매칭 목록이 안 나오는 문제 | `error/2026-04-12-mypage-mentor-matchings-missing.md` |

그 외 `error/` 폴더에 기록된 해결된 이슈:
- `2026-04-12-mentor-seed-password-mismatch.md` — 시드 데이터 멘토 비밀번호 불일치 (DB 초기화 필요)

---

## 6. 메타 / 프로젝트 규칙

- 🆕 `CLAUDE.md` (프로젝트 루트) — 에러 로깅 규칙 문서화
  - 원인 파악 후 해결한 에러는 반드시 `error/YYYY-MM-DD-짧은-제목.md`로 기록
  - `error/README.md`의 템플릿 사용
  - 해결한 "그 턴에" 바로 작성
- `error/README.md` — 템플릿과 인덱스
- `.claude/settings.local.json` — Claude Code 로컬 권한 목록 (팀원 환경에는 영향 없음)

---

## 7. 배포 체크리스트 (중요)

운영(HTTPS) 환경에 이번 변경을 반영하기 전, **반드시** 아래를 확인하세요.

### 7.1 환경변수 필수 변경
| 변수 | 값 | 이유 |
|---|---|---|
| `REFRESH_COOKIE_SECURE` | `true` | HTTPS 환경에서 refresh 쿠키가 전송되도록. `false`면 브라우저가 쿠키를 보내지 않아 로그인 유지 불가 |

### 7.2 Redis 영속화
- `docker-compose.yml`의 Redis command에 AOF 관련 옵션이 추가됨
- 기존 Redis 볼륨이 있다면 재기동 시 AOF 파일이 신규 생성됨 (정상)
- 별도 Redis 호스팅 쓰는 경우: AOF 또는 RDB 영속화가 설정되어 있는지 확인

### 7.3 DB 스키마 변경
- `MentorTimeSlot.proposedByMentee` (Boolean) 컬럼 추가
- JPA `ddl-auto`가 운영에서 `validate`/`none`이라면 **마이그레이션 스크립트 필요**:
  ```sql
  ALTER TABLE mentor_time_slot
    ADD COLUMN proposed_by_mentee BOOLEAN NOT NULL DEFAULT FALSE;
  ```
- `SessionStatus`에 `PENDING` enum 추가 → `mentoring_session.status` 컬럼이 enum 문자열로 저장되는지 확인 (JPA `@Enumerated(EnumType.STRING)` 전제)

### 7.4 사용자 재로그인 공지
- 기존 사용자 localStorage의 refresh token은 무효
- 배포 직후 401 → 재로그인 유도 흐름이 자연스러운지 확인
- 필요 시 로그인 페이지에 공지 배너

### 7.5 배포 후 동작 확인 (최소)
- [ ] 신규 회원가입 → 로그인 → 대시보드 진입 → D-Day 카드 2행 정상 표시
- [ ] 기존 사용자 재로그인 → access 만료 시 자동 refresh 동작
- [ ] 멘티 세션 예약 → 멘토 캘린더에서 PENDING 확인 → 승인 → SCHEDULED 전이
- [ ] 커리큘럼 주차 제한 동작 (결제 정보에 맞게)
- [ ] 브라우저 DevTools > Cookies에서 `refresh_token`이 `HttpOnly`, `Secure`, `SameSite=Strict`로 세팅되는지

---

## 8. 알려진 이슈 / 후속 작업 제안

### 8.1 타임존 미세 버그 (🟡 Minor)
- **위치**: `frontend/src/lib/lms-dday.ts::parseDate`
- **증상**: `new Date("2026-04-12")`가 UTC 자정으로 해석되어, 사용자 로컬 자정 경계에서 D-day 라벨이 1일 어긋날 수 있음 (ex: KST 23:59에 접속 시 "D-0"이어야 하는데 "D-1"로 표시)
- **재현**: 타임존을 UTC+9로 설정한 상태에서 로컬 시간 23:30~24:00 사이에 대시보드 접속
- **수정 방향**: 날짜만 있는 문자열은 로컬 자정으로 파싱해야 함. `new Date(Y, M-1, D)` 형식 사용
  ```ts
  function parseDate(iso: string): Date | null {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
    if (!m) return null;
    return new Date(+m[1], +m[2] - 1, +m[3]);
  }
  ```
- **우선순위**: 낮음 (자정 경계 진입 사용자만 영향)

### 8.2 `discordUrl` 적용 범위 확대 확인 필요 (🟡 Minor)
- **위치**: `backend/src/main/java/com/devmatch/service/LmsDashboardService.java`
- **변경 전**: 커리큘럼 `totalWeeks > 0` 조건 블록 내에서만 `discordUrl` 세팅
- **변경 후**: 블록 밖으로 이동해 `totalWeeks == 0`인 커리큘럼에서도 `discordUrl`이 내려감
- **판단**: 의도된 버그 픽스로 보이나, 요구사항상 의도였는지 리뷰 필요

### 8.3 `GlobalExceptionHandler` 커밋 스플릿 (ℹ️ 참고)
- `GlobalExceptionHandler`에 `RefreshSessionService.InvalidRefreshSessionException`(Topic 2) 핸들러와 `CurriculumWeekLimitException`(Topic 3) 핸들러가 한 커밋(`326657c`)에 묶여 있음
- Topic 2(인증 쿠키)는 `9c2fad3`에 별도로 커밋됨 → 해당 커밋만 체리픽하면 InvalidRefreshSessionException에 대해 기본 500이 반환됨 (401 대신)
- 체리픽/롤백 시 주의

### 8.4 예정된 대규모 수동 테스트
이번 푸시는 여러 feature가 묶여 있어 단일 커밋보다 회귀 위험이 큽니다. QA 세션에서 아래 플로우 체크 권장:
- 로그인/로그아웃/access 만료 후 재요청 (인증 리팩터)
- 커리큘럼 주차 추가/삭제, 제한 테스트
- 세션 예약 전체 플로우 (제안 → 예약 → PENDING → 승인/거절)
- 대시보드 카드 네비게이션 5건 (진도율/출석률/과제/세션행/커리큘럼행)

---

## 9. 롤백 가이드

만약 특정 feature만 되돌려야 한다면:

```bash
# 전체 롤백 (푸시 전 f03a8f6으로)
git revert --no-edit 326657c 9c2fad3 9a43b91 dbe85f3 6321c1b a3329e6 f8e1d90 ba6cc76 95f001b

# 개별 롤백
git revert --no-edit 326657c    # LMS 기능 + 버그 픽스 묶음
git revert --no-edit 9c2fad3    # 인증 쿠키 리팩터
git revert --no-edit 9a43b91 dbe85f3 6321c1b a3329e6 f8e1d90  # D-Day 기능
```

**주의**:
- 인증 리팩터(`9c2fad3`)만 되돌리는 경우: `326657c`에 포함된 `GlobalExceptionHandler`의 `InvalidRefreshSessionException` import가 깨짐 → 수동 수정 필요
- DB 스키마 변경(`proposedByMentee`, `PENDING` enum)은 revert만으로 복구되지 않음 → 별도 롤백 SQL 필요

---

## 10. 변경 파일 요약 (검색용)

<details>
<summary>펼쳐보기 — 전체 파일 목록</summary>

**Backend Java**
- `backend/src/main/java/com/devmatch/config/AuthProperties.java` (new)
- `backend/src/main/java/com/devmatch/config/SecurityConfig.java`
- `backend/src/main/java/com/devmatch/controller/AuthController.java`
- `backend/src/main/java/com/devmatch/controller/CurriculumController.java`
- `backend/src/main/java/com/devmatch/controller/LmsSessionController.java`
- `backend/src/main/java/com/devmatch/dto/auth/TokenRefreshRequest.java` (deleted)
- `backend/src/main/java/com/devmatch/dto/auth/TokenResponse.java`
- `backend/src/main/java/com/devmatch/dto/lms/CurriculumLimitResponse.java` (new)
- `backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java`
- `backend/src/main/java/com/devmatch/dto/lms/DirectSessionCreateRequest.java` (new)
- `backend/src/main/java/com/devmatch/dto/lms/TimeSlotResponse.java`
- `backend/src/main/java/com/devmatch/entity/CurriculumWeek.java`
- `backend/src/main/java/com/devmatch/entity/MentorTimeSlot.java`
- `backend/src/main/java/com/devmatch/entity/MentoringSession.java`
- `backend/src/main/java/com/devmatch/entity/SessionStatus.java`
- `backend/src/main/java/com/devmatch/exception/CurriculumWeekLimitException.java` (new)
- `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/devmatch/repository/MentorTimeSlotRepository.java`
- `backend/src/main/java/com/devmatch/security/JwtTokenProvider.java`
- `backend/src/main/java/com/devmatch/service/AuthService.java`
- `backend/src/main/java/com/devmatch/service/CurriculumService.java`
- `backend/src/main/java/com/devmatch/service/LmsDashboardService.java`
- `backend/src/main/java/com/devmatch/service/LmsSessionService.java`
- `backend/src/main/java/com/devmatch/service/RefreshSessionService.java` (new)

**Backend Config**
- `backend/src/main/resources/application.yml`
- `docker-compose.yml`

**Frontend**
- `frontend/src/app/lms/assignments/page.tsx`
- `frontend/src/app/lms/curriculum/page.tsx`
- `frontend/src/app/lms/dashboard/page.tsx`
- `frontend/src/app/lms/sessions/page.tsx`
- `frontend/src/app/mypage/page.tsx`
- `frontend/src/components/lms/DDayStatCard.tsx` (new)
- `frontend/src/components/lms/StatCard.tsx`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/auth.ts`
- `frontend/src/lib/lms-dday.ts` (new)
- `frontend/src/lib/lms-types.ts`
- `frontend/src/lib/lms.ts`
- `frontend/src/lib/token.ts`
- `frontend/src/lib/types.ts`

**Docs & Meta**
- `CLAUDE.md` (new)
- `docs/superpowers/specs/2026-04-12-lms-dashboard-dday-navigation-design.md` (new)
- `docs/superpowers/plans/2026-04-12-lms-dashboard-dday-navigation.md` (new)
- `error/README.md` (new)
- `error/2026-04-12-*.md` (new, 7 files)

</details>

---

## 문의

이 문서에 보완이 필요하거나 실제 배포 중 이슈가 발생하면, 이 파일을 직접 수정해서 PR 또는 커밋을 올려주세요. 각 섹션은 독립적이라 일부만 수정해도 괜찮습니다.

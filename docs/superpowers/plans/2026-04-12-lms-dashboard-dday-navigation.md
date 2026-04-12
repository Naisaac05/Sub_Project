# LMS 대시보드 D-day 표시 및 카드 네비게이션 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LMS 대시보드 상단 StatCard에 "다음 세션 D-day"와 "멘토링 종료 D-day"를 명시적으로 표시하고, 모든 상단 카드를 각 상세 페이지 진입점으로 만든다.

**Architecture:** 백엔드는 서버 계산 `dDay` 필드를 제거하고 절대 날짜(`mentoringEndDate`)만 내려준다. 프론트는 순수 함수 유틸(`lms-dday.ts`)에서 D-day를 `DdayState` 타입으로 계산하고, 기존 `StatCard`는 `href` prop으로 Link 래핑을 옵션화한다. D-Day 카드는 2행 구조가 필요해 별도 컴포넌트(`DDayStatCard`)로 분리한다.

**Tech Stack:** Spring Boot + Lombok (백엔드), Next.js 14 App Router + TypeScript + Tailwind + lucide-react (프론트엔드)

**Related Spec:** `docs/superpowers/specs/2026-04-12-lms-dashboard-dday-navigation-design.md`

---

## Task 1: 백엔드 DTO — `dDay` → `mentoringEndDate` 필드 교체

**Files:**
- Modify: `backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java:16`

- [ ] **Step 1: DTO 필드 교체**

`DashboardResponse.java`에서 `long dDay`를 `String mentoringEndDate`로 교체한다.

변경 전:
```java
private int progressRate;
private int attendanceRate;
private long dDay;
private AssignmentStats assignmentStats;
```

변경 후:
```java
private int progressRate;
private int attendanceRate;
private String mentoringEndDate;
private AssignmentStats assignmentStats;
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

예상: `LmsDashboardService.java`에서 `.dDay(dDay)` 호출이 존재하지 않는 메서드라 컴파일 에러 발생. Task 2에서 수정.

- [ ] **Step 3: 커밋 보류**

이 태스크는 다음 태스크와 함께 커밋한다 (컴파일이 깨진 상태에서 커밋 금지).

---

## Task 2: 백엔드 서비스 — `mentoringEndDate` 세팅 로직

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/LmsDashboardService.java:30-154`

- [ ] **Step 1: import 정리**

`LmsDashboardService.java` 상단 import에서 `ChronoUnit`을 제거한다 (더 이상 사용 안 함).

변경 전:
```java
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
```

변경 후:
```java
import java.time.LocalDate;
import java.util.ArrayList;
```

- [ ] **Step 2: 커리큘럼 로딩 통합 + `mentoringEndDate` 추출**

현재 `getDashboard()`에서 커리큘럼을 **두 번** 로딩하고 있다 (progressRate 계산용 + dDay 계산용). 이번 기회에 한 번만 로딩하도록 합친다.

`getDashboard()` 메서드 상단(`Matching matching = ...` 바로 아래)에서 커리큘럼 1회 로딩. `LmsDashboardService.java:33-68` 범위의 기존 로직을 아래로 교체한다.

변경 전 (line 33-68):
```java
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

// ... (출석률 계산 블록 — 변경 없음)

// D-Day: 커리큘럼 종료일까지 남은 일수
long dDay = 0;
if (curriculumRepository.existsByMatchingId(matchingId)) {
    Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId).orElse(null);
    if (curriculum != null) {
        dDay = ChronoUnit.DAYS.between(LocalDate.now(), curriculum.getEndDate());
    }
}
```

변경 후 (두 블록을 한 곳으로 합쳐 맨 위에 배치, 출석률 블록은 그대로):
```java
// 커리큘럼 로딩 (1회) — 진도율, discordUrl, 종료일에 공통 사용
Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId).orElse(null);
int progressRate = 0;
String discordUrl = null;
String mentoringEndDate = null;
if (curriculum != null) {
    if (curriculum.getTotalWeeks() > 0) {
        long completedWeeks = weekRepository.countByCurriculumIdAndIsCompletedTrue(curriculum.getId());
        progressRate = (int) ((completedWeeks * 100) / curriculum.getTotalWeeks());
    }
    discordUrl = curriculum.getDiscordUrl();
    mentoringEndDate = curriculum.getEndDate() != null ? curriculum.getEndDate().toString() : null;
}
```

그리고 기존 "D-Day: 커리큘럼 종료일까지 남은 일수" 블록(원본 line 61-68)은 **완전히 삭제**한다.

- [ ] **Step 3: builder 호출 수정**

`getDashboard()` 메서드 끝부분의 `DashboardResponse.builder()` 블록에서 `.dDay(dDay)`를 `.mentoringEndDate(mentoringEndDate)`로 교체.

변경 전:
```java
return DashboardResponse.builder()
        .progressRate(progressRate)
        .attendanceRate(attendanceRate)
        .dDay(dDay)
        .assignmentStats(...)
```

변경 후:
```java
return DashboardResponse.builder()
        .progressRate(progressRate)
        .attendanceRate(attendanceRate)
        .mentoringEndDate(mentoringEndDate)
        .assignmentStats(...)
```

- [ ] **Step 4: 컴파일 & 실행 확인**

```bash
cd backend && ./gradlew compileJava
```
예상: BUILD SUCCESSFUL

```bash
cd backend && ./gradlew bootRun
```
백엔드 기동 후 `curl` 또는 브라우저로 대시보드 API 한 번 호출해 200 응답 확인:
```bash
# 다른 터미널에서 (로그인 쿠키/토큰 필요):
curl -s "http://localhost:8080/api/lms/dashboard?matchingId=1" -H "Authorization: Bearer <token>" | jq .
```
예상 응답 JSON에 `"mentoringEndDate": "2026-05-31"` (또는 null) 포함, `dDay` 필드는 없음.

응답 확인 후 `bootRun`은 Ctrl+C로 종료.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java backend/src/main/java/com/devmatch/service/LmsDashboardService.java
git commit -m "refactor(lms): dDay 필드를 mentoringEndDate 절대 날짜로 교체

서버에서 D-day를 계산해 내리면 자정 경계에서 하루 어긋나는 문제가 있어,
절대 날짜만 내려주고 D-day 계산은 프론트에서 수행하도록 변경.
커리큘럼 로딩도 기존 2회에서 1회로 통합."
```

---

## Task 3: 프론트 타입 — `DashboardResponse` 인터페이스 교체

**Files:**
- Modify: `frontend/src/lib/lms-types.ts:7-16`

- [ ] **Step 1: 타입 수정**

`lms-types.ts`의 `DashboardResponse` 인터페이스에서 `dDay`를 `mentoringEndDate`로 교체.

변경 전 (line 7-16):
```ts
export interface DashboardResponse {
  progressRate: number;
  attendanceRate: number;
  dDay: number;
  assignmentStats: { total: number; submitted: number; reviewed: number; };
  nextSession: { id: number; date: string; startTime: string; endTime: string; meetLink: string; category: string; } | null;
  recentActivities: { type: string; title: string; createdAt: string; }[];
  mentorInfo: { name: string; specialty: string[]; email: string; };
  communicationLinks: { discord: string | null; jitsiMeet: string | null; };
}
```

변경 후:
```ts
export interface DashboardResponse {
  progressRate: number;
  attendanceRate: number;
  mentoringEndDate: string | null;
  assignmentStats: { total: number; submitted: number; reviewed: number; };
  nextSession: { id: number; date: string; startTime: string; endTime: string; meetLink: string; category: string; } | null;
  recentActivities: { type: string; title: string; createdAt: string; }[];
  mentorInfo: { name: string; specialty: string[]; email: string; };
  communicationLinks: { discord: string | null; jitsiMeet: string | null; };
}
```

- [ ] **Step 2: 타입 에러 확인**

```bash
cd frontend && npx tsc --noEmit
```
예상: `app/lms/dashboard/page.tsx:55`의 `data.dDay` 참조에서 타입 에러 발생. Task 7에서 수정.

- [ ] **Step 3: 커밋 보류**

이 태스크도 컴파일이 깨진 상태이므로 Task 7까지 한 번에 커밋한다. 다만 프론트는 작업 범위가 넓어 **중간 커밋**을 위해 임시로 `data.dDay`를 `0`으로 바꾸지 **않는다** — 어차피 Task 7에서 통째로 교체된다.

---

## Task 4: D-day 유틸 — `lms-dday.ts` 신규 작성

**Files:**
- Create: `frontend/src/lib/lms-dday.ts`

- [ ] **Step 1: 파일 생성**

`frontend/src/lib/lms-dday.ts`를 아래 내용으로 생성한다.

```ts
/**
 * LMS 대시보드 D-day 계산 유틸.
 *
 * 순수 함수. `now` 인자를 주입 가능해 테스트/수동 검증이 쉽다.
 *
 * ─── 검증 케이스 ───
 * computeSessionDday(date, start, end, now) →
 *   (null, null, null, *)                                → none
 *   ("2026-04-13", "14:00", "15:00", 2026-04-12 23:00)   → upcoming, days=1
 *   ("2026-04-12", "14:00", "15:00", 2026-04-12 10:00)   → today
 *   ("2026-04-12", "14:00", "15:00", 2026-04-12 14:30)   → inProgress
 *   ("2026-04-12", "14:00", "15:00", 2026-04-12 15:30)   → past, days=0
 *   ("2026-04-11", "14:00", "15:00", 2026-04-12 10:00)   → past, days=1
 *   ("not-a-date", *, *, *)                              → none
 *
 * computeEndDateDday(endDate, now) →
 *   (null, *)                                  → none
 *   ("2026-05-31", 2026-04-12)                 → upcoming, days=49
 *   ("2026-04-12", 2026-04-12)                 → today
 *   ("2026-04-09", 2026-04-12)                 → past, days=3
 *   ("not-a-date", *)                          → none
 */

export type DdayState =
  | { kind: 'none'; label: string }
  | { kind: 'upcoming'; days: number; label: string }
  | { kind: 'today'; label: string }
  | { kind: 'inProgress'; label: string }
  | { kind: 'past'; days: number; label: string };

const MS_PER_DAY = 24 * 60 * 60 * 1000;

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function parseDate(iso: string): Date | null {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

function daysBetween(target: Date, now: Date): number {
  // 자정 기준 일수 차이
  return Math.round((startOfDay(target).getTime() - startOfDay(now).getTime()) / MS_PER_DAY);
}

export function computeSessionDday(
  date: string | null,
  startTime: string | null,
  endTime: string | null,
  now: Date = new Date()
): DdayState {
  if (!date || !startTime || !endTime) {
    return { kind: 'none', label: '예정된 세션 없음' };
  }
  const parsed = parseDate(date);
  if (!parsed) {
    return { kind: 'none', label: '예정된 세션 없음' };
  }

  const diff = daysBetween(parsed, now);

  if (diff > 0) {
    return { kind: 'upcoming', days: diff, label: `D-${diff}` };
  }
  if (diff < 0) {
    return { kind: 'past', days: -diff, label: `D+${-diff}` };
  }

  // diff === 0 → 오늘. 시작/종료 시간으로 inProgress 판정.
  const startAt = new Date(`${date}T${startTime}`);
  const endAt = new Date(`${date}T${endTime}`);
  if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime())) {
    return { kind: 'today', label: `D-0 · 오늘 ${startTime}` };
  }

  if (now >= startAt && now < endAt) {
    return { kind: 'inProgress', label: '진행 중' };
  }
  if (now >= endAt) {
    return { kind: 'past', days: 0, label: 'D+0' };
  }
  return { kind: 'today', label: `D-0 · 오늘 ${startTime}` };
}

export function computeEndDateDday(
  endDate: string | null,
  now: Date = new Date()
): DdayState {
  if (!endDate) {
    return { kind: 'none', label: '커리큘럼 미설정' };
  }
  const parsed = parseDate(endDate);
  if (!parsed) {
    return { kind: 'none', label: '커리큘럼 미설정' };
  }

  const diff = daysBetween(parsed, now);
  if (diff > 0) {
    return { kind: 'upcoming', days: diff, label: `D-${diff}` };
  }
  if (diff === 0) {
    return { kind: 'today', label: 'D-0 · 오늘 종료' };
  }
  return { kind: 'past', days: -diff, label: `D+${-diff} · 종료됨` };
}
```

- [ ] **Step 2: 타입 체크**

```bash
cd frontend && npx tsc --noEmit
```
예상: `lms-dday.ts` 자체는 타입 에러 없음. `dashboard/page.tsx` 쪽 기존 에러는 여전히 남아 있음 (Task 7에서 해결).

- [ ] **Step 3: 수동 검증**

브라우저 콘솔이나 Node REPL에서 검증 케이스를 돌려본다. 직접 import가 어려우면 Task 7에서 대시보드 렌더 결과로 간접 확인해도 된다.

빠른 검증 예 (임시 테스트 스크립트, 저장하지 않음):
```ts
import { computeSessionDday, computeEndDateDday } from './lms-dday';

const NOW = new Date('2026-04-12T10:00:00');
console.log(computeSessionDday('2026-04-13', '14:00', '15:00', NOW)); // upcoming, days=1
console.log(computeSessionDday('2026-04-12', '14:00', '15:00', NOW)); // today
console.log(computeEndDateDday('2026-05-31', NOW));                    // upcoming, days=49
console.log(computeEndDateDday('2026-04-09', NOW));                    // past, days=3
```

파일 상단 주석 표와 출력이 일치하는지 확인.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/lib/lms-types.ts frontend/src/lib/lms-dday.ts
git commit -m "feat(lms): D-day 계산 유틸 lms-dday.ts 추가

DashboardResponse 타입에서 dDay를 mentoringEndDate로 교체하고,
프론트에서 D-day를 계산하는 순수 함수 2개(computeSessionDday,
computeEndDateDday)와 DdayState discriminated union 타입 추가.
파일 상단에 검증 케이스 표를 주석으로 남김."
```

> 주: `lms-types.ts`는 Task 3에서 수정했지만 Task 4와 함께 커밋한다 (Task 3 단독으로는 컴파일이 깨짐).

---

## Task 5: `StatCard`에 `href` prop 추가

**Files:**
- Modify: `frontend/src/components/lms/StatCard.tsx`

- [ ] **Step 1: 컴포넌트 수정**

`StatCard.tsx` 전체를 아래로 교체한다.

```tsx
import Link from 'next/link';
import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: string | number;
  icon: LucideIcon;
  color?: string;
  href?: string;
}

export default function StatCard({ label, value, icon: Icon, color = 'blue', href }: StatCardProps) {
  const colorMap: Record<string, string> = {
    blue: 'from-blue-500 to-cyan-400',
    green: 'from-green-500 to-emerald-400',
    purple: 'from-purple-500 to-violet-400',
    orange: 'from-orange-500 to-amber-400',
  };

  const content = (
    <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6 h-full transition-all duration-200 group-hover:bg-[#121828] group-hover:border-white/10">
      <div className="flex items-center justify-between mb-4">
        <span className="text-gray-400 text-sm">{label}</span>
        <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${colorMap[color] || colorMap.blue} flex items-center justify-center`}>
          <Icon size={20} className="text-white" />
        </div>
      </div>
      <p className="text-white text-3xl font-bold">{value}</p>
    </div>
  );

  if (!href) {
    return <div className="h-full">{content}</div>;
  }

  return (
    <Link
      href={href}
      className="group block h-full rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50"
    >
      {content}
    </Link>
  );
}
```

핵심 변경:
- `Link` import 추가
- `href?: string` prop 추가
- 내부 `<div>`를 `content` 변수로 추출, `h-full` + `transition-all` + `group-hover` 스타일 추가
- `href` 없으면 기존과 동일한 non-interactive 카드, 있으면 `<Link>` 래핑 + `group` 클래스 + focus ring

- [ ] **Step 2: 타입 체크**

```bash
cd frontend && npx tsc --noEmit
```
예상: `StatCard.tsx` 자체는 에러 없음. `dashboard/page.tsx`의 기존 에러만 남음.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/lms/StatCard.tsx
git commit -m "feat(lms): StatCard에 href prop 추가해 링크 래핑 옵션화

href 지정 시 Next Link로 래핑되고, 키보드 포커스 링과 hover 전이
효과 적용. 기존 href 없는 호출은 동작 변화 없음."
```

---

## Task 6: `DDayStatCard.tsx` 신규 작성

**Files:**
- Create: `frontend/src/components/lms/DDayStatCard.tsx`

- [ ] **Step 1: 파일 생성**

`frontend/src/components/lms/DDayStatCard.tsx`를 아래 내용으로 생성.

```tsx
import Link from 'next/link';
import { Clock, Calendar, GraduationCap, ArrowRight } from 'lucide-react';
import type { DdayState } from '@/lib/lms-dday';

interface DDayStatCardProps {
  sessionDday: DdayState;
  sessionDate: string | null;
  sessionStartTime: string | null;
  endDateDday: DdayState;
  endDate: string | null;
  matchingId: number;
}

const KO_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function formatSessionDate(date: string | null, startTime: string | null): string | null {
  if (!date) return null;
  const d = new Date(date);
  if (Number.isNaN(d.getTime())) return null;
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const w = KO_WEEKDAYS[d.getDay()];
  return startTime ? `${mm}/${dd} (${w}) ${startTime}` : `${mm}/${dd} (${w})`;
}

function formatEndDate(date: string | null): string | null {
  if (!date) return null;
  const d = new Date(date);
  if (Number.isNaN(d.getTime())) return null;
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const w = KO_WEEKDAYS[d.getDay()];
  return `${mm}/${dd} (${w})`;
}

function valueColor(state: DdayState): string {
  switch (state.kind) {
    case 'none':
    case 'past':
      return 'text-gray-500';
    case 'today':
      return 'text-amber-400';
    case 'inProgress':
      return 'text-green-400';
    case 'upcoming':
    default:
      return 'text-white';
  }
}

export default function DDayStatCard({
  sessionDday,
  sessionDate,
  sessionStartTime,
  endDateDday,
  endDate,
  matchingId,
}: DDayStatCardProps) {
  const sessionAbsolute = sessionDday.kind === 'upcoming'
    ? formatSessionDate(sessionDate, sessionStartTime)
    : null;
  const endAbsolute = endDateDday.kind === 'upcoming'
    ? formatEndDate(endDate)
    : null;

  return (
    <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6 h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <span className="text-gray-400 text-sm">D-Day</span>
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-orange-500 to-amber-400 flex items-center justify-center">
          <Clock size={20} className="text-white" />
        </div>
      </div>

      {/* 두 행 */}
      <div className="flex-1 flex flex-col -mx-2">
        {/* 다음 세션 */}
        <Link
          href={`/lms/sessions?matchingId=${matchingId}`}
          className="group/row flex items-start gap-3 px-2 py-2 rounded-lg transition-colors hover:bg-white/3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50"
        >
          <Calendar size={16} className="text-gray-500 mt-1 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-gray-500">다음 세션</p>
            <p className={`text-lg font-bold ${valueColor(sessionDday)} flex items-center gap-2`}>
              {sessionDday.label}
              {sessionDday.kind === 'inProgress' && (
                <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
              )}
            </p>
            {sessionAbsolute && (
              <p className="text-xs text-gray-500 mt-0.5">{sessionAbsolute}</p>
            )}
          </div>
          <ArrowRight
            size={14}
            className="text-gray-600 mt-1 shrink-0 opacity-0 group-hover/row:opacity-100 transition-opacity"
          />
        </Link>

        {/* 구분선 */}
        <div className="border-t border-white/5 my-1 mx-2" />

        {/* 멘토링 종료 */}
        <Link
          href={`/lms/curriculum?matchingId=${matchingId}`}
          className="group/row flex items-start gap-3 px-2 py-2 rounded-lg transition-colors hover:bg-white/3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50"
        >
          <GraduationCap size={16} className="text-gray-500 mt-1 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-gray-500">멘토링 종료</p>
            <p className={`text-lg font-bold ${valueColor(endDateDday)}`}>
              {endDateDday.label}
            </p>
            {endAbsolute && (
              <p className="text-xs text-gray-500 mt-0.5">{endAbsolute}</p>
            )}
          </div>
          <ArrowRight
            size={14}
            className="text-gray-600 mt-1 shrink-0 opacity-0 group-hover/row:opacity-100 transition-opacity"
          />
        </Link>
      </div>
    </div>
  );
}
```

구조 포인트:
- 외곽 `<div>`는 non-interactive, 내부 두 `<Link>`만 클릭 타겟 → 이벤트 버블링 충돌 없음
- 두 링크 각각 `focus-visible:ring` → 키보드 네비게이션 지원
- `group/row`로 행별 hover 영역 독립
- `sessionAbsolute`/`endAbsolute`는 `upcoming` 상태에서만 렌더 (나머지 상태는 label 자체에 정보 포함)
- `h-full flex flex-col` + `flex-1` → 다른 StatCard와 높이 맞춰 stretch

- [ ] **Step 2: 타입 체크**

```bash
cd frontend && npx tsc --noEmit
```
예상: `DDayStatCard.tsx` 자체는 에러 없음. `dashboard/page.tsx` 기존 에러만 남음.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/lms/DDayStatCard.tsx
git commit -m "feat(lms): DDayStatCard 신규 — 다음 세션 + 멘토링 종료 2행 구조

외곽 div는 non-interactive, 내부 두 Link가 각각 sessions/curriculum
페이지로 이동. DdayState.kind에 따라 색상 분기(오늘=amber, 진행중=green
+펄스, past=회색). upcoming 상태에서만 절대 날짜 보조 텍스트 표시."
```

---

## Task 7: 대시보드 페이지 — StatCard 섹션 교체

**Files:**
- Modify: `frontend/src/app/lms/dashboard/page.tsx:1-56`

- [ ] **Step 1: import 수정**

`dashboard/page.tsx` 상단 import 블록을 수정한다.

변경 전 (line 1-9):
```tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { TrendingUp, Calendar, ClipboardList, Clock, MessageCircle, Video } from 'lucide-react';
import StatCard from '@/components/lms/StatCard';
import ActivityFeed from '@/components/lms/ActivityFeed';
import { getDashboard } from '@/lib/lms';
import type { DashboardResponse } from '@/lib/lms-types';
```

변경 후:
```tsx
'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { TrendingUp, Calendar, ClipboardList, MessageCircle, Video } from 'lucide-react';
import StatCard from '@/components/lms/StatCard';
import DDayStatCard from '@/components/lms/DDayStatCard';
import ActivityFeed from '@/components/lms/ActivityFeed';
import { getDashboard } from '@/lib/lms';
import { computeSessionDday, computeEndDateDday } from '@/lib/lms-dday';
import type { DashboardResponse } from '@/lib/lms-types';
```

변경점:
- `Clock` 제거 (이제 `DDayStatCard` 내부에서 사용)
- `DDayStatCard` import 추가
- `computeSessionDday`, `computeEndDateDday` import 추가

- [ ] **Step 2: StatCard 섹션 교체**

`dashboard/page.tsx`에서 상단 통계 카드 섹션(line 50-56)을 교체한다.

변경 전:
```tsx
{/* 통계 카드 */}
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
  <StatCard label="진도율" value={`${data.progressRate}%`} icon={TrendingUp} color="blue" />
  <StatCard label="출석률" value={`${data.attendanceRate}%`} icon={Calendar} color="green" />
  <StatCard label="과제" value={`${data.assignmentStats.submitted}/${data.assignmentStats.total}`} icon={ClipboardList} color="purple" />
  <StatCard label="D-Day" value={`D-${data.dDay}`} icon={Clock} color="orange" />
</div>
```

변경 후:
```tsx
{/* 통계 카드 */}
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 items-stretch">
  <StatCard
    label="진도율"
    value={`${data.progressRate}%`}
    icon={TrendingUp}
    color="blue"
    href={`/lms/curriculum?matchingId=${matchingId}`}
  />
  <StatCard
    label="출석률"
    value={`${data.attendanceRate}%`}
    icon={Calendar}
    color="green"
    href={`/lms/sessions?matchingId=${matchingId}`}
  />
  <StatCard
    label="과제"
    value={`${data.assignmentStats.submitted}/${data.assignmentStats.total}`}
    icon={ClipboardList}
    color="purple"
    href={`/lms/assignments?matchingId=${matchingId}`}
  />
  <DDayStatCard
    sessionDday={computeSessionDday(
      data.nextSession?.date ?? null,
      data.nextSession?.startTime ?? null,
      data.nextSession?.endTime ?? null
    )}
    sessionDate={data.nextSession?.date ?? null}
    sessionStartTime={data.nextSession?.startTime ?? null}
    endDateDday={computeEndDateDday(data.mentoringEndDate)}
    endDate={data.mentoringEndDate}
    matchingId={matchingId}
  />
</div>
```

- [ ] **Step 3: 타입 체크 전체 통과 확인**

```bash
cd frontend && npx tsc --noEmit
```
예상: 에러 없이 종료 (`Exit code 0`).

- [ ] **Step 4: 빌드 확인**

```bash
cd frontend && npm run build
```
예상: BUILD SUCCESS. `/lms/dashboard` 페이지가 `○ (Static)` 또는 `λ (Dynamic)`으로 표시됨.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/app/lms/dashboard/page.tsx
git commit -m "feat(lms): 대시보드 상단 카드에 네비게이션과 D-Day 2행 카드 연결

- 진도율/출석률/과제 카드에 href 추가 → 각 상세 페이지 진입
- 기존 D-Day StatCard를 DDayStatCard로 교체 (다음 세션 + 멘토링 종료)
- D-day 계산은 computeSessionDday/computeEndDateDday 유틸로 프론트 수행"
```

---

## Task 8: 수동 검증 (로컬 실행)

**Files:** 변경 없음 — 런타임 검증 전용.

- [ ] **Step 1: 백엔드 실행**

```bash
cd backend && ./gradlew bootRun
```
`Started DevmatchApplication` 로그 확인 후 유지.

- [ ] **Step 2: 프론트 실행**

다른 터미널에서:
```bash
cd frontend && npm run dev
```
`http://localhost:3000` 기동 확인.

- [ ] **Step 3: 정상 케이스 확인**

1. 브라우저에서 로그인 (ACCEPTED/TRIAL 상태 매칭이 있는 계정)
2. `/mypage`에서 "LMS" 버튼 클릭 → `/lms/dashboard?matchingId=N` 진입
3. 상단 카드 4개 확인:
   - 진도율, 출석률, 과제가 정상 표시되고 **마우스 호버 시** 살짝 밝아지는지
   - D-Day 카드에 "다음 세션 D-N" + "멘토링 종료 D-N" 두 행이 보이는지
   - D-Day 카드의 두 행에 호버하면 각각 배경이 밝아지고 우측 화살표가 나타나는지

- [ ] **Step 4: 네비게이션 확인**

각 카드를 클릭해서 아래대로 이동하는지:
- 진도율 → `/lms/curriculum?matchingId=N`
- 출석률 → `/lms/sessions?matchingId=N`
- 과제 → `/lms/assignments?matchingId=N`
- D-Day 다음 세션 행 → `/lms/sessions?matchingId=N`
- D-Day 멘토링 종료 행 → `/lms/curriculum?matchingId=N`

- [ ] **Step 5: 키보드 접근성 확인**

대시보드에서 `Tab` 키를 눌러 네 카드에 순서대로 포커스가 가는지, `Enter`로 이동되는지, 포커스 링(파란색)이 보이는지 확인.

D-Day 카드 안에서는 두 링크에 **각각** Tab으로 포커스가 가야 함.

- [ ] **Step 6: 빈 상태 확인**

DB에서 해당 matching의 커리큘럼을 임시로 삭제하거나, 커리큘럼이 없는 매칭으로 진입:
- "멘토링 종료" 행이 "커리큘럼 미설정"으로 표시되는지
- 클릭하면 여전히 `/lms/curriculum`으로 이동하는지

예정 세션이 없는 경우:
- "다음 세션" 행이 "예정된 세션 없음"으로 표시되는지
- 클릭하면 `/lms/sessions`로 이동하는지

- [ ] **Step 7: 경계 케이스 확인 (선택)**

DB에서 세션 하나의 `session_date`를 오늘로, `start_time`/`end_time`을 현재 시각 주변으로 조정해서:
- 시작 전: "D-0 · 오늘 HH:mm" (앰버)
- 진행 중: "진행 중" + 초록 펄스 점
- 종료 직후: "D+0" (회색)

이 과정이 번거로우면 브라우저 DevTools로 `now` 값을 임시 변경해 검증 — 단, 구현이 `new Date()`를 직접 쓰므로 이 경우엔 코드를 잠깐 건드려야 함. 번거로우면 스킵하고 Step 6까지만 확인.

- [ ] **Step 8: 회귀 확인**

다른 페이지(커리큘럼, 세션, 과제)가 정상 동작하는지 빠르게 확인 — 특히 `matchingId` 쿼리 파라미터로 진입 시 기존과 동일하게 데이터를 로딩하는지.

- [ ] **Step 9: 에러 로그 확인**

브라우저 DevTools Console, Network 탭에 빨간 에러 없는지 확인. 백엔드 콘솔에도 스택 트레이스 없는지 확인.

- [ ] **Step 10: 검증 완료 — 커밋 불필요**

코드 변경 없음. 모든 검증 통과 시 완료.

---

## 실패 시 디버깅 가이드

**타입 에러 `Property 'dDay' does not exist`**
→ Task 7 Step 2를 아직 안 한 것. `dashboard/page.tsx`의 `data.dDay` 참조를 교체.

**런타임 에러 `Cannot read properties of null (reading 'date')`**
→ `data.nextSession?.date` 옵셔널 체이닝을 빠뜨림. Task 7 Step 2 코드 그대로 복사해 사용.

**D-Day 카드 높이가 다른 카드와 어긋남**
→ `items-stretch` 그리드 클래스 누락. Task 7 Step 2의 `<div className="grid ... items-stretch">` 확인.

**화살표가 항상 보임 / 안 보임**
→ `group/row` Tailwind 문법 확인. Tailwind 3.4 이상에서 지원. `package.json`에 `tailwindcss: ^3.4.0` 확인됨.

**절대 날짜가 "NaN/undefined"로 나옴**
→ 백엔드가 `LocalDate.toString()`으로 "YYYY-MM-DD" 내려주는지 확인. `curriculum.endDate`가 null일 때 `null`이 그대로 JSON으로 내려가는지 확인.

**백엔드 `mentoringEndDate`가 응답에 없음**
→ `DashboardResponse.java`의 필드 이름과 `LmsDashboardService.java`의 `.mentoringEndDate(...)` 호출명 일치 확인 (Lombok builder는 필드명 기반).

---

## 스코프 외 (이번 계획에서 하지 않음)

- 다른 LMS 페이지의 카드 클릭 가능화
- 과제 필터링 쿼리 파라미터
- D-0 푸시 알림
- 프론트 테스트 프레임워크(jest/vitest) 도입
- D-day 자동 재계산 타이머

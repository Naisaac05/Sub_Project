---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "LMS 대시보드 D-day 표시 및 카드 네비게이션 설계 상세 요구사항 및 기능 동작 명세서"

---

# LMS 대시보드 D-day 표시 및 카드 네비게이션 설계

- **작성일**: 2026-04-12
- **대상**: LMS 대시보드 상단 StatCard 영역
- **목적**: (1) 다음 세션 시작과 멘토링 종료일을 D-day로 표시하고, (2) 상단 카드를 각 상세 페이지로 이동하는 진입점으로 만든다.

---

## 1. 배경과 문제

현재 대시보드 (`frontend/src/app/lms/dashboard/page.tsx:51-56`) 상단에는 StatCard 4개(진도율 / 출석률 / 과제 / D-Day)가 있지만:

1. **D-Day의 의미가 모호** — 현재 `dDay`는 커리큘럼 종료일까지의 남은 일수만 의미하는데 라벨에 명시되지 않아 "무엇의 D-day인지" 혼동된다 (`backend/.../LmsDashboardService.java:62-68`).
2. **카드가 정적** — 클릭해도 아무 동작이 없어 상세 페이지로 이동하려면 사이드바를 거쳐야 한다.
3. **"다음 세션 시작일" 정보가 한눈에 안 보임** — 하단 "다음 멘토링" 섹션에 있지만 스크롤이 필요하다.

사용자는 상단 카드에서 (a) 가장 임박한 세션 시작, (b) 멘토링 종료일 두 가지를 동시에 보고, 필요 시 각 상세 페이지로 바로 이동하길 원한다.

## 2. 설계 원칙

- **한 카드 안에 관련 정보를 묶되, 링크는 분리한다** — D-Day 카드는 두 개의 독립된 클릭 행으로 구성.
- **라벨은 명시적으로** — "D-Day" 단독이 아니라 "다음 세션" / "멘토링 종료"로 무엇에 대한 카운트다운인지 밝힌다.
- **D-day 계산은 프론트에서** — 서버가 미리 계산해 내려주면 자정 경계에서 하루 어긋나고, 캐시/재검증과 엮여 복잡해진다. 서버는 절대 날짜(ISO)만 내려준다.
- **경계 상태를 상태 객체로 모델링** — 문자열이 아니라 `DdayState` discriminated union을 반환해 UI가 `kind`로 분기한다.
- **접근성 1급 처리** — 링크 래핑, `focus-visible` 링, 키보드 Tab 네비게이션 작동.

## 3. 백엔드 변경

### 3.1 `DashboardResponse.java`

`dDay: long` 필드를 삭제하고 `mentoringEndDate: String`을 추가한다. 다음 세션 날짜는 기존 `nextSession.date` 필드를 그대로 재사용한다 (중복 금지).

```java
// 변경 전
long dDay;

// 변경 후
String mentoringEndDate;  // "2026-05-31" 형식, 커리큘럼 없으면 null
```

### 3.2 `LmsDashboardService.java`

`getDashboard()` 내부에서 `dDay` 계산 블록(line 62-68)을 제거하고 커리큘럼 로딩 블록에서 `curriculum.getEndDate().toString()`을 `mentoringEndDate`로 담는다. 커리큘럼이 없으면 `null`.

`nextSession` 로직(line 81-94)은 변경 없음 — 이미 "오늘 이상인 SCHEDULED 세션 중 가장 빠른 것"을 반환하고 있음.

### 3.3 백엔드 테스트

- `LmsDashboardService` 테스트가 있으면 `dDay` 검증을 `mentoringEndDate` 검증으로 교체.
- 커리큘럼 없는 케이스에서 `mentoringEndDate == null` 명시적 assertion 추가.

## 4. 프론트엔드 변경

### 4.1 타입 — `frontend/src/lib/lms-types.ts`

```ts
export interface DashboardResponse {
  progressRate: number;
  attendanceRate: number;
  mentoringEndDate: string | null;  // ← 신규 (ISO date)
  assignmentStats: { total: number; submitted: number; reviewed: number; };
  nextSession: { id: number; date: string; startTime: string; endTime: string;
                 meetLink: string; category: string; } | null;
  recentActivities: { type: string; title: string; createdAt: string; }[];
  mentorInfo: { name: string; specialty: string[]; email: string; };
  communicationLinks: { discord: string | null; jitsiMeet: string | null; };
}
```

### 4.2 D-day 유틸 — `frontend/src/lib/lms-dday.ts` (신규)

순수 함수 모듈. `now`를 주입 가능하게 해 테스트에서 `Date` 모킹 불필요.

```ts
export type DdayState =
  | { kind: 'none'; label: string }
  | { kind: 'upcoming'; days: number; label: string }
  | { kind: 'today'; label: string }
  | { kind: 'inProgress'; label: string }   // 세션 전용
  | { kind: 'past'; days: number; label: string };

export function computeSessionDday(
  date: string | null,
  startTime: string | null,
  endTime: string | null,
  now?: Date
): DdayState;

export function computeEndDateDday(
  endDate: string | null,
  now?: Date
): DdayState;
```

**계산 규칙**

- `days`는 자정 기준 (시/분/초 무시): `(target - 오늘자정) / 1일`
- `days > 0` → `upcoming`, `days == 0` → `today`, `days < 0` → `past` (|days| 저장)
- **세션만**: `today` + 현재 시각이 `[startTime, endTime)` 사이면 `inProgress`로 승격
- 입력이 `null`이거나 `new Date(input)`가 `NaN`이면 `none`
  - 다음 세션 `none`의 label: `"예정된 세션 없음"`
  - 종료일 `none`의 label: `"커리큘럼 미설정"`

**label 포맷 예시**

| kind | 세션 | 종료 |
|---|---|---|
| `upcoming` | `"D-3"` | `"D-42"` |
| `today` | `"D-0 · 오늘 14:00"` | `"D-0 · 오늘 종료"` |
| `inProgress` | `"진행 중"` | — |
| `past` | `"D+1"` (방어적) | `"D+3 · 종료됨"` |

### 4.3 유틸 검증

프로젝트 프론트엔드에 테스트 프레임워크(jest/vitest)가 없어 단일 유틸 모듈을 위한 프레임워크 도입은 스코프 밖이다. 대신 아래 두 가지로 검증한다.

**(a) 타입 안전성** — `DdayState` discriminated union + TypeScript strict mode로 컴포넌트에서 `kind` 미처리 시 타입 에러로 드러남.

**(b) 수동 검증 케이스** (dev 서버에서 `now` 인자 주입으로 확인)

`computeSessionDday(date, start, end, now)`:
- `null, null, null, *` → `none`
- `"2026-04-13", "14:00", "15:00", 2026-04-12 23:00` → `upcoming, days=1`
- `"2026-04-12", "14:00", "15:00", 2026-04-12 10:00` → `today`
- `"2026-04-12", "14:00", "15:00", 2026-04-12 14:30` → `inProgress`
- `"2026-04-12", "14:00", "15:00", 2026-04-12 15:30` → `past, days=0`
- `"2026-04-11", _, _, 2026-04-12` → `past, days=1`
- `"not-a-date", _, _, *` → `none`

`computeEndDateDday(endDate, now)`:
- `null` → `none`
- 미래/오늘/과거 → `upcoming/today/past`
- `"not-a-date"` → `none`

위 케이스는 구현 시 `lms-dday.ts` 상단에 주석으로 표 형태로 남기고, 구현이 각 케이스를 만족하는지 눈으로 확인한다. 테스트 프레임워크 도입은 별도 티켓.

### 4.4 `StatCard.tsx` 확장

`href?: string` prop 추가. 있으면 `<Link>`로 래핑, 없으면 기존 `<div>`.

```tsx
interface StatCardProps {
  label: string;
  value: string;
  icon: LucideIcon;
  color: 'blue' | 'green' | 'purple' | 'orange';
  href?: string;
}
```

래퍼 스타일:
```
group block rounded-2xl transition-all duration-200
hover:bg-white/5 hover:border-white/15 hover:scale-[1.01]
focus-visible:ring-2 focus-visible:ring-blue-500/50 focus-visible:outline-none
```

내부 콘텐츠는 기존 그대로.

### 4.5 `DDayStatCard.tsx` (신규)

D-Day 카드는 2행 구조라 `StatCard`로 표현할 수 없어 별도 컴포넌트로 분리.

**Props**
```tsx
interface DDayStatCardProps {
  sessionDday: DdayState;
  sessionDate: string | null;
  endDateDday: DdayState;
  endDate: string | null;
  matchingId: number;
}
```

**레이아웃**
```
┌─────────────────────────────────┐
│ ⏰ D-Day                         │
├─────────────────────────────────┤
│ 📅 다음 세션        D-3        →│ ← Link → /lms/sessions?matchingId=...
│    04/15 (화) 14:00             │
├─────────────────────────────────┤
│ 🎓 멘토링 종료      D-42       →│ ← Link → /lms/curriculum?matchingId=...
│    05/31 (토)                   │
└─────────────────────────────────┘
```

- 외곽 스타일은 다른 StatCard와 일치 (`glass-card / rounded-2xl`).
- 카드 외곽은 non-interactive `<div>`. 내부 두 행만 `<Link>` — 이벤트 버블링 충돌 없음.
- 각 행에 `group/row hover:bg-white/3` + 우측 `ArrowRight size={14}` (hover 시만 표시).
- 두 행 사이 `border-t border-white/5`.
- 보조 텍스트(절대 날짜)는 `text-xs text-gray-500`, `upcoming` 상태에서만 표시. `none/today/inProgress/past`에서는 label에 이미 충분한 정보가 포함됨.
- 절대 날짜 포맷: `MM/DD (요일) HH:mm` (세션), `MM/DD (요일)` (종료). `toLocaleDateString('ko-KR', …)` 사용.

**상태별 색상**
| kind | 세션 | 종료 |
|---|---|---|
| `none` | `text-gray-500` | `text-gray-500` |
| `upcoming` | `text-white` | `text-white` |
| `today` | `text-amber-400` | `text-amber-400` |
| `inProgress` | `text-green-400` + 펄스 점 | — |
| `past` | `text-gray-500` | `text-gray-500` |

### 4.6 대시보드 페이지 교체

`dashboard/page.tsx:51-56` 블록만 교체.

```tsx
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 items-stretch">
  <StatCard label="진도율" value={`${data.progressRate}%`} icon={TrendingUp}
            color="blue" href={`/lms/curriculum?matchingId=${matchingId}`} />
  <StatCard label="출석률" value={`${data.attendanceRate}%`} icon={Calendar}
            color="green" href={`/lms/sessions?matchingId=${matchingId}`} />
  <StatCard label="과제"
            value={`${data.assignmentStats.submitted}/${data.assignmentStats.total}`}
            icon={ClipboardList} color="purple"
            href={`/lms/assignments?matchingId=${matchingId}`} />
  <DDayStatCard
    sessionDday={computeSessionDday(
      data.nextSession?.date ?? null,
      data.nextSession?.startTime ?? null,
      data.nextSession?.endTime ?? null
    )}
    sessionDate={data.nextSession?.date ?? null}
    endDateDday={computeEndDateDday(data.mentoringEndDate)}
    endDate={data.mentoringEndDate}
    matchingId={matchingId}
  />
</div>
```

`items-stretch` + 다른 카드들을 `h-full`로 설정해 4개 카드 높이 통일.

## 5. 검증

### 5.1 자동 테스트
- 백엔드 테스트(있을 경우) — `mentoringEndDate` 필드 교체 검증.
- 프론트 유틸은 4.3의 수동 검증 케이스로 대체 (테스트 프레임워크 부재).

### 5.2 수동 검증 시나리오

다음 4개 시드 상태를 DB에 만들어 눈으로 확인한다.

1. **정상** — 미래 세션 + 미래 endDate → "D-3" / "D-42"
2. **빈 상태** — 커리큘럼 미생성 + 세션 미예약 → "예정된 세션 없음" / "커리큘럼 미설정". 링크 클릭 시 각 페이지로 이동.
3. **경계** — 오늘 세션 시작 직전/진행 중/직후, 오늘이 endDate인 경우.
4. **종료** — endDate가 과거 → "D+N · 종료됨".

**네비게이션 검증**
- 4개 카드 hover 효과 일관성.
- 키보드 Tab → Enter로 각 카드 이동.
- D-Day 카드 두 행이 **각각 다른 페이지**로 이동.

## 6. 실패 시나리오

- **세션 종료 직후, `nextSession` 레이스** — 백엔드 필터가 날짜 기반(`LmsDashboardService.java:83`)이라 당일 22시 세션이 23시에 끝나도 자정까지 `nextSession`에 남는다. 이 시점에 `computeSessionDday`는 `inProgress` → `past(days=0)`으로 내려가며 UI는 "진행 중" → "D+0"으로 자연스럽게 전환된다. 깨지지 않음.
- **`matchingId` 미존재** — 기존 가드(`dashboard/page.tsx:25-31`)가 이미 처리.
- **`endDate < startDate`** — 백엔드 엔티티 검증이 책임. 프론트는 음수 days를 `past`로 처리하므로 UI는 깨지지 않음.
- **잘못된 ISO 문자열** — `computeEndDateDday`/`computeSessionDday`가 방어적으로 `none` 반환.

## 7. 스코프 밖 (이번 작업에 포함하지 않음)

- 다른 LMS 페이지의 카드 클릭 가능화 (대시보드 한정).
- 과제 필터링 쿼리 파라미터 (`?filter=pending` 등).
- D-0 당일 푸시 알림.
- D-day 자동 재계산 (타이머). 페이지 재방문 시 재계산으로 충분.
- 모바일 long-press / 고급 제스처.
- 프론트 테스트 프레임워크(jest/vitest) 도입.

## 8. 파일 변경 요약

**백엔드 (2 파일)**
- `backend/src/main/java/com/devmatch/dto/lms/DashboardResponse.java`
- `backend/src/main/java/com/devmatch/service/LmsDashboardService.java`

**프론트 (5 파일)**
- `frontend/src/lib/lms-types.ts` — 타입 교체
- `frontend/src/lib/lms-dday.ts` — 신규 유틸 (상단에 검증 케이스 주석)
- `frontend/src/components/lms/StatCard.tsx` — `href` prop 추가
- `frontend/src/components/lms/DDayStatCard.tsx` — 신규
- `frontend/src/app/lms/dashboard/page.tsx` — 카드 섹션 교체

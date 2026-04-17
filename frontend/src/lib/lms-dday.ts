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

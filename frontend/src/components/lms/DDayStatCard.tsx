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

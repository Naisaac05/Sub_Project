'use client';

import { useEffect, useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { getQueueStatus, type QueueStatus } from '@/lib/payment';

const POLL_INTERVAL_MS = 3000;
/** 대략적인 예상 대기 시간 계산에 쓰는 초당 처리량(서버 승격 배치와 맞춘 값). */
const PROMOTE_PER_SECOND = 100 / 1;

interface WaitingRoomProps {
  /** 진입 시점에 받은 최초 상태 (POST /queue/enter 응답) */
  initialStatus: QueueStatus;
  /** 입장 확정 시 호출 — 부모가 결제 단계로 전환한다 */
  onAdmitted: () => void;
  /** 폴링 중 오류가 반복될 때 */
  onError?: (message: string) => void;
}

function formatEta(rank: number): string {
  const seconds = Math.ceil(rank / PROMOTE_PER_SECOND);
  if (seconds < 60) return '1분 이내';
  return `약 ${Math.ceil(seconds / 60)}분`;
}

export default function WaitingRoom({ initialStatus, onAdmitted, onError }: WaitingRoomProps) {
  const [status, setStatus] = useState<QueueStatus>(initialStatus);
  // 최초 진입 순번 — 진행률 바의 분모로 쓴다 (줄어든 만큼 채워짐)
  const initialRankRef = useRef<number>(initialStatus.rank ?? 0);
  const failCountRef = useRef(0);

  useEffect(() => {
    let cancelled = false;

    const timer = setInterval(async () => {
      try {
        const res = await getQueueStatus();
        if (cancelled) return;

        const next = res.data;
        failCountRef.current = 0;

        if (next.active) {
          clearInterval(timer);   // 입장 확정 — 더 이상 폴링하지 않는다
          onAdmitted();
          return;
        }
        setStatus(next);
      } catch {
        if (cancelled) return;
        // 일시적 오류는 무시하고 재시도. 연속 실패가 쌓이면 상위에 알린다.
        failCountRef.current += 1;
        if (failCountRef.current >= 3) {
          clearInterval(timer);
          onError?.('대기열 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.');
        }
      }
    }, POLL_INTERVAL_MS);

    // 페이지를 떠나거나 컴포넌트가 사라지면 반드시 타이머를 정리한다.
    // 정리하지 않으면 사용자가 나가도 요청이 계속 나가, 부하를 줄이려 만든 대기열이
    // 오히려 부하를 만든다.
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [onAdmitted, onError]);

  const rank = status.rank ?? 0;
  const total = status.total ?? 0;
  const initialRank = initialRankRef.current;
  const progress = initialRank > 0
    ? Math.min(100, Math.max(0, Math.round(((initialRank - rank) / initialRank) * 100)))
    : 0;

  return (
    <Card className="mx-auto w-full max-w-md">
      <CardContent className="flex flex-col items-center px-6 py-10 text-center">
        <Loader2 className="mb-4 h-10 w-10 animate-spin text-indigo-500" aria-hidden />

        <p className="text-3xl font-extrabold tracking-tight text-gray-900">
          {rank.toLocaleString('ko-KR')}
          <span className="ml-1 text-base font-semibold text-gray-600">명 앞에 있어요</span>
        </p>
        <p className="mt-1.5 text-sm text-gray-500">예상 대기 {formatEta(rank)}</p>

        <div
          className="mt-4 h-[7px] w-full overflow-hidden rounded-full bg-gray-100"
          role="progressbar"
          aria-valuenow={progress}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="대기열 진행률"
        >
          <div
            className="h-full rounded-full bg-indigo-500 transition-[width] duration-500"
            style={{ width: `${progress}%` }}
          />
        </div>

        {total > 0 && (
          <p className="mt-2 text-sm text-gray-500">
            전체 대기 {total.toLocaleString('ko-KR')}명
          </p>
        )}
        <p className="mt-3 text-xs text-gray-400">창을 닫으면 순번이 사라져요</p>
      </CardContent>
    </Card>
  );
}

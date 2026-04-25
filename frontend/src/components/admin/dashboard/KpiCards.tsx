'use client';

import type { DashboardKpi } from '@/lib/admin/dashboard';
import { Users, Wallet, Handshake, UserCheck } from 'lucide-react';

const KRW = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 });
const NUM = new Intl.NumberFormat('ko-KR');

function DeltaLabel({ value, percent }: { value: number; percent: number | null }) {
  if (percent === null) return <span className="text-slate-400">—</span>;
  const up = value >= 0;
  return (
    <span className={up ? 'text-emerald-600' : 'text-red-600'}>
      {up ? '▲' : '▼'} {Math.abs(percent).toFixed(1)}%
    </span>
  );
}

export function KpiCards({ kpi }: { kpi: DashboardKpi }) {
  const cards = [
    { label: '활성 회원', value: NUM.format(kpi.totalActiveUsers.current), icon: Users,
      sub: <>지난달 대비 <DeltaLabel value={kpi.totalActiveUsers.deltaFromLastMonth} percent={kpi.totalActiveUsers.deltaPercent} /></> },
    { label: '이번달 순매출', value: KRW.format(kpi.currentMonthRevenue.current), icon: Wallet,
      sub: <>지난달 대비 <DeltaLabel value={kpi.currentMonthRevenue.deltaFromLastMonth} percent={kpi.currentMonthRevenue.deltaPercent} /></> },
    { label: '누적 매칭', value: NUM.format(kpi.totalAcceptedMatchings.current), icon: Handshake,
      sub: <span className="text-slate-500">이번달 +{NUM.format(kpi.totalAcceptedMatchings.newThisMonth)}</span> },
    { label: '승인 멘토', value: NUM.format(kpi.approvedMentors.current), icon: UserCheck,
      sub: <span className="text-slate-500">대기 {NUM.format(kpi.approvedMentors.pending)}명</span> },
  ];

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((c) => {
        const Icon = c.icon;
        return (
          <div key={c.label} className="rounded-lg border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between text-slate-500">
              <span className="text-sm font-medium">{c.label}</span>
              <Icon className="h-4 w-4" aria-hidden />
            </div>
            <div className="mt-2 text-2xl font-semibold text-slate-900">{c.value}</div>
            <div className="mt-1 text-xs">{c.sub}</div>
          </div>
        );
      })}
    </div>
  );
}

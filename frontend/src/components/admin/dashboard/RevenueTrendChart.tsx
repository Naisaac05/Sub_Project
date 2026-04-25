'use client';

import type { RevenueTrendPoint } from '@/lib/admin/dashboard';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, ReferenceLine } from 'recharts';

const KRW = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 });

export function RevenueTrendChart({ data }: { data: RevenueTrendPoint[] }) {
  const chartData = data.map((p) => ({
    month: p.month.slice(2),  // YY-MM
    gross: p.grossRevenue,
    refund: p.refundAmount,
    net: p.netRevenue,
  }));

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h3 className="mb-4 text-sm font-semibold text-slate-900">월별 순매출 (최근 12개월)</h3>
      <div className="h-[240px]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
            <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#94a3b8" />
            <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8"
                   tickFormatter={(v) => `${(v / 1_000_000).toFixed(0)}M`} />
            <Tooltip
              contentStyle={{ fontSize: 12, borderRadius: 6 }}
              formatter={(v: number, name: string) => {
                const label = name === 'gross' ? '매출' : name === 'refund' ? '환불' : '순매출';
                return [KRW.format(v), label];
              }}
            />
            <ReferenceLine y={0} stroke="#64748b" />
            <Bar dataKey="net" fill="#f97316" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

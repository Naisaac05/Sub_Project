'use client';

import Link from 'next/link';
import type { DashboardQueue } from '@/lib/admin/dashboard';
import { UserCheck, CreditCard, ChevronRight } from 'lucide-react';

export function ActionQueue({ queue }: { queue: DashboardQueue }) {
  const items = [
    {
      label: '승인 대기 멘토',
      count: queue.pendingMentorCount,
      href: '/admin/mentor',
      icon: UserCheck,
      emphasize: queue.pendingMentorCount > 0,
    },
    {
      label: '실패 결제',
      count: queue.failedPaymentCount,
      href: '/admin/payments?status=FAILED',
      icon: CreditCard,
      emphasize: queue.failedPaymentCount > 0,
    },
  ];

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-5 py-3">
        <h3 className="text-sm font-semibold text-slate-900">처리 큐</h3>
      </div>
      <ul className="divide-y divide-slate-200">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className="flex items-center justify-between px-5 py-4 hover:bg-slate-50"
              >
                <div className="flex items-center gap-3">
                  <Icon className="h-4 w-4 text-slate-500" aria-hidden />
                  <span className="text-sm text-slate-700">{item.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={
                    'text-sm font-semibold ' +
                    (item.emphasize ? 'text-amber-600' : 'text-slate-400')
                  }>
                    {item.count}{item.label.endsWith('멘토') ? '명' : '건'}
                  </span>
                  <ChevronRight className="h-4 w-4 text-slate-400" aria-hidden />
                </div>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

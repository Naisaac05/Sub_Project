'use client';

import Link from 'next/link';
import type { AuditLogItem } from '@/lib/admin/dashboard';

function formatRelative(iso: string): string {
  const d = new Date(iso).getTime();
  const now = Date.now();
  const diffMin = Math.round((now - d) / 60_000);
  if (diffMin < 1) return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHr = Math.round(diffMin / 60);
  if (diffHr < 24) return `${diffHr}시간 전`;
  const diffDay = Math.round(diffHr / 24);
  return `${diffDay}일 전`;
}

export function RecentAuditLog({ items }: { items: AuditLogItem[] }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="border-b border-slate-200 px-5 py-3">
        <h3 className="text-sm font-semibold text-slate-900">최근 관리자 활동</h3>
        <p className="text-xs text-slate-500">최대 10건 · SUPER_ADMIN 만 조회</p>
      </div>
      {items.length === 0 ? (
        <p className="px-5 py-6 text-sm text-slate-400">최근 활동 없음</p>
      ) : (
        <ul className="divide-y divide-slate-200">
          {items.map((item) => (
            <li key={item.id}>
              <Link href={item.targetHref} className="block px-5 py-3 hover:bg-slate-50">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-sm text-slate-800">
                      <span className="font-medium">{item.adminName}</span>: {item.description}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs text-slate-500">
                    {formatRelative(item.createdAt)}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AdminListHeader } from '@/components/admin/AdminListHeader';
import { Pagination } from '@/components/admin/Pagination';
import {
  listAdminMentorChangeRequests,
  type AdminMentorChangeListItem,
  type MentorChangeRequestStatus,
} from '@/lib/admin/mentor-change-requests';

const TABS: Array<{ key: MentorChangeRequestStatus | 'ALL'; label: string }> = [
  { key: 'PENDING', label: '대기' },
  { key: 'APPROVED', label: '승인됨' },
  { key: 'REJECTED', label: '반려됨' },
  { key: 'CANCELLED', label: '취소됨' },
  { key: 'ALL', label: '전체' },
];

const STATUS_BADGE: Record<MentorChangeRequestStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-rose-100 text-rose-800',
  CANCELLED: 'bg-slate-200 text-slate-700',
};

export default function AdminMentorChangeRequestsPage() {
  const [tab, setTab] = useState<MentorChangeRequestStatus | 'ALL'>('PENDING');
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<AdminMentorChangeListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listAdminMentorChangeRequests({
      page,
      size: 20,
      status: tab === 'ALL' ? undefined : tab,
    })
      .then((res) => {
        if (cancelled) return;
        setItems(res.content);
        setTotalPages(res.totalPages);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다');
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [tab, page]);

  return (
    <div className="space-y-6">
      <AdminListHeader title="멘토 교체 신청 관리" />

      <div className="flex gap-1 border-b border-slate-200">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => {
              setTab(t.key);
              setPage(0);
            }}
            className={
              'px-4 py-2 text-sm font-medium border-b-2 transition-colors ' +
              (tab === t.key
                ? 'border-slate-900 text-slate-900'
                : 'border-transparent text-slate-500 hover:text-slate-900')
            }
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase text-slate-500">
            <tr>
              <th className="px-4 py-3">신청일</th>
              <th className="px-4 py-3">멘티</th>
              <th className="px-4 py-3">현재 멘토</th>
              <th className="px-4 py-3">사유</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3">처리일</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  불러오는 중…
                </td>
              </tr>
            )}
            {!loading && error && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-rose-600">
                  {error}
                </td>
              </tr>
            )}
            {!loading && !error && items.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  신청이 없습니다
                </td>
              </tr>
            )}
            {!loading &&
              !error &&
              items.map((it) => (
                <tr key={it.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-600">
                    {new Date(it.createdAt).toLocaleString('ko-KR')}
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      href={`/admin/mentor-change-requests/${it.id}`}
                      className="font-medium text-slate-900 hover:underline"
                    >
                      {it.menteeName}
                    </Link>
                    <div className="text-xs text-slate-500">{it.menteeEmail}</div>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{it.currentMentorName}</td>
                  <td className="px-4 py-3 text-slate-700">{it.reasonPreview}</td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium ' +
                        STATUS_BADGE[it.status]
                      }
                    >
                      {it.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {it.respondedAt
                      ? new Date(it.respondedAt).toLocaleString('ko-KR')
                      : '-'}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
        />
      )}
    </div>
  );
}

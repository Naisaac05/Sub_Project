'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import * as api from '@/lib/admin/users';
import { Pagination } from '@/components/admin/Pagination';
import { DebouncedSearchInput } from '@/components/admin/DebouncedSearchInput';
import { AdminListHeader } from '@/components/admin/AdminListHeader';
import { AdminTabs } from '@/components/admin/AdminTabs';
import { AdminStatusBadge } from '@/components/admin/AdminStatusBadge';
import type { UserRole, UserStatus } from '@/lib/types';

const ROLE_TABS: Array<{ value: UserRole | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'MENTEE', label: '멘티' },
  { value: 'MENTOR', label: '멘토' },
  { value: 'ADMIN', label: '관리자' },
  { value: 'SUPER_ADMIN', label: '슈퍼관리자' },
];

const STATUS_TABS: Array<{ value: UserStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'DEACTIVATED', label: '비활성' },
  { value: 'DELETED', label: '삭제' },
];

const ROLE_KO: Record<UserRole, string> = {
  MENTEE: '멘티',
  MENTOR: '멘토',
  ADMIN: '관리자',
  SUPER_ADMIN: '슈퍼관리자',
};

const STATUS_KO: Record<UserStatus, string> = {
  ACTIVE: '활성',
  DEACTIVATED: '비활성',
  DELETED: '삭제',
};

const ROLE_BADGE: Record<UserRole, string> = {
  MENTEE: 'bg-emerald-100 text-emerald-800',
  MENTOR: 'bg-sky-100 text-sky-800',
  ADMIN: 'bg-violet-100 text-violet-800',
  SUPER_ADMIN: 'bg-red-100 text-red-800',
};

const STATUS_BADGE: Record<UserStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-800',
  DEACTIVATED: 'bg-amber-100 text-amber-800',
  DELETED: 'bg-zinc-200 text-zinc-700',
};

export default function AdminUsersPage() {
  const sp = useSearchParams();
  const router = useRouter();

  const initRole = (sp.get('role') as UserRole | null) ?? 'ALL';
  const initStatus = (sp.get('status') as UserStatus | null) ?? 'ALL';

  const [role, setRole] = useState<UserRole | 'ALL'>(initRole);
  const [status, setStatus] = useState<UserStatus | 'ALL'>(initStatus);
  const [q, setQ] = useState(sp.get('q') ?? '');
  const [page, setPage] = useState(Number(sp.get('page') ?? 0));
  const [data, setData] = useState<api.PageResponse<api.AdminUserListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    api
      .listUsers({
        role: role === 'ALL' ? undefined : role,
        status: status === 'ALL' ? undefined : status,
        q: q || undefined,
        page,
        size: 20,
      })
      .then(setData)
      .catch((e) => setError(e?.response?.data?.message ?? String(e)))
      .finally(() => setLoading(false));
  }, [role, status, q, page]);

  // URL 동기화
  useEffect(() => {
    const params = new URLSearchParams();
    if (role !== 'ALL') params.set('role', role);
    if (status !== 'ALL') params.set('status', status);
    if (q) params.set('q', q);
    if (page > 0) params.set('page', String(page));
    const qs = params.toString();
    router.replace(`/admin/users${qs ? `?${qs}` : ''}`, { scroll: false });
  }, [role, status, q, page, router]);

  return (
    <div className="space-y-4">
      <AdminListHeader
        title="회원 관리"
        description="전체 회원을 조회하고 상태·비밀번호를 관리합니다."
      />

      <AdminTabs
        items={ROLE_TABS}
        value={role}
        onChange={(next) => { setRole(next); setPage(0); }}
        ariaLabel="역할 필터"
        variant="primary"
      />

      <AdminTabs
        items={STATUS_TABS}
        value={status}
        onChange={(next) => { setStatus(next); setPage(0); }}
        ariaLabel="상태 필터"
        variant="secondary"
        trailing={
          <DebouncedSearchInput
            value={q}
            onChange={(next) => {
              setQ(next);
              setPage(0);
            }}
            placeholder="이름·이메일 검색"
          />
        }
      />

      {loading && <div className="text-sm text-slate-500 py-6 text-center">불러오는 중…</div>}
      {error && (
        <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-700">
          에러: {error}
        </div>
      )}

      {data && !loading && (
        <>
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">이름</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">이메일</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">역할</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">상태</th>
                  <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">가입일</th>
                  <th className="px-4 py-2"></th>
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-sm text-slate-500">
                      해당 조건의 회원이 없습니다.
                    </td>
                  </tr>
                )}
                {data.content.map((u) => {
                  const isDeleted = u.status === 'DELETED';
                  return (
                    <tr
                      key={u.id}
                      className={
                        'border-b border-slate-100 ' + (isDeleted ? 'text-slate-400' : 'text-slate-900')
                      }
                    >
                      <td className="px-4 py-3 text-sm">{u.name}</td>
                      <td className="px-4 py-3 text-sm">{u.email}</td>
                      <td className="px-4 py-3">
                        <AdminStatusBadge label={ROLE_KO[u.role]} className={ROLE_BADGE[u.role]} />
                      </td>
                      <td className="px-4 py-3">
                        <AdminStatusBadge label={STATUS_KO[u.status]} className={STATUS_BADGE[u.status]} />
                      </td>
                      <td className="px-4 py-3 text-sm">{u.createdAt.slice(0, 10)}</td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          href={`/admin/users/${u.id}`}
                          className="text-sm text-blue-600 hover:underline"
                        >
                          상세 →
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={data.number} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}

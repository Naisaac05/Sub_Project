'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import * as api from '@/lib/admin/users';
import type { UserResponse, UserRole, UserStatus } from '@/lib/types';
import { PrimaryButton } from '@/components/admin/Modal';
import { CreateAdminModal } from './_CreateAdminModal';
import { AdminListHeader } from '@/components/admin/AdminListHeader';
import { AdminStatusBadge } from '@/components/admin/AdminStatusBadge';

const ROLE_KO: Record<UserRole, string> = {
  MENTEE: '멘티', MENTOR: '멘토', ADMIN: '관리자', SUPER_ADMIN: '슈퍼관리자',
};
const STATUS_KO: Record<UserStatus, string> = {
  ACTIVE: '활성', DEACTIVATED: '비활성', DELETED: '삭제',
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

export default function AdminsPage() {
  const { user: me, isLoading: authLoading } = useAuth();
  const [admins, setAdmins] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const reload = () => {
    setLoading(true);
    setErr(null);
    api.listAdmins()
      .then(setAdmins)
      .catch((e) => setErr((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (me?.role === 'SUPER_ADMIN') {
      reload();
    } else {
      setLoading(false);
    }
  }, [me]);

  if (authLoading) return <div className="text-sm text-slate-500 py-6">불러오는 중…</div>;

  if (me?.role !== 'SUPER_ADMIN') {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-center">
        <p className="text-amber-900 font-semibold">SUPER_ADMIN 권한이 필요합니다</p>
        <p className="text-sm text-amber-700 mt-1">이 페이지는 슈퍼관리자만 접근할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <AdminListHeader
        title="관리자 계정"
        description="ADMIN/SUPER_ADMIN 계정을 조회하고 신규 ADMIN 을 생성합니다."
        actions={<PrimaryButton onClick={() => setShowCreate(true)}>+ 관리자 추가</PrimaryButton>}
      />

      {loading && <div className="text-sm text-slate-500 py-6 text-center">불러오는 중…</div>}
      {err && <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-700">에러: {err}</div>}

      {!loading && (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">이름</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">이메일</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">회사직책</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">역할</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">상태</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-600">가입일</th>
              </tr>
            </thead>
            <tbody>
              {admins.length === 0 && (
                <tr><td colSpan={6} className="py-8 text-center text-sm text-slate-500">관리자 계정이 없습니다.</td></tr>
              )}
              {admins.map((a) => (
                <tr key={a.id} className="border-b border-slate-100">
                  <td className="px-4 py-3 text-sm text-slate-900">{a.name}</td>
                  <td className="px-4 py-3 text-sm text-slate-700">{a.email}</td>
                  <td className="px-4 py-3 text-sm text-slate-700">{a.jobTitle ?? '-'}</td>
                  <td className="px-4 py-3">
                    <AdminStatusBadge label={ROLE_KO[a.role]} className={ROLE_BADGE[a.role]} />
                  </td>
                  <td className="px-4 py-3">
                    <AdminStatusBadge label={STATUS_KO[a.status]} className={STATUS_BADGE[a.status]} />
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-700">{a.createdAt.slice(0, 10)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CreateAdminModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSuccess={reload}
      />
    </div>
  );
}

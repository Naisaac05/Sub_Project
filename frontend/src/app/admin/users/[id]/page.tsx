'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import * as api from '@/lib/admin/users';
import type { UserRole, UserStatus } from '@/lib/types';
import { useAuth } from '@/contexts/AuthContext';
import { DeactivateOrDeleteModal } from './_DeactivateOrDeleteModal';
import { ResetPasswordModal } from './_ResetPasswordModal';
import { SwapMentorModal } from './_SwapMentorModal';
import { CancelButton, PrimaryButton } from '@/components/admin/Modal';
import { AdminStatusBadge } from '@/components/admin/AdminStatusBadge';
import { AdminListHeader } from '@/components/admin/AdminListHeader';

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

type ActiveModal = null | 'deactivate' | 'reactivateConfirm' | 'delete' | 'reset' | 'swap';

export default function AdminUserDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { user: me } = useAuth();
  const id = Number(params.id);

  const [user, setUser] = useState<api.AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [active, setActive] = useState<ActiveModal>(null);

  const reload = () => {
    setLoading(true);
    setErr(null);
    api.getUserDetail(id)
      .then(setUser)
      .catch((e) => setErr((e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e)))
      .finally(() => setLoading(false));
  };

  useEffect(reload, [id]);

  if (loading) return <div className="text-sm text-slate-500 py-6">불러오는 중…</div>;
  if (err) return <div className="text-sm text-red-600">에러: {err}</div>;
  if (!user) return <div className="text-sm text-slate-500">회원을 찾을 수 없습니다.</div>;

  const isAdminTarget = user.role === 'ADMIN' || user.role === 'SUPER_ADMIN';
  const isSuperAdminTarget = user.role === 'SUPER_ADMIN';
  const isDeleted = user.status === 'DELETED';
  const isSelf = me?.id === user.id;

  const reactivate = async () => {
    try {
      await api.reactivateUser(user.id);
      reload();
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e);
      setErr(message);
    }
  };

  return (
    <div className="space-y-4">
      <AdminListHeader
        title={user.name}
        preTitle={
          <button onClick={() => router.back()} className="text-xs text-slate-500 hover:text-slate-900 mb-1">
            ← 목록
          </button>
        }
        subtitle={
          <div className="mt-1 flex items-center gap-2 text-sm text-slate-600">
            <span>{user.email}</span>
            <span className="text-slate-300">·</span>
            <AdminStatusBadge label={ROLE_KO[user.role]} className={ROLE_BADGE[user.role]} />
            <AdminStatusBadge label={STATUS_KO[user.status]} className={STATUS_BADGE[user.status]} />
          </div>
        }
      />

      <section className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-2 font-semibold text-slate-900">기본 정보</h2>
        <dl className="grid grid-cols-[120px_1fr] gap-y-1 text-sm">
          <dt className="text-slate-500">이메일</dt><dd className="text-slate-900">{user.email}</dd>
          <dt className="text-slate-500">가입 경로</dt><dd className="text-slate-900">{user.provider ?? 'email'}</dd>
          <dt className="text-slate-500">가입일</dt><dd className="text-slate-900">{user.createdAt}</dd>
          <dt className="text-slate-500">최종 수정</dt><dd className="text-slate-900">{user.updatedAt}</dd>
          {user.jobTitle && (<><dt className="text-slate-500">회사직책</dt><dd className="text-slate-900">{user.jobTitle}</dd></>)}
        </dl>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="mb-2 font-semibold text-slate-900">연관 활동</h2>
        <ul className="space-y-1 text-sm text-slate-700">
          <li>💳 결제 {user.paymentCount}건</li>
          <li>📝 게시글 {user.postCount}건</li>
          {user.mentorProfileId && <li>🧑‍🏫 멘토 프로필 #{user.mentorProfileId}</li>}
        </ul>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-4 space-y-3">
        <h2 className="font-semibold text-slate-900">관리자 액션</h2>
        {isDeleted && <p className="text-sm text-slate-500">이미 삭제된 계정입니다. 추가 작업은 불가합니다.</p>}
        {isSelf && <p className="text-sm text-amber-700">본인 계정에는 액션을 수행할 수 없습니다.</p>}
        <div className="flex flex-wrap gap-2">
          {!isDeleted && !isSelf && user.status === 'ACTIVE' && !isAdminTarget && (
            <PrimaryButton onClick={() => setActive('deactivate')} variant="warning">비활성화</PrimaryButton>
          )}
          {!isDeleted && user.status === 'DEACTIVATED' && !isAdminTarget && (
            <PrimaryButton onClick={reactivate}>재활성화</PrimaryButton>
          )}
          {!isDeleted && !isSelf && !isAdminTarget && (
            <PrimaryButton onClick={() => setActive('delete')} variant="destructive">영구 삭제</PrimaryButton>
          )}
          {!isDeleted && !isSuperAdminTarget && (
            <CancelButton onClick={() => setActive('reset')} label="비밀번호 리셋" />
          )}
          {!isDeleted && user.role === 'MENTEE' && (
            <PrimaryButton onClick={() => setActive('swap')}>멘토 교체</PrimaryButton>
          )}
        </div>
      </section>

      <DeactivateOrDeleteModal
        mode="deactivate"
        userId={user.id}
        userName={user.name}
        open={active === 'deactivate'}
        onClose={() => setActive(null)}
        onSuccess={reload}
      />
      <DeactivateOrDeleteModal
        mode="delete"
        userId={user.id}
        userName={user.name}
        open={active === 'delete'}
        onClose={() => setActive(null)}
        onSuccess={reload}
      />
      <ResetPasswordModal
        userId={user.id}
        userName={user.name}
        open={active === 'reset'}
        onClose={() => setActive(null)}
      />
      <SwapMentorModal
        menteeId={user.id}
        menteeName={user.name}
        open={active === 'swap'}
        onClose={() => setActive(null)}
        onSuccess={reload}
      />
    </div>
  );
}

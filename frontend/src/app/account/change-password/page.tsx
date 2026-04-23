'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import * as api from '@/lib/admin/users';
import { AmberAlert } from '@/components/admin/Modal';

export default function ChangePasswordPage() {
  const { user, refreshUser } = useAuth();
  const router = useRouter();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const valid =
    current.length > 0 &&
    next.length >= 8 &&
    next === confirm &&
    next !== current;

  const submit = async () => {
    if (!valid) return;
    setSubmitting(true);
    setErr(null);
    try {
      await api.changePassword(current, next);
      await refreshUser();
      router.push('/mypage');
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e);
      setErr(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto py-12 px-4 space-y-4">
      <h1 className="text-2xl font-bold text-slate-900">비밀번호 변경</h1>

      {user?.mustChangePassword && (
        <AmberAlert
          lines={[
            '관리자가 발급한 임시 비밀번호로 로그인했습니다.',
            '계속 진행하려면 새 비밀번호로 변경해 주세요.',
          ]}
        />
      )}

      <label className="block">
        <span className="text-sm text-slate-700">현재 비밀번호</span>
        <input
          type="password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </label>

      <label className="block">
        <span className="text-sm text-slate-700">새 비밀번호 (최소 8자)</span>
        <input
          type="password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </label>

      <label className="block">
        <span className="text-sm text-slate-700">새 비밀번호 확인</span>
        <input
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        {confirm.length > 0 && next !== confirm && (
          <p className="mt-1 text-xs text-red-600">새 비밀번호가 일치하지 않습니다.</p>
        )}
        {next.length > 0 && current.length > 0 && next === current && (
          <p className="mt-1 text-xs text-red-600">새 비밀번호는 현재 비밀번호와 달라야 합니다.</p>
        )}
      </label>

      {err && <p className="text-sm text-red-600">{err}</p>}

      <button
        onClick={submit}
        disabled={!valid || submitting}
        className="w-full rounded-md bg-slate-900 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {submitting ? '변경 중…' : '변경'}
      </button>
    </div>
  );
}

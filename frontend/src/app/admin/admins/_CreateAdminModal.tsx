'use client';

import { useState } from 'react';
import { Modal, CancelButton, PrimaryButton, AmberAlert } from '@/components/admin/Modal';
import * as api from '@/lib/admin/users';

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function CreateAdminModal({ open, onClose, onSuccess }: Props) {
  const [step, setStep] = useState<'form' | 'result'>('form');
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [tempPwd, setTempPwd] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const valid = email.trim() !== '' && name.trim() !== '' && jobTitle.trim() !== '';

  const close = () => {
    setStep('form');
    setEmail('');
    setName('');
    setJobTitle('');
    setTempPwd('');
    setErr(null);
    onClose();
  };

  const submit = async () => {
    if (!valid) return;
    setSubmitting(true);
    setErr(null);
    try {
      const r = await api.createAdmin({ email, name, jobTitle });
      setTempPwd(r.temporaryPassword);
      setStep('result');
      onSuccess();
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e);
      setErr(message);
    } finally {
      setSubmitting(false);
    }
  };

  if (step === 'form') {
    return (
      <Modal
        open={open}
        onClose={close}
        title="관리자 계정 추가"
        footer={
          <>
            <CancelButton onClick={close} />
            <PrimaryButton onClick={submit} disabled={!valid || submitting}>생성 (임시 비번 발급)</PrimaryButton>
          </>
        }
      >
        <AmberAlert
          lines={[
            '신규 ADMIN 계정으로 생성됩니다 (SUPER_ADMIN 아님).',
            '임시 비밀번호가 1회 표시되며, 사용자가 첫 로그인 시 변경하게 됩니다.',
          ]}
        />

        <label className="block">
          <span className="text-sm text-slate-700">이메일</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="newadmin@devmatch.com"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="block">
          <span className="text-sm text-slate-700">이름</span>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="홍길동"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="block">
          <span className="text-sm text-slate-700">회사직책</span>
          <input
            type="text"
            value={jobTitle}
            onChange={(e) => setJobTitle(e.target.value)}
            placeholder="운영팀장"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </label>

        {err && <p className="text-sm text-red-600">{err}</p>}
      </Modal>
    );
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="관리자 계정 생성 완료"
      footer={<PrimaryButton onClick={close}>닫기</PrimaryButton>}
    >
      <p className="text-sm text-slate-700">
        <span className="font-semibold">{email}</span> 계정이 생성되었습니다.
      </p>
      <div className="rounded-md bg-slate-100 p-3 flex items-center justify-between">
        <span className="font-mono text-base text-slate-900 select-all">{tempPwd}</span>
        <button
          onClick={() => navigator.clipboard.writeText(tempPwd)}
          className="text-xs text-blue-600 hover:underline"
        >
          복사
        </button>
      </div>
      <AmberAlert
        lines={[
          '이 화면을 닫으면 임시 비밀번호를 다시 볼 수 없습니다.',
          '새 관리자에게 안전하게 전달해 주세요.',
        ]}
      />
    </Modal>
  );
}

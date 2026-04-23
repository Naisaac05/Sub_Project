'use client';

import { useState } from 'react';
import { Modal, CancelButton, PrimaryButton, AmberAlert } from '@/components/admin/Modal';
import * as api from '@/lib/admin/users';

interface Props {
  userId: number;
  userName: string;
  open: boolean;
  onClose: () => void;
}

export function ResetPasswordModal({ userId, userName, open, onClose }: Props) {
  const [step, setStep] = useState<'confirm' | 'result'>('confirm');
  const [tempPwd, setTempPwd] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const close = () => {
    setStep('confirm');
    setTempPwd('');
    setErr(null);
    onClose();
  };

  const submit = async () => {
    setSubmitting(true);
    setErr(null);
    try {
      const r = await api.resetUserPassword(userId);
      setTempPwd(r.temporaryPassword);
      setStep('result');
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e);
      setErr(message);
    } finally {
      setSubmitting(false);
    }
  };

  if (step === 'confirm') {
    return (
      <Modal
        open={open}
        onClose={close}
        title="비밀번호 리셋"
        footer={
          <>
            <CancelButton onClick={close} />
            <PrimaryButton onClick={submit} disabled={submitting}>발급</PrimaryButton>
          </>
        }
      >
        <p className="text-sm text-slate-700">
          <span className="font-semibold">{userName}</span> 님의 임시 비밀번호를 발급합니다.
        </p>
        <AmberAlert
          lines={[
            '임시 비밀번호가 새로 생성됩니다.',
            '사용자는 다음 로그인 시 비밀번호 변경 페이지로 강제 이동됩니다.',
          ]}
        />
        {err && <p className="text-sm text-red-600">{err}</p>}
      </Modal>
    );
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="임시 비밀번호 발급 완료"
      footer={
        <PrimaryButton onClick={close}>닫기</PrimaryButton>
      }
    >
      <div className="rounded-md bg-slate-100 p-3 flex items-center justify-between">
        <span className="font-mono text-base text-slate-900 select-all">{tempPwd}</span>
        <button
          onClick={() => navigator.clipboard.writeText(tempPwd)}
          className="text-xs text-blue-600 hover:underline"
        >
          📋 복사
        </button>
      </div>
      <AmberAlert
        lines={[
          '이 화면을 닫으면 임시 비밀번호를 다시 볼 수 없습니다.',
          '사용자에게 안전하게 전달해 주세요.',
        ]}
      />
    </Modal>
  );
}

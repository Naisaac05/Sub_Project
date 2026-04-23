'use client';

import { useState } from 'react';
import { Modal, CancelButton, PrimaryButton, AmberAlert } from '@/components/admin/Modal';
import * as api from '@/lib/admin/users';

type Mode = 'deactivate' | 'delete';

interface Props {
  mode: Mode;
  userId: number;
  userName: string;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function DeactivateOrDeleteModal({ mode, userId, userName, open, onClose, onSuccess }: Props) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const valid = reason.trim().length >= 10 && reason.length <= 500;

  const isDelete = mode === 'delete';
  const title = isDelete ? '회원 영구 삭제' : '회원 비활성화';
  const confirmLabel = isDelete ? '삭제 확정' : '비활성화 확정';
  const alertLines = isDelete
    ? [
        '삭제 시: 회원 정보가 마스킹되며 복구할 수 없습니다.',
        '관련 데이터(결제·게시글·매칭)는 보존되지만 작성자 표시가 "탈퇴한 회원" 으로 변경됩니다.',
      ]
    : [
        '비활성화 시: 사용자가 로그인할 수 없게 됩니다.',
        '데이터는 그대로 보존되며 재활성화로 복구 가능합니다.',
      ];

  const submit = async () => {
    if (!valid) return;
    setSubmitting(true);
    setErr(null);
    try {
      if (isDelete) {
        await api.deleteUser(userId, reason);
      } else {
        await api.deactivateUser(userId, reason);
      }
      setReason('');
      onSuccess();
      onClose();
    } catch (e: unknown) {
      const message =
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e);
      setErr(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <CancelButton onClick={onClose} />
          <PrimaryButton onClick={submit} disabled={!valid || submitting} variant="destructive">
            {confirmLabel}
          </PrimaryButton>
        </>
      }
    >
      <p className="text-sm text-slate-600">
        <span className="font-semibold">{userName}</span> 님 계정을 처리합니다.
      </p>

      <AmberAlert lines={alertLines} />

      <label className="block">
        <span className="text-sm text-slate-700">사유 (10~500자)</span>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={4}
          placeholder="신청자에게 안내할 사유를 입력하세요."
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900"
        />
        <div className="mt-1 text-right text-xs text-slate-500">{reason.length} / 500</div>
      </label>

      {err && <p className="text-sm text-red-600">{err}</p>}
    </Modal>
  );
}

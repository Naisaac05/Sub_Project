'use client';

import { useEffect, useState } from 'react';
import { Modal, CancelButton, PrimaryButton, AmberAlert } from '@/components/admin/Modal';
import * as api from '@/lib/admin/users';

interface MentorOption {
  userId: number;
  name: string;
}

interface Props {
  menteeId: number;
  menteeName: string;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function SwapMentorModal({ menteeId, menteeName, open, onClose, onSuccess }: Props) {
  const [mentors, setMentors] = useState<MentorOption[]>([]);
  const [selected, setSelected] = useState<string>('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    // /api/admin/users?role=MENTOR&status=ACTIVE 로 멘토 목록 가져오기
    // 백엔드의 mentor swap 서비스가 newMentorProfile.status==APPROVED 도 검증함
    api
      .listUsers({ role: 'MENTOR', status: 'ACTIVE', size: 100 })
      .then((r) => setMentors(r.content.map((u) => ({ userId: u.id, name: u.name }))))
      .catch(() => setMentors([]));
  }, [open]);

  const valid = selected !== '' && reason.trim().length >= 10 && reason.length <= 500;

  const submit = async () => {
    if (!valid || selected === '') return;
    setSubmitting(true);
    setErr(null);
    try {
      await api.swapMentor(menteeId, parseInt(selected, 10), reason);
      setSelected('');
      setReason('');
      onSuccess();
      onClose();
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? String(e);
      setErr(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="멘토 교체"
      width={520}
      footer={
        <>
          <CancelButton onClick={onClose} />
          <PrimaryButton onClick={submit} disabled={!valid || submitting}>교체 확정</PrimaryButton>
        </>
      }
    >
      <p className="text-sm text-slate-700">
        <span className="font-semibold">{menteeName}</span> 님의 매칭을 다른 멘토로 변경합니다.
      </p>

      <AmberAlert
        lines={[
          '기존 매칭은 SWAPPED 상태로 전환됩니다.',
          '신규 매칭이 자동 생성됩니다.',
          '결제 환불은 결제 관리 메뉴에서 별도로 처리해야 합니다.',
        ]}
      />

      <label className="block">
        <span className="text-sm text-slate-700">새 멘토</span>
        <select
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="">선택하세요</option>
          {mentors.map((m) => (
            <option key={m.userId} value={m.userId}>{m.name}</option>
          ))}
        </select>
      </label>

      <label className="block">
        <span className="text-sm text-slate-700">사유 (10~500자)</span>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="멘티 요청 등"
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        <div className="mt-1 text-right text-xs text-slate-500">{reason.length} / 500</div>
      </label>

      {err && <p className="text-sm text-red-600">{err}</p>}
    </Modal>
  );
}

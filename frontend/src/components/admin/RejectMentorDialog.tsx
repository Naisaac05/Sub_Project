'use client';

import { useEffect, useState } from 'react';
import { Info } from 'lucide-react';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';

const MIN_LEN = 10;
const MAX_LEN = 500;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mentorName: string;
  onConfirm: (reason: string) => Promise<void>;
}

export default function RejectMentorDialog({
  open,
  onOpenChange,
  mentorName,
  onConfirm,
}: Props) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 다이얼로그가 닫힐 때 상태 리셋
  useEffect(() => {
    if (!open) {
      setReason('');
      setError(null);
      setSubmitting(false);
    }
  }, [open]);

  const trimmed = reason.trim();
  const length = trimmed.length;
  const tooShort = length > 0 && length < MIN_LEN;
  const canSubmit = length >= MIN_LEN && length <= MAX_LEN && !submitting;

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm(trimmed);
      // 성공 시 부모가 onOpenChange(false) 를 호출해 모달 닫도록 위임
    } catch (e) {
      const message =
        (e as { response?: { data?: { message?: string } }; message?: string })
          ?.response?.data?.message ??
        (e as Error)?.message ??
        '반려 처리 중 오류가 발생했습니다.';
      setError(message);
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>멘토 신청 반려</DialogTitle>
          <DialogDescription>
            {mentorName} 님의 신청을 반려합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="reject-reason">
            반려 사유 <span className="text-red-500">*</span>
          </Label>
          <Textarea
            id="reject-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={5}
            maxLength={MAX_LEN}
            placeholder="신청자에게 안내할 사유를 10~500자로 입력하세요."
            disabled={submitting}
            aria-invalid={tooShort}
          />
          <div className="flex justify-between text-xs">
            <span
              className={tooShort ? 'text-red-500' : 'text-slate-500'}
            >
              {tooShort
                ? `최소 ${MIN_LEN}자 이상 입력해주세요.`
                : `최소 ${MIN_LEN}자, 최대 ${MAX_LEN}자`}
            </span>
            <span className="text-slate-500">
              {length} / {MAX_LEN}
            </span>
          </div>
        </div>

        <Alert className="border-slate-200 bg-slate-50">
          <Info className="h-4 w-4" aria-hidden="true" />
          <AlertDescription className="text-slate-700">
            입력한 사유는 신청자에게 노출됩니다. 정중한 표현으로 작성해 주세요.
          </AlertDescription>
        </Alert>

        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <DialogFooter className="gap-2 sm:gap-2">
          <Button
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={submitting}
          >
            취소
          </Button>
          <Button
            variant="destructive"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            {submitting ? '반려 처리 중...' : '반려 확정'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

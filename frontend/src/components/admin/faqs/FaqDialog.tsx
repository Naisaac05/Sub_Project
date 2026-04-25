'use client';

import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { type Faq, type FaqCategory, CATEGORY_LABEL, CATEGORY_ORDER } from '@/lib/faqs';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initial?: Faq;  // undefined 면 생성 모드
  onSubmit: (data: {
    category: FaqCategory;
    question: string;
    answer: string;
    published: boolean;
  }) => Promise<void>;
}

export function FaqDialog({ open, onOpenChange, initial, onSubmit }: Props) {
  const isEdit = !!initial;
  const [category, setCategory] = useState<FaqCategory>('SERVICE_INTRO');
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [published, setPublished] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setCategory(initial?.category ?? 'SERVICE_INTRO');
      setQuestion(initial?.question ?? '');
      setAnswer(initial?.answer ?? '');
      setPublished(initial?.published ?? true);
      setSubmitting(false);
    }
  }, [open, initial]);

  const canSubmit = question.trim().length > 0 && answer.trim().length > 0 && !submitting;

  async function handleSubmit() {
    setSubmitting(true);
    try {
      await onSubmit({ category, question: question.trim(), answer: answer.trim(), published });
      onOpenChange(false);
    } catch (e) {
      // 호출자가 toast 등으로 처리
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'FAQ 수정' : 'FAQ 추가'}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <Label className="mb-1.5 block text-sm">카테고리</Label>
            <Select value={category} onValueChange={(v) => setCategory(v as FaqCategory)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {CATEGORY_ORDER.map((c) => (
                  <SelectItem key={c} value={c}>{CATEGORY_LABEL[c]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div>
            <Label htmlFor="faq-q" className="mb-1.5 block text-sm">질문</Label>
            <Input
              id="faq-q"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              maxLength={200}
              placeholder="짧은 질문 한 줄"
            />
          </div>

          <div>
            <Label htmlFor="faq-a" className="mb-1.5 block text-sm">답변</Label>
            <Textarea
              id="faq-a"
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              rows={6}
              maxLength={5000}
              placeholder="답변 내용"
            />
            <p className="mt-1 text-xs text-slate-400">
              줄바꿈만 지원합니다. (Markdown/HTML 미지원)
            </p>
          </div>

          <div className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
            <Label htmlFor="faq-pub" className="text-sm">공개 여부</Label>
            <Switch id="faq-pub" checked={published} onCheckedChange={setPublished} />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            취소
          </Button>
          <Button onClick={handleSubmit} disabled={!canSubmit}>
            {submitting ? '저장 중…' : '저장'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

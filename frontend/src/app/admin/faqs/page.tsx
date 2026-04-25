'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, Pencil, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { type Faq, type FaqCategory, CATEGORY_LABEL, CATEGORY_ORDER } from '@/lib/faqs';
import * as adminFaq from '@/lib/admin/faqs';
import { FaqDialog } from '@/components/admin/faqs/FaqDialog';

type Filter = 'ALL' | FaqCategory;

export default function AdminFaqsPage() {
  const [faqs, setFaqs] = useState<Faq[] | null>(null);
  const [error, setError] = useState(false);
  const [filter, setFilter] = useState<Filter>('ALL');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Faq | undefined>(undefined);
  const [deleteTarget, setDeleteTarget] = useState<Faq | null>(null);

  const reload = useCallback(async () => {
    try {
      setError(false);
      const res = await adminFaq.fetchAdminFaqs();
      setFaqs(res);
    } catch {
      setError(true);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const filtered = useMemo(() => {
    if (!faqs) return [];
    return filter === 'ALL' ? faqs : faqs.filter((f) => f.category === filter);
  }, [faqs, filter]);

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      const ai = CATEGORY_ORDER.indexOf(a.category);
      const bi = CATEGORY_ORDER.indexOf(b.category);
      if (ai !== bi) return ai - bi;
      return a.orderIndex - b.orderIndex;
    });
  }, [filtered]);

  const byCategory = useMemo(() => {
    if (!faqs) return new Map<FaqCategory, Faq[]>();
    const map = new Map<FaqCategory, Faq[]>();
    for (const f of faqs) {
      const list = map.get(f.category) ?? [];
      list.push(f);
      map.set(f.category, list);
    }
    Array.from(map.values()).forEach((list) =>
      list.sort((a, b) => a.orderIndex - b.orderIndex),
    );
    return map;
  }, [faqs]);

  function isFirstInCategory(f: Faq): boolean {
    return byCategory.get(f.category)?.[0]?.id === f.id;
  }
  function isLastInCategory(f: Faq): boolean {
    const list = byCategory.get(f.category);
    return list?.[list.length - 1]?.id === f.id;
  }

  async function handleTogglePublished(f: Faq, next: boolean) {
    try {
      await adminFaq.updateFaq(f.id, { published: next });
      await reload();
    } catch {
      alert('변경에 실패했습니다.');
    }
  }

  async function handleSwap(f: Faq, direction: 'up' | 'down') {
    const list = byCategory.get(f.category);
    if (!list) return;
    const idx = list.findIndex((x) => x.id === f.id);
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= list.length) return;
    const other = list[swapIdx];

    try {
      await adminFaq.updateFaq(f.id, { orderIndex: other.orderIndex });
      await adminFaq.updateFaq(other.id, { orderIndex: f.orderIndex });
      await reload();
    } catch {
      alert('순서 변경에 실패했습니다.');
    }
  }

  async function handleCreateOrUpdate(data: {
    category: FaqCategory; question: string; answer: string; published: boolean;
  }) {
    if (editing) {
      await adminFaq.updateFaq(editing.id, data);
    } else {
      await adminFaq.createFaq(data);
    }
    await reload();
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    try {
      await adminFaq.deleteFaq(deleteTarget.id);
      setDeleteTarget(null);
      await reload();
    } catch {
      alert('삭제에 실패했습니다.');
    }
  }

  return (
    <div className="space-y-6 p-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">FAQ 관리</h1>
          <p className="mt-1 text-sm text-slate-500">자주 묻는 질문을 관리하고 공개 여부·순서를 조정합니다.</p>
        </div>
        <Button onClick={() => { setEditing(undefined); setDialogOpen(true); }}>
          + FAQ 추가
        </Button>
      </header>

      <div className="flex flex-wrap gap-2">
        {(['ALL', ...CATEGORY_ORDER] as Filter[]).map((c) => (
          <button
            key={c}
            onClick={() => setFilter(c)}
            className={
              'rounded-full px-3 py-1 text-sm font-medium transition-colors ' +
              (filter === c
                ? 'bg-slate-900 text-white'
                : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50')
            }
          >
            {c === 'ALL' ? '전체' : CATEGORY_LABEL[c]}
          </button>
        ))}
      </div>

      {error ? (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
          <p>FAQ 를 불러오지 못했습니다.</p>
          <Button variant="outline" onClick={reload}>재시도</Button>
        </div>
      ) : faqs === null ? (
        <div className="h-[200px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
      ) : sorted.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-200 p-10 text-center text-sm text-slate-400">
          등록된 FAQ 가 없습니다.
        </p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3 text-left">카테고리</th>
                <th className="px-4 py-3 text-left">질문</th>
                <th className="px-4 py-3 text-center">공개</th>
                <th className="px-4 py-3 text-center">순서</th>
                <th className="px-4 py-3 text-center">액션</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sorted.map((f) => (
                <tr key={f.id}>
                  <td className="px-4 py-3 text-slate-600">{CATEGORY_LABEL[f.category]}</td>
                  <td className="px-4 py-3">
                    <p className="line-clamp-1 font-medium text-slate-900">{f.question}</p>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Switch
                      checked={f.published}
                      onCheckedChange={(next) => handleTogglePublished(f, next)}
                    />
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        type="button"
                        disabled={isFirstInCategory(f)}
                        onClick={() => handleSwap(f, 'up')}
                        className="rounded p-1 text-slate-500 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
                        aria-label="위로"
                      >
                        <ArrowUp className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        disabled={isLastInCategory(f)}
                        onClick={() => handleSwap(f, 'down')}
                        className="rounded p-1 text-slate-500 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
                        aria-label="아래로"
                      >
                        <ArrowDown className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        type="button"
                        onClick={() => { setEditing(f); setDialogOpen(true); }}
                        className="rounded p-1 text-slate-500 hover:bg-slate-100"
                        aria-label="수정"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeleteTarget(f)}
                        className="rounded p-1 text-red-500 hover:bg-red-50"
                        aria-label="삭제"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <FaqDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        initial={editing}
        onSubmit={handleCreateOrUpdate}
      />

      <AlertDialog open={!!deleteTarget} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>FAQ 를 삭제하시겠습니까?</AlertDialogTitle>
            <AlertDialogDescription>
              "{deleteTarget?.question}" 항목이 영구 삭제됩니다. 되돌릴 수 없습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} className="bg-red-600 hover:bg-red-700">
              삭제
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

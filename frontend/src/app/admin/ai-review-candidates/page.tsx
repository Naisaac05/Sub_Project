'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Check, GitMerge, RefreshCw, Search, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  fetchAiReviewCandidatesV2,
  reviewAiCandidateV2,
  type AiReviewCandidateV2,
  type AiReviewCandidateWorkflowPhase,
} from '@/lib/admin/aiReviewCandidates';

type Filter = 'ALL' | AiReviewCandidateWorkflowPhase;

const filters: Array<{ value: Filter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'DRAFTED', label: '승인 대기' },
  { value: 'HUMAN_REVIEW', label: '검토 중' },
  { value: 'APPROVED', label: '반영 완료' },
  { value: 'PUBLISH_FAILED', label: '반영 실패' },
  { value: 'REJECTED', label: '거절' },
  { value: 'MERGED', label: '병합' },
];

export default function AdminAiReviewCandidatesPage() {
  const [rows, setRows] = useState<AiReviewCandidateV2[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [filter, setFilter] = useState<Filter>('DRAFTED');
  const [query, setQuery] = useState('');
  const [definition, setDefinition] = useState('');
  const [reason, setReason] = useState('');
  const [mergeId, setMergeId] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  const reload = useCallback(async () => {
    setLoading(true);
    setMessage('');
    try {
      const candidates = await fetchAiReviewCandidatesV2();
      setRows(candidates);
      setSelectedId((current) => current ?? candidates[0]?.id ?? null);
    } catch {
      setMessage('후보 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => void reload(), [reload]);

  const selected = rows.find((row) => row.id === selectedId) ?? null;
  useEffect(() => {
    setDefinition(selected?.reviewerEditedAnswer || selected?.definition || selected?.definitionDraft || '');
    setReason(selected?.rejectedReason || '');
    setMergeId(selected?.mergedIntoId ? String(selected.mergedIntoId) : '');
  }, [selected]);

  const visible = useMemo(() => rows.filter((row) => {
    const phaseMatch = filter === 'ALL' || row.workflowPhase === filter
      || (filter === 'DRAFTED' && row.workflowPhase === 'CAPTURED');
    const text = `${row.term} ${row.category} ${row.sourceQuestion ?? ''}`.toLowerCase();
    return phaseMatch && text.includes(query.trim().toLowerCase());
  }), [rows, filter, query]);

  async function review(action: 'START_REVIEW' | 'EDIT_AND_APPROVE' | 'REJECT' | 'MERGE') {
    if (!selected) return;
    setSaving(true);
    setMessage('');
    try {
      const updated = await reviewAiCandidateV2(selected.id, {
        action,
        definition,
        reviewerEditedAnswer: action === 'EDIT_AND_APPROVE' ? definition : undefined,
        rejectedReason: reason,
        mergedIntoId: action === 'MERGE' ? Number(mergeId) : undefined,
        reviewer: 'admin-ui',
        retentionDays: 365,
      });
      setRows((current) => current.map((row) => row.id === updated.id ? updated : row));
      setMessage(updated.workflowPhase === 'PUBLISH_FAILED'
        ? `v2 카드 반영 실패: ${updated.publishError ?? '원인을 확인하세요.'}`
        : '처리가 완료되었습니다.');
    } catch {
      setMessage('요청 처리에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6 p-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">AI 지식 후보 관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            Ollama 답변 후보를 검토하고, 승인된 정의를 concepts_v2 카드로 반영합니다.
          </p>
        </div>
        <Button variant="outline" onClick={reload} disabled={loading}>
          <RefreshCw className="mr-2 h-4 w-4" /> 새로고침
        </Button>
      </header>

      {message && <div className="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm">{message}</div>}

      <div className="flex flex-wrap justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {filters.map((item) => (
            <button key={item.value} onClick={() => setFilter(item.value)}
              className={`rounded-md border px-3 py-1.5 text-sm ${filter === item.value ? 'bg-slate-900 text-white' : 'bg-white text-slate-600'}`}>
              {item.label} ({rows.filter((row) => item.value === 'ALL' || row.workflowPhase === item.value || (item.value === 'DRAFTED' && row.workflowPhase === 'CAPTURED')).length})
            </button>
          ))}
        </div>
        <label className="relative">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input value={query} onChange={(event) => setQuery(event.target.value)}
            placeholder="개념, 코스, 질문 검색" className="rounded-md border py-2 pl-9 pr-3 text-sm" />
        </label>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
        <section className="overflow-hidden rounded-lg border bg-white">
          {loading ? <p className="p-8 text-center text-slate-500">불러오는 중...</p> : (
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr><th className="p-3">개념</th><th className="p-3">상태</th><th className="p-3">출처</th></tr>
              </thead>
              <tbody>
                {visible.map((row) => (
                  <tr key={row.id} onClick={() => setSelectedId(row.id)}
                    className={`cursor-pointer border-t ${selectedId === row.id ? 'bg-blue-50' : 'hover:bg-slate-50'}`}>
                    <td className="p-3"><strong>{row.term}</strong><div className="text-xs text-slate-500">{row.category}</div></td>
                    <td className="p-3"><Status phase={row.workflowPhase} /></td>
                    <td className="p-3">{row.source}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="space-y-5 rounded-lg border bg-white p-5">
          {!selected ? <p className="text-slate-500">후보를 선택하세요.</p> : <>
            <div>
              <div className="flex items-center gap-2"><h2 className="text-xl font-semibold">{selected.term}</h2><Status phase={selected.workflowPhase} /></div>
              <p className="text-sm text-slate-500">{selected.category} · 후보 ID {selected.externalCandidateId ?? selected.id}</p>
            </div>
            <Info label="원문 질문" value={selected.sourceQuestion} />
            <Info label="해석 질문" value={selected.resolvedQuery} />
            <Info label="후보 생성 이유" value={selected.needsReviewReason} />
            <label className="block space-y-2 text-sm font-medium">
              검토 정의
              <textarea value={definition} onChange={(event) => setDefinition(event.target.value)}
                className="min-h-40 w-full rounded-md border p-3 font-normal" />
            </label>
            {(selected.publishedCardId || selected.publishError) && (
              <div className={`rounded-md border p-3 text-sm ${selected.publishError ? 'border-red-200 bg-red-50' : 'border-emerald-200 bg-emerald-50'}`}>
                <Info label="v2 card_id" value={selected.publishedCardId} />
                <Info label="저장 경로" value={selected.publishedCardPath} />
                <Info label="반영 오류" value={selected.publishError} />
              </div>
            )}
            <label className="block space-y-2 text-sm font-medium">
              거절 또는 병합 사유
              <input value={reason} onChange={(event) => setReason(event.target.value)} className="w-full rounded-md border p-2 font-normal" />
            </label>
            <label className="block space-y-2 text-sm font-medium">
              병합 대상 후보 ID
              <input value={mergeId} onChange={(event) => setMergeId(event.target.value)} className="w-full rounded-md border p-2 font-normal" />
            </label>
            <div className="flex flex-wrap gap-2">
              {selected.status === 'PENDING' && selected.workflowPhase !== 'HUMAN_REVIEW' && (
                <Button variant="outline" onClick={() => review('START_REVIEW')} disabled={saving}>검토</Button>
              )}
              <Button onClick={() => review('EDIT_AND_APPROVE')} disabled={saving || !definition.trim()}><Check className="mr-2 h-4 w-4" />승인 및 반영</Button>
              <Button variant="outline" onClick={() => review('MERGE')} disabled={saving || !Number(mergeId)}><GitMerge className="mr-2 h-4 w-4" />병합</Button>
              <Button variant="destructive" onClick={() => review('REJECT')} disabled={saving}><X className="mr-2 h-4 w-4" />거절</Button>
            </div>
          </>}
        </section>
      </div>
    </div>
  );
}

function Info({ label, value }: { label: string; value?: string | null }) {
  if (!value) return null;
  return <div className="text-sm"><strong className="text-slate-600">{label}</strong><p className="mt-1 whitespace-pre-wrap">{value}</p></div>;
}

function Status({ phase }: { phase: AiReviewCandidateWorkflowPhase }) {
  const labels: Record<AiReviewCandidateWorkflowPhase, string> = {
    CAPTURED: '승인 대기', DRAFTED: '승인 대기', HUMAN_REVIEW: '검토 중',
    PUBLISH_FAILED: '반영 실패', APPROVED: '반영 완료', REJECTED: '거절', MERGED: '병합',
  };
  return <span className="rounded bg-slate-100 px-2 py-1 text-xs font-semibold">{labels[phase]}</span>;
}

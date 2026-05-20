'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Check, CheckSquare, GitMerge, RefreshCw, Upload, X, ArrowRight, HelpCircle, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  fetchAiReviewCandidatesV2,
  importAiReviewCandidatesV2,
  reviewAiCandidateV2,
  type AiReviewCandidateV2,
  type AiReviewCandidateV2Status,
} from '@/lib/admin/aiReviewCandidates';

type StatusFilter = 'ALL' | AiReviewCandidateV2Status;

const FILTERS: Array<{ value: StatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING', label: '승인 대기' },
  { value: 'APPROVED', label: '승인 완료' },
  { value: 'REJECTED', label: '거절됨' },
  { value: 'MERGED', label: '병합됨' },
];

export default function AdminAiReviewCandidatesPage() {
  const [candidates, setCandidates] = useState<AiReviewCandidateV2[]>([]);
  const [selected, setSelected] = useState<AiReviewCandidateV2 | null>(null);
  const [selectedBulkIds, setSelectedBulkIds] = useState<Set<number>>(new Set());
  const [filter, setFilter] = useState<StatusFilter>('PENDING');
  const [searchQuery, setSearchQuery] = useState('');
  const [editedAnswer, setEditedAnswer] = useState('');
  const [rejectedReason, setRejectedReason] = useState('');
  const [mergedIntoId, setMergedIntoId] = useState('');
  const [retentionDays, setRetentionDays] = useState('365');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  const reload = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const rows = await fetchAiReviewCandidatesV2();
      setCandidates(rows);
      setSelectedBulkIds(new Set());
      setSelected((current) => {
        if (!current) return rows[0] ?? null;
        return rows.find((row) => row.id === current.id) ?? rows[0] ?? null;
      });
    } catch {
      setError('AI 지식 후보를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    if (!selected) {
      setEditedAnswer('');
      setRejectedReason('');
      setMergedIntoId('');
      return;
    }
    setEditedAnswer(selected.reviewerEditedAnswer || selected.definition || selected.definitionDraft || '');
    setRejectedReason(selected.rejectedReason || '');
    setMergedIntoId(selected.mergedIntoId ? String(selected.mergedIntoId) : '');
  }, [selected]);

  const filtered = useMemo(() => {
    return candidates.filter((candidate) => {
      const matchFilter = filter === 'ALL' || candidate.status === filter;
      const matchSearch = !searchQuery.trim() || 
        candidate.term.toLowerCase().includes(searchQuery.toLowerCase()) || 
        (candidate.category && candidate.category.toLowerCase().includes(searchQuery.toLowerCase()));
      return matchFilter && matchSearch;
    });
  }, [candidates, filter, searchQuery]);

  const paginated = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage;
    return filtered.slice(start, start + itemsPerPage);
  }, [filtered, currentPage]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / itemsPerPage));

  async function handleImport() {
    try {
      setSaving(true);
      setNotice('');
      const imported = await importAiReviewCandidatesV2();
      setNotice(`새로운 지식 후보 ${imported}개를 성공적으로 불러왔습니다.`);
      await reload();
    } catch {
      setError('JSONL 데이터를 불러오지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  async function handleReview(action: 'EDIT_AND_APPROVE' | 'REJECT' | 'MERGE') {
    if (!selected) return;
    try {
      setSaving(true);
      setError('');
      const updated = await reviewAiCandidateV2(selected.id, {
        action,
        definition: editedAnswer,
        reviewerEditedAnswer: action === 'EDIT_AND_APPROVE' ? editedAnswer : undefined,
        rejectedReason,
        mergedIntoId: action === 'MERGE' ? Number(mergedIntoId) : undefined,
        reviewer: 'admin-ui',
        retentionDays: Number(retentionDays) || 365,
      });
      setCandidates((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      setSelected(updated);
    } catch {
      setError('승인 처리에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  }

  async function handleBulkReview(action: 'EDIT_AND_APPROVE' | 'REJECT') {
    if (selectedBulkIds.size === 0) return;
    try {
      setSaving(true);
      setError('');
      const ids = Array.from(selectedBulkIds);
      const updates = await Promise.all(
        ids.map((id) => {
          const candidate = candidates.find((c) => c.id === id);
          return reviewAiCandidateV2(id, {
            action,
            definition: candidate?.definitionDraft || candidate?.definition || '',
            reviewerEditedAnswer: action === 'EDIT_AND_APPROVE' ? (candidate?.definitionDraft || candidate?.definition || '') : undefined,
            rejectedReason: '',
            reviewer: 'admin-ui',
            retentionDays: 365,
          });
        })
      );
      
      setCandidates((prev) => 
        prev.map((item) => {
          const updatedItem = updates.find((u) => u.id === item.id);
          return updatedItem ? updatedItem : item;
        })
      );
      setSelectedBulkIds(new Set());
      setNotice(`선택한 ${ids.length}개의 항목이 일괄 처리되었습니다.`);
    } catch {
      setError('일괄 처리에 실패했습니다. 일부 항목만 처리되었을 수 있습니다.');
    } finally {
      setSaving(false);
    }
  }

  const toggleBulkSelect = (id: number, checked: boolean) => {
    setSelectedBulkIds(prev => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const toggleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedBulkIds(new Set(filtered.map(c => c.id)));
    } else {
      setSelectedBulkIds(new Set());
    }
  };

  return (
    <div className="space-y-6 p-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">AI 지식베이스 승인 관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            AI가 사용자 대화 중 수집한 지식 후보를 검토하고 승인, 거절, 병합할 수 있습니다.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleImport} disabled={saving}>
            <Upload className="mr-2 h-4 w-4" />
            새로운 지식 후보 불러오기
          </Button>
          <Button variant="outline" onClick={reload} disabled={loading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            새로고침
          </Button>
        </div>
      </header>

      {error && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {notice && <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{notice}</div>}

      <div className="flex flex-col sm:flex-row flex-wrap justify-between gap-4">
        <div className="flex flex-wrap gap-2">
          {FILTERS.map((item) => (
            <button
              key={item.value}
              type="button"
              onClick={() => {
                setFilter(item.value);
                setSelectedBulkIds(new Set());
                setSelected(null);
                setCurrentPage(1);
              }}
              className={
                'rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ' +
                (filter === item.value
                  ? 'border-slate-900 bg-slate-900 text-white'
                  : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50')
              }
            >
              {item.label}
            </button>
          ))}
        </div>
        <div className="relative w-full sm:max-w-xs">
          <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
            <Search size={16} />
          </div>
          <input
            type="text"
            placeholder="키워드 또는 출처 검색..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setCurrentPage(1);
            }}
            className="block w-full rounded-md border border-slate-200 bg-white py-1.5 pl-10 pr-3 text-sm placeholder:text-slate-400 focus:border-blue-400 focus:outline-none"
          />
        </div>
      </div>

      {selectedBulkIds.size > 0 && (
        <div className="flex items-center gap-3 rounded-lg bg-blue-50 border border-blue-100 p-3">
          <span className="text-sm font-semibold text-blue-800">{selectedBulkIds.size}개 선택됨</span>
          <Button size="sm" onClick={() => handleBulkReview('EDIT_AND_APPROVE')} disabled={saving} className="bg-emerald-600 hover:bg-emerald-700">
            <CheckSquare className="mr-2 h-4 w-4" /> 일괄 승인
          </Button>
          <Button size="sm" variant="destructive" onClick={() => handleBulkReview('REJECT')} disabled={saving}>
            <X className="mr-2 h-4 w-4" /> 일괄 거절
          </Button>
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(460px,1fr)_minmax(430px,0.9fr)]">
        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3 w-10">
                  <input 
                    type="checkbox" 
                    checked={filtered.length > 0 && selectedBulkIds.size === filtered.length}
                    onChange={(e) => toggleSelectAll(e.target.checked)}
                    className="rounded border-slate-300"
                  />
                </th>
                <th className="px-4 py-3">키워드</th>
                <th className="px-4 py-3">출처</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">
                  <div className="flex items-center gap-1">
                    보관 만료일
                    <span title="지식이 만료되어 AI가 더 이상 참고하지 않게 되는 날짜입니다." className="cursor-help text-slate-400">
                      <HelpCircle size={14} />
                    </span>
                  </div>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-slate-400">
                    로딩 중...
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-slate-400">
                    조건에 맞는 데이터가 없습니다.
                  </td>
                </tr>
              ) : (
                paginated.map((candidate) => {
                  const active = selected?.id === candidate.id;
                  return (
                    <tr
                      key={candidate.id}
                      onClick={(e) => {
                        // @ts-ignore
                        if (e.target.type !== 'checkbox') setSelected(candidate);
                      }}
                      className={'cursor-pointer transition-colors ' + (active ? 'bg-blue-50' : 'hover:bg-slate-50')}
                    >
                      <td className="px-4 py-3">
                        <input 
                          type="checkbox" 
                          checked={selectedBulkIds.has(candidate.id)}
                          onChange={(e) => toggleBulkSelect(candidate.id, e.target.checked)}
                          className="rounded border-slate-300"
                        />
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-slate-900">{candidate.term}</div>
                        <div className="text-xs text-slate-500">{candidate.category}</div>
                      </td>
                      <td className="px-4 py-3"><SourceBadge value={candidate.source} /></td>
                      <td className="px-4 py-3"><StatusBadge value={candidate.status} /></td>
                      <td className="px-4 py-3 text-slate-500">{candidate.retentionUntil || '-'}</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
          {filtered.length > itemsPerPage && (
            <div className="border-t border-slate-100 px-4 py-3 flex flex-wrap items-center justify-between gap-4">
              <span className="whitespace-nowrap text-sm text-slate-500">
                총 {filtered.length}개 중 {(currentPage - 1) * itemsPerPage + 1}-
                {Math.min(currentPage * itemsPerPage, filtered.length)}개
              </span>
              <div className="flex flex-wrap items-center gap-1">
                {(() => {
                  const currentBlock = Math.floor((currentPage - 1) / 5);
                  const start = currentBlock * 5 + 1;
                  
                  return (
                    <button
                      onClick={() => setCurrentPage(Math.max(1, start - 5))}
                      disabled={start === 1}
                      className="h-8 w-8 flex items-center justify-center rounded text-slate-500 hover:bg-slate-100 disabled:opacity-50"
                    >
                      &lt;
                    </button>
                  );
                })()}
                {(() => {
                  const currentBlock = Math.floor((currentPage - 1) / 5);
                  const start = currentBlock * 5 + 1;
                  const end = Math.min(totalPages, start + 4);
                  
                  const pages = [];
                  for (let i = start; i <= end; i++) pages.push(i);
                  return pages.map((p) => (
                    <button
                      key={p}
                      onClick={() => setCurrentPage(p)}
                      className={`h-8 w-8 flex items-center justify-center rounded text-sm font-medium transition-colors ${
                        currentPage === p
                          ? 'bg-blue-600 text-white'
                          : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      }`}
                    >
                      {p}
                    </button>
                  ));
                })()}
                {(() => {
                  const currentBlock = Math.floor((currentPage - 1) / 5);
                  const start = currentBlock * 5 + 1;
                  const nextBlockStart = start + 5;
                  
                  return (
                    <button
                      onClick={() => setCurrentPage(Math.min(totalPages, nextBlockStart))}
                      disabled={nextBlockStart > totalPages}
                      className="h-8 w-8 flex items-center justify-center rounded text-slate-500 hover:bg-slate-100 disabled:opacity-50"
                    >
                      &gt;
                    </button>
                  );
                })()}
              </div>
            </div>
          )}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-5">
          {selected ? (
            <div className="space-y-5">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-xl font-semibold text-slate-900">{selected.term}</h2>
                  <SourceBadge value={selected.source} />
                  <StatusBadge value={selected.status} />
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  ID {selected.id} / 연결된 문제 ID {selected.externalCandidateId || '없음'} / {selected.category}
                </p>
              </div>

              <Field label="수집된 문맥 (Context Flow)">
                {(selected.sourceQuestion || selected.resolvedQuery || selected.needsReviewReason) ? (
                  <div className="space-y-3 rounded-md bg-slate-50 p-4 text-sm text-slate-800 border border-slate-200">
                    {selected.sourceQuestion && (
                      <div className="flex flex-col">
                        <span className="text-xs font-bold text-slate-500 mb-1">👤 사용자 질문</span>
                        <div className="bg-white p-2 rounded border border-slate-200">{selected.sourceQuestion}</div>
                      </div>
                    )}
                    {selected.resolvedQuery && (
                      <>
                        <ArrowRight className="text-slate-300 mx-auto" size={16} />
                        <div className="flex flex-col">
                          <span className="text-xs font-bold text-blue-600 mb-1">🤖 AI 분석 의도</span>
                          <div className="bg-blue-50 p-2 rounded border border-blue-100 text-blue-900">{selected.resolvedQuery}</div>
                        </div>
                      </>
                    )}
                    {selected.needsReviewReason && (
                      <>
                        <ArrowRight className="text-slate-300 mx-auto" size={16} />
                        <div className="flex flex-col">
                          <span className="text-xs font-bold text-amber-600 mb-1">⚠️ 승인 대기 사유</span>
                          <div className="bg-amber-50 p-2 rounded border border-amber-100 text-amber-900">{selected.needsReviewReason}</div>
                        </div>
                      </>
                    )}
                    <div className="pt-2 border-t border-slate-200 text-xs text-slate-400">
                      라우트: {selected.route || 'unknown'} / 신뢰도 점수:{' '}
                      {selected.confidenceScore === null ? '알 수 없음' : selected.confidenceScore?.toFixed(2)}
                    </div>
                  </div>
                ) : (
                  <div className="rounded-md bg-slate-50 p-4 text-sm text-slate-500 border border-slate-200 text-center leading-relaxed">
                    수집된 대화 문맥 정보가 없습니다.<br/>
                    {selected.source === 'MANUAL' 
                      ? '관리자나 시스템 파이프라인에서 수동(MANUAL)으로 등록한 지식 키워드입니다.' 
                      : '대화 내역에서 추출된 원본 문장이 존재하지 않습니다.'}
                    
                    {selected.externalCandidateId && (
                      <div className="mt-3 inline-block rounded-full bg-blue-100 px-3 py-1 text-xs font-bold text-blue-700">
                        🔗 연결된 문제 번호(ID): {selected.externalCandidateId}
                      </div>
                    )}
                  </div>
                )}
              </Field>

              <Field label="AI 답변 내용 (관리자 수정 가능)">
                <textarea
                  value={editedAnswer}
                  onChange={(event) => setEditedAnswer(event.target.value)}
                  placeholder="AI가 아직 답변을 생성하지 않았거나 수동으로 추가된 항목입니다. 여기에 지식 내용을 직접 작성해주세요."
                  className="min-h-[160px] w-full rounded-md border border-slate-200 p-3 text-sm outline-none focus:border-blue-400"
                />
              </Field>

              <div className="grid gap-3 md:grid-cols-2">
                <Field label="보관 기간 (일)">
                  <div>
                    <input
                      value={retentionDays}
                      onChange={(event) => setRetentionDays(event.target.value)}
                      className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-400"
                      inputMode="numeric"
                    />
                    <p className="mt-1.5 text-xs text-slate-500">
                      승인 후 이 지식을 AI가 며칠 동안 기억할지 설정합니다. (기본값: 365일)
                    </p>
                  </div>
                </Field>
                <Field label="병합 대상 ID (Merge Target)">
                  <div className="relative">
                    <input
                      value={mergedIntoId}
                      onChange={(event) => setMergedIntoId(event.target.value)}
                      className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-400"
                      inputMode="numeric"
                      placeholder="병합할 ID 번호 입력"
                    />
                    <p className="mt-1.5 text-xs text-slate-500">
                      이미 존재하는 지식과 겹칠 경우, 흡수될 <b>대상 ID 번호</b>를 리스트에서 찾아 입력하세요.
                    </p>
                  </div>
                </Field>
              </div>

              <Field label="거절 또는 병합 사유">
                <input
                  value={rejectedReason}
                  onChange={(event) => setRejectedReason(event.target.value)}
                  className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-400"
                  placeholder="예: 이미 유사한 지식이 있음, 답변 품질 미달"
                />
              </Field>

              <div className="flex flex-wrap gap-2">
                <Button onClick={() => handleReview('EDIT_AND_APPROVE')} disabled={saving || !editedAnswer.trim()} className="bg-emerald-600 hover:bg-emerald-700">
                  <Check className="mr-2 h-4 w-4" />
                  수정 및 승인
                </Button>
                <Button variant="outline" onClick={() => handleReview('MERGE')} disabled={saving || !Number(mergedIntoId)}>
                  <GitMerge className="mr-2 h-4 w-4" />
                  병합 (Merge)
                </Button>
                <Button variant="destructive" onClick={() => handleReview('REJECT')} disabled={saving}>
                  <X className="mr-2 h-4 w-4" />
                  거절
                </Button>
              </div>

              {(selected.reviewedAt || selected.reviewer) && (
                <p className="text-xs text-slate-500">
                  마지막 검토: {selected.reviewer || '알 수 없음'} ({selected.reviewedAt || '시간 알 수 없음'})
                </p>
              )}
            </div>
          ) : (
            <div className="py-20 text-center text-sm text-slate-400">목록에서 항목을 선택해주세요.</div>
          )}
        </section>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-2">
      <span className="text-xs font-bold text-slate-700">{label}</span>
      {children}
    </label>
  );
}

function SourceBadge({ value }: { value: string }) {
  const color = value === 'AUTO' ? 'bg-blue-100 text-blue-700' : value === 'COURSE' ? 'bg-slate-100 text-slate-700' : 'bg-purple-100 text-purple-700';
  return <span className={`rounded px-2 py-1 text-xs font-bold ${color}`}>{value === 'AUTO' ? '자동수집' : value}</span>;
}

function StatusBadge({ value }: { value: AiReviewCandidateV2Status }) {
  const labelMap = {
    APPROVED: '승인됨',
    REJECTED: '거절됨',
    MERGED: '병합됨',
    PENDING: '대기중'
  };
  const label = labelMap[value] || value;
  
  const color =
    value === 'APPROVED'
      ? 'bg-emerald-100 text-emerald-700'
      : value === 'REJECTED'
        ? 'bg-red-100 text-red-700'
        : value === 'MERGED'
          ? 'bg-blue-100 text-blue-700'
          : 'bg-amber-100 text-amber-700';
  return <span className={`rounded px-2 py-1 text-xs font-bold ${color}`}>{label}</span>;
}

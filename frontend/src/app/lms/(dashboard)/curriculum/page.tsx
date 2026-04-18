'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { BookOpen, CheckCircle, Circle, Plus, Loader2, ExternalLink } from 'lucide-react';
import { getCurriculum, createCurriculum, updateCurriculum, toggleWeekComplete, getCurriculumLimit } from '@/lib/lms';
import type { CurriculumResponse, CurriculumWeekResponse, CurriculumWeekRequest, CurriculumLimitResponse } from '@/lib/lms-types';

export default function CurriculumPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [curriculum, setCurriculum] = useState<CurriculumResponse | null>(null);
  const [limit, setLimit] = useState<CurriculumLimitResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [createModal, setCreateModal] = useState(false);
  const [weekModal, setWeekModal] = useState<{ editing?: CurriculumWeekResponse } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Create form
  const [createForm, setCreateForm] = useState({ title: '', description: '', totalWeeks: 4, startDate: '', endDate: '' });
  // Week form
  const [weekForm, setWeekForm] = useState<CurriculumWeekRequest>({ weekNumber: 1, title: '', description: '', topics: [], resources: [] });
  const [topicInput, setTopicInput] = useState('');
  const [resourceInput, setResourceInput] = useState('');

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const res = await getCurriculum(matchingId);
      setCurriculum(res.data.data);
    } catch { setCurriculum(null); }
    finally { setLoading(false); }
  };

  const fetchLimit = async () => {
    if (!matchingId) return;
    try {
      const res = await getCurriculumLimit(matchingId);
      setLimit(res.data.data);
    } catch { setLimit(null); }
  };

  useEffect(() => { fetchData(); fetchLimit(); }, [matchingId]);

  const maxWeeks = limit?.maxWeeks ?? 0;
  const atLimit = maxWeeks > 0 && (curriculum?.weeks.length ?? 0) >= maxWeeks;

  const handleToggle = async (weekId: number) => {
    if (isMentor) return;
    setError('');
    try {
      await toggleWeekComplete(weekId);
      fetchData();
    } catch { setError('상태 변경에 실패했습니다'); }
  };

  const handleCreate = async () => {
    setSubmitting(true); setError('');
    try {
      await createCurriculum({
        matchingId, title: createForm.title, description: createForm.description,
        totalWeeks: createForm.totalWeeks, startDate: createForm.startDate, endDate: createForm.endDate, weeks: [],
      });
      setCreateModal(false);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '생성에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleUpdateCurriculum = async () => {
    if (!curriculum || !weekModal) return;
    setSubmitting(true); setError('');
    try {
      const existingWeeks: CurriculumWeekRequest[] = curriculum.weeks.map(w => ({
        weekNumber: w.weekNumber, title: w.title, description: w.description || '', topics: w.topics, resources: w.resources,
      }));
      if (weekModal.editing) {
        const idx = existingWeeks.findIndex(w => w.weekNumber === weekModal.editing!.weekNumber);
        if (idx >= 0) existingWeeks[idx] = weekForm;
      } else {
        existingWeeks.push(weekForm);
      }
      await updateCurriculum(curriculum.id, {
        matchingId, title: curriculum.title, description: curriculum.description || '',
        totalWeeks: Math.max(curriculum.totalWeeks, existingWeeks.length),
        startDate: curriculum.startDate, endDate: curriculum.endDate, weeks: existingWeeks,
      });
      setWeekModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '수정에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const completedCount = curriculum?.weeks.filter(w => w.isCompleted).length || 0;
  const totalCount = curriculum?.weeks.length || 0;
  const progressPct = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  if (!curriculum) {
    return (
      <div className="space-y-8">
        <h1 className="text-2xl font-bold text-white">커리큘럼</h1>
        <div className="text-center py-20">
          <BookOpen size={48} className="mx-auto text-gray-600 mb-4" />
          <p className="text-gray-400 mb-4">아직 등록된 커리큘럼이 없습니다.</p>
          {isMentor && (
            <>
              <button onClick={() => {
                  setError('');
                  setCreateForm(f => ({ ...f, totalWeeks: Math.min(f.totalWeeks || 4, maxWeeks || 4) }));
                  setCreateModal(true);
                }}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold text-white bg-gradient-to-r from-blue-600 to-blue-500">
                <Plus size={16} />커리큘럼 만들기
              </button>
              {limit && (
                <p className="text-xs text-gray-500 mt-3">
                  {limit.hasConfirmedPayment
                    ? `결제 ${limit.monthsBundled}개월 기준 최대 ${limit.maxWeeks}주차까지 설정 가능`
                    : `결제 미확인 — 기본 ${limit.maxWeeks}주차까지 설정 가능`}
                </p>
              )}
            </>
          )}
        </div>
        {createModal && renderCreateModal()}
      </div>
    );
  }

  function renderCreateModal() {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="glass-card rounded-2xl w-full max-w-md p-6">
          <h3 className="text-lg font-semibold text-white mb-4">커리큘럼 생성</h3>
          {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
          <div className="space-y-4">
            <div>
              <label className="text-gray-400 text-sm">제목</label>
              <input type="text" value={createForm.title} onChange={e => setCreateForm({ ...createForm, title: e.target.value })} placeholder="예: Java Backend 마스터 과정"
                className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
            </div>
            <div>
              <label className="text-gray-400 text-sm">설명</label>
              <textarea value={createForm.description} onChange={e => setCreateForm({ ...createForm, description: e.target.value })} rows={2}
                className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="text-gray-400 text-sm">총 주차{maxWeeks > 0 && <span className="text-gray-500"> (최대 {maxWeeks})</span>}</label>
                <input type="number" value={createForm.totalWeeks}
                  onChange={e => {
                    const v = Number(e.target.value);
                    setCreateForm({ ...createForm, totalWeeks: maxWeeks > 0 ? Math.min(v, maxWeeks) : v });
                  }}
                  min={1} max={maxWeeks > 0 ? maxWeeks : undefined}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">시작일</label>
                <input type="date" value={createForm.startDate} onChange={e => setCreateForm({ ...createForm, startDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">종료일</label>
                <input type="date" value={createForm.endDate} onChange={e => setCreateForm({ ...createForm, endDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
            </div>
          </div>
          <div className="flex gap-3 mt-6">
            <button onClick={() => setCreateModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
            <button onClick={handleCreate} disabled={submitting || !createForm.title || !createForm.startDate || !createForm.endDate}
              className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
              {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '생성'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">{curriculum.title}</h1>
          {curriculum.description && <p className="text-gray-400 mt-1">{curriculum.description}</p>}
        </div>
        {isMentor && (
          <div className="flex flex-col items-end gap-1">
            <button
              onClick={() => {
                if (atLimit) return;
                setError('');
                setWeekForm({ weekNumber: (curriculum.weeks.length || 0) + 1, title: '', description: '', topics: [], resources: [] });
                setTopicInput(''); setResourceInput('');
                setWeekModal({});
              }}
              disabled={atLimit}
              title={atLimit ? `결제한 ${limit?.monthsBundled ?? ''}개월(${maxWeeks}주차) 한도에 도달했습니다` : undefined}
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-blue-500/10">
              <Plus size={16} />주차 추가
            </button>
            {limit && maxWeeks > 0 && (
              <p className="text-[11px] text-gray-500">
                {curriculum.weeks.length}/{maxWeeks}주차
                {limit.hasConfirmedPayment ? ` · 결제 ${limit.monthsBundled}개월` : ' · 결제 미확인'}
              </p>
            )}
          </div>
        )}
      </div>

      {/* Progress Bar */}
      <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
        <div className="flex items-center justify-between mb-3">
          <span className="text-gray-400 text-sm">전체 진도</span>
          <span className="text-white text-sm font-semibold">{completedCount}/{totalCount} 주차 ({progressPct}%)</span>
        </div>
        <div className="w-full h-2 bg-white/5 rounded-full overflow-hidden">
          <div className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full transition-all duration-500" style={{ width: `${progressPct}%` }} />
        </div>
      </div>

      {/* Week List */}
      <div className="space-y-4">
        {curriculum.weeks.sort((a, b) => a.weekNumber - b.weekNumber).map(week => (
          <div key={week.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
            <div className="flex items-start gap-4">
              {isMentor ? (
                <div className="mt-0.5 shrink-0" title="멘티만 완료 처리할 수 있습니다">
                  {week.isCompleted ? <CheckCircle size={22} className="text-green-400" /> : <Circle size={22} className="text-gray-600" />}
                </div>
              ) : (
                <button onClick={() => handleToggle(week.id)} className="mt-0.5 shrink-0">
                  {week.isCompleted ? <CheckCircle size={22} className="text-green-400" /> : <Circle size={22} className="text-gray-600 hover:text-blue-400 transition-colors" />}
                </button>
              )}
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <span className="text-blue-400 text-xs font-semibold">{week.weekNumber}주차</span>
                  <h3 className={`text-white font-semibold ${week.isCompleted ? 'line-through opacity-60' : ''}`}>{week.title}</h3>
                  {isMentor && (
                    <button onClick={() => {
                      setError('');
                      setWeekForm({ weekNumber: week.weekNumber, title: week.title, description: week.description || '', topics: [...week.topics], resources: [...week.resources] });
                      setTopicInput(''); setResourceInput('');
                      setWeekModal({ editing: week });
                    }} className="text-gray-500 hover:text-blue-400 text-xs transition-colors">수정</button>
                  )}
                </div>
                {week.description && <p className="text-gray-400 text-sm mt-1">{week.description}</p>}
                {week.topics.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-3">
                    {week.topics.map((t, i) => <span key={i} className="px-2 py-0.5 rounded-md text-xs bg-blue-500/10 text-blue-400">{t}</span>)}
                  </div>
                )}
                {week.resources.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-2">
                    {week.resources.map((r, i) => (
                      <a key={i} href={r} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 text-xs text-cyan-400 hover:text-cyan-300">
                        <ExternalLink size={12} />{new URL(r).hostname}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Week Add/Edit Modal */}
      {weekModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">{weekModal.editing ? `${weekModal.editing.weekNumber}주차 수정` : '주차 추가'}</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              {!weekModal.editing && (
                <div>
                  <label className="text-gray-400 text-sm">주차 번호</label>
                  <input type="number" value={weekForm.weekNumber} onChange={e => setWeekForm({ ...weekForm, weekNumber: Number(e.target.value) })} min={1}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              )}
              <div>
                <label className="text-gray-400 text-sm">제목</label>
                <input type="text" value={weekForm.title} onChange={e => setWeekForm({ ...weekForm, title: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">설명</label>
                <textarea value={weekForm.description} onChange={e => setWeekForm({ ...weekForm, description: e.target.value })} rows={2}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">주제 (Enter로 추가)</label>
                <div className="flex gap-2 mt-1">
                  <input type="text" value={topicInput} onChange={e => setTopicInput(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && topicInput.trim()) { e.preventDefault(); setWeekForm({ ...weekForm, topics: [...weekForm.topics, topicInput.trim()] }); setTopicInput(''); } }}
                    className="flex-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {weekForm.topics.map((t, i) => (
                    <span key={i} className="flex items-center gap-1 px-2 py-0.5 rounded-md text-xs bg-blue-500/10 text-blue-400">
                      {t} <button onClick={() => setWeekForm({ ...weekForm, topics: weekForm.topics.filter((_, j) => j !== i) })} className="hover:text-red-400">×</button>
                    </span>
                  ))}
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">학습자료 URL (Enter로 추가)</label>
                <div className="flex gap-2 mt-1">
                  <input type="text" value={resourceInput} onChange={e => setResourceInput(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && resourceInput.trim()) { e.preventDefault(); setWeekForm({ ...weekForm, resources: [...weekForm.resources, resourceInput.trim()] }); setResourceInput(''); } }}
                    className="flex-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {weekForm.resources.map((r, i) => (
                    <span key={i} className="flex items-center gap-1 px-2 py-0.5 rounded-md text-xs bg-cyan-500/10 text-cyan-400 max-w-[200px] truncate">
                      {r} <button onClick={() => setWeekForm({ ...weekForm, resources: weekForm.resources.filter((_, j) => j !== i) })} className="hover:text-red-400 shrink-0">×</button>
                    </span>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setWeekModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleUpdateCurriculum} disabled={submitting || !weekForm.title}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : (weekModal.editing ? '수정' : '추가')}
              </button>
            </div>
          </div>
        </div>
      )}
      {createModal && renderCreateModal()}
    </div>
  );
}

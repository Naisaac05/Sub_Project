'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { ClipboardList, Plus, Loader2, Clock, Star, ExternalLink } from 'lucide-react';
import { getAssignments, createAssignment, submitAssignment, feedbackAssignment } from '@/lib/lms';
import type { AssignmentResponse, AssignmentType } from '@/lib/lms-types';

export default function AssignmentsPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');
  const [createModal, setCreateModal] = useState(false);
  const [submitModal, setSubmitModal] = useState<AssignmentResponse | null>(null);
  const [feedbackModal, setFeedbackModal] = useState<AssignmentResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [createForm, setCreateForm] = useState({ type: 'TASK' as AssignmentType, title: '', description: '', dueDate: '' });
  const [submitForm, setSubmitForm] = useState({ submissionUrl: '', submissionNote: '' });
  const [fbForm, setFbForm] = useState({ feedbackContent: '', grade: '' });

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const res = await getAssignments(matchingId, filter || undefined);
      setAssignments(res.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId, filter]);

  const handleCreate = async () => {
    setSubmitting(true); setError('');
    try {
      await createAssignment({ matchingId, ...createForm, dueDate: createForm.dueDate || undefined });
      setCreateModal(false);
      setCreateForm({ type: 'TASK', title: '', description: '', dueDate: '' });
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '생성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleSubmit = async () => {
    if (!submitModal) return;
    setSubmitting(true); setError('');
    try {
      await submitAssignment(submitModal.id, { submissionUrl: submitForm.submissionUrl, submissionNote: submitForm.submissionNote || undefined });
      setSubmitModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '제출에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleFeedback = async () => {
    if (!feedbackModal) return;
    setSubmitting(true); setError('');
    try {
      await feedbackAssignment(feedbackModal.id, { feedbackContent: fbForm.feedbackContent, grade: fbForm.grade || undefined });
      setFeedbackModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '피드백 작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const statusLabel: Record<string, string> = { ASSIGNED: '미제출', SUBMITTED: '제출됨', REVIEWED: '리뷰 완료' };
  const statusColor: Record<string, string> = {
    ASSIGNED: 'bg-amber-500/10 text-amber-400',
    SUBMITTED: 'bg-blue-500/10 text-blue-400',
    REVIEWED: 'bg-green-500/10 text-green-400',
  };
  const typeLabel: Record<string, string> = { TASK: '과제', CODE_REVIEW: '코드리뷰' };

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">과제 / 코드리뷰</h1>
          <p className="text-gray-400 mt-1">과제를 확인하고 제출하세요</p>
        </div>
        {isMentor && (
          <button onClick={() => setCreateModal(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
            <Plus size={16} />과제 만들기
          </button>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2">
        {[{ label: '전체', value: '' }, { label: '과제', value: 'TASK' }, { label: '코드리뷰', value: 'CODE_REVIEW' }].map(tab => (
          <button key={tab.value} onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${filter === tab.value ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
            {tab.label}
          </button>
        ))}
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {assignments.length === 0 ? (
        <div className="text-center py-20">
          <ClipboardList size={48} className="mx-auto text-gray-600 mb-4" />
          <p className="text-gray-400">아직 등록된 과제가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {assignments.map(a => (
            <div key={a.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    <span className="px-2 py-0.5 rounded text-xs bg-violet-500/10 text-violet-400">{typeLabel[a.type]}</span>
                    <span className={`px-2 py-0.5 rounded text-xs ${statusColor[a.status]}`}>{statusLabel[a.status]}</span>
                  </div>
                  <h3 className="text-white font-semibold">{a.title}</h3>
                  {a.description && <p className="text-gray-400 text-sm mt-1 line-clamp-2">{a.description}</p>}
                  {a.dueDate && (
                    <div className="flex items-center gap-1.5 mt-2 text-gray-500 text-xs">
                      <Clock size={12} />마감: {a.dueDate}
                    </div>
                  )}
                  {/* Submission Info */}
                  {a.submission && (
                    <div className="mt-3 p-3 rounded-xl bg-white/3 border border-white/5">
                      <a href={a.submission.submissionUrl} target="_blank" rel="noopener noreferrer"
                        className="flex items-center gap-1 text-cyan-400 text-sm hover:text-cyan-300">
                        <ExternalLink size={14} />{a.submission.submissionUrl}
                      </a>
                      {a.submission.submissionNote && <p className="text-gray-400 text-xs mt-1">{a.submission.submissionNote}</p>}
                      {a.submission.feedbackContent && (
                        <div className="mt-2 pt-2 border-t border-white/5">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-green-400 text-xs font-medium">멘토 피드백</span>
                            {a.submission.grade && <span className="flex items-center gap-0.5 text-amber-400 text-xs"><Star size={10} />{a.submission.grade}</span>}
                          </div>
                          <p className="text-gray-300 text-sm">{a.submission.feedbackContent}</p>
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <div className="flex gap-2 shrink-0 ml-4">
                  {!isMentor && a.status === 'ASSIGNED' && (
                    <button onClick={() => { setSubmitForm({ submissionUrl: '', submissionNote: '' }); setSubmitModal(a); }}
                      className="px-3 py-1.5 rounded-lg text-xs font-medium bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors">제출</button>
                  )}
                  {isMentor && a.status === 'SUBMITTED' && (
                    <button onClick={() => { setFbForm({ feedbackContent: '', grade: '' }); setFeedbackModal(a); }}
                      className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">피드백</button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {createModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">과제 만들기</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">타입</label>
                <select value={createForm.type} onChange={e => setCreateForm({ ...createForm, type: e.target.value as AssignmentType })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50">
                  <option value="TASK">과제</option>
                  <option value="CODE_REVIEW">코드리뷰</option>
                </select>
              </div>
              <div>
                <label className="text-gray-400 text-sm">제목</label>
                <input type="text" value={createForm.title} onChange={e => setCreateForm({ ...createForm, title: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">설명</label>
                <textarea value={createForm.description} onChange={e => setCreateForm({ ...createForm, description: e.target.value })} rows={3}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">마감일</label>
                <input type="date" value={createForm.dueDate} onChange={e => setCreateForm({ ...createForm, dueDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setCreateModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleCreate} disabled={submitting || !createForm.title}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Submit Modal */}
      {submitModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-2">과제 제출</h3>
            <p className="text-gray-400 text-sm mb-4">{submitModal.title}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">GitHub URL</label>
                <input type="url" value={submitForm.submissionUrl} onChange={e => setSubmitForm({ ...submitForm, submissionUrl: e.target.value })} placeholder="https://github.com/..."
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">메모</label>
                <textarea value={submitForm.submissionNote} onChange={e => setSubmitForm({ ...submitForm, submissionNote: e.target.value })} rows={2} placeholder="제출 관련 메모"
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setSubmitModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleSubmit} disabled={submitting || !submitForm.submissionUrl}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '제출'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Feedback Modal */}
      {feedbackModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-2">피드백 작성</h3>
            <p className="text-gray-400 text-sm mb-4">{feedbackModal.title}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">피드백 내용</label>
                <textarea value={fbForm.feedbackContent} onChange={e => setFbForm({ ...fbForm, feedbackContent: e.target.value })} rows={4}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">등급</label>
                <select value={fbForm.grade} onChange={e => setFbForm({ ...fbForm, grade: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50">
                  <option value="">선택 안함</option>
                  <option value="A">A</option><option value="B">B</option><option value="C">C</option><option value="D">D</option><option value="F">F</option>
                </select>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setFeedbackModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleFeedback} disabled={submitting || !fbForm.feedbackContent}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-green-600 to-green-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '피드백 등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { Briefcase, FileUp, Plus, Loader2, Star, MessageSquare, ExternalLink } from 'lucide-react';
import { getResumes, uploadResume, feedbackResume, getMockInterviews, createMockInterview } from '@/lib/lms';
import type { ResumeResponse, MockInterviewResponse } from '@/lib/lms-types';

export default function CareerPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';
  const isMentee = user?.role === 'MENTEE';

  const [tab, setTab] = useState<'resume' | 'interview'>('resume');
  const [resumes, setResumes] = useState<ResumeResponse[]>([]);
  const [interviews, setInterviews] = useState<MockInterviewResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Modals
  const [uploadModal, setUploadModal] = useState(false);
  const [fbModal, setFbModal] = useState<ResumeResponse | null>(null);
  const [interviewModal, setInterviewModal] = useState(false);

  // Forms
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fbContent, setFbContent] = useState('');
  const [interviewForm, setInterviewForm] = useState({ interviewDate: '', topic: '', questionsAndAnswers: '', mentorFeedback: '', rating: 3 });

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const [rRes, iRes] = await Promise.all([getResumes(matchingId), getMockInterviews(matchingId)]);
      setResumes(rRes.data.data || []);
      setInterviews(iRes.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId]);

  const handleUpload = async () => {
    if (!selectedFile) return;
    setSubmitting(true); setError('');
    try {
      await uploadResume(matchingId, selectedFile);
      setUploadModal(false); setSelectedFile(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '업로드에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleFeedback = async () => {
    if (!fbModal) return;
    setSubmitting(true); setError('');
    try {
      await feedbackResume(fbModal.id, { mentorFeedback: fbContent });
      setFbModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '피드백 작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleCreateInterview = async () => {
    setSubmitting(true); setError('');
    try {
      await createMockInterview({
        matchingId, interviewDate: interviewForm.interviewDate, topic: interviewForm.topic,
        questionsAndAnswers: interviewForm.questionsAndAnswers || undefined,
        mentorFeedback: interviewForm.mentorFeedback || undefined, rating: interviewForm.rating,
      });
      setInterviewModal(false);
      setInterviewForm({ interviewDate: '', topic: '', questionsAndAnswers: '', mentorFeedback: '', rating: 3 });
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '생성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">취업 지원</h1>
        <p className="text-gray-400 mt-1">이력서 관리와 모의면접 기록</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2">
        <button onClick={() => setTab('resume')}
          className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${tab === 'resume' ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
          이력서
        </button>
        <button onClick={() => setTab('interview')}
          className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${tab === 'interview' ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
          모의면접
        </button>
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {/* Resume Tab */}
      {tab === 'resume' && (
        <div className="space-y-4">
          {isMentee && (
            <button onClick={() => setUploadModal(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
              <FileUp size={16} />이력서 업로드
            </button>
          )}
          {resumes.length === 0 ? (
            <div className="text-center py-20">
              <Briefcase size={48} className="mx-auto text-gray-600 mb-4" />
              <p className="text-gray-400">등록된 이력서가 없습니다.</p>
            </div>
          ) : (
            resumes.map(r => (
              <div key={r.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-3 mb-1">
                      <h3 className="text-white font-semibold">{r.fileName}</h3>
                      <span className="px-2 py-0.5 rounded text-xs bg-blue-500/10 text-blue-400">v{r.version}</span>
                    </div>
                    <p className="text-gray-500 text-xs">{new Date(r.uploadedAt).toLocaleDateString('ko-KR')}</p>
                    {r.mentorFeedback && (
                      <div className="mt-3 p-3 rounded-xl bg-white/3 border border-white/5">
                        <span className="text-green-400 text-xs font-medium">멘토 피드백</span>
                        <p className="text-gray-300 text-sm mt-1">{r.mentorFeedback}</p>
                      </div>
                    )}
                  </div>
                  <div className="flex gap-2 shrink-0 ml-4">
                    <a href={r.fileUrl} target="_blank" rel="noopener noreferrer"
                      className="px-3 py-1.5 rounded-lg text-xs font-medium bg-cyan-500/10 text-cyan-400 hover:bg-cyan-500/20 transition-colors">
                      <ExternalLink size={14} />
                    </a>
                    {isMentor && !r.mentorFeedback && (
                      <button onClick={() => { setFbContent(''); setFbModal(r); }}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">피드백</button>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Interview Tab */}
      {tab === 'interview' && (
        <div className="space-y-4">
          {isMentor && (
            <button onClick={() => setInterviewModal(true)}
              className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
              <Plus size={16} />모의면접 기록
            </button>
          )}
          {interviews.length === 0 ? (
            <div className="text-center py-20">
              <MessageSquare size={48} className="mx-auto text-gray-600 mb-4" />
              <p className="text-gray-400">모의면접 기록이 없습니다.</p>
            </div>
          ) : (
            interviews.map(iv => (
              <div key={iv.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-center gap-3 mb-2">
                  <h3 className="text-white font-semibold">{iv.topic}</h3>
                  {iv.rating && (
                    <span className="flex items-center gap-0.5 text-amber-400 text-xs">
                      <Star size={10} fill="currentColor" />{iv.rating}/5
                    </span>
                  )}
                </div>
                <p className="text-gray-500 text-xs mb-3">{iv.interviewDate}</p>
                {iv.questionsAndAnswers && <p className="text-gray-400 text-sm whitespace-pre-wrap mb-3">{iv.questionsAndAnswers}</p>}
                {iv.mentorFeedback && (
                  <div className="p-3 rounded-xl bg-white/3 border border-white/5">
                    <span className="text-green-400 text-xs font-medium">피드백</span>
                    <p className="text-gray-300 text-sm mt-1">{iv.mentorFeedback}</p>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* Upload Modal */}
      {uploadModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">이력서 업로드</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="mb-4">
              <input type="file" accept=".pdf,.doc,.docx" onChange={e => setSelectedFile(e.target.files?.[0] || null)}
                className="w-full text-sm text-gray-400 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-blue-500/10 file:text-blue-400 hover:file:bg-blue-500/20" />
            </div>
            <div className="flex gap-3">
              <button onClick={() => setUploadModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleUpload} disabled={submitting || !selectedFile}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '업로드'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Resume Feedback Modal */}
      {fbModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-2">이력서 피드백</h3>
            <p className="text-gray-400 text-sm mb-4">{fbModal.fileName} v{fbModal.version}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <textarea value={fbContent} onChange={e => setFbContent(e.target.value)} rows={5} placeholder="이력서에 대한 피드백을 작성하세요"
              className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
            <div className="flex gap-3 mt-4">
              <button onClick={() => setFbModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleFeedback} disabled={submitting || !fbContent.trim()}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-green-600 to-green-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '피드백 등록'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Interview Create Modal */}
      {interviewModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-lg p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">모의면접 기록</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-gray-400 text-sm">면접 날짜</label>
                  <input type="date" value={interviewForm.interviewDate} onChange={e => setInterviewForm({ ...interviewForm, interviewDate: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div>
                  <label className="text-gray-400 text-sm">주제</label>
                  <input type="text" value={interviewForm.topic} onChange={e => setInterviewForm({ ...interviewForm, topic: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">질문 & 답변</label>
                <textarea value={interviewForm.questionsAndAnswers} onChange={e => setInterviewForm({ ...interviewForm, questionsAndAnswers: e.target.value })} rows={4}
                  placeholder={"Q: 질문 내용\nA: 답변 내용"}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">피드백</label>
                <textarea value={interviewForm.mentorFeedback} onChange={e => setInterviewForm({ ...interviewForm, mentorFeedback: e.target.value })} rows={3}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">평가 ({interviewForm.rating}/5)</label>
                <div className="flex gap-1 mt-1">
                  {[1, 2, 3, 4, 5].map(n => (
                    <button key={n} onClick={() => setInterviewForm({ ...interviewForm, rating: n })}>
                      <Star size={20} className={n <= interviewForm.rating ? 'text-amber-400 fill-amber-400' : 'text-gray-600'} />
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setInterviewModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleCreateInterview} disabled={submitting || !interviewForm.interviewDate || !interviewForm.topic}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '기록 저장'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

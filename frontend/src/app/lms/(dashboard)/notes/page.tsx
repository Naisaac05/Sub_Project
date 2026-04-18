'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { NotebookPen, Plus, Loader2, Star, MessageSquare, ChevronDown, ChevronUp, Send } from 'lucide-react';
import { getNotes, getNote, createNote, addNoteComment } from '@/lib/lms';
import type { NoteResponse, NoteType } from '@/lib/lms-types';

export default function NotesPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentee = user?.role === 'MENTEE';

  const [notes, setNotes] = useState<NoteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');
  const [createModal, setCreateModal] = useState(false);
  const [expandedNote, setExpandedNote] = useState<number | null>(null);
  const [detailNote, setDetailNote] = useState<NoteResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [createForm, setCreateForm] = useState({ type: 'SESSION_REVIEW' as NoteType, title: '', content: '', weekNumber: 1, selfRating: 3 });
  const [commentInput, setCommentInput] = useState('');

  const fetchData = async () => {
    if (!matchingId) return;
    try {
      const res = await getNotes(matchingId, filter || undefined);
      setNotes(res.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, [matchingId, filter]);

  const handleCreate = async () => {
    setSubmitting(true); setError('');
    try {
      await createNote({ matchingId, type: createForm.type, title: createForm.title, content: createForm.content, weekNumber: createForm.weekNumber, selfRating: createForm.selfRating });
      setCreateModal(false);
      setCreateForm({ type: 'SESSION_REVIEW', title: '', content: '', weekNumber: 1, selfRating: 3 });
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const handleExpand = async (noteId: number) => {
    if (expandedNote === noteId) { setExpandedNote(null); setDetailNote(null); return; }
    try {
      const res = await getNote(noteId);
      setDetailNote(res.data.data);
      setExpandedNote(noteId);
      setCommentInput('');
    } catch { setError('노트 조회에 실패했습니다'); }
  };

  const handleComment = async () => {
    if (!detailNote || !commentInput.trim()) return;
    setSubmitting(true);
    try {
      await addNoteComment(detailNote.id, { content: commentInput });
      const res = await getNote(detailNote.id);
      setDetailNote(res.data.data);
      setCommentInput('');
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '코멘트 작성에 실패했습니다'); }
    finally { setSubmitting(false); }
  };

  const typeLabel: Record<string, string> = { SESSION_REVIEW: '세션 리뷰', WEEKLY_JOURNAL: '주간 학습일지' };
  const typeColor: Record<string, string> = { SESSION_REVIEW: 'bg-blue-500/10 text-blue-400', WEEKLY_JOURNAL: 'bg-violet-500/10 text-violet-400' };

  if (loading) {
    return <div className="flex items-center justify-center h-64"><div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" /></div>;
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">학습 노트</h1>
          <p className="text-gray-400 mt-1">학습 내용을 기록하고 피드백을 받으세요</p>
        </div>
        {isMentee && (
          <button onClick={() => setCreateModal(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-blue-400 bg-blue-500/10 hover:bg-blue-500/20 transition-colors">
            <Plus size={16} />노트 작성
          </button>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2">
        {[{ label: '전체', value: '' }, { label: '세션 리뷰', value: 'SESSION_REVIEW' }, { label: '주간 학습일지', value: 'WEEKLY_JOURNAL' }].map(tab => (
          <button key={tab.value} onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${filter === tab.value ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}>
            {tab.label}
          </button>
        ))}
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      {notes.length === 0 ? (
        <div className="text-center py-20">
          <NotebookPen size={48} className="mx-auto text-gray-600 mb-4" />
          <p className="text-gray-400">아직 작성된 노트가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {notes.map(note => (
            <div key={note.id} className="bg-[#0f1420] border border-white/5 rounded-2xl overflow-hidden">
              <button onClick={() => handleExpand(note.id)} className="w-full p-6 text-left">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <span className={`px-2 py-0.5 rounded text-xs ${typeColor[note.type]}`}>{typeLabel[note.type]}</span>
                      {note.weekNumber && <span className="text-gray-500 text-xs">{note.weekNumber}주차</span>}
                      {note.selfRating && (
                        <span className="flex items-center gap-0.5 text-amber-400 text-xs">
                          <Star size={10} fill="currentColor" />{note.selfRating}
                        </span>
                      )}
                    </div>
                    <h3 className="text-white font-semibold">{note.title}</h3>
                    <p className="text-gray-400 text-sm mt-1 line-clamp-2">{note.content}</p>
                    <div className="flex items-center gap-3 mt-2 text-gray-500 text-xs">
                      <span>{note.authorName}</span>
                      <span>{new Date(note.createdAt).toLocaleDateString('ko-KR')}</span>
                      <span className="flex items-center gap-1"><MessageSquare size={10} />{note.comments?.length || 0}</span>
                    </div>
                  </div>
                  <div className="shrink-0 ml-4 mt-1">
                    {expandedNote === note.id ? <ChevronUp size={18} className="text-gray-500" /> : <ChevronDown size={18} className="text-gray-500" />}
                  </div>
                </div>
              </button>

              {/* Expanded Detail */}
              {expandedNote === note.id && detailNote && (
                <div className="px-6 pb-6 border-t border-white/5 pt-4">
                  <div className="text-gray-300 text-sm whitespace-pre-wrap mb-6">{detailNote.content}</div>

                  {/* Comments */}
                  <div className="space-y-3">
                    <h4 className="text-gray-400 text-sm font-medium">코멘트 ({detailNote.comments?.length || 0})</h4>
                    {detailNote.comments?.map(c => (
                      <div key={c.id} className="p-3 rounded-xl bg-white/3 border border-white/5">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-white text-xs font-medium">{c.authorName}</span>
                          <span className="text-gray-600 text-xs">{new Date(c.createdAt).toLocaleDateString('ko-KR')}</span>
                        </div>
                        <p className="text-gray-300 text-sm">{c.content}</p>
                      </div>
                    ))}

                    {/* Comment Input */}
                    <div className="flex gap-2">
                      <input type="text" value={commentInput} onChange={e => setCommentInput(e.target.value)} placeholder="코멘트를 입력하세요"
                        onKeyDown={e => { if (e.key === 'Enter') handleComment(); }}
                        className="flex-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50" />
                      <button onClick={handleComment} disabled={submitting || !commentInput.trim()}
                        className="px-3 py-2 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors disabled:opacity-60">
                        <Send size={16} />
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {createModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-lg p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">학습 노트 작성</h3>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-gray-400 text-sm">타입</label>
                  <select value={createForm.type} onChange={e => setCreateForm({ ...createForm, type: e.target.value as NoteType })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50">
                    <option value="SESSION_REVIEW">세션 리뷰</option>
                    <option value="WEEKLY_JOURNAL">주간 학습일지</option>
                  </select>
                </div>
                <div>
                  <label className="text-gray-400 text-sm">주차</label>
                  <input type="number" value={createForm.weekNumber} onChange={e => setCreateForm({ ...createForm, weekNumber: Number(e.target.value) })} min={1}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">제목</label>
                <input type="text" value={createForm.title} onChange={e => setCreateForm({ ...createForm, title: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">내용</label>
                <textarea value={createForm.content} onChange={e => setCreateForm({ ...createForm, content: e.target.value })} rows={8}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">자기 평가 ({createForm.selfRating}/5)</label>
                <div className="flex gap-1 mt-1">
                  {[1, 2, 3, 4, 5].map(n => (
                    <button key={n} onClick={() => setCreateForm({ ...createForm, selfRating: n })}>
                      <Star size={20} className={n <= createForm.selfRating ? 'text-amber-400 fill-amber-400' : 'text-gray-600'} />
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setCreateModal(false)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5">취소</button>
              <button onClick={handleCreate} disabled={submitting || !createForm.title || !createForm.content}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-blue-600 to-blue-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '작성'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

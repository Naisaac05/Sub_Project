'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { NotebookPen, Star } from 'lucide-react';
import { getNotes } from '@/lib/lms';
import type { NoteResponse } from '@/lib/lms-types';

export default function NotesPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [notes, setNotes] = useState<NoteResponse[]>([]);
  const [filter, setFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    getNotes(matchingId, filter || undefined)
      .then((res) => setNotes(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId, filter]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">학습 노트</h1>
        <p className="text-gray-400 mt-1">세션 회고와 주간 일지를 기록하세요</p>
      </div>

      <div className="flex gap-2">
        {[
          { label: '전체', value: '' },
          { label: '세션 회고', value: 'SESSION_REVIEW' },
          { label: '주간 일지', value: 'WEEKLY_JOURNAL' },
        ].map((tab) => (
          <button
            key={tab.value}
            onClick={() => setFilter(tab.value)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              filter === tab.value
                ? 'bg-blue-500/10 text-blue-400'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {notes.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">작성된 노트가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {notes.map((note) => (
            <div key={note.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-start gap-3">
                <NotebookPen size={20} className="text-cyan-400 mt-0.5 shrink-0" />
                <div className="flex-1">
                  <div className="flex items-center gap-3">
                    <h3 className="text-white font-semibold">{note.title}</h3>
                    <span className="px-2.5 py-0.5 rounded-md text-xs font-medium bg-white/5 text-gray-400">
                      {note.type === 'SESSION_REVIEW' ? '세션 회고' : '주간 일지'}
                    </span>
                    {note.selfRating && (
                      <span className="inline-flex items-center gap-1 text-xs text-yellow-400">
                        <Star size={12} fill="currentColor" /> {note.selfRating}
                      </span>
                    )}
                  </div>
                  <p className="text-gray-400 text-sm mt-2 line-clamp-3">{note.content}</p>
                  <div className="flex items-center gap-3 mt-3 text-xs text-gray-500">
                    <span>{note.authorName}</span>
                    <span>{new Date(note.createdAt).toLocaleDateString('ko-KR')}</span>
                    {note.comments.length > 0 && <span>코멘트 {note.comments.length}개</span>}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

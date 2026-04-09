'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { FileText, MessageSquare } from 'lucide-react';
import { getResumes, getMockInterviews } from '@/lib/lms';
import type { ResumeResponse, MockInterviewResponse } from '@/lib/lms-types';

export default function CareerPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [resumes, setResumes] = useState<ResumeResponse[]>([]);
  const [interviews, setInterviews] = useState<MockInterviewResponse[]>([]);
  const [tab, setTab] = useState<'resume' | 'interview'>('resume');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    Promise.all([
      getResumes(matchingId).then((res) => setResumes(res.data.data || [])),
      getMockInterviews(matchingId).then((res) => setInterviews(res.data.data || [])),
    ])
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId]);

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
        <h1 className="text-2xl font-bold text-white">취업 지원</h1>
        <p className="text-gray-400 mt-1">이력서 관리와 모의 면접 기록</p>
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => setTab('resume')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            tab === 'resume' ? 'bg-blue-500/10 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'
          }`}
        >
          이력서 ({resumes.length})
        </button>
        <button
          onClick={() => setTab('interview')}
          className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
            tab === 'interview' ? 'bg-blue-500/10 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'
          }`}
        >
          모의 면접 ({interviews.length})
        </button>
      </div>

      {tab === 'resume' ? (
        resumes.length === 0 ? (
          <div className="text-center py-20"><p className="text-gray-400">등록된 이력서가 없습니다.</p></div>
        ) : (
          <div className="space-y-4">
            {resumes.map((r) => (
              <div key={r.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-start gap-3">
                  <FileText size={20} className="text-blue-400 mt-0.5" />
                  <div className="flex-1">
                    <div className="flex items-center gap-3">
                      <h3 className="text-white font-semibold">{r.fileName}</h3>
                      <span className="text-gray-500 text-xs">v{r.version}</span>
                    </div>
                    <p className="text-gray-500 text-xs mt-1">
                      {new Date(r.uploadedAt).toLocaleDateString('ko-KR')} 업로드
                    </p>
                    {r.mentorFeedback && (
                      <div className="mt-3 p-3 rounded-lg bg-white/3">
                        <p className="text-gray-400 text-xs mb-1">멘토 피드백</p>
                        <p className="text-gray-300 text-sm">{r.mentorFeedback}</p>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      ) : (
        interviews.length === 0 ? (
          <div className="text-center py-20"><p className="text-gray-400">기록된 모의 면접이 없습니다.</p></div>
        ) : (
          <div className="space-y-4">
            {interviews.map((mi) => (
              <div key={mi.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
                <div className="flex items-start gap-3">
                  <MessageSquare size={20} className="text-purple-400 mt-0.5" />
                  <div className="flex-1">
                    <h3 className="text-white font-semibold">{mi.topic}</h3>
                    <p className="text-gray-500 text-xs mt-1">{mi.interviewDate}</p>
                    {mi.rating && (
                      <span className="text-yellow-400 text-xs">평가: {mi.rating}/5</span>
                    )}
                    {mi.mentorFeedback && (
                      <div className="mt-3 p-3 rounded-lg bg-white/3">
                        <p className="text-gray-400 text-xs mb-1">멘토 피드백</p>
                        <p className="text-gray-300 text-sm">{mi.mentorFeedback}</p>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      )}
    </div>
  );
}

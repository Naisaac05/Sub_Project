'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ClipboardList, GitPullRequest, ExternalLink } from 'lucide-react';
import { getAssignments } from '@/lib/lms';
import type { AssignmentResponse } from '@/lib/lms-types';

export default function AssignmentsPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [filter, setFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    getAssignments(matchingId, filter || undefined)
      .then((res) => setAssignments(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId, filter]);

  const statusLabel: Record<string, string> = {
    ASSIGNED: '진행중',
    SUBMITTED: '제출완료',
    REVIEWED: '리뷰완료',
  };

  const statusColor: Record<string, string> = {
    ASSIGNED: 'bg-yellow-500/10 text-yellow-400',
    SUBMITTED: 'bg-blue-500/10 text-blue-400',
    REVIEWED: 'bg-green-500/10 text-green-400',
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">과제 / 코드리뷰</h1>
          <p className="text-gray-400 mt-1">과제 제출과 코드리뷰 현황을 관리하세요</p>
        </div>
      </div>

      {/* 필터 탭 */}
      <div className="flex gap-2">
        {[
          { label: '전체', value: '' },
          { label: '과제', value: 'TASK' },
          { label: '코드리뷰', value: 'CODE_REVIEW' },
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

      {assignments.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">등록된 과제가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {assignments.map((a) => (
            <div key={a.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  {a.type === 'TASK' ? (
                    <ClipboardList size={20} className="text-blue-400 mt-0.5" />
                  ) : (
                    <GitPullRequest size={20} className="text-purple-400 mt-0.5" />
                  )}
                  <div>
                    <h3 className="text-white font-semibold">{a.title}</h3>
                    {a.description && (
                      <p className="text-gray-400 text-sm mt-1 line-clamp-2">{a.description}</p>
                    )}
                    <div className="flex items-center gap-3 mt-2 text-xs text-gray-500">
                      {a.dueDate && <span>마감: {a.dueDate}</span>}
                      <span>{new Date(a.createdAt).toLocaleDateString('ko-KR')}</span>
                    </div>
                    {a.referenceUrls && a.referenceUrls.length > 0 && (
                      <div className="flex gap-2 mt-2">
                        {a.referenceUrls.map((url, i) => (
                          <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-blue-400">
                            <ExternalLink size={12} /> 링크 {i + 1}
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
                <span className={`px-2.5 py-0.5 rounded-md text-xs font-medium shrink-0 ${statusColor[a.status] || ''}`}>
                  {statusLabel[a.status] || a.status}
                </span>
              </div>
              {a.submission && (
                <div className="mt-4 pt-4 border-t border-white/5">
                  <p className="text-gray-400 text-xs mb-1">제출물</p>
                  <a href={a.submission.submissionUrl} target="_blank" rel="noopener noreferrer"
                    className="text-blue-400 text-sm hover:underline">{a.submission.submissionUrl}</a>
                  {a.submission.feedbackContent && (
                    <div className="mt-2 p-3 rounded-lg bg-white/3">
                      <p className="text-gray-400 text-xs mb-1">멘토 피드백 {a.submission.grade && `(${a.submission.grade})`}</p>
                      <p className="text-gray-300 text-sm">{a.submission.feedbackContent}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

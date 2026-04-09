'use client';

import { useEffect, useState } from 'react';
import { Video, Clock } from 'lucide-react';
import apiClient from '@/lib/api';
import type { ApiResponse } from '@/lib/types';

interface Session {
  id: number;
  matchingId: number;
  category: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  status: string;
  meetLink: string | null;
  memo: string | null;
}

export default function SessionsPage() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiClient.get<ApiResponse<Session[]>>('/sessions')
      .then((res) => setSessions(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  const statusLabel: Record<string, string> = {
    SCHEDULED: '예정',
    COMPLETED: '완료',
    CANCELLED: '취소',
  };

  const statusColor: Record<string, string> = {
    SCHEDULED: 'bg-blue-500/10 text-blue-400',
    COMPLETED: 'bg-green-500/10 text-green-400',
    CANCELLED: 'bg-red-500/10 text-red-400',
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">멘토링 세션</h1>
        <p className="text-gray-400 mt-1">예정된 세션과 지난 세션을 확인하세요</p>
      </div>

      {sessions.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">아직 예정된 세션이 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {sessions.map((session) => (
            <div key={session.id} className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-white font-semibold">{session.category}</h3>
                    <span className={`px-2.5 py-0.5 rounded-md text-xs font-medium ${statusColor[session.status] || ''}`}>
                      {statusLabel[session.status] || session.status}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-2 text-gray-400 text-sm">
                    <Clock size={14} />
                    <span>{session.sessionDate} {session.startTime} ~ {session.endTime}</span>
                  </div>
                  {session.memo && (
                    <p className="text-gray-500 text-sm mt-2">{session.memo}</p>
                  )}
                </div>
                {session.meetLink && session.status === 'SCHEDULED' && (
                  <a
                    href={session.meetLink}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors text-sm font-medium"
                  >
                    <Video size={16} />
                    참여
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

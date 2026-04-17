'use client';

import { useState, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import LmsSidebar from '@/components/lms/LmsSidebar';
import { useAuth } from '@/contexts/AuthContext';
import { Globe, Video, Check, Link as LinkIcon, ExternalLink } from 'lucide-react';

export default function VideoMeetingsPage() {
  const searchParams = useSearchParams();
  const matchingId = searchParams.get('matchingId') || '1'; // Default to 1 for demo if none
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [sessions, setSessions] = useState<any[]>([]);
  const [meetings, setMeetings] = useState<Record<number, any>>({});
  const [loading, setLoading] = useState(true);

  // Form state
  const [editingSessionId, setEditingSessionId] = useState<number | null>(null);
  const [platform, setPlatform] = useState('Google Meet');
  const [url, setUrl] = useState('');

  useEffect(() => {
    // In a real app, fetch sessions and then their meetings
    // Mock data for UI demonstration
    setTimeout(() => {
      setSessions([
        { id: 101, sessionDate: '2026-05-01', startTime: '19:00', endTime: '20:00', status: 'SCHEDULED' },
        { id: 102, sessionDate: '2026-05-08', startTime: '19:00', endTime: '20:00', status: 'SCHEDULED' },
      ]);
      setMeetings({
        101: { platform: 'Google Meet', url: 'https://meet.google.com/abc-defg-hij' }
      });
      setLoading(false);
    }, 500);
  }, [matchingId]);

  const handleSave = async (sessionId: number) => {
    // Call the newly implemented backend API
    try {
      const res = await fetch('/api/video-meetings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId, platform, url })
      });
      const data = await res.json();
      setMeetings(prev => ({ ...prev, [sessionId]: data }));
      setEditingSessionId(null);
    } catch (e) {
      console.error(e);
      alert('저장 실패');
    }
  };

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-3xl font-extrabold text-white tracking-tight flex items-center gap-3">
          <Globe className="text-blue-500" size={32} />
          화상회의 관리
        </h1>
        <p className="mt-2 text-gray-400">
          멘토링 세션에 사용할 화상회의(Zoom, Google Meet 등) 링크를 관리하고 접속할 수 있습니다.
        </p>
      </header>

      {loading ? (
        <div className="text-center py-20 text-gray-500">로딩 중...</div>
      ) : (
        <div className="space-y-4">
          {sessions.map(session => {
            const meeting = meetings[session.id];
            const isEditing = editingSessionId === session.id;

            return (
              <div key={session.id} className="bg-white/5 border border-white/10 rounded-2xl p-6 transition-colors hover:bg-white/10">
                <div className="flex flex-col sm:flex-row gap-6 justify-between items-start sm:items-center">
                  <div>
                    <div className="flex items-center gap-3 mb-2">
                      <span className="px-3 py-1 rounded-full bg-blue-500/20 text-blue-400 text-xs font-bold font-mono">
                        SESSION #{session.id}
                      </span>
                      <span className="text-gray-400 text-sm">{session.sessionDate} {session.startTime}~{session.endTime}</span>
                    </div>
                    <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                      <Video size={18} className="text-gray-400" />
                      {meeting ? meeting.platform : '미등록 상태'}
                    </h3>
                  </div>

                  <div className="w-full sm:w-auto">
                    {!isEditing && !meeting && isMentor && (
                      <button
                        onClick={() => {
                          setEditingSessionId(session.id);
                          setPlatform('Google Meet');
                          setUrl('');
                        }}
                        className="w-full sm:w-auto px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-xl transition-colors"
                      >
                        링크 등록하기
                      </button>
                    )}
                    {!isEditing && !meeting && !isMentor && (
                      <span className="text-gray-500 text-sm">멘토님이 링크를 등록할 예정입니다.</span>
                    )}
                    {!isEditing && meeting && (
                      <div className="flex gap-2">
                        {isMentor && (
                          <button
                            onClick={() => {
                              setEditingSessionId(session.id);
                              setPlatform(meeting.platform);
                              setUrl(meeting.url);
                            }}
                            className="px-4 py-2 bg-white/10 hover:bg-white/20 text-white text-sm font-medium rounded-xl transition-colors"
                          >
                            수정
                          </button>
                        )}
                        <a
                          href={meeting.url}
                          target="_blank"
                          rel="noreferrer"
                          className="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-xl transition-colors flex items-center gap-2"
                        >
                          참가하기 <ExternalLink size={16} />
                        </a>
                      </div>
                    )}
                  </div>
                </div>

                {isEditing && (
                  <div className="mt-5 pt-5 border-t border-white/10 flex flex-col gap-4">
                    <div className="grid sm:grid-cols-[200px_1fr] gap-4">
                      <div>
                        <label className="block text-sm font-medium text-gray-400 mb-2">플랫폼</label>
                        <select
                          value={platform}
                          onChange={(e) => setPlatform(e.target.value)}
                          className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-blue-500 focus:outline-none"
                        >
                          <option value="Google Meet">Google Meet</option>
                          <option value="Zoom">Zoom</option>
                          <option value="Microsoft Teams">Microsoft Teams</option>
                          <option value="기타">기타</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-400 mb-2">화상회의 URL</label>
                        <div className="relative">
                          <LinkIcon size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500" />
                          <input
                            type="url"
                            value={url}
                            onChange={(e) => setUrl(e.target.value)}
                            placeholder="https://..."
                            className="w-full bg-white/5 border border-white/10 rounded-xl pl-11 pr-4 py-3 text-white focus:border-blue-500 focus:outline-none"
                          />
                        </div>
                      </div>
                    </div>
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={() => setEditingSessionId(null)}
                        className="px-4 py-2 bg-white/5 hover:bg-white/10 text-white text-sm font-medium rounded-xl transition-colors"
                      >
                        취소
                      </button>
                      <button
                        onClick={() => handleSave(session.id)}
                        disabled={!url}
                        className="px-6 py-2 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-600 text-white text-sm font-medium rounded-xl transition-colors flex items-center gap-2"
                      >
                        <Check size={16} /> 저장
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

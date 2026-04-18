'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { AxiosError } from 'axios';
import { Check, ExternalLink, Globe, Link as LinkIcon, PlusCircle, Video } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import apiClient from '@/lib/api';
import { getLmsSessions } from '@/lib/lms';
import { getMyMatchingsAsMentee, getMyMatchingsAsMentor } from '@/lib/matching';
import type { SessionListResponse } from '@/lib/lms-types';
import type { ApiResponse, MatchingResponse } from '@/lib/types';

type VideoMeetingResponse = {
  id: number;
  sessionId: number;
  platform: string;
  title: string | null;
  url: string;
  createdAt: string;
};

const DEFAULT_PLATFORM = 'Google Meet';

export default function VideoMeetingsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user, isLoading: authLoading } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [sessions, setSessions] = useState<SessionListResponse[]>([]);
  const [meetings, setMeetings] = useState<Record<number, VideoMeetingResponse>>({});
  const [loading, setLoading] = useState(true);
  const [pageMessage, setPageMessage] = useState('');
  const [editingSessionId, setEditingSessionId] = useState<number | null>(null);
  const [savingSessionId, setSavingSessionId] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [platform, setPlatform] = useState(DEFAULT_PLATFORM);
  const [url, setUrl] = useState('');

  const sortedSessions = useMemo(
    () =>
      [...sessions].sort((a, b) => {
        const left = `${a.sessionDate}T${a.startTime}`;
        const right = `${b.sessionDate}T${b.startTime}`;
        return left.localeCompare(right);
      }),
    [sessions]
  );

  useEffect(() => {
    const resolveMatching = async () => {
      if (authLoading) {
        return;
      }

      if (matchingId) {
        return;
      }

      if (!user) {
        setPageMessage('로그인 정보를 확인한 뒤 화상회의를 열 수 있습니다.');
        setLoading(false);
        return;
      }

      try {
        const response = user.role === 'MENTOR'
          ? await getMyMatchingsAsMentor()
          : await getMyMatchingsAsMentee();

        const activeMatching = response.data.find(
          (matching: MatchingResponse) => matching.status === 'ACCEPTED' || matching.status === 'TRIAL'
        );

        if (activeMatching) {
          router.replace(`/lms/video-meetings?matchingId=${activeMatching.id}`);
          return;
        }

        setPageMessage('연결된 LMS 매칭이 아직 없어 화상회의를 표시할 수 없습니다.');
      } catch (error) {
        console.error(error);
        setPageMessage('매칭 정보를 불러오지 못해 화상회의 화면을 열 수 없습니다.');
      } finally {
        setLoading(false);
      }
    };

    void resolveMatching();
  }, [authLoading, matchingId, router, user]);

  useEffect(() => {
    const fetchData = async () => {
      if (!matchingId) {
        return;
      }

      setLoading(true);
      setPageMessage('');

      try {
        const sessionsRes = await getLmsSessions(matchingId);
        const nextSessions = sessionsRes.data.data || [];
        setSessions(nextSessions);

        const meetingEntries = await Promise.all(
          nextSessions.map(async (session) => {
            try {
              const meetingRes = await apiClient.get<ApiResponse<VideoMeetingResponse>>(
                `/video-meetings/session/${session.id}`
              );
              return meetingRes.data.data ? ([session.id, meetingRes.data.data] as const) : null;
            } catch (error) {
              const axiosError = error as AxiosError;
              if (axiosError.response?.status === 404) {
                return null;
              }
              console.warn(`video meeting lookup failed for session ${session.id}`, axiosError.response?.status);
              return null;
            }
          })
        );

        setMeetings(
          Object.fromEntries(
            meetingEntries.filter((entry): entry is readonly [number, VideoMeetingResponse] => entry !== null)
          )
        );
      } catch (error) {
        console.error(error);
        alert('화상회의 정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void fetchData();
  }, [matchingId]);

  const resetForm = () => {
    setEditingSessionId(null);
    setTitle('');
    setPlatform(DEFAULT_PLATFORM);
    setUrl('');
  };

  const startEditing = (session: SessionListResponse) => {
    const meeting = meetings[session.id];
    setEditingSessionId(session.id);
    setTitle(meeting?.title ?? session.title ?? '');
    setPlatform(meeting?.platform ?? DEFAULT_PLATFORM);
    setUrl(meeting?.url ?? session.meetLink ?? '');
  };

  const handleSave = async (sessionId: number) => {
    const trimmedUrl = url.trim();
    const trimmedTitle = title.trim();

    if (!trimmedUrl) {
      return;
    }

    setSavingSessionId(sessionId);

    try {
      const response = await apiClient.post<ApiResponse<VideoMeetingResponse>>('/video-meetings', {
        sessionId,
        title: trimmedTitle,
        platform,
        url: trimmedUrl,
      });

      const savedMeeting = response.data.data;

      setMeetings((prev) => ({
        ...prev,
        [sessionId]: savedMeeting,
      }));
      setSessions((prev) =>
        prev.map((session) =>
          session.id === sessionId
            ? {
                ...session,
                title: savedMeeting.title,
                meetLink: savedMeeting.url,
              }
            : session
        )
      );
      resetForm();
    } catch (error) {
      console.error(error);
      alert('화상회의 정보를 저장하지 못했습니다.');
    } finally {
      setSavingSessionId(null);
    }
  };

  return (
    <div className="space-y-8">
      <header>
        <h1 className="flex items-center gap-3 text-3xl font-extrabold tracking-tight text-white">
          <Globe className="text-blue-500" size={32} />
          화상회의
        </h1>
        <p className="mt-2 text-gray-400">
          멘토는 세션별 화상회의 제목과 링크를 등록하고 수정할 수 있고, 멘티는 최신 링크로 바로 입장할 수 있습니다.
        </p>
      </header>

      {loading ? (
        <div className="py-20 text-center text-gray-500">불러오는 중...</div>
      ) : pageMessage ? (
        <div className="rounded-2xl border border-white/10 bg-white/5 p-6 text-sm text-gray-400">
          {pageMessage}
        </div>
      ) : sortedSessions.length === 0 ? (
        <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
          <p className="text-sm text-gray-300">
            아직 등록된 멘토링 세션이 없습니다.
          </p>
          <p className="mt-2 text-sm text-gray-500">
            화상회의 링크는 세션 단위로 등록되기 때문에, 먼저 멘토링 세션을 만든 뒤 이 화면에서 회의 링크를 등록할 수 있습니다.
          </p>
          {isMentor ? (
            <button
              onClick={() => router.push(matchingId ? `/lms/sessions?matchingId=${matchingId}` : '/lms/sessions')}
              className="mt-4 inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-500"
            >
              <PlusCircle size={16} />
              멘토링 세션 만들러 가기
            </button>
          ) : (
            <p className="mt-4 text-sm text-gray-500">
              멘토가 세션을 만들면 여기에서 화상회의 링크를 확인할 수 있습니다.
            </p>
          )}
        </div>
      ) : (
        <div className="space-y-4">
          {sortedSessions.map((session) => {
            const meeting = meetings[session.id];
            const isEditing = editingSessionId === session.id;
            const displayUrl = meeting?.url || session.meetLink;
            const displayTitle = meeting?.title || session.title || meeting?.platform || '미등록 상태';
            const displayPlatform = meeting?.platform || DEFAULT_PLATFORM;

            return (
              <div
                key={session.id}
                className="rounded-2xl border border-white/10 bg-white/5 p-6 transition-colors hover:bg-white/10"
              >
                <div className="flex flex-col items-start justify-between gap-6 sm:flex-row sm:items-center">
                  <div>
                    <div className="mb-2 flex flex-wrap items-center gap-3">
                      <span className="rounded-full bg-blue-500/20 px-3 py-1 font-mono text-xs font-bold text-blue-400">
                        SESSION #{session.id}
                      </span>
                      <span className="text-sm text-gray-400">
                        {session.sessionDate} {session.startTime.slice(0, 5)}~{session.endTime.slice(0, 5)}
                      </span>
                      {meeting?.createdAt && (
                        <span className="text-xs text-gray-500">
                          등록: {new Date(meeting.createdAt).toLocaleString()}
                        </span>
                      )}
                    </div>
                    <h3 className="flex items-center gap-2 text-lg font-semibold text-white">
                      <Video size={18} className="text-gray-400" />
                      {displayTitle}
                      {(meeting?.title || meeting?.platform) && (
                        <span className="text-sm font-normal text-gray-400">({displayPlatform})</span>
                      )}
                    </h3>
                  </div>

                  <div className="w-full sm:w-auto">
                    {!isEditing && !displayUrl && isMentor && (
                      <button
                        onClick={() => startEditing(session)}
                        className="w-full rounded-xl bg-blue-600 px-4 py-2 font-medium text-white transition-colors hover:bg-blue-500 sm:w-auto"
                      >
                        화상회의 등록
                      </button>
                    )}

                    {!isEditing && !displayUrl && !isMentor && (
                      <span className="text-sm text-gray-500">멘토가 아직 화상회의 정보를 등록하지 않았습니다.</span>
                    )}

                    {!isEditing && displayUrl && (
                      <div className="flex gap-2">
                        {isMentor && (
                          <button
                            onClick={() => startEditing(session)}
                            className="rounded-xl bg-white/10 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-white/20"
                          >
                            수정
                          </button>
                        )}
                        <a
                          href={displayUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="flex items-center gap-2 rounded-xl bg-blue-600 px-6 py-2 font-medium text-white transition-colors hover:bg-blue-500"
                        >
                          회의 입장 <ExternalLink size={16} />
                        </a>
                      </div>
                    )}
                  </div>
                </div>

                {isEditing && (
                  <div className="mt-5 flex flex-col gap-4 border-t border-white/10 pt-5">
                    <div>
                      <label className="mb-2 block text-sm font-medium text-gray-400">화상회의 제목</label>
                      <input
                        type="text"
                        value={title}
                        onChange={(event) => setTitle(event.target.value)}
                        placeholder="예: 프론트엔드 포트폴리오 피드백"
                        className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white focus:border-blue-500 focus:outline-none"
                      />
                    </div>

                    <div className="grid gap-4 sm:grid-cols-[200px_1fr]">
                      <div>
                        <label className="mb-2 block text-sm font-medium text-gray-400">플랫폼</label>
                        <select
                          value={platform}
                          onChange={(event) => setPlatform(event.target.value)}
                          className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white focus:border-blue-500 focus:outline-none"
                        >
                          <option value="Google Meet">Google Meet</option>
                          <option value="Zoom">Zoom</option>
                          <option value="Microsoft Teams">Microsoft Teams</option>
                          <option value="기타">기타</option>
                        </select>
                      </div>

                      <div>
                        <label className="mb-2 block text-sm font-medium text-gray-400">화상회의 URL</label>
                        <div className="relative">
                          <LinkIcon
                            size={18}
                            className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500"
                          />
                          <input
                            type="url"
                            value={url}
                            onChange={(event) => setUrl(event.target.value)}
                            placeholder="https://..."
                            className="w-full rounded-xl border border-white/10 bg-white/5 py-3 pl-11 pr-4 text-white focus:border-blue-500 focus:outline-none"
                          />
                        </div>
                      </div>
                    </div>

                    <div className="flex justify-end gap-2">
                      <button
                        onClick={resetForm}
                        className="rounded-xl bg-white/5 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-white/10"
                      >
                        취소
                      </button>
                      <button
                        onClick={() => handleSave(session.id)}
                        disabled={!url.trim() || savingSessionId === session.id}
                        className="flex items-center gap-2 rounded-xl bg-blue-600 px-6 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-500 disabled:bg-gray-600"
                      >
                        <Check size={16} />
                        {savingSessionId === session.id ? '저장 중...' : '저장'}
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

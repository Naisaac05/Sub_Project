'use client';

import { useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import { Clock, AlertTriangle, ChevronRight, Loader2, Video } from 'lucide-react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import type { DateClickArg } from '@fullcalendar/interaction';
import type { EventClickArg, EventInput } from '@fullcalendar/core';
import {
  getLmsSessions, completeLmsSession, cancelLmsSession,
  approveLmsSession, rejectLmsSession,
  getTimeSlots, createTimeSlot, deleteTimeSlot,
  getAvailableSlots, bookSession, createLmsSessionDirect,
  proposeTimeSlot,
  getChangeRequests, createChangeRequest, approveChangeRequest, rejectChangeRequest,
} from '@/lib/lms';
import type {
  SessionListResponse, TimeSlotResponse, ChangeRequestResponse,
} from '@/lib/lms-types';

export default function SessionsPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const { user } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [sessions, setSessions] = useState<SessionListResponse[]>([]);
  const [slots, setSlots] = useState<TimeSlotResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  });

  // Modals
  const [slotModal, setSlotModal] = useState<{ date: string; proposed: TimeSlotResponse[] } | null>(null);
  const [bookingModal, setBookingModal] = useState<{ date: string; available: TimeSlotResponse[]; mode: 'book' | 'propose' } | null>(null);
  const [changeModal, setChangeModal] = useState<{ session: SessionListResponse } | null>(null);
  const [changeDetailModal, setChangeDetailModal] = useState<{ session: SessionListResponse; requests: ChangeRequestResponse[] } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Form state
  const [slotForm, setSlotForm] = useState({ startTime: '19:00', endTime: '20:00' });
  const [slotMode, setSlotMode] = useState<'slot' | 'session'>('slot');
  const [slotMemo, setSlotMemo] = useState('');
  const [bookingMemo, setBookingMemo] = useState('');
  const [changeForm, setChangeForm] = useState({ newDate: '', newStartTime: '19:00', newEndTime: '20:00', reason: '' });

  const fetchData = useCallback(async () => {
    if (!matchingId) return;
    try {
      const [sessRes, slotsRes] = await Promise.all([
        getLmsSessions(matchingId),
        getTimeSlots(matchingId, currentMonth),
      ]);
      setSessions(sessRes.data.data || []);
      setSlots(slotsRes.data.data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [matchingId, currentMonth]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Calendar events
  const calendarEvents: EventInput[] = [
    ...slots.map(s => ({
      id: `slot-${s.id}`,
      title: s.proposedByMentee ? '멘티 제안' : (s.isBooked ? '예약됨' : '가능'),
      start: s.slotDate,
      allDay: true,
      color: s.proposedByMentee ? '#f59e0b' : (s.isBooked ? '#6366f1' : '#3b82f6'),
      extendedProps: { type: 'slot' as const, ...s },
    })),
    ...sessions.map(s => ({
      id: `session-${s.id}`,
      title: s.status === 'COMPLETED' ? '완료' : s.status === 'CANCELLED' ? '취소' : s.status === 'PENDING' ? '승인 대기' : '세션',
      start: s.sessionDate,
      allDay: true,
      color: s.status === 'COMPLETED' ? '#22c55e' : s.status === 'CANCELLED' ? '#ef4444' : s.status === 'PENDING' ? '#f59e0b' : '#8b5cf6',
      extendedProps: { type: 'session' as const, ...s },
    })),
  ];

  const openModalForDate = async (dateStr: string) => {
    setError('');
    if (isMentor) {
      setSlotForm({ startTime: '19:00', endTime: '20:00' });
      setSlotMode('slot');
      setSlotMemo('');
      const proposed = slots.filter(s => s.proposedByMentee && !s.isBooked && s.slotDate === dateStr);
      setSlotModal({ date: dateStr, proposed });
    } else {
      try {
        const res = await getAvailableSlots(matchingId, dateStr);
        const available = res.data.data || [];
        setBookingMemo('');
        setSlotForm({ startTime: '19:00', endTime: '20:00' });
        setBookingModal({ date: dateStr, available, mode: available.length > 0 ? 'book' : 'propose' });
      } catch { setError('슬롯 조회에 실패했습니다'); }
    }
  };

  const handleDateClick = (info: DateClickArg) => {
    openModalForDate(info.dateStr);
  };

  const handleEventClick = (info: EventClickArg) => {
    info.jsEvent.preventDefault();
    const type = info.event.extendedProps?.type;
    if (type === 'slot') {
      const date = info.event.startStr;
      if (date) openModalForDate(date);
    }
  };

  const handlePickProposed = (slot: TimeSlotResponse) => {
    setSlotMode('session');
    setSlotForm({
      startTime: slot.startTime?.slice(0, 5) || '19:00',
      endTime: slot.endTime?.slice(0, 5) || '20:00',
    });
  };

  const handleApproveProposed = async (slot: TimeSlotResponse) => {
    setSubmitting(true); setError('');
    try {
      await createLmsSessionDirect(matchingId, {
        sessionDate: slot.slotDate,
        startTime: slot.startTime?.slice(0, 5) || '',
        endTime: slot.endTime?.slice(0, 5) || '',
        memo: slotMemo || undefined,
      });
      await deleteTimeSlot(matchingId, slot.id);
      setSlotModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '승인에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleRejectProposed = async (slot: TimeSlotResponse) => {
    setSubmitting(true); setError('');
    try {
      await deleteTimeSlot(matchingId, slot.id);
      setSlotModal(prev => prev ? { ...prev, proposed: prev.proposed.filter(p => p.id !== slot.id) } : null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '거절에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handlePropose = async () => {
    if (!bookingModal) return;
    setSubmitting(true); setError('');
    try {
      await proposeTimeSlot(matchingId, {
        slotDate: bookingModal.date,
        startTime: slotForm.startTime,
        endTime: slotForm.endTime,
      });
      setBookingModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '희망 시간 제안에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleCreateSlot = async () => {
    if (!slotModal) return;
    setSubmitting(true); setError('');
    try {
      if (slotMode === 'session') {
        await createLmsSessionDirect(matchingId, {
          sessionDate: slotModal.date,
          startTime: slotForm.startTime,
          endTime: slotForm.endTime,
          memo: slotMemo || undefined,
        });
      } else {
        await createTimeSlot(matchingId, {
          slotDate: slotModal.date,
          startTime: slotForm.startTime,
          endTime: slotForm.endTime,
        });
      }
      setSlotModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || (slotMode === 'session' ? '세션 등록에 실패했습니다' : '슬롯 등록에 실패했습니다'));
    } finally { setSubmitting(false); }
  };

  const handleDeleteSlot = async (slotId: number) => {
    try {
      await deleteTimeSlot(matchingId, slotId);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '슬롯 삭제에 실패했습니다');
    }
  };

  const handleBook = async (slotId: number) => {
    setSubmitting(true); setError('');
    try {
      await bookSession(matchingId, { slotId, memo: bookingMemo || undefined });
      setBookingModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '예약에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleComplete = async (sessionId: number) => {
    try {
      await completeLmsSession(matchingId, sessionId);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '완료 처리에 실패했습니다'); }
  };

  const handleCancel = async (sessionId: number) => {
    try {
      await cancelLmsSession(matchingId, sessionId);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '취소에 실패했습니다'); }
  };

  const handleCreateChange = async () => {
    if (!changeModal) return;
    setSubmitting(true); setError('');
    try {
      await createChangeRequest(matchingId, {
        sessionId: changeModal.session.id,
        newDate: changeForm.newDate,
        newStartTime: changeForm.newStartTime,
        newEndTime: changeForm.newEndTime,
        reason: changeForm.reason || undefined,
      });
      setChangeModal(null);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '변경 요청에 실패했습니다');
    } finally { setSubmitting(false); }
  };

  const handleViewChanges = async (session: SessionListResponse) => {
    try {
      const res = await getChangeRequests(matchingId, session.id);
      setChangeDetailModal({ session, requests: res.data.data || [] });
    } catch { setError('변경 요청 조회에 실패했습니다'); }
  };

  const handleApprove = async (requestId: number) => {
    try {
      await approveChangeRequest(matchingId, requestId);
      setChangeDetailModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '승인에 실패했습니다'); }
  };

  const handleReject = async (requestId: number) => {
    try {
      await rejectChangeRequest(matchingId, requestId);
      setChangeDetailModal(null);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '거절에 실패했습니다'); }
  };

  const statusLabel: Record<string, string> = { PENDING: '승인 대기', SCHEDULED: '예정', COMPLETED: '완료', CANCELLED: '취소' };
  const statusColor: Record<string, string> = {
    PENDING: 'bg-amber-500/10 text-amber-400',
    SCHEDULED: 'bg-blue-500/10 text-blue-400',
    COMPLETED: 'bg-green-500/10 text-green-400',
    CANCELLED: 'bg-red-500/10 text-red-400',
  };

  const handleApproveBooking = async (sessionId: number) => {
    try {
      await approveLmsSession(matchingId, sessionId);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '승인에 실패했습니다'); }
  };

  const handleRejectBooking = async (sessionId: number) => {
    try {
      await rejectLmsSession(matchingId, sessionId);
      fetchData();
    } catch (e: any) { setError(e.response?.data?.message || '거절에 실패했습니다'); }
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
      <div>
        <h1 className="text-2xl font-bold text-white">멘토링 세션</h1>
        <p className="text-gray-400 mt-1">
          {isMentor
            ? '날짜를 클릭해 가용 시간을 열거나, 시간을 자유롭게 설정해 세션을 바로 등록할 수 있습니다'
            : '파란 날짜를 클릭하여 세션을 예약하세요'}
        </p>
      </div>

      {error && (
        <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">{error}</div>
      )}

      {/* Calendar */}
      <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
        <style>{`
          .fc { --fc-border-color: rgba(255,255,255,0.05); --fc-today-bg-color: rgba(59,130,246,0.1); }
          .fc .fc-daygrid-day-number { color: #e5e7eb; font-size: 0.875rem; }
          .fc .fc-col-header-cell-cushion { color: #9ca3af; font-size: 0.75rem; text-transform: uppercase; }
          .fc .fc-button { background: rgba(59,130,246,0.2); border: 1px solid rgba(59,130,246,0.3); color: #93c5fd; font-size: 0.75rem; }
          .fc .fc-button:hover { background: rgba(59,130,246,0.3); }
          .fc .fc-button-active { background: rgba(59,130,246,0.4) !important; }
          .fc .fc-toolbar-title { color: white; font-size: 1.125rem; }
          .fc .fc-event { font-size: 0.625rem; padding: 1px 4px; border-radius: 4px; border: none; cursor: pointer; }
          .fc .fc-daygrid-day:hover { background: rgba(255,255,255,0.02); cursor: pointer; }
          .fc .fc-day-other .fc-daygrid-day-number { color: #4b5563; }
        `}</style>
        <FullCalendar
          plugins={[dayGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          locale="ko"
          headerToolbar={{ left: 'prev', center: 'title', right: 'next' }}
          events={calendarEvents}
          dateClick={handleDateClick}
          eventClick={handleEventClick}
          datesSet={(info) => {
            const mid = new Date((info.start.getTime() + info.end.getTime()) / 2);
            const m = `${mid.getFullYear()}-${String(mid.getMonth() + 1).padStart(2, '0')}`;
            if (m !== currentMonth) setCurrentMonth(m);
          }}
          height="auto"
        />
      </div>

      {/* Session List */}
      <div>
        <h2 className="text-lg font-semibold text-white mb-4">세션 목록</h2>
        {sessions.length === 0 ? (
          <div className="text-center py-12 text-gray-400">아직 예약된 세션이 없습니다.</div>
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
                      {session.hasPendingChangeRequest && (
                        <button
                          onClick={() => handleViewChanges(session)}
                          className="flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-colors"
                        >
                          <AlertTriangle size={12} />
                          변경 요청
                        </button>
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-2 text-gray-400 text-sm">
                      <Clock size={14} />
                      <span>{session.sessionDate} {session.startTime} ~ {session.endTime}</span>
                    </div>
                    {session.memo && <p className="text-gray-500 text-sm mt-2">{session.memo}</p>}
                    {session.status === 'SCHEDULED' && session.sessionDate === `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-${String(new Date().getDate()).padStart(2, '0')}` && (
                      session.meetLink ? (
                        <a
                          href={session.meetLink}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1.5 mt-3 px-3 py-1.5 rounded-lg text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 hover:bg-emerald-500/20 transition-colors"
                        >
                          <Video size={14} />
                          화상 회의 참여하기
                        </a>
                      ) : (
                        <button
                          className="inline-flex items-center gap-1.5 mt-3 px-3 py-1.5 rounded-lg text-xs font-medium bg-blue-500/10 text-blue-400 border border-blue-500/20 hover:bg-blue-500/20 transition-colors"
                        >
                          <Video size={14} />
                          화상 회의 생성하기
                        </button>
                      )
                    )}
                  </div>
                  {session.status === 'PENDING' && (
                    <div className="flex items-center gap-2 shrink-0">
                      {isMentor ? (
                        <>
                          <button onClick={() => handleApproveBooking(session.id)}
                            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">
                            승인
                          </button>
                          <button onClick={() => handleRejectBooking(session.id)}
                            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors">
                            거절
                          </button>
                        </>
                      ) : (
                        <>
                          <span className="text-amber-400 text-xs">멘토 승인 대기 중</span>
                          <button onClick={() => handleCancel(session.id)}
                            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors">
                            취소
                          </button>
                        </>
                      )}
                    </div>
                  )}
                  {session.status === 'SCHEDULED' && (
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => {
                          setChangeForm({ newDate: session.sessionDate, newStartTime: session.startTime?.slice(0,5) || '19:00', newEndTime: session.endTime?.slice(0,5) || '20:00', reason: '' });
                          setChangeModal({ session });
                        }}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-colors"
                      >
                        변경 요청
                      </button>
                      {isMentor && (
                        <button onClick={() => handleComplete(session.id)}
                          className="px-3 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">
                          완료
                        </button>
                      )}
                      <button onClick={() => handleCancel(session.id)}
                        className="px-3 py-1.5 rounded-lg text-xs font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors">
                        취소
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Slot/Session Create Modal (Mentor) */}
      {slotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">
              {slotMode === 'session' ? '세션 바로 등록' : '가용시간 등록'} — {slotModal.date}
            </h3>

            <div className="flex gap-2 mb-4 p-1 rounded-xl bg-white/5 border border-white/5">
              <button
                type="button"
                onClick={() => setSlotMode('slot')}
                className={`flex-1 py-2 rounded-lg text-xs font-medium transition-colors ${
                  slotMode === 'slot'
                    ? 'bg-blue-500/20 text-blue-300 border border-blue-500/30'
                    : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                가용시간 슬롯
              </button>
              <button
                type="button"
                onClick={() => setSlotMode('session')}
                className={`flex-1 py-2 rounded-lg text-xs font-medium transition-colors ${
                  slotMode === 'session'
                    ? 'bg-violet-500/20 text-violet-300 border border-violet-500/30'
                    : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                세션 바로 등록
              </button>
            </div>

            <p className="text-gray-500 text-xs mb-4">
              {slotMode === 'session'
                ? '멘티의 예약 없이 바로 세션으로 등록됩니다. 시작/종료 시간을 자유롭게 설정하세요.'
                : '멘티가 선택해 예약할 수 있는 가용시간을 등록합니다.'}
            </p>

            {slotModal.proposed.length > 0 && (
              <div className="mb-4 p-3 rounded-xl bg-amber-500/5 border border-amber-500/20">
                <p className="text-amber-300 text-xs font-medium mb-2">멘티 제안 시간</p>
                <p className="text-amber-400/70 text-[11px] mb-3">승인하면 해당 시간으로 세션이 바로 등록되고 제안은 정리됩니다.</p>
                <div className="space-y-2">
                  {slotModal.proposed.map(p => (
                    <div key={p.id} className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-amber-500/5 border border-amber-500/20">
                      <div className="flex items-center gap-2 text-amber-200 text-sm">
                        <Clock size={13} />
                        {p.startTime?.slice(0,5)} ~ {p.endTime?.slice(0,5)}
                      </div>
                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => handlePickProposed(p)}
                          disabled={submitting}
                          className="px-2 py-1 rounded text-[11px] font-medium bg-white/5 text-gray-300 hover:bg-white/10 disabled:opacity-60"
                        >
                          양식에 채우기
                        </button>
                        <button
                          type="button"
                          onClick={() => handleApproveProposed(p)}
                          disabled={submitting}
                          className="px-2 py-1 rounded text-[11px] font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 disabled:opacity-60"
                        >
                          승인
                        </button>
                        <button
                          type="button"
                          onClick={() => handleRejectProposed(p)}
                          disabled={submitting}
                          className="px-2 py-1 rounded text-[11px] font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 disabled:opacity-60"
                        >
                          거절
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}

            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">시작 시간</label>
                <input type="time" step={60} value={slotForm.startTime}
                  onChange={e => setSlotForm({ ...slotForm, startTime: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div>
                <label className="text-gray-400 text-sm">종료 시간</label>
                <input type="time" step={60} value={slotForm.endTime}
                  onChange={e => setSlotForm({ ...slotForm, endTime: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              {slotMode === 'session' && (
                <div>
                  <label className="text-gray-400 text-sm">메모 (선택)</label>
                  <input type="text" value={slotMemo} onChange={e => setSlotMemo(e.target.value)}
                    placeholder="세션 주제나 준비사항"
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-violet-500/50" />
                </div>
              )}
            </div>

            <div className="flex gap-3 mt-6">
              <button onClick={() => setSlotModal(null)}
                className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">
                취소
              </button>
              <button onClick={handleCreateSlot} disabled={submitting}
                className={`flex-1 py-2.5 rounded-xl text-white text-sm font-bold disabled:opacity-60 ${
                  slotMode === 'session'
                    ? 'bg-gradient-to-r from-violet-600 to-violet-500'
                    : 'bg-gradient-to-r from-blue-600 to-blue-500'
                }`}>
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : (slotMode === 'session' ? '세션 등록' : '슬롯 등록')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Booking Modal (Mentee) */}
      {bookingModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">
              {bookingModal.mode === 'book' ? '세션 예약' : '희망 시간 제안'} — {bookingModal.date}
            </h3>

            <div className="flex gap-2 mb-4 p-1 rounded-xl bg-white/5 border border-white/5">
              <button
                type="button"
                onClick={() => setBookingModal({ ...bookingModal, mode: 'book' })}
                disabled={bookingModal.available.length === 0}
                className={`flex-1 py-2 rounded-lg text-xs font-medium transition-colors ${
                  bookingModal.mode === 'book'
                    ? 'bg-blue-500/20 text-blue-300 border border-blue-500/30'
                    : 'text-gray-400 hover:text-gray-200 disabled:opacity-40'
                }`}
              >
                가용시간 예약 {bookingModal.available.length > 0 && `(${bookingModal.available.length})`}
              </button>
              <button
                type="button"
                onClick={() => setBookingModal({ ...bookingModal, mode: 'propose' })}
                className={`flex-1 py-2 rounded-lg text-xs font-medium transition-colors ${
                  bookingModal.mode === 'propose'
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                    : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                희망 시간 제안
              </button>
            </div>

            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}

            {bookingModal.mode === 'book' ? (
              <>
                {bookingModal.available.length === 0 ? (
                  <p className="text-gray-400 text-sm mb-4">예약 가능한 가용시간이 없습니다. 상단에서 희망 시간을 제안하세요.</p>
                ) : (
                  <>
                    <p className="text-gray-400 text-sm mb-4">원하는 시간대를 선택하세요</p>
                    <div className="space-y-2 mb-4">
                      {bookingModal.available.map(slot => (
                        <button key={slot.id} onClick={() => handleBook(slot.id)} disabled={submitting}
                          className="w-full flex items-center justify-between p-3 rounded-xl bg-white/3 border border-white/5 hover:border-blue-500/30 hover:bg-blue-500/5 transition-all text-left">
                          <div className="flex items-center gap-2 text-white text-sm">
                            <Clock size={14} className="text-blue-400" />
                            {slot.startTime} ~ {slot.endTime}
                          </div>
                          <ChevronRight size={16} className="text-gray-500" />
                        </button>
                      ))}
                    </div>
                    <div className="mb-4">
                      <label className="text-gray-400 text-sm">메모 (선택)</label>
                      <input type="text" value={bookingMemo} onChange={e => setBookingMemo(e.target.value)} placeholder="세션에 대한 메모"
                        className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50" />
                    </div>
                  </>
                )}
                <button onClick={() => setBookingModal(null)} className="w-full py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">닫기</button>
              </>
            ) : (
              <>
                <p className="text-gray-500 text-xs mb-4">원하는 시간을 제안하면 멘토가 확인 후 세션을 등록합니다.</p>
                <div className="space-y-4 mb-4">
                  <div>
                    <label className="text-gray-400 text-sm">시작 시간</label>
                    <input type="time" step={60} value={slotForm.startTime}
                      onChange={e => setSlotForm({ ...slotForm, startTime: e.target.value })}
                      className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-amber-500/50" />
                  </div>
                  <div>
                    <label className="text-gray-400 text-sm">종료 시간</label>
                    <input type="time" step={60} value={slotForm.endTime}
                      onChange={e => setSlotForm({ ...slotForm, endTime: e.target.value })}
                      className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-amber-500/50" />
                  </div>
                </div>
                <div className="flex gap-3">
                  <button onClick={() => setBookingModal(null)}
                    className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">취소</button>
                  <button onClick={handlePropose} disabled={submitting}
                    className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-amber-600 to-amber-500 disabled:opacity-60">
                    {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '시간 제안'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Change Request Create Modal */}
      {changeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold text-white mb-4">세션 변경 요청</h3>
            <p className="text-gray-400 text-sm mb-4">현재: {changeModal.session.sessionDate} {changeModal.session.startTime} ~ {changeModal.session.endTime}</p>
            {error && <p className="text-red-400 text-sm mb-3">{error}</p>}
            <div className="space-y-4">
              <div>
                <label className="text-gray-400 text-sm">변경 희망 날짜</label>
                <input type="date" value={changeForm.newDate} onChange={e => setChangeForm({ ...changeForm, newDate: e.target.value })}
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-gray-400 text-sm">시작 시간</label>
                  <input type="time" step={60} value={changeForm.newStartTime} onChange={e => setChangeForm({ ...changeForm, newStartTime: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
                <div>
                  <label className="text-gray-400 text-sm">종료 시간</label>
                  <input type="time" step={60} value={changeForm.newEndTime} onChange={e => setChangeForm({ ...changeForm, newEndTime: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-blue-500/50" />
                </div>
              </div>
              <div>
                <label className="text-gray-400 text-sm">변경 사유</label>
                <textarea value={changeForm.reason} onChange={e => setChangeForm({ ...changeForm, reason: e.target.value })} rows={2} placeholder="변경 사유를 입력하세요"
                  className="w-full mt-1 px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-blue-500/50 resize-none" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={() => setChangeModal(null)} className="flex-1 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">취소</button>
              <button onClick={handleCreateChange} disabled={submitting}
                className="flex-1 py-2.5 rounded-xl text-white text-sm font-bold bg-gradient-to-r from-amber-600 to-amber-500 disabled:opacity-60">
                {submitting ? <Loader2 size={16} className="animate-spin mx-auto" /> : '변경 요청'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change Request Detail Modal */}
      {changeDetailModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="glass-card rounded-2xl w-full max-w-md p-6 max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-white mb-4">변경 요청 내역</h3>
            <div className="space-y-4">
              {changeDetailModal.requests.map(cr => (
                <div key={cr.id} className="p-4 rounded-xl bg-white/3 border border-white/5">
                  <div className="flex items-center justify-between mb-2">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      cr.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400' :
                      cr.status === 'APPROVED' ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
                    }`}>{cr.status === 'PENDING' ? '대기' : cr.status === 'APPROVED' ? '승인' : '거절'}</span>
                    <span className="text-gray-500 text-xs">{new Date(cr.createdAt).toLocaleDateString('ko-KR')}</span>
                  </div>
                  <p className="text-white text-sm">{cr.newDate} {cr.newStartTime} ~ {cr.newEndTime}</p>
                  {cr.reason && <p className="text-gray-400 text-xs mt-1">{cr.reason}</p>}
                  {cr.status === 'PENDING' && cr.requesterId !== user?.id && (
                    <div className="flex gap-2 mt-3">
                      <button onClick={() => handleApprove(cr.id)}
                        className="flex-1 py-1.5 rounded-lg text-xs font-medium bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-colors">승인</button>
                      <button onClick={() => handleReject(cr.id)}
                        className="flex-1 py-1.5 rounded-lg text-xs font-medium bg-red-500/10 text-red-400 hover:bg-red-500/20 transition-colors">거절</button>
                    </div>
                  )}
                  {cr.status === 'PENDING' && cr.requesterId === user?.id && (
                    <p className="text-gray-500 text-xs mt-2">상대방의 응답을 기다리고 있습니다</p>
                  )}
                </div>
              ))}
            </div>
            <button onClick={() => setChangeDetailModal(null)} className="w-full mt-4 py-2.5 rounded-xl text-gray-400 text-sm border border-white/10 hover:bg-white/5 transition-all">닫기</button>
          </div>
        </div>
      )}
    </div>
  );
}

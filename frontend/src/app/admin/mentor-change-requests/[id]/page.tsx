'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  getAdminMentorChangeRequest,
  listCandidateMentors,
  approveMentorChangeRequest,
  rejectMentorChangeRequest,
  type AdminMentorChangeDetail,
  type CandidateMentor,
} from '@/lib/admin/mentor-change-requests';

export default function AdminMentorChangeRequestDetailPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Number(params.id);

  const [detail, setDetail] = useState<AdminMentorChangeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [keyword, setKeyword] = useState('');
  const [sameCategoryOnly, setSameCategoryOnly] = useState(true);
  const [candidates, setCandidates] = useState<CandidateMentor[]>([]);
  const [candidatePage, setCandidatePage] = useState(0);
  const [candidateTotalPages, setCandidateTotalPages] = useState(0);
  const [selectedMentorId, setSelectedMentorId] = useState<number | null>(null);

  const [rejectReason, setRejectReason] = useState('');
  const [submitting, setSubmitting] = useState<'approve' | 'reject' | null>(null);

  const reloadDetail = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await getAdminMentorChangeRequest(id));
    } catch (e) {
      setError(e instanceof Error ? e.message : '상세 조회 실패');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    reloadDetail();
  }, [reloadDetail]);

  useEffect(() => {
    if (!detail || detail.status !== 'PENDING') return;
    let cancelled = false;
    listCandidateMentors(id, keyword, candidatePage, 10, sameCategoryOnly)
      .then((res) => {
        if (cancelled) return;
        setCandidates(res.content);
        setCandidateTotalPages(res.totalPages);
      })
      .catch(() => {
        if (cancelled) return;
        setCandidates([]);
        setCandidateTotalPages(0);
      });
    return () => {
      cancelled = true;
    };
  }, [id, detail, keyword, candidatePage, sameCategoryOnly]);

  if (loading) return <div className="text-slate-500">불러오는 중…</div>;
  if (error) return <div className="text-rose-600">{error}</div>;
  if (!detail) return null;

  const isPending = detail.status === 'PENDING';

  const handleApprove = async () => {
    if (!selectedMentorId) return;
    if (!confirm('이 멘토로 교체합니다. 되돌릴 수 없습니다. 진행할까요?')) return;
    setSubmitting('approve');
    try {
      await approveMentorChangeRequest(id, selectedMentorId);
      alert('멘토 교체가 완료되었습니다');
      router.push('/admin/mentor-change-requests');
    } catch (e) {
      alert(e instanceof Error ? e.message : '승인 실패');
    } finally {
      setSubmitting(null);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) return;
    if (!confirm('이 신청을 반려합니다. 진행할까요?')) return;
    setSubmitting('reject');
    try {
      await rejectMentorChangeRequest(id, rejectReason);
      alert('신청을 반려했습니다');
      router.push('/admin/mentor-change-requests');
    } catch (e) {
      alert(e instanceof Error ? e.message : '반려 실패');
    } finally {
      setSubmitting(null);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
        <h2 className="text-lg font-semibold">신청 정보</h2>
        <dl className="grid grid-cols-3 gap-y-3 text-sm">
          <dt className="text-slate-500">신청일</dt>
          <dd className="col-span-2">
            {new Date(detail.createdAt).toLocaleString('ko-KR')}
          </dd>
          <dt className="text-slate-500">멘티</dt>
          <dd className="col-span-2">
            {detail.menteeName}{' '}
            <span className="text-slate-400">({detail.menteeEmail})</span>
          </dd>
          <dt className="text-slate-500">분야</dt>
          <dd className="col-span-2">{detail.currentCategory ?? '-'}</dd>
          <dt className="text-slate-500">현재 멘토</dt>
          <dd className="col-span-2">{detail.currentMentorName}</dd>
          <dt className="text-slate-500">상태</dt>
          <dd className="col-span-2">{detail.status}</dd>
        </dl>
        <div>
          <h3 className="text-sm font-medium text-slate-700">멘티 사유</h3>
          <p className="mt-2 whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm text-slate-700">
            {detail.reason}
          </p>
        </div>
      </section>

      {isPending ? (
        <div className="space-y-6">
          <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
            <h2 className="text-lg font-semibold">승인하고 멘토 교체</h2>
            <input
              type="text"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setCandidatePage(0);
              }}
              placeholder="멘토 이름 검색"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <label className="flex items-center gap-2 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={sameCategoryOnly}
                onChange={(e) => {
                  setSameCategoryOnly(e.target.checked);
                  setCandidatePage(0);
                  setSelectedMentorId(null);
                }}
              />
              같은 분야 멘토만 보기 (해제 시 전체 멘토)
            </label>
            <div className="max-h-72 overflow-y-auto rounded-md border border-slate-200">
              {candidates.length === 0 ? (
                <p className="p-4 text-center text-sm text-slate-400">
                  {sameCategoryOnly
                    ? '같은 분야 후보 멘토가 없습니다 (위 체크 해제 시 전체 멘토 보기)'
                    : '후보 멘토가 없습니다'}
                </p>
              ) : (
                <table className="w-full text-sm">
                  <tbody className="divide-y divide-slate-100">
                    {candidates.map((c) => (
                      <tr
                        key={c.userId}
                        className="cursor-pointer hover:bg-slate-50"
                        onClick={() => setSelectedMentorId(c.userId)}
                      >
                        <td className="w-10 px-3 py-2">
                          <input
                            type="radio"
                            name="candidate"
                            checked={selectedMentorId === c.userId}
                            onChange={() => setSelectedMentorId(c.userId)}
                          />
                        </td>
                        <td className="px-3 py-2">
                          <div className="font-medium">{c.name}</div>
                          <div className="text-xs text-slate-500">{c.email}</div>
                        </td>
                        <td className="px-3 py-2 text-right text-xs text-slate-500">
                          진행중 {c.activeMenteeCount}명
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {candidateTotalPages > 1 && (
              <div className="flex items-center justify-between text-sm">
                <button
                  disabled={candidatePage === 0}
                  onClick={() => setCandidatePage((p) => p - 1)}
                  className="rounded-md border border-slate-300 px-2 py-1 disabled:opacity-50"
                >
                  이전
                </button>
                <span className="text-slate-500">
                  {candidatePage + 1} / {candidateTotalPages}
                </span>
                <button
                  disabled={candidatePage >= candidateTotalPages - 1}
                  onClick={() => setCandidatePage((p) => p + 1)}
                  className="rounded-md border border-slate-300 px-2 py-1 disabled:opacity-50"
                >
                  다음
                </button>
              </div>
            )}
            <button
              disabled={!selectedMentorId || submitting === 'approve'}
              onClick={handleApprove}
              className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {submitting === 'approve' ? '처리 중…' : '교체 승인'}
            </button>
          </section>

          <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
            <h2 className="text-lg font-semibold">반려</h2>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="반려 사유 (1~500자)"
              maxLength={500}
              rows={4}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            <div className="text-right text-xs text-slate-400">
              {rejectReason.length} / 500
            </div>
            <button
              disabled={!rejectReason.trim() || submitting === 'reject'}
              onClick={handleReject}
              className="w-full rounded-md border border-rose-600 px-4 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50 disabled:opacity-50"
            >
              {submitting === 'reject' ? '처리 중…' : '반려'}
            </button>
          </section>
        </div>
      ) : (
        <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="text-lg font-semibold">처리 결과</h2>
          {detail.status === 'APPROVED' && (
            <p className="text-sm text-slate-700">
              <strong>{detail.newMentorName}</strong> (id: {detail.newMentorId}) 로
              교체됨 · 처리일{' '}
              {detail.respondedAt
                ? new Date(detail.respondedAt).toLocaleString('ko-KR')
                : '-'}
            </p>
          )}
          {detail.status === 'REJECTED' && (
            <>
              <p className="text-sm text-slate-700">
                반려 · 처리일{' '}
                {detail.respondedAt
                  ? new Date(detail.respondedAt).toLocaleString('ko-KR')
                  : '-'}
              </p>
              <p className="whitespace-pre-wrap rounded-md bg-rose-50 p-3 text-sm text-rose-800">
                {detail.rejectReason}
              </p>
            </>
          )}
          {detail.status === 'CANCELLED' && (
            <p className="text-sm text-slate-700">
              멘티 본인이 취소함 ·{' '}
              {detail.respondedAt
                ? new Date(detail.respondedAt).toLocaleString('ko-KR')
                : '-'}
            </p>
          )}
        </section>
      )}
    </div>
  );
}

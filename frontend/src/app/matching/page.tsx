'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyMatchingsAsMentee, getMyMatchingsAsMentor, acceptMatching } from '@/lib/matching';
import type { MatchingResponse } from '@/lib/types';
import {
  Clock,
  CheckCircle,
  XCircle,
  AlertCircle,
  Loader2,
  MessageSquare,
  User,
  Send,
  ArrowRight,
} from 'lucide-react';

type TabType = 'mentee' | 'mentor';

function formatKoreanDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

function StatusBadge({ status }: { status: MatchingResponse['status'] }) {
  const config: Record<
    MatchingResponse['status'],
    { label: string; className: string; icon: React.ReactNode }
  > = {
    PENDING: {
      label: '대기 중',
      className: 'bg-amber-50 text-amber-600 border border-amber-200',
      icon: <Clock size={12} />,
    },
    ACCEPTED: {
      label: '수락됨',
      className: 'bg-emerald-50 text-emerald-600 border border-emerald-200',
      icon: <CheckCircle size={12} />,
    },
    REJECTED: {
      label: '거절됨',
      className: 'bg-red-50 text-red-600 border border-red-200',
      icon: <XCircle size={12} />,
    },
    CANCELLED: {
      label: '취소됨',
      className: 'bg-gray-50 text-gray-500 border border-gray-200',
      icon: <AlertCircle size={12} />,
    },
  };

  const { label, className, icon } = config[status];

  return (
    <span
      className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold ${className}`}
    >
      {icon}
      {label}
    </span>
  );
}

interface MenteeCardProps {
  matching: MatchingResponse;
}

function MenteeCard({ matching }: MenteeCardProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 hover:border-blue-100 hover:shadow-lg hover:shadow-blue-50 transition-all duration-300">
      {/* Header row */}
      <div className="flex items-start justify-between gap-4 mb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center flex-shrink-0">
            <User size={18} className="text-white" />
          </div>
          <div>
            <p className="font-bold text-gray-900">{matching.mentorName} 멘토</p>
            <span className="inline-block mt-0.5 px-2 py-0.5 rounded-md text-xs font-medium bg-blue-50 text-blue-600 border border-blue-100">
              {matching.category}
            </span>
          </div>
        </div>
        <StatusBadge status={matching.status} />
      </div>

      {/* Message */}
      {matching.message && (
        <div className="flex items-start gap-2 mb-3 p-3 rounded-xl bg-gray-50">
          <MessageSquare size={14} className="text-gray-400 mt-0.5 flex-shrink-0" />
          <p className="text-sm text-gray-600 line-clamp-2">{matching.message}</p>
        </div>
      )}

      {/* Test score */}
      {matching.testScore !== null && (
        <div className="flex items-center gap-2 mb-3">
          <span className="text-xs text-gray-500">테스트 점수</span>
          <span className="px-2 py-0.5 rounded-md bg-violet-50 text-violet-600 border border-violet-100 text-xs font-semibold">
            {matching.testScore}점
          </span>
        </div>
      )}

      {/* Rejected reason */}
      {matching.status === 'REJECTED' && matching.rejectedReason && (
        <div className="mb-3 p-3 rounded-xl bg-red-50 border border-red-100">
          <p className="text-xs text-red-500 font-medium mb-0.5">거절 사유</p>
          <p className="text-sm text-red-600">{matching.rejectedReason}</p>
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-gray-50 mt-3">
        <span className="text-xs text-gray-400">{formatKoreanDate(matching.createdAt)}</span>
      </div>
    </div>
  );
}

interface MentorCardProps {
  matching: MatchingResponse;
  onUpdate: (updated: MatchingResponse) => void;
}

function MentorCard({ matching, onUpdate }: MentorCardProps) {
  const [rejectMode, setRejectMode] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleAccept = async () => {
    setIsSubmitting(true);
    setError('');
    try {
      const res = await acceptMatching(matching.id, { accepted: true });
      if (res.success) {
        onUpdate(res.data);
      } else {
        setError(res.message || '수락 처리 중 오류가 발생했습니다.');
      }
    } catch {
      setError('수락 처리 중 오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      setError('거절 사유를 입력해주세요.');
      return;
    }
    setIsSubmitting(true);
    setError('');
    try {
      const res = await acceptMatching(matching.id, {
        accepted: false,
        rejectedReason: rejectReason.trim(),
      });
      if (res.success) {
        onUpdate(res.data);
        setRejectMode(false);
        setRejectReason('');
      } else {
        setError(res.message || '거절 처리 중 오류가 발생했습니다.');
      }
    } catch {
      setError('거절 처리 중 오류가 발생했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 hover:border-blue-100 hover:shadow-lg hover:shadow-blue-50 transition-all duration-300">
      {/* Header row */}
      <div className="flex items-start justify-between gap-4 mb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-pink-500 flex items-center justify-center flex-shrink-0">
            <User size={18} className="text-white" />
          </div>
          <div>
            <p className="font-bold text-gray-900">{matching.menteeName}</p>
            <span className="inline-block mt-0.5 px-2 py-0.5 rounded-md text-xs font-medium bg-blue-50 text-blue-600 border border-blue-100">
              {matching.category}
            </span>
          </div>
        </div>
        <StatusBadge status={matching.status} />
      </div>

      {/* Message */}
      {matching.message && (
        <div className="flex items-start gap-2 mb-3 p-3 rounded-xl bg-gray-50">
          <MessageSquare size={14} className="text-gray-400 mt-0.5 flex-shrink-0" />
          <p className="text-sm text-gray-600 line-clamp-2">{matching.message}</p>
        </div>
      )}

      {/* Test score */}
      {matching.testScore !== null && (
        <div className="flex items-center gap-2 mb-3">
          <span className="text-xs text-gray-500">테스트 점수</span>
          <span className="px-2 py-0.5 rounded-md bg-violet-50 text-violet-600 border border-violet-100 text-xs font-semibold">
            {matching.testScore}점
          </span>
        </div>
      )}

      {/* Rejected reason (already decided) */}
      {matching.status === 'REJECTED' && matching.rejectedReason && (
        <div className="mb-3 p-3 rounded-xl bg-red-50 border border-red-100">
          <p className="text-xs text-red-500 font-medium mb-0.5">거절 사유</p>
          <p className="text-sm text-red-600">{matching.rejectedReason}</p>
        </div>
      )}

      {/* Error message */}
      {error && (
        <div className="mb-3 p-3 rounded-xl bg-red-50 border border-red-100">
          <p className="text-sm text-red-600">{error}</p>
        </div>
      )}

      {/* Reject reason textarea */}
      {rejectMode && matching.status === 'PENDING' && (
        <div className="mb-3">
          <label className="block text-xs font-medium text-gray-600 mb-1.5">
            거절 사유 <span className="text-red-500">*</span>
          </label>
          <textarea
            value={rejectReason}
            onChange={(e) => {
              setRejectReason(e.target.value);
              setError('');
            }}
            placeholder="거절 사유를 입력해주세요..."
            rows={3}
            className="w-full px-3 py-2.5 rounded-xl bg-gray-50 border border-gray-200 text-sm text-gray-900 placeholder-gray-400 resize-none focus:outline-none focus:border-red-300 focus:ring-2 focus:ring-red-100 transition-all duration-200"
          />
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-gray-50 mt-3">
        <span className="text-xs text-gray-400">{formatKoreanDate(matching.createdAt)}</span>

        {/* Action buttons — only for PENDING */}
        {matching.status === 'PENDING' && (
          <div className="flex items-center gap-2">
            {!rejectMode ? (
              <>
                <button
                  onClick={handleAccept}
                  disabled={isSubmitting}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-600 text-white text-xs font-semibold transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {isSubmitting ? (
                    <Loader2 size={12} className="animate-spin" />
                  ) : (
                    <CheckCircle size={12} />
                  )}
                  수락
                </button>
                <button
                  onClick={() => {
                    setRejectMode(true);
                    setError('');
                  }}
                  disabled={isSubmitting}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500 hover:bg-red-600 text-white text-xs font-semibold transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  <XCircle size={12} />
                  거절
                </button>
              </>
            ) : (
              <>
                <button
                  onClick={handleReject}
                  disabled={isSubmitting}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-500 hover:bg-red-600 text-white text-xs font-semibold transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {isSubmitting ? (
                    <Loader2 size={12} className="animate-spin" />
                  ) : (
                    <Send size={12} />
                  )}
                  거절 확인
                </button>
                <button
                  onClick={() => {
                    setRejectMode(false);
                    setRejectReason('');
                    setError('');
                  }}
                  disabled={isSubmitting}
                  className="px-3 py-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-600 text-xs font-semibold transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  취소
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default function MatchingPage() {
  const router = useRouter();
  const { user, isLoading: authLoading, isLoggedIn } = useAuth();

  const [activeTab, setActiveTab] = useState<TabType>('mentee');

  const [menteeMatchings, setMenteeMatchings] = useState<MatchingResponse[]>([]);
  const [mentorMatchings, setMentorMatchings] = useState<MatchingResponse[]>([]);

  const [menteeLoading, setMenteeLoading] = useState(false);
  const [mentorLoading, setMentorLoading] = useState(false);

  const [menteeError, setMenteeError] = useState('');
  const [mentorError, setMentorError] = useState('');

  // Auth guard
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.push('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  // Fetch mentee matchings when tab is active
  useEffect(() => {
    if (!isLoggedIn) return;
    if (activeTab !== 'mentee') return;

    const fetchMenteeMatchings = async () => {
      setMenteeLoading(true);
      setMenteeError('');
      try {
        const res = await getMyMatchingsAsMentee();
        if (res.success) {
          setMenteeMatchings(res.data);
        } else {
          setMenteeError(res.message || '데이터를 불러오지 못했습니다.');
        }
      } catch {
        setMenteeError('데이터를 불러오는 중 오류가 발생했습니다.');
      } finally {
        setMenteeLoading(false);
      }
    };

    fetchMenteeMatchings();
  }, [activeTab, isLoggedIn]);

  // Fetch mentor matchings when tab is active
  useEffect(() => {
    if (!isLoggedIn) return;
    if (activeTab !== 'mentor') return;
    if (user?.role !== 'MENTOR') return;

    const fetchMentorMatchings = async () => {
      setMentorLoading(true);
      setMentorError('');
      try {
        const res = await getMyMatchingsAsMentor();
        if (res.success) {
          setMentorMatchings(res.data);
        } else {
          setMentorError(res.message || '데이터를 불러오지 못했습니다.');
        }
      } catch {
        setMentorError('데이터를 불러오는 중 오류가 발생했습니다.');
      } finally {
        setMentorLoading(false);
      }
    };

    fetchMentorMatchings();
  }, [activeTab, isLoggedIn, user?.role]);

  // In-place update for mentor accept/reject
  const handleMentorMatchingUpdate = (updated: MatchingResponse) => {
    setMentorMatchings((prev) =>
      prev.map((m) => (m.id === updated.id ? updated : m))
    );
  };

  // While auth is loading, show spinner
  if (authLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-blue-500" />
      </div>
    );
  }

  if (!isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Hero */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-16 overflow-hidden">
          <div className="orb w-[400px] h-[400px] bg-blue-600/15 -top-20 -right-20" />
          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span className="inline-block px-4 py-1.5 rounded-full glass-card text-cyan-400 text-xs font-bold tracking-wider uppercase mb-4">
              Matching
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              매칭 내역
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              멘토링 신청 현황을 확인하고 관리하세요.
            </p>
          </div>
        </section>

        {/* Tab Navigation */}
        <section className="max-w-7xl mx-auto px-6 -mt-6 relative z-20 mb-8">
          <div className="bg-white rounded-2xl shadow-lg shadow-gray-200/50 border border-gray-100 p-2 inline-flex gap-1">
            <button
              onClick={() => setActiveTab('mentee')}
              className={`px-5 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 ${
                activeTab === 'mentee'
                  ? 'bg-gray-900 text-white shadow-sm'
                  : 'bg-white text-gray-500 border border-gray-200 hover:text-gray-700 hover:bg-gray-50'
              }`}
            >
              내 신청 내역
            </button>
            {user?.role === 'MENTOR' && (
              <button
                onClick={() => setActiveTab('mentor')}
                className={`px-5 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 ${
                  activeTab === 'mentor'
                    ? 'bg-gray-900 text-white shadow-sm'
                    : 'bg-white text-gray-500 border border-gray-200 hover:text-gray-700 hover:bg-gray-50'
                }`}
              >
                받은 매칭 요청
              </button>
            )}
          </div>
        </section>

        {/* Tab Content */}
        <section className="max-w-7xl mx-auto px-6 pb-20">
          {/* ── Mentee Tab ── */}
          {activeTab === 'mentee' && (
            <>
              {menteeLoading ? (
                <div className="flex items-center justify-center py-24">
                  <Loader2 size={32} className="animate-spin text-blue-500" />
                </div>
              ) : menteeError ? (
                <div className="flex flex-col items-center justify-center py-24 text-center">
                  <AlertCircle size={40} className="text-red-400 mb-3" />
                  <p className="text-gray-500 text-lg mb-1">오류가 발생했습니다</p>
                  <p className="text-gray-400 text-sm">{menteeError}</p>
                </div>
              ) : menteeMatchings.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-24 text-center">
                  <div className="w-16 h-16 rounded-2xl bg-gray-100 flex items-center justify-center mb-4">
                    <MessageSquare size={28} className="text-gray-400" />
                  </div>
                  <p className="text-gray-700 font-semibold text-lg mb-1">매칭 내역이 없습니다</p>
                  <p className="text-gray-400 text-sm mb-6">
                    테스트를 응시하거나 멘토를 찾아 매칭을 신청해보세요.
                  </p>
                  <div className="flex items-center gap-3">
                    <Link
                      href="/tests"
                      className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition-colors duration-200"
                    >
                      실력 테스트 하기
                      <ArrowRight size={14} />
                    </Link>
                    <Link
                      href="/mentors"
                      className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 text-sm font-semibold transition-colors duration-200"
                    >
                      멘토 찾기
                      <ArrowRight size={14} />
                    </Link>
                  </div>
                </div>
              ) : (
                <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
                  {menteeMatchings.map((matching) => (
                    <MenteeCard key={matching.id} matching={matching} />
                  ))}
                </div>
              )}
            </>
          )}

          {/* ── Mentor Tab ── */}
          {activeTab === 'mentor' && user?.role === 'MENTOR' && (
            <>
              {mentorLoading ? (
                <div className="flex items-center justify-center py-24">
                  <Loader2 size={32} className="animate-spin text-blue-500" />
                </div>
              ) : mentorError ? (
                <div className="flex flex-col items-center justify-center py-24 text-center">
                  <AlertCircle size={40} className="text-red-400 mb-3" />
                  <p className="text-gray-500 text-lg mb-1">오류가 발생했습니다</p>
                  <p className="text-gray-400 text-sm">{mentorError}</p>
                </div>
              ) : mentorMatchings.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-24 text-center">
                  <div className="w-16 h-16 rounded-2xl bg-gray-100 flex items-center justify-center mb-4">
                    <User size={28} className="text-gray-400" />
                  </div>
                  <p className="text-gray-700 font-semibold text-lg mb-1">매칭 내역이 없습니다</p>
                  <p className="text-gray-400 text-sm mb-6">
                    아직 받은 매칭 요청이 없습니다. 멘티들이 신청하면 여기서 확인할 수 있습니다.
                  </p>
                  <Link
                    href="/mentors"
                    className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition-colors duration-200"
                  >
                    멘토 프로필 보기
                    <ArrowRight size={14} />
                  </Link>
                </div>
              ) : (
                <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
                  {mentorMatchings.map((matching) => (
                    <MentorCard
                      key={matching.id}
                      matching={matching}
                      onUpdate={handleMentorMatchingUpdate}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </main>
      <Footer />
    </>
  );
}

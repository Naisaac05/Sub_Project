'use client';

import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { recommendMentors, requestMatching } from '@/lib/matching';
import type { MentorRecommendResponse } from '@/lib/types';
import {
  Star,
  Award,
  Briefcase,
  Send,
  Loader2,
  CheckCircle,
  X,
  Users,
  ArrowRight,
} from 'lucide-react';

// ─── Match Score Bar ───────────────────────────────────────────────────────────

function MatchScoreBar({ score }: { score: number }) {
  const color =
    score >= 70
      ? 'bg-emerald-500'
      : score >= 40
      ? 'bg-amber-500'
      : 'bg-red-500';

  const textColor =
    score >= 70
      ? 'text-emerald-400'
      : score >= 40
      ? 'text-amber-400'
      : 'text-red-400';

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs">
        <span className="text-gray-500 font-medium">매칭 적합도</span>
        <span className={`font-bold ${textColor}`}>{score}%</span>
      </div>
      <div className="w-full bg-gray-100 rounded-full h-2 overflow-hidden">
        <div
          className={`h-2 rounded-full transition-all duration-700 ${color}`}
          style={{ width: `${Math.min(100, Math.max(0, score))}%` }}
        />
      </div>
    </div>
  );
}

// ─── Mentor Card ───────────────────────────────────────────────────────────────

interface MentorCardProps {
  mentor: MentorRecommendResponse;
  onApply: (mentor: MentorRecommendResponse) => void;
}

function MentorCard({ mentor, onApply }: MentorCardProps) {
  const initial = mentor.name ? mentor.name[0].toUpperCase() : '?';

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-md
                    hover:border-blue-100 transition-all duration-300 flex flex-col p-6 gap-4">
      {/* Header: avatar + name/company */}
      <div className="flex items-start gap-4">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400
                        flex items-center justify-center text-white font-bold text-lg shrink-0">
          {initial}
        </div>
        <div className="min-w-0">
          <h3 className="text-gray-900 font-bold text-base leading-tight">{mentor.name}</h3>
          <div className="flex items-center gap-1.5 mt-0.5 text-gray-500 text-sm">
            <Briefcase size={12} className="shrink-0" />
            <span className="truncate">{mentor.company}</span>
          </div>
          <div className="flex items-center gap-1 mt-0.5 text-gray-400 text-xs">
            <Award size={11} className="shrink-0" />
            <span>경력 {mentor.careerYears}년</span>
          </div>
        </div>
      </div>

      {/* Specialty tags */}
      <div className="flex flex-wrap gap-1.5">
        {mentor.specialty.map((tag) => (
          <span
            key={tag}
            className="px-2.5 py-1 rounded-full text-xs font-medium
                       bg-blue-50 text-blue-600 border border-blue-100"
          >
            {tag}
          </span>
        ))}
      </div>

      {/* Bio — 2-line clamp */}
      {mentor.bio && (
        <p className="text-gray-500 text-sm leading-relaxed line-clamp-2">{mentor.bio}</p>
      )}

      {/* Match score */}
      <MatchScoreBar score={mentor.matchScore} />

      {/* Apply button */}
      <button
        onClick={() => onApply(mentor)}
        className="mt-auto w-full py-2.5 rounded-xl text-sm font-semibold text-white
                   bg-gradient-to-r from-blue-600 to-blue-500
                   hover:from-blue-500 hover:to-blue-400
                   shadow-sm hover:shadow-blue-500/20
                   transition-all duration-200 flex items-center justify-center gap-2"
      >
        <Send size={14} />
        매칭 신청
      </button>
    </div>
  );
}

// ─── Matching Modal ────────────────────────────────────────────────────────────

interface MatchingModalProps {
  mentor: MentorRecommendResponse;
  category: string;
  testResultId?: number;
  onClose: () => void;
  onSuccess: () => void;
}

function MatchingModal({
  mentor,
  category,
  testResultId,
  onClose,
  onSuccess,
}: MatchingModalProps) {
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const MAX_CHARS = 500;

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const res = await requestMatching({
        mentorId: mentor.mentorId,
        category,
        testResultId,
        message: message.trim() || undefined,
      });
      if (res.success) {
        onSuccess();
      } else {
        setError(res.message || '신청 중 오류가 발생했습니다.');
      }
    } catch (err: unknown) {
      const anyErr = err as { response?: { data?: { message?: string } }; message?: string };
      setError(
        anyErr?.response?.data?.message ||
          anyErr?.message ||
          '신청 중 오류가 발생했습니다.'
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backdropFilter: 'blur(6px)', backgroundColor: 'rgba(0,0,0,0.5)' }}
    >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 relative animate-fade-in">
        {/* Close */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 rounded-lg text-gray-400
                     hover:text-gray-600 hover:bg-gray-100 transition-colors"
          aria-label="닫기"
        >
          <X size={18} />
        </button>

        <h2 className="text-gray-900 font-bold text-lg mb-4">매칭 신청</h2>

        {/* Mentor summary */}
        <div className="bg-gray-50 rounded-xl p-4 mb-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-blue-500 to-cyan-400
                          flex items-center justify-center text-white font-bold shrink-0">
            {mentor.name[0].toUpperCase()}
          </div>
          <div className="min-w-0">
            <p className="text-gray-900 font-semibold text-sm">{mentor.name}</p>
            <p className="text-gray-500 text-xs truncate">{mentor.company}</p>
            <div className="flex flex-wrap gap-1 mt-1">
              {mentor.specialty.slice(0, 3).map((tag) => (
                <span
                  key={tag}
                  className="px-1.5 py-0.5 rounded text-xs bg-blue-100 text-blue-600"
                >
                  {tag}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Message textarea */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">
            메시지 <span className="text-gray-400 font-normal">(선택)</span>
          </label>
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value.slice(0, MAX_CHARS))}
            placeholder="멘토에게 전하고 싶은 메시지를 입력하세요"
            rows={4}
            className="w-full border border-gray-200 rounded-xl px-3.5 py-3 text-sm
                       text-gray-800 placeholder-gray-400
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
                       resize-none transition-shadow"
          />
          <div className="flex justify-end mt-1">
            <span
              className={`text-xs ${
                message.length >= MAX_CHARS ? 'text-red-500' : 'text-gray-400'
              }`}
            >
              {message.length}/{MAX_CHARS}
            </span>
          </div>
        </div>

        {/* Error */}
        {error && (
          <div className="mb-4 px-3.5 py-3 rounded-xl bg-red-50 border border-red-100 text-red-600 text-sm">
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex gap-2.5">
          <button
            onClick={onClose}
            disabled={submitting}
            className="flex-1 py-2.5 rounded-xl text-sm font-semibold text-gray-600
                       border border-gray-200 hover:bg-gray-50 transition-colors disabled:opacity-50"
          >
            취소
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="flex-1 py-2.5 rounded-xl text-sm font-semibold text-white
                       bg-gradient-to-r from-blue-600 to-blue-500
                       hover:from-blue-500 hover:to-blue-400
                       transition-all duration-200 disabled:opacity-60
                       flex items-center justify-center gap-2"
          >
            {submitting ? (
              <>
                <Loader2 size={14} className="animate-spin" />
                신청 중...
              </>
            ) : (
              <>
                <Send size={14} />
                신청하기
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Main Content (uses useSearchParams) ──────────────────────────────────────

function RecommendContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const category = searchParams.get('category') ?? '';
  const resultIdParam = searchParams.get('resultId');
  const testResultId = resultIdParam ? parseInt(resultIdParam, 10) : undefined;

  const [mentors, setMentors] = useState<MentorRecommendResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [selectedMentor, setSelectedMentor] = useState<MentorRecommendResponse | null>(null);
  const [successState, setSuccessState] = useState(false);

  // Auth guard
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  // Fetch mentors when category is known
  useEffect(() => {
    if (!category || authLoading || !isLoggedIn) return;

    const fetchMentors = async () => {
      setLoading(true);
      setFetchError(null);
      try {
        const res = await recommendMentors(category);
        if (res.success) {
          setMentors(res.data ?? []);
        } else {
          setFetchError(res.message || '멘토 목록을 불러오지 못했습니다.');
        }
      } catch (err: unknown) {
        const anyErr = err as { response?: { data?: { message?: string } }; message?: string };
        setFetchError(
          anyErr?.response?.data?.message ||
            anyErr?.message ||
            '멘토 목록을 불러오지 못했습니다.'
        );
      } finally {
        setLoading(false);
      }
    };

    fetchMentors();
  }, [category, authLoading, isLoggedIn]);

  // ── Loading / auth guard ──
  if (authLoading) {
    return (
      <div className="min-h-screen bg-[#0a0e1a] flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-blue-400" />
      </div>
    );
  }

  if (!isLoggedIn) return null;

  // ── No category ──
  if (!category) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50 flex items-center justify-center px-6 pt-24 pb-16">
          <div className="text-center max-w-md">
            <div className="w-16 h-16 rounded-2xl bg-amber-100 flex items-center justify-center mx-auto mb-4">
              <Star size={28} className="text-amber-500" />
            </div>
            <h2 className="text-gray-900 font-bold text-xl mb-2">분야를 선택해주세요</h2>
            <p className="text-gray-500 text-sm mb-6">
              추천 멘토를 보려면 테스트 결과 페이지에서 분야를 선택해주세요.
            </p>
            <Link
              href="/tests/results"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold
                         text-white bg-gradient-to-r from-blue-600 to-blue-500
                         hover:from-blue-500 hover:to-blue-400 transition-all duration-200"
            >
              테스트 결과 보러 가기
              <ArrowRight size={14} />
            </Link>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  // ── Success state ──
  if (successState) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50 flex items-center justify-center px-6 pt-24 pb-16">
          <div className="text-center max-w-md">
            <div className="w-16 h-16 rounded-2xl bg-emerald-100 flex items-center justify-center mx-auto mb-4">
              <CheckCircle size={28} className="text-emerald-500" />
            </div>
            <h2 className="text-gray-900 font-bold text-xl mb-2">매칭 신청이 완료되었습니다!</h2>
            <p className="text-gray-500 text-sm mb-8">
              멘토가 신청을 검토한 후 수락 또는 거절 의사를 전달해드립니다.
            </p>
            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <Link
                href="/matching"
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm
                           font-semibold text-white bg-gradient-to-r from-blue-600 to-blue-500
                           hover:from-blue-500 hover:to-blue-400 transition-all duration-200"
              >
                <Users size={14} />
                매칭 내역 보기
              </Link>
              <button
                onClick={() => setSuccessState(false)}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm
                           font-semibold text-gray-700 border border-gray-200 hover:bg-gray-50
                           transition-colors duration-200"
              >
                <ArrowRight size={14} />
                다른 멘토 보기
              </button>
            </div>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Hero */}
        <section
          className="relative pt-28 pb-16 overflow-hidden"
          style={{
            background:
              'linear-gradient(135deg, #0a0e1a 0%, #0f172a 50%, #0a1628 100%)',
          }}
        >
          {/* Decorative orbs */}
          <div
            className="absolute w-96 h-96 rounded-full opacity-10 -top-20 -right-20 pointer-events-none"
            style={{ background: 'radial-gradient(circle, #3b82f6, transparent)' }}
          />
          <div
            className="absolute w-72 h-72 rounded-full opacity-10 bottom-0 -left-20 pointer-events-none"
            style={{ background: 'radial-gradient(circle, #06b6d4, transparent)' }}
          />

          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span
              className="inline-block px-4 py-1.5 rounded-full text-cyan-400
                          text-xs font-bold tracking-wider uppercase mb-4
                          border border-white/10 bg-white/5"
            >
              AI 매칭 추천
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              {category} 분야 추천 멘토
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              테스트 결과를 기반으로 가장 적합한 멘토를 추천해드립니다.
            </p>
          </div>
        </section>

        {/* Content */}
        <section className="max-w-7xl mx-auto px-6 py-12">
          {/* Loading */}
          {loading && (
            <div className="flex flex-col items-center justify-center py-24 gap-4">
              <Loader2 size={36} className="animate-spin text-blue-500" />
              <p className="text-gray-500 text-sm">추천 멘토를 불러오는 중...</p>
            </div>
          )}

          {/* Fetch error */}
          {!loading && fetchError && (
            <div className="flex flex-col items-center justify-center py-24 gap-4 text-center">
              <div className="w-14 h-14 rounded-2xl bg-red-100 flex items-center justify-center">
                <X size={24} className="text-red-500" />
              </div>
              <p className="text-gray-700 font-semibold">{fetchError}</p>
              <button
                onClick={() => {
                  setFetchError(null);
                  recommendMentors(category)
                    .then((res) => {
                      if (res.success) setMentors(res.data ?? []);
                      else setFetchError(res.message);
                    })
                    .catch(() => setFetchError('멘토 목록을 불러오지 못했습니다.'));
                }}
                className="px-4 py-2 rounded-lg text-sm font-medium text-blue-600
                           border border-blue-200 hover:bg-blue-50 transition-colors"
              >
                다시 시도
              </button>
            </div>
          )}

          {/* Empty state */}
          {!loading && !fetchError && mentors.length === 0 && (
            <div className="flex flex-col items-center justify-center py-24 gap-4 text-center">
              <div className="w-14 h-14 rounded-2xl bg-gray-100 flex items-center justify-center">
                <Users size={24} className="text-gray-400" />
              </div>
              <p className="text-gray-600 font-semibold">
                현재 {category} 분야에 추천 가능한 멘토가 없습니다.
              </p>
              <Link
                href="/mentors"
                className="px-4 py-2 rounded-lg text-sm font-medium text-blue-600
                           border border-blue-200 hover:bg-blue-50 transition-colors"
              >
                전체 멘토 보기
              </Link>
            </div>
          )}

          {/* Mentor grid */}
          {!loading && !fetchError && mentors.length > 0 && (
            <>
              <p className="text-gray-500 text-sm mb-6">
                총{' '}
                <span className="font-semibold text-gray-800">{mentors.length}</span>명의
                멘토가 추천되었습니다.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                {mentors.map((mentor) => (
                  <MentorCard
                    key={mentor.mentorId}
                    mentor={mentor}
                    onApply={setSelectedMentor}
                  />
                ))}
              </div>
            </>
          )}
        </section>
      </main>
      <Footer />

      {/* Modal */}
      {selectedMentor && (
        <MatchingModal
          mentor={selectedMentor}
          category={category}
          testResultId={testResultId}
          onClose={() => setSelectedMentor(null)}
          onSuccess={() => {
            setSelectedMentor(null);
            setSuccessState(true);
          }}
        />
      )}
    </>
  );
}

// ─── Page Export (Suspense boundary for useSearchParams) ─────────────────────

export default function RecommendPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-[#0a0e1a] flex items-center justify-center">
          <Loader2 size={32} className="animate-spin text-blue-400" />
        </div>
      }
    >
      <RecommendContent />
    </Suspense>
  );
}

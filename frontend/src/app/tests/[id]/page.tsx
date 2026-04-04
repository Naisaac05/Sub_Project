'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getTestDetail, submitTest } from '@/lib/test';
import type { TestDetailResponse, TestResultResponse } from '@/lib/types';
import {
  Clock,
  CheckCircle,
  XCircle,
  ArrowRight,
  ArrowLeft,
  Loader2,
  AlertCircle,
  Send,
  Trophy,
  BarChart3,
} from 'lucide-react';

/* ─── Difficulty helpers ─── */
const difficultyLabel: Record<string, string> = {
  BEGINNER: '입문',
  INTERMEDIATE: '중급',
  ADVANCED: '고급',
};

const difficultyColor: Record<string, string> = {
  BEGINNER: 'bg-green-50 text-green-600 border-green-100',
  INTERMEDIATE: 'bg-amber-50 text-amber-600 border-amber-100',
  ADVANCED: 'bg-red-50 text-red-600 border-red-100',
};

/* ─── Page states ─── */
type PageState = 'loading' | 'taking' | 'submitting' | 'result';

export default function TestTakingPage() {
  const params = useParams();
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const testId = Number(params.id);

  /* ── Data ── */
  const [test, setTest] = useState<TestDetailResponse | null>(null);
  const [result, setResult] = useState<TestResultResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  /* ── UI state ── */
  const [pageState, setPageState] = useState<PageState>('loading');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [showConfirm, setShowConfirm] = useState(false);

  /* ── Timer ── */
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const autoSubmittedRef = useRef(false);

  /* ── Auth redirect ── */
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  /* ── Fetch test detail ── */
  useEffect(() => {
    if (authLoading || !isLoggedIn) return;

    const fetchTest = async () => {
      setPageState('loading');
      setError(null);
      try {
        const res = await getTestDetail(testId);
        if (res.success && res.data) {
          const data = res.data;
          // Sort questions by orderIndex
          data.questions.sort((a, b) => a.orderIndex - b.orderIndex);
          setTest(data);
          setRemainingSeconds(data.timeLimit * 60);
          setPageState('taking');
        } else {
          setError(res.message || '테스트를 불러오지 못했습니다.');
        }
      } catch {
        setError('테스트를 불러오는 중 오류가 발생했습니다.');
      }
    };

    fetchTest();
  }, [testId, authLoading, isLoggedIn]);

  /* ── Submit handler ── */
  const handleSubmit = useCallback(async () => {
    if (!test || pageState === 'submitting' || pageState === 'result') return;

    // Stop timer
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    setPageState('submitting');
    setShowConfirm(false);

    try {
      const answerList = test.questions.map((q) => ({
        questionId: q.id,
        selectedAnswer: answers[q.id] ?? 0,
      }));

      const res = await submitTest(testId, { answers: answerList });
      if (res.success && res.data) {
        setResult(res.data);
        setPageState('result');
      } else {
        setError(res.message || '제출에 실패했습니다.');
        setPageState('taking');
      }
    } catch {
      setError('제출 중 오류가 발생했습니다.');
      setPageState('taking');
    }
  }, [test, testId, answers, pageState]);

  /* ── Countdown timer ── */
  useEffect(() => {
    if (pageState !== 'taking') return;

    timerRef.current = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          // Time's up — auto-submit
          if (!autoSubmittedRef.current) {
            autoSubmittedRef.current = true;
            // Use setTimeout to avoid state update during render
            setTimeout(() => handleSubmit(), 0);
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [pageState, handleSubmit]);

  /* ── Helpers ── */
  const formatTime = (seconds: number): string => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const selectAnswer = (questionId: number, optionIndex: number) => {
    setAnswers((prev) => ({ ...prev, [questionId]: optionIndex }));
  };

  const unansweredCount = test
    ? test.questions.filter((q) => answers[q.id] === undefined).length
    : 0;

  const currentQuestion = test?.questions[currentIndex] ?? null;
  const totalQuestions = test?.questions.length ?? 0;
  const isLastQuestion = currentIndex === totalQuestions - 1;

  /* ── Early returns ── */
  if (authLoading || !isLoggedIn) return null;

  /* ─────────────────────────────────────────
     RENDER: Loading
  ───────────────────────────────────────── */
  if (pageState === 'loading' || (!test && !error)) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50 flex items-center justify-center pt-16">
          <div className="flex flex-col items-center gap-4">
            <Loader2 size={40} className="animate-spin text-blue-500" />
            <p className="text-gray-500 font-medium">테스트를 불러오는 중...</p>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  /* ─────────────────────────────────────────
     RENDER: Error
  ───────────────────────────────────────── */
  if (error && pageState !== 'result') {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50 flex items-center justify-center pt-16">
          <div className="flex flex-col items-center gap-4 text-center">
            <AlertCircle size={40} className="text-red-400" />
            <p className="text-red-500 font-semibold">{error}</p>
            <button
              onClick={() => router.back()}
              className="px-6 py-2.5 rounded-xl bg-gray-900 text-white text-sm font-semibold
                         hover:bg-gray-700 transition-colors"
            >
              돌아가기
            </button>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  /* ─────────────────────────────────────────
     RENDER: Submitting
  ───────────────────────────────────────── */
  if (pageState === 'submitting') {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50 flex items-center justify-center pt-16">
          <div className="flex flex-col items-center gap-4">
            <Loader2 size={40} className="animate-spin text-blue-500" />
            <p className="text-gray-500 font-medium">채점 중입니다...</p>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  /* ─────────────────────────────────────────
     RENDER: Result
  ───────────────────────────────────────── */
  if (pageState === 'result' && result) {
    const scorePercent = Math.round(
      (result.correctCount / result.questionCount) * 100
    );

    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50">
          {/* Hero section */}
          <section className="relative hero-gradient grid-pattern pt-28 pb-20 overflow-hidden">
            <div className="orb w-[300px] h-[300px] bg-cyan-500/15 -top-10 right-20" />
            <div className="max-w-3xl mx-auto px-6 relative z-10 text-center">
              <div className="inline-flex items-center justify-center w-20 h-20 rounded-full mb-6
                              bg-white/10 backdrop-blur-xl border border-white/10">
                {result.passed ? (
                  <Trophy size={36} className="text-yellow-400" />
                ) : (
                  <XCircle size={36} className="text-red-400" />
                )}
              </div>
              <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-2">
                테스트 결과
              </h1>
              <p className="text-gray-400 text-lg">{result.testTitle}</p>
            </div>
          </section>

          <section className="max-w-2xl mx-auto px-6 -mt-10 relative z-20 pb-20">
            {/* Score card */}
            <div className="bg-white rounded-2xl border border-gray-100 shadow-xl shadow-gray-200/40 p-8 sm:p-10 text-center">
              {/* Pass / Fail badge */}
              <span
                className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-bold mb-6 ${
                  result.passed
                    ? 'bg-green-50 text-green-600 border border-green-200'
                    : 'bg-red-50 text-red-600 border border-red-200'
                }`}
              >
                {result.passed ? (
                  <CheckCircle size={16} />
                ) : (
                  <XCircle size={16} />
                )}
                {result.passed ? '합격' : '불합격'}
              </span>

              {/* Large score */}
              <div className="mb-2">
                <span className="text-6xl sm:text-7xl font-extrabold text-gray-900 tracking-tight">
                  {result.totalScore}
                </span>
                <span className="text-2xl font-bold text-gray-400 ml-1">점</span>
              </div>

              {/* Correct count */}
              <p className="text-gray-500 text-lg font-medium mb-8">
                {result.correctCount} / {result.questionCount} 문항 정답
              </p>

              {/* Progress bar */}
              <div className="w-full bg-gray-100 rounded-full h-3 mb-8">
                <div
                  className={`h-3 rounded-full transition-all duration-700 ${
                    result.passed
                      ? 'bg-gradient-to-r from-green-400 to-emerald-500'
                      : 'bg-gradient-to-r from-red-400 to-red-500'
                  }`}
                  style={{ width: `${scorePercent}%` }}
                />
              </div>

              {/* Meta info */}
              <div className="flex items-center justify-center gap-6 text-sm text-gray-400 mb-8">
                <span className="flex items-center gap-1.5">
                  <BarChart3 size={16} />
                  {result.category}
                </span>
              </div>

              {/* CTAs */}
              <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
                <Link
                  href="/tests/results"
                  className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl
                             text-sm font-semibold text-gray-700 bg-gray-100
                             hover:bg-gray-200 transition-colors"
                >
                  결과 목록 보기
                </Link>
                {result.passed && (
                  <Link
                    href={`/matching/recommend?category=${encodeURIComponent(result.category)}&resultId=${result.id}`}
                    className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl
                               text-sm font-semibold text-white
                               bg-gradient-to-r from-blue-600 to-cyan-500
                               hover:from-blue-500 hover:to-cyan-400
                               shadow-lg shadow-blue-600/20 transition-all"
                  >
                    멘토 추천 받기
                    <ArrowRight size={16} />
                  </Link>
                )}
              </div>
            </div>
          </section>
        </main>
        <Footer />
      </>
    );
  }

  /* ─────────────────────────────────────────
     RENDER: Test-taking
  ───────────────────────────────────────── */
  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Top bar with timer & submit */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-14 overflow-hidden">
          <div className="orb w-[300px] h-[300px] bg-cyan-500/15 -top-10 right-20" />
          <div className="max-w-5xl mx-auto px-6 relative z-10">
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              {/* Test info */}
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <h1 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight">
                    {test!.title}
                  </h1>
                  <span
                    className={`px-3 py-0.5 text-xs font-bold rounded-full border ${
                      difficultyColor[test!.difficulty] ?? 'bg-gray-50 text-gray-600 border-gray-100'
                    }`}
                  >
                    {difficultyLabel[test!.difficulty] ?? test!.difficulty}
                  </span>
                </div>
                <p className="text-gray-400 text-sm">
                  {currentIndex + 1} / {totalQuestions} 문항
                </p>
              </div>

              {/* Timer & Submit */}
              <div className="flex items-center gap-3">
                <div
                  className={`flex items-center gap-2 px-4 py-2 rounded-xl glass-card text-sm font-bold ${
                    remainingSeconds < 60
                      ? 'text-red-400'
                      : 'text-cyan-400'
                  }`}
                >
                  <Clock size={16} />
                  {formatTime(remainingSeconds)}
                </div>
                <button
                  onClick={() => setShowConfirm(true)}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold
                             text-white bg-gradient-to-r from-blue-600 to-cyan-500
                             hover:from-blue-500 hover:to-cyan-400
                             shadow-lg shadow-blue-600/20 transition-all"
                >
                  <Send size={14} />
                  제출하기
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* Question area */}
        <section className="max-w-3xl mx-auto px-6 py-10">
          {currentQuestion && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-8 sm:p-10 mb-8">
              {/* Question header */}
              <div className="flex items-start gap-4 mb-8">
                <span className="flex-shrink-0 w-9 h-9 rounded-lg bg-gradient-to-br from-blue-50 to-violet-50
                                 flex items-center justify-center text-blue-600 font-bold text-sm">
                  {currentIndex + 1}
                </span>
                <div>
                  <h2 className="text-lg font-bold text-gray-900 leading-relaxed">
                    {currentQuestion.content}
                  </h2>
                  <p className="text-xs text-gray-400 mt-1">{currentQuestion.score}점 배점</p>
                </div>
              </div>

              {/* Options */}
              <div className="space-y-3">
                {currentQuestion.options.map((option, idx) => {
                  const isSelected = answers[currentQuestion.id] === idx;
                  return (
                    <button
                      key={idx}
                      onClick={() => selectAnswer(currentQuestion.id, idx)}
                      className={`w-full text-left px-5 py-4 rounded-xl border-2 transition-all duration-200
                                  flex items-center gap-4 group ${
                        isSelected
                          ? 'border-blue-500 bg-blue-50/60 shadow-sm shadow-blue-100'
                          : 'border-gray-100 bg-white hover:border-gray-200 hover:bg-gray-50'
                      }`}
                    >
                      <span
                        className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center
                                    text-sm font-bold transition-colors ${
                          isSelected
                            ? 'bg-blue-600 text-white'
                            : 'bg-gray-100 text-gray-500 group-hover:bg-gray-200'
                        }`}
                      >
                        {idx + 1}
                      </span>
                      <span
                        className={`text-sm font-medium ${
                          isSelected ? 'text-blue-900' : 'text-gray-700'
                        }`}
                      >
                        {option}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Navigation buttons */}
          <div className="flex items-center justify-between mb-8">
            <button
              onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
              disabled={currentIndex === 0}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                currentIndex === 0
                  ? 'text-gray-300 bg-gray-100 cursor-not-allowed'
                  : 'text-gray-700 bg-white border border-gray-200 hover:border-gray-300 hover:text-gray-900'
              }`}
            >
              <ArrowLeft size={16} />
              이전
            </button>

            {isLastQuestion ? (
              <button
                onClick={() => setShowConfirm(true)}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold
                           text-white bg-gradient-to-r from-blue-600 to-cyan-500
                           hover:from-blue-500 hover:to-cyan-400
                           shadow-lg shadow-blue-600/20 transition-all"
              >
                제출하기
                <Send size={14} />
              </button>
            ) : (
              <button
                onClick={() => setCurrentIndex((prev) => Math.min(totalQuestions - 1, prev + 1))}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold
                           text-white bg-gray-900 hover:bg-gray-700 transition-colors"
              >
                다음
                <ArrowRight size={16} />
              </button>
            )}
          </div>

          {/* Question number indicators */}
          <div className="flex flex-wrap items-center justify-center gap-2">
            {test!.questions.map((q, idx) => {
              const isAnswered = answers[q.id] !== undefined;
              const isCurrent = idx === currentIndex;

              return (
                <button
                  key={q.id}
                  onClick={() => setCurrentIndex(idx)}
                  className={`w-9 h-9 rounded-lg text-xs font-bold transition-all duration-200 ${
                    isCurrent
                      ? 'bg-blue-600 text-white shadow-md shadow-blue-200'
                      : isAnswered
                        ? 'bg-blue-100 text-blue-600 border border-blue-200'
                        : 'bg-white text-gray-400 border border-gray-200 hover:border-gray-300'
                  }`}
                >
                  {idx + 1}
                </button>
              );
            })}
          </div>
        </section>
      </main>
      <Footer />

      {/* Confirmation dialog overlay */}
      {showConfirm && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl p-8 max-w-md w-full mx-6 animate-fade-in">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center">
                <AlertCircle size={22} className="text-amber-500" />
              </div>
              <h3 className="text-lg font-bold text-gray-900">제출 확인</h3>
            </div>
            <p className="text-gray-600 text-sm mb-6">
              제출하시겠습니까?
              {unansweredCount > 0 && (
                <span className="block mt-1 text-amber-600 font-semibold">
                  {unansweredCount}개의 미답변 문항이 있습니다.
                </span>
              )}
            </p>
            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setShowConfirm(false)}
                className="px-5 py-2.5 rounded-xl text-sm font-semibold text-gray-600
                           bg-gray-100 hover:bg-gray-200 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleSubmit}
                className="px-5 py-2.5 rounded-xl text-sm font-semibold text-white
                           bg-gradient-to-r from-blue-600 to-cyan-500
                           hover:from-blue-500 hover:to-cyan-400 transition-all"
              >
                제출하기
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

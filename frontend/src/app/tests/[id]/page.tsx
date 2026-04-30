'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getTestDetail, submitTest } from '@/lib/test';
import type { TestDetailResponse, TestResultResponse } from '@/lib/types';
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  BarChart3,
  CheckCircle,
  Clock,
  Loader2,
  Send,
  Trophy,
} from 'lucide-react';

type PageState = 'loading' | 'taking' | 'submitting' | 'result';

const LABELS = {
  beginner: '\uC785\uBB38',
  intermediate: '\uC911\uAE09',
  advanced: '\uACE0\uAE09',
  loading: '\uD14C\uC2A4\uD2B8\uB97C \uBD88\uB7EC\uC624\uB294 \uC911\uC785\uB2C8\uB2E4...',
  loadError: '\uD14C\uC2A4\uD2B8\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.',
  submitError: '\uC81C\uCD9C \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4.',
  back: '\uB3CC\uC544\uAC00\uAE30',
  submitting: '\uACB0\uACFC\uB97C \uCC44\uC810\uD558\uB294 \uC911\uC785\uB2C8\uB2E4...',
  resultTitle: '\uD14C\uC2A4\uD2B8 \uACB0\uACFC',
  currentStatus: '\uD604\uC7AC \uC0C1\uD0DC',
  point: '\uC810',
  correct: '\uBB38\uD56D \uC815\uB2F5',
  question: '\uBB38\uD56D',
  score: '\uBC30\uC810',
  submit: '\uC81C\uCD9C\uD558\uAE30',
  prev: '\uC774\uC804',
  next: '\uB2E4\uC74C',
  resultList: '\uACB0\uACFC \uBAA9\uB85D \uBCF4\uAE30',
  testsHome: '\uD14C\uC2A4\uD2B8 \uBAA9\uB85D',
  smartReview: '\uC2A4\uB9C8\uD2B8 \uBCF5\uC2B5 \uC2DC\uC791',
  confirmTitle: '\uC81C\uCD9C\uD560\uAE4C\uC694?',
  confirmBody: '\uC81C\uCD9C\uD558\uBA74 \uB2F5\uC548\uC744 \uC218\uC815\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.',
  unansweredPrefix: '\uC544\uC9C1 \uD480\uC9C0 \uC54A\uC740 \uBB38\uD56D\uC774',
  unansweredSuffix: '\uAC1C \uC788\uC2B5\uB2C8\uB2E4.',
  cancel: '\uCDE8\uC18C',
  diagnosticNote:
    '\uC774 \uACB0\uACFC\uB294 \uD569\uACA9/\uBD88\uD569\uACA9\uBCF4\uB2E4 \uD604\uC7AC \uC2E4\uB825\uC744 \uD655\uC778\uD558\uAE30 \uC704\uD55C \uC9C4\uB2E8\uC6A9\uC785\uB2C8\uB2E4.',
};

const difficultyLabel: Record<string, string> = {
  BEGINNER: LABELS.beginner,
  INTERMEDIATE: LABELS.intermediate,
  ADVANCED: LABELS.advanced,
};

const difficultyColor: Record<string, string> = {
  BEGINNER: 'bg-green-50 text-green-600 border-green-100',
  INTERMEDIATE: 'bg-amber-50 text-amber-600 border-amber-100',
  ADVANCED: 'bg-red-50 text-red-600 border-red-100',
};

function formatTime(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}

function getScoreStatus(score: number) {
  if (score >= 80) {
    return {
      label: '\uD0C4\uD0C4\uD55C \uC0C1\uD0DC',
      description: '\uD575\uC2EC \uAC1C\uB150\uACFC \uC2E4\uBB34 \uD310\uB2E8\uC744 \uC548\uC815\uC801\uC73C\uB85C \uC774\uD574\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4.',
      className: 'bg-emerald-50 text-emerald-600 border-emerald-200',
    };
  }
  if (score >= 60) {
    return {
      label: '\uAE30\uBCF8\uAE30\uB294 \uC7A1\uD78C \uC0C1\uD0DC',
      description: '\uAE30\uBCF8 \uD750\uB984\uC740 \uC774\uD574\uD558\uACE0 \uC788\uC9C0\uB9CC \uC2E4\uBB34 \uC0C1\uD669 \uD310\uB2E8\uC744 \uB354 \uBCF4\uAC15\uD558\uBA74 \uC88B\uC2B5\uB2C8\uB2E4.',
      className: 'bg-blue-50 text-blue-600 border-blue-200',
    };
  }
  return {
    label: '\uBCF5\uC2B5\uC774 \uD544\uC694\uD55C \uC0C1\uD0DC',
    description: '\uD575\uC2EC \uAC1C\uB150\uC744 \uBA3C\uC800 \uC815\uB9AC\uD558\uACE0 \uC26C\uC6B4 \uBB38\uC81C\uBD80\uD130 \uB2E4\uC2DC \uD480\uC5B4\uBCF4\uB294 \uAC83\uC774 \uC88B\uC2B5\uB2C8\uB2E4.',
    className: 'bg-amber-50 text-amber-600 border-amber-200',
  };
}

export default function TestTakingPage() {
  const params = useParams();
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const testId = Number(params.id);
  const [test, setTest] = useState<TestDetailResponse | null>(null);
  const [result, setResult] = useState<TestResultResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pageState, setPageState] = useState<PageState>('loading');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [showConfirm, setShowConfirm] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const autoSubmittedRef = useRef(false);

  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    if (authLoading || !isLoggedIn) {
      return;
    }

    let mounted = true;

    const fetchTest = async () => {
      setPageState('loading');
      setError(null);
      try {
        const res = await getTestDetail(testId);
        if (!mounted) {
          return;
        }
        if (res.success && res.data) {
          const data = res.data;
          data.questions.sort((a, b) => a.orderIndex - b.orderIndex);
          setTest(data);
          setRemainingSeconds(data.timeLimit * 60);
          setPageState('taking');
        } else {
          setError(res.message || LABELS.loadError);
        }
      } catch {
        if (mounted) {
          setError(LABELS.loadError);
        }
      }
    };

    fetchTest();
    return () => {
      mounted = false;
    };
  }, [testId, authLoading, isLoggedIn]);

  const handleSubmit = useCallback(async () => {
    if (!test || pageState === 'submitting' || pageState === 'result') {
      return;
    }

    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    setPageState('submitting');
    setShowConfirm(false);

    try {
      const answerList = test.questions.map((question) => ({
        questionId: question.id,
        selectedAnswer: answers[question.id] ?? -1,
      }));

      const res = await submitTest(testId, { answers: answerList });
      if (res.success && res.data) {
        setResult(res.data);
        setPageState('result');
      } else {
        setError(res.message || LABELS.submitError);
        setPageState('taking');
      }
    } catch {
      setError(LABELS.submitError);
      setPageState('taking');
    }
  }, [answers, pageState, test, testId]);

  useEffect(() => {
    if (pageState !== 'taking') {
      return;
    }

    timerRef.current = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          if (!autoSubmittedRef.current) {
            autoSubmittedRef.current = true;
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
  }, [handleSubmit, pageState]);

  const selectAnswer = (questionId: number, optionIndex: number) => {
    setAnswers((prev) => ({ ...prev, [questionId]: optionIndex }));
  };

  const unansweredCount = test
    ? test.questions.filter((question) => answers[question.id] === undefined).length
    : 0;
  const currentQuestion = test?.questions[currentIndex] ?? null;
  const totalQuestions = test?.questions.length ?? 0;
  const isLastQuestion = currentIndex === totalQuestions - 1;

  if (authLoading || !isLoggedIn) {
    return null;
  }

  if (pageState === 'loading' || (!test && !error)) {
    return (
      <>
        <Header />
        <main className="flex min-h-screen items-center justify-center bg-gray-50 pt-16">
          <div className="flex flex-col items-center gap-4">
            <Loader2 size={40} className="animate-spin text-blue-500" />
            <p className="font-medium text-gray-500">{LABELS.loading}</p>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  if (error && pageState !== 'result') {
    return (
      <>
        <Header />
        <main className="flex min-h-screen items-center justify-center bg-gray-50 pt-16">
          <div className="flex flex-col items-center gap-4 text-center">
            <AlertCircle size={40} className="text-red-400" />
            <p className="font-semibold text-red-500">{error}</p>
            <button
              onClick={() => router.back()}
              className="rounded-xl bg-gray-950 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800"
            >
              {LABELS.back}
            </button>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  if (pageState === 'submitting') {
    return (
      <>
        <Header />
        <main className="flex min-h-screen items-center justify-center bg-gray-50 pt-16">
          <div className="flex flex-col items-center gap-4">
            <Loader2 size={40} className="animate-spin text-blue-500" />
            <p className="font-medium text-gray-500">{LABELS.submitting}</p>
          </div>
        </main>
        <Footer />
      </>
    );
  }

  if (pageState === 'result' && result) {
    const scorePercent = Math.round((result.correctCount / result.questionCount) * 100);
    const status = getScoreStatus(result.totalScore);

    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50">
          <section className="relative overflow-hidden bg-[#07111f] pt-28 pb-20">
            <div className="mx-auto max-w-3xl px-6 text-center">
              <div className="mb-6 inline-flex h-20 w-20 items-center justify-center rounded-full border border-white/10 bg-white/10">
                <Trophy size={36} className="text-yellow-300" />
              </div>
              <h1 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
                {LABELS.resultTitle}
              </h1>
              <p className="mt-2 text-lg text-gray-300">{result.testTitle}</p>
            </div>
          </section>

          <section className="relative z-20 mx-auto max-w-2xl px-6 -mt-10 pb-20">
            <div className="rounded-2xl border border-gray-100 bg-white p-8 text-center shadow-xl shadow-gray-200/50 sm:p-10">
              <span className={`mb-6 inline-flex items-center gap-1.5 rounded-full border px-4 py-1.5 text-sm font-bold ${status.className}`}>
                <CheckCircle size={16} />
                {LABELS.currentStatus}: {status.label}
              </span>

              <div className="mb-2">
                <span className="text-6xl font-extrabold tracking-tight text-gray-950 sm:text-7xl">
                  {result.totalScore}
                </span>
                <span className="ml-1 text-2xl font-bold text-gray-400">{LABELS.point}</span>
              </div>

              <p className="mb-6 text-base font-medium text-gray-500">
                {result.correctCount} / {result.questionCount} {LABELS.correct}
              </p>
              <p className="mb-8 break-keep text-sm leading-6 text-gray-500">{status.description}</p>

              <div className="mb-8 h-3 w-full rounded-full bg-gray-100">
                <div
                  className="h-3 rounded-full bg-gradient-to-r from-blue-600 to-cyan-500 transition-all duration-700"
                  style={{ width: `${scorePercent}%` }}
                />
              </div>

              <div className="mb-8 rounded-xl bg-gray-50 p-4 text-sm leading-6 text-gray-600">
                {LABELS.diagnosticNote}
              </div>

              <div className="flex flex-col items-center justify-center gap-3 sm:flex-row">
                {result.correctCount < result.questionCount ? (
                  <Link
                    href={`/tests/results/${result.id}/review`}
                    className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-amber-500 to-orange-500 px-6 py-3 text-sm font-semibold text-white shadow-lg shadow-amber-500/20 transition-opacity hover:opacity-90 sm:w-auto"
                  >
                    {LABELS.smartReview}
                    <ArrowRight size={16} />
                  </Link>
                ) : null}
                <Link
                  href="/tests/results"
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-gray-100 px-6 py-3 text-sm font-semibold text-gray-700 transition-colors hover:bg-gray-200 sm:w-auto"
                >
                  {LABELS.resultList}
                </Link>
                <Link
                  href="/tests"
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 px-6 py-3 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition-opacity hover:opacity-90 sm:w-auto"
                >
                  {LABELS.testsHome}
                  <ArrowRight size={16} />
                </Link>
              </div>
            </div>
          </section>
        </main>
        <Footer />
      </>
    );
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="relative overflow-hidden bg-[#07111f] pt-28 pb-14">
          <div className="mx-auto max-w-5xl px-6">
            <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
              <div>
                <div className="mb-2 flex flex-wrap items-center gap-3">
                  <h1 className="break-keep text-xl font-extrabold tracking-tight text-white sm:text-2xl">
                    {test!.title}
                  </h1>
                  <span className={`rounded-full border px-3 py-0.5 text-xs font-bold ${difficultyColor[test!.difficulty] ?? 'bg-gray-50 text-gray-600 border-gray-100'}`}>
                    {difficultyLabel[test!.difficulty] ?? test!.difficulty}
                  </span>
                </div>
                <p className="text-sm text-gray-400">
                  {currentIndex + 1} / {totalQuestions} {LABELS.question}
                </p>
              </div>

              <div className="flex items-center gap-3">
                <div className={`flex items-center gap-2 rounded-xl border border-white/10 bg-white/10 px-4 py-2 text-sm font-bold ${remainingSeconds < 60 ? 'text-red-300' : 'text-cyan-300'}`}>
                  <Clock size={16} />
                  {formatTime(remainingSeconds)}
                </div>
                <button
                  onClick={() => setShowConfirm(true)}
                  className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition-opacity hover:opacity-90"
                >
                  <Send size={14} />
                  {LABELS.submit}
                </button>
              </div>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-3xl px-6 py-10">
          {currentQuestion ? (
            <div className="mb-8 rounded-2xl border border-gray-100 bg-white p-8 shadow-sm sm:p-10">
              <div className="mb-8 flex items-start gap-4">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-sm font-bold text-blue-600">
                  {currentIndex + 1}
                </span>
                <div>
                  <h2 className="break-keep text-lg font-bold leading-relaxed text-gray-950">
                    {currentQuestion.content}
                  </h2>
                  <p className="mt-1 text-xs text-gray-400">
                    {currentQuestion.score}{LABELS.score}
                  </p>
                </div>
              </div>

              <div className="space-y-3">
                {currentQuestion.options.map((option, index) => {
                  const isSelected = answers[currentQuestion.id] === index;
                  return (
                    <button
                      key={option}
                      onClick={() => selectAnswer(currentQuestion.id, index)}
                      className={`group flex w-full items-center gap-4 rounded-xl border-2 px-5 py-4 text-left transition-all duration-200 ${
                        isSelected
                          ? 'border-blue-500 bg-blue-50/70 shadow-sm shadow-blue-100'
                          : 'border-gray-100 bg-white hover:border-gray-200 hover:bg-gray-50'
                      }`}
                    >
                      <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-bold transition-colors ${
                        isSelected
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-100 text-gray-500 group-hover:bg-gray-200'
                      }`}>
                        {index + 1}
                      </span>
                      <span className={`break-keep text-sm font-medium ${isSelected ? 'text-blue-950' : 'text-gray-700'}`}>
                        {option}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ) : null}

          <div className="mb-8 flex items-center justify-between">
            <button
              onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
              disabled={currentIndex === 0}
              className={`flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-semibold transition-colors ${
                currentIndex === 0
                  ? 'cursor-not-allowed bg-gray-100 text-gray-300'
                  : 'border border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:text-gray-950'
              }`}
            >
              <ArrowLeft size={16} />
              {LABELS.prev}
            </button>

            {isLastQuestion ? (
              <button
                onClick={() => setShowConfirm(true)}
                className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition-opacity hover:opacity-90"
              >
                {LABELS.submit}
                <Send size={14} />
              </button>
            ) : (
              <button
                onClick={() => setCurrentIndex((prev) => Math.min(totalQuestions - 1, prev + 1))}
                className="flex items-center gap-2 rounded-xl bg-gray-950 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800"
              >
                {LABELS.next}
                <ArrowRight size={16} />
              </button>
            )}
          </div>

          <div className="flex flex-wrap items-center justify-center gap-2">
            {test!.questions.map((question, index) => {
              const isAnswered = answers[question.id] !== undefined;
              const isCurrent = index === currentIndex;

              return (
                <button
                  key={question.id}
                  onClick={() => setCurrentIndex(index)}
                  className={`h-9 w-9 rounded-lg text-xs font-bold transition-all duration-200 ${
                    isCurrent
                      ? 'bg-blue-600 text-white shadow-md shadow-blue-200'
                      : isAnswered
                        ? 'border border-blue-200 bg-blue-100 text-blue-600'
                        : 'border border-gray-200 bg-white text-gray-400 hover:border-gray-300'
                  }`}
                >
                  {index + 1}
                </button>
              );
            })}
          </div>
        </section>
      </main>
      <Footer />

      {showConfirm ? (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 px-6 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50">
                <AlertCircle size={22} className="text-amber-500" />
              </div>
              <h3 className="text-lg font-bold text-gray-950">{LABELS.confirmTitle}</h3>
            </div>
            <p className="mb-6 break-keep text-sm leading-6 text-gray-600">
              {LABELS.confirmBody}
              {unansweredCount > 0 ? (
                <span className="mt-1 block font-semibold text-amber-600">
                  {LABELS.unansweredPrefix} {unansweredCount}{LABELS.unansweredSuffix}
                </span>
              ) : null}
            </p>
            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setShowConfirm(false)}
                className="rounded-xl bg-gray-100 px-5 py-2.5 text-sm font-semibold text-gray-600 transition-colors hover:bg-gray-200"
              >
                {LABELS.cancel}
              </button>
              <button
                onClick={handleSubmit}
                className="rounded-xl bg-gradient-to-r from-blue-600 to-cyan-500 px-5 py-2.5 text-sm font-semibold text-white transition-opacity hover:opacity-90"
              >
                {LABELS.submit}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

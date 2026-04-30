'use client';

import { useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { startAiReview, submitAiReviewAnswer } from '@/lib/ai-review';
import type { AiReviewSessionResponse } from '@/lib/types';
import { ArrowLeft, Bot, CheckCircle, Loader2, Send, User } from 'lucide-react';

const LABELS = {
  title: '\uC2A4\uB9C8\uD2B8 \uAC1C\uB150 \uBCF5\uC2B5',
  description:
    '\uD2C0\uB9B0 \uBB38\uC81C\uB97C \uAE30\uBC18\uC73C\uB85C \uACE0\uC815 \uAF2C\uB9AC\uC9C8\uBB38\uC744 \uC81C\uACF5\uD558\uACE0, \uB2F5\uBCC0\uC744 \uD0A4\uC6CC\uB4DC \uAE30\uC900\uC73C\uB85C \uD3C9\uAC00\uD569\uB2C8\uB2E4.',
  loading: '\uBCF5\uC2B5 \uC138\uC158\uC744 \uC900\uBE44\uD558\uB294 \uC911\uC785\uB2C8\uB2E4.',
  back: '\uACB0\uACFC\uB85C \uB3CC\uC544\uAC00\uAE30',
  wrongQuestions: '\uD2C0\uB9B0 \uBB38\uC81C',
  correct: '\uC815\uB2F5',
  selected: '\uB0B4 \uC120\uD0DD',
  answerPlaceholder: '\uB098\uC758 \uC0DD\uAC01\uC744 \uC9E7\uAC8C \uC801\uC5B4\uBCF4\uC138\uC694.',
  checkAnswer: '\uD655\uC778 \uC9C8\uBB38\uC5D0 \uB2F5\uD558\uAE30',
  freeQuestion: '\uAD81\uAE08\uD55C \uC810 \uC9C8\uBB38\uD558\uAE30',
  nextQuestion: '\uB2E4\uC74C \uBB38\uC81C\uB85C',
  completed: '\uBCF5\uC2B5 \uC644\uB8CC',
  summary: '\uBCF5\uC2B5 \uC694\uC57D',
  weaknessTags: '\uC57D\uC810 \uD0DC\uADF8',
  noWrong: '\uD2C0\uB9B0 \uBB38\uC81C\uAC00 \uC5C6\uC5B4 \uBCF5\uC2B5\uD560 \uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.',
};

function evaluationLabel(evaluation: string | null) {
  switch (evaluation) {
    case 'UNDERSTOOD':
      return '\uC774\uD574\uD568';
    case 'PARTIAL':
      return '\uBD80\uBD84 \uC774\uD574';
    case 'NEEDS_REVIEW':
      return '\uBCF5\uC2B5 \uD544\uC694';
    default:
      return null;
  }
}

export default function AiReviewPage() {
  const params = useParams();
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();
  const testResultId = Number(params.id);

  const [session, setSession] = useState<AiReviewSessionResponse | null>(null);
  const [answer, setAnswer] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    if (authLoading || !isLoggedIn || Number.isNaN(testResultId)) {
      return;
    }

    const start = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await startAiReview(testResultId);
        if (res.success) {
          setSession(res.data);
        } else {
          setError(res.message || LABELS.loading);
        }
      } catch {
        setError('\uBCF5\uC2B5 \uC138\uC158\uC744 \uC2DC\uC791\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
      } finally {
        setLoading(false);
      }
    };

    start();
  }, [authLoading, isLoggedIn, testResultId]);

  const activeQuestionId = useMemo(() => {
    const lastAi = [...(session?.messages ?? [])].reverse().find((message) => message.role === 'AI');
    return lastAi?.questionId ?? null;
  }, [session?.messages]);

  const handleSubmit = async (mode: 'CHECK_ANSWER' | 'FREE_QUESTION' | 'NEXT_QUESTION') => {
    if (!session || submitting || session.status === 'COMPLETED') {
      return;
    }
    if (mode !== 'NEXT_QUESTION' && !answer.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const res = await submitAiReviewAnswer(session.sessionId, answer.trim(), mode);
      if (res.success) {
        setSession({
          ...session,
          status: res.data.completed ? 'COMPLETED' : session.status,
          summary: res.data.summary ?? session.summary,
          messages: res.data.messages,
        });
        setAnswer('');
      } else {
        setError(res.message || '\uB2F5\uBCC0\uC744 \uC81C\uCD9C\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
      }
    } catch {
      setError('\uB2F5\uBCC0\uC744 \uC81C\uCD9C\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
    } finally {
      setSubmitting(false);
    }
  };

  if (authLoading || !isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="bg-[#07111f] pt-28 pb-14">
          <div className="mx-auto max-w-7xl px-6">
            <Link
              href="/tests/results"
              className="mb-6 inline-flex items-center gap-2 text-sm font-semibold text-gray-300 hover:text-white"
            >
              <ArrowLeft size={16} />
              {LABELS.back}
            </Link>
            <h1 className="text-3xl font-extrabold text-white sm:text-4xl">{LABELS.title}</h1>
            <p className="mt-4 max-w-3xl break-keep text-base leading-7 text-gray-300">
              {LABELS.description}
            </p>
          </div>
        </section>

        <section className="mx-auto grid max-w-7xl gap-6 px-6 py-10 lg:grid-cols-[360px_1fr]">
          {loading ? (
            <div className="col-span-full flex flex-col items-center justify-center gap-3 py-24 text-gray-500">
              <Loader2 className="animate-spin text-blue-500" size={36} />
              <p className="text-sm font-medium">{LABELS.loading}</p>
            </div>
          ) : null}

          {!loading && error ? (
            <div className="col-span-full rounded-2xl border border-red-100 bg-white p-8 text-center font-semibold text-red-500">
              {error}
            </div>
          ) : null}

          {!loading && !error && session ? (
            <>
              <aside className="rounded-2xl border border-gray-200 bg-white p-5">
                <h2 className="mb-4 text-lg font-extrabold text-gray-950">{LABELS.wrongQuestions}</h2>
                {session.wrongQuestions.length === 0 ? (
                  <p className="break-keep text-sm leading-6 text-gray-500">{LABELS.noWrong}</p>
                ) : (
                  <div className="space-y-3">
                    {session.wrongQuestions.map((question, index) => (
                      <div
                        key={question.questionId}
                        className={`rounded-xl border p-4 ${
                          question.questionId === activeQuestionId
                            ? 'border-blue-200 bg-blue-50'
                            : 'border-gray-100 bg-gray-50'
                        }`}
                      >
                        <div className="mb-2 flex items-center justify-between gap-2">
                          <span className="text-xs font-bold text-blue-600">Q{index + 1}</span>
                          <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-gray-500">
                            {question.area}
                          </span>
                        </div>
                        <p className="break-keep text-sm font-semibold leading-5 text-gray-800">
                          {question.content}
                        </p>
                        <p className="mt-3 text-xs leading-5 text-gray-500">
                          {LABELS.selected}: {question.selectedAnswer}
                        </p>
                        <p className="text-xs leading-5 text-gray-500">
                          {LABELS.correct}: {question.correctAnswer}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </aside>

              <div className="rounded-2xl border border-gray-200 bg-white p-5 sm:p-7">
                <div className="space-y-4">
                  {session.messages.map((message) => {
                    const isAi = message.role === 'AI';
                    const label = evaluationLabel(message.evaluation);
                    return (
                      <div
                        key={message.id}
                        className={`flex gap-3 ${isAi ? 'justify-start' : 'justify-end'}`}
                      >
                        {isAi ? (
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                            <Bot size={18} />
                          </div>
                        ) : null}
                        <div
                          className={`max-w-[82%] rounded-2xl px-4 py-3 text-sm leading-6 ${
                            isAi
                              ? 'bg-gray-100 text-gray-800'
                              : 'bg-blue-600 text-white'
                          }`}
                        >
                          <p className="whitespace-pre-line break-keep">{message.content}</p>
                          {label ? (
                            <span className="mt-2 inline-flex rounded-full bg-white/20 px-2 py-0.5 text-xs font-semibold">
                              {label}
                            </span>
                          ) : null}
                        </div>
                        {!isAi ? (
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-900 text-white">
                            <User size={18} />
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>

                {session.status === 'COMPLETED' ? (
                  <div className="mt-8 rounded-2xl border border-emerald-100 bg-emerald-50 p-5">
                    <div className="mb-3 flex items-center gap-2 text-emerald-700">
                      <CheckCircle size={18} />
                      <h2 className="font-extrabold">{LABELS.completed}</h2>
                    </div>
                    <p className="break-keep text-sm leading-6 text-emerald-900">
                      {session.summary}
                    </p>
                    {session.weaknessTags ? (
                      <p className="mt-3 text-sm font-semibold text-emerald-700">
                        {LABELS.weaknessTags}: {session.weaknessTags}
                      </p>
                    ) : null}
                  </div>
                ) : (
                  <div className="mt-8 space-y-3">
                    <textarea
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                      maxLength={700}
                      rows={3}
                      className="min-h-[88px] w-full resize-none rounded-xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-blue-400"
                      placeholder={LABELS.answerPlaceholder}
                    />
                    <div className="grid gap-2 sm:grid-cols-3">
                      <button
                        onClick={() => handleSubmit('CHECK_ANSWER')}
                        disabled={!answer.trim() || submitting}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:bg-gray-300"
                      >
                        {submitting ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                        {LABELS.checkAnswer}
                      </button>
                      <button
                        onClick={() => handleSubmit('FREE_QUESTION')}
                        disabled={!answer.trim() || submitting}
                        className="inline-flex items-center justify-center rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm font-bold text-blue-700 transition-colors hover:bg-blue-100 disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-100 disabled:text-gray-400"
                      >
                        {LABELS.freeQuestion}
                      </button>
                      <button
                        onClick={() => handleSubmit('NEXT_QUESTION')}
                        disabled={submitting}
                        className="inline-flex items-center justify-center rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-bold text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
                      >
                        {LABELS.nextQuestion}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : null}
        </section>
      </main>
      <Footer />
    </>
  );
}

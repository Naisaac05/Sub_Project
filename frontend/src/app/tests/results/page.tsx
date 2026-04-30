'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyResults } from '@/lib/test';
import type { TestResultResponse } from '@/lib/types';
import { ArrowRight, Calendar, CheckCircle, FileText, Loader2, Trophy } from 'lucide-react';

const LABELS = {
  eyebrow: 'My Results',
  title: '\uB0B4 \uD14C\uC2A4\uD2B8 \uACB0\uACFC',
  description:
    '\uD480\uC5B4\uBCF8 \uC9C4\uB2E8 \uD14C\uC2A4\uD2B8 \uACB0\uACFC\uB97C \uC810\uC218\uC640 \uD604\uC7AC \uC0C1\uD0DC \uAE30\uC900\uC73C\uB85C \uD655\uC778\uD569\uB2C8\uB2E4.',
  backToTests: '\uD14C\uC2A4\uD2B8 \uBAA9\uB85D',
  loading: '\uACB0\uACFC\uB97C \uBD88\uB7EC\uC624\uB294 \uC911\uC785\uB2C8\uB2E4.',
  loadError: '\uACB0\uACFC\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.',
  retry: '\uB2E4\uC2DC \uBD88\uB7EC\uC624\uAE30',
  empty: '\uC544\uC9C1 \uD480\uC5B4\uBCF8 \uD14C\uC2A4\uD2B8\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.',
  startTest: '\uD14C\uC2A4\uD2B8 \uD480\uB7EC\uAC00\uAE30',
  smartReview: '\uC2A4\uB9C8\uD2B8 \uBCF5\uC2B5',
  score: '\uC810\uC218',
  correct: '\uC815\uB2F5',
  currentStatus: '\uD604\uC7AC \uC0C1\uD0DC',
  diagnosticNote:
    '\uC774 \uACB0\uACFC\uB294 \uD569\uACA9/\uBD88\uD569\uACA9\uBCF4\uB2E4 \uD604\uC7AC \uC5B4\uB290 \uBD80\uBD84\uC744 \uBCF5\uC2B5\uD558\uBA74 \uC88B\uC744\uC9C0 \uD655\uC778\uD558\uAE30 \uC704\uD55C \uC9C4\uB2E8\uC6A9\uC785\uB2C8\uB2E4.',
};

function formatKoreanDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

function getDiagnosticStatus(score: number) {
  if (score >= 80) {
    return {
      label: '\uD0C4\uD0C4\uD55C \uC0C1\uD0DC',
      description: '\uD575\uC2EC \uAC1C\uB150\uACFC \uC2E4\uBB34 \uD310\uB2E8\uC774 \uC548\uC815\uC801\uC785\uB2C8\uB2E4.',
      badgeClass: 'bg-emerald-50 text-emerald-600 border-emerald-100',
      barClass: 'bg-gradient-to-r from-emerald-400 to-green-500',
    };
  }
  if (score >= 60) {
    return {
      label: '\uAE30\uBCF8\uAE30\uB294 \uC7A1\uD78C \uC0C1\uD0DC',
      description: '\uAE30\uBCF8 \uD750\uB984\uC740 \uC7A1\uD600 \uC788\uACE0, \uC2E4\uBB34 \uD310\uB2E8\uC744 \uB354 \uBCF4\uAC15\uD558\uBA74 \uC88B\uC2B5\uB2C8\uB2E4.',
      badgeClass: 'bg-blue-50 text-blue-600 border-blue-100',
      barClass: 'bg-gradient-to-r from-blue-500 to-cyan-500',
    };
  }
  return {
    label: '\uBCF5\uC2B5\uC774 \uD544\uC694\uD55C \uC0C1\uD0DC',
    description: '\uD575\uC2EC \uAC1C\uB150\uC744 \uBA3C\uC800 \uC815\uB9AC\uD558\uACE0 \uC26C\uC6B4 \uBB38\uC81C\uBD80\uD130 \uB2E4\uC2DC \uD480\uC5B4\uBCF4\uBA74 \uC88B\uC2B5\uB2C8\uB2E4.',
    badgeClass: 'bg-amber-50 text-amber-600 border-amber-100',
    barClass: 'bg-gradient-to-r from-amber-400 to-orange-500',
  };
}

export default function TestResultsPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();
  const [results, setResults] = useState<TestResultResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  const fetchResults = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getMyResults();
      if (res.success) {
        setResults(res.data);
      } else {
        setError(res.message || LABELS.loadError);
      }
    } catch {
      setError(LABELS.loadError);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (authLoading || !isLoggedIn) {
      return;
    }
    fetchResults();
  }, [authLoading, isLoggedIn]);

  if (authLoading || !isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="relative overflow-hidden bg-[#07111f] pt-28 pb-16">
          <div className="mx-auto max-w-7xl px-6">
            <span className="inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-4 py-1.5 text-xs font-bold uppercase tracking-wider text-cyan-300">
              {LABELS.eyebrow}
            </span>
            <div className="mt-5 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
              <div className="max-w-3xl">
                <h1 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
                  {LABELS.title}
                </h1>
                <p className="mt-4 break-keep text-base leading-7 text-gray-300 sm:text-lg">
                  {LABELS.description}
                </p>
              </div>
              <Link
                href="/tests"
                className="inline-flex w-fit items-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-gray-950 transition-colors hover:bg-gray-100"
              >
                {LABELS.backToTests}
                <ArrowRight size={16} />
              </Link>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 py-12">
          {loading ? (
            <div className="flex flex-col items-center justify-center gap-3 py-24 text-gray-500">
              <Loader2 size={36} className="animate-spin text-blue-500" />
              <p className="text-sm font-medium">{LABELS.loading}</p>
            </div>
          ) : null}

          {!loading && error ? (
            <div className="flex flex-col items-center gap-4 rounded-2xl border border-red-100 bg-white p-10">
              <p className="font-semibold text-red-500">{error}</p>
              <button
                onClick={fetchResults}
                className="rounded-xl bg-gray-950 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800"
              >
                {LABELS.retry}
              </button>
            </div>
          ) : null}

          {!loading && !error && results.length === 0 ? (
            <div className="flex flex-col items-center gap-4 py-24 text-center">
              <FileText size={48} className="text-gray-300" />
              <p className="text-lg font-medium text-gray-400">{LABELS.empty}</p>
              <Link
                href="/tests"
                className="inline-flex items-center gap-2 rounded-xl bg-gray-950 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-gray-800"
              >
                {LABELS.startTest}
                <ArrowRight size={14} />
              </Link>
            </div>
          ) : null}

          {!loading && !error && results.length > 0 ? (
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {results.map((result) => {
                const status = getDiagnosticStatus(result.totalScore);

                return (
                  <article
                    key={result.id}
                    className="rounded-2xl border border-gray-100 bg-white p-7 shadow-sm transition-all duration-300 hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50"
                  >
                    <div className="mb-5 flex items-start justify-between gap-3">
                      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-amber-50">
                        <Trophy size={22} className="text-amber-500" />
                      </div>
                      <span className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs font-bold ${status.badgeClass}`}>
                        <CheckCircle size={12} />
                        {status.label}
                      </span>
                    </div>

                    <h3 className="mb-4 break-keep text-lg font-bold leading-snug text-gray-950">
                      {result.testTitle}
                    </h3>

                    <div className="mb-4">
                      <div className="mb-1.5 flex items-end justify-between">
                        <span className="text-sm font-medium text-gray-500">{LABELS.score}</span>
                        <span className="text-2xl font-extrabold text-gray-950">
                          {result.totalScore}
                          <span className="text-base font-semibold text-gray-400"> / 100</span>
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-gray-100">
                        <div
                          className={`h-2 rounded-full transition-all duration-500 ${status.barClass}`}
                          style={{ width: `${result.totalScore}%` }}
                        />
                      </div>
                    </div>

                    <p className="mb-5 break-keep text-sm leading-6 text-gray-500">
                      {status.description}
                    </p>

                    <div className="flex items-center gap-4 border-t border-gray-50 pt-4 text-xs text-gray-400">
                      <span className="flex items-center gap-1">
                        <CheckCircle size={13} className="text-gray-300" />
                        {result.correctCount} / {result.questionCount} {LABELS.correct}
                      </span>
                      <span className="ml-auto flex items-center gap-1">
                        <Calendar size={13} />
                        {formatKoreanDate(result.submittedAt)}
                      </span>
                    </div>

                    {result.correctCount < result.questionCount ? (
                      <div className="mt-5">
                        <Link
                          href={`/tests/results/${result.id}/review`}
                          className="flex w-full items-center justify-center gap-2 rounded-xl bg-amber-50 px-5 py-3 text-sm font-bold text-amber-700 transition-colors hover:bg-amber-100"
                        >
                          {LABELS.smartReview}
                          <ArrowRight size={14} />
                        </Link>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          ) : null}

          {!loading && !error && results.length > 0 ? (
            <div className="mt-10 rounded-2xl border border-blue-100 bg-blue-50 p-6 text-sm leading-6 text-gray-600">
              {LABELS.diagnosticNote}
            </div>
          ) : null}
        </section>
      </main>
      <Footer />
    </>
  );
}

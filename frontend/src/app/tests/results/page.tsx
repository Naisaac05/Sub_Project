'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyResults } from '@/lib/test';
import type { TestResultResponse } from '@/lib/types';
import { Trophy, CheckCircle, XCircle, ArrowRight, Loader2, FileText, Calendar } from 'lucide-react';

function formatKoreanDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

export default function TestResultsPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const [results, setResults] = useState<TestResultResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  // Fetch results once auth is resolved
  useEffect(() => {
    if (authLoading || !isLoggedIn) return;

    const fetchResults = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await getMyResults();
        if (res.success) {
          setResults(res.data);
        } else {
          setError(res.message || '결과를 불러오지 못했습니다.');
        }
      } catch {
        setError('결과를 불러오는 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchResults();
  }, [authLoading, isLoggedIn]);

  // Suppress render until auth state is known
  if (authLoading || !isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Hero */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-16 overflow-hidden">
          <div className="orb w-[300px] h-[300px] bg-amber-500/15 -top-10 right-20" />
          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span className="inline-block px-4 py-1.5 rounded-full glass-card text-cyan-400
                            text-xs font-bold tracking-wider uppercase mb-4">
              My Results
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              내 테스트 결과
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              응시한 테스트 결과를 확인하고 나에게 맞는 멘토를 추천받으세요.
            </p>
            <div className="mt-5">
              <Link
                href="/tests"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl
                           bg-white/10 text-white text-sm font-semibold
                           hover:bg-white/20 transition-colors"
              >
                테스트 목록 보기
                <ArrowRight size={14} />
              </Link>
            </div>
          </div>
        </section>

        <section className="max-w-7xl mx-auto px-6 py-12">
          {/* Loading State */}
          {loading && (
            <div className="flex justify-center items-center py-24">
              <Loader2 size={36} className="animate-spin text-blue-500" />
            </div>
          )}

          {/* Error State */}
          {!loading && error && (
            <div className="flex flex-col items-center py-24 gap-3">
              <p className="text-red-500 font-semibold">{error}</p>
              <button
                onClick={() => {
                  setLoading(true);
                  getMyResults()
                    .then((res) => {
                      if (res.success) setResults(res.data);
                      else setError(res.message || '결과를 불러오지 못했습니다.');
                    })
                    .catch(() => setError('결과를 불러오는 중 오류가 발생했습니다.'))
                    .finally(() => setLoading(false));
                }}
                className="px-5 py-2 rounded-xl bg-gray-900 text-white text-sm font-semibold
                           hover:bg-gray-700 transition-colors"
              >
                다시 시도
              </button>
            </div>
          )}

          {/* Empty State */}
          {!loading && !error && results.length === 0 && (
            <div className="flex flex-col items-center py-24 gap-4">
              <FileText size={48} className="text-gray-300" />
              <p className="text-gray-400 font-medium text-lg">아직 응시한 테스트가 없습니다</p>
              <Link
                href="/tests"
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl
                           bg-gray-900 text-white text-sm font-semibold
                           hover:bg-gray-700 transition-colors"
              >
                테스트 보러가기
                <ArrowRight size={14} />
              </Link>
            </div>
          )}

          {/* Results Grid */}
          {!loading && !error && results.length > 0 && (
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
              {results.map((result) => (
                <div
                  key={result.id}
                  className="group relative bg-white rounded-2xl border border-gray-100 p-7
                             transition-all duration-300
                             hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50"
                >
                  {/* Header Row: Trophy icon + passed badge + category badge */}
                  <div className="flex items-center justify-between mb-5">
                    <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-amber-50 to-orange-50
                                    flex items-center justify-center">
                      <Trophy size={22} className="text-amber-500" />
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="px-3 py-1 text-xs font-bold rounded-full border
                                       bg-blue-50 text-blue-600 border-blue-100">
                        {result.category}
                      </span>
                      {result.passed ? (
                        <span className="flex items-center gap-1 px-3 py-1 text-xs font-bold
                                         rounded-full bg-green-50 text-green-600 border border-green-100">
                          <CheckCircle size={12} />
                          합격
                        </span>
                      ) : (
                        <span className="flex items-center gap-1 px-3 py-1 text-xs font-bold
                                         rounded-full bg-red-50 text-red-500 border border-red-100">
                          <XCircle size={12} />
                          불합격
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Test Title */}
                  <h3 className="text-lg font-bold text-gray-900 mb-4 leading-snug">
                    {result.testTitle}
                  </h3>

                  {/* Score */}
                  <div className="mb-3">
                    <div className="flex items-end justify-between mb-1.5">
                      <span className="text-sm text-gray-500 font-medium">점수</span>
                      <span className="text-2xl font-extrabold text-gray-900">
                        {result.totalScore}
                        <span className="text-base font-semibold text-gray-400"> / 100</span>
                      </span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div
                        className={`h-2 rounded-full transition-all duration-500 ${
                          result.passed ? 'bg-gradient-to-r from-green-400 to-emerald-500' : 'bg-gradient-to-r from-red-400 to-rose-500'
                        }`}
                        style={{ width: `${result.totalScore}%` }}
                      />
                    </div>
                  </div>

                  {/* Meta: correct count + date */}
                  <div className="flex items-center gap-4 pt-4 border-t border-gray-50 text-xs text-gray-400">
                    <span className="flex items-center gap-1">
                      <CheckCircle size={13} className="text-gray-300" />
                      {result.correctCount} / {result.questionCount} 정답
                    </span>
                    <span className="flex items-center gap-1 ml-auto">
                      <Calendar size={13} />
                      {formatKoreanDate(result.submittedAt)}
                    </span>
                  </div>

                  {/* CTA: 멘토 추천 받기 (only when passed) */}
                  {result.passed && (
                    <div className="mt-5">
                      <Link
                        href={`/matching/recommend?category=${encodeURIComponent(result.category)}&resultId=${result.id}`}
                        className="w-full flex items-center justify-center gap-2 py-3 rounded-xl
                                   text-sm font-semibold text-blue-600 bg-blue-50
                                   group-hover:bg-gradient-to-r group-hover:from-blue-600 group-hover:to-cyan-500
                                   group-hover:text-white transition-all duration-300"
                      >
                        멘토 추천 받기
                        <ArrowRight size={14} />
                      </Link>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      </main>
      <Footer />
    </>
  );
}

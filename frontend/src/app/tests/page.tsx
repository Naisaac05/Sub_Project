'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getTests } from '@/lib/test';
import type { TestListResponse } from '@/lib/types';
import {
  Clock, FileText, BarChart3, ArrowRight, CheckCircle,
  Loader2, Code2,
} from 'lucide-react';

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

const CATEGORIES = ['전체', 'Java', 'Spring', 'React', 'Python', 'Algorithm'];

export default function TestsPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [tests, setTests] = useState<TestListResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  // Fetch tests whenever the selected category changes
  useEffect(() => {
    if (authLoading || !isLoggedIn) return;

    const fetchTests = async () => {
      setLoading(true);
      setError(null);
      try {
        const category = selectedCategory === '전체' ? undefined : selectedCategory;
        const res = await getTests(category);
        if (res.success) {
          setTests(res.data);
        } else {
          setError(res.message || '테스트 목록을 불러오지 못했습니다.');
        }
      } catch {
        setError('테스트 목록을 불러오는 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchTests();
  }, [selectedCategory, authLoading, isLoggedIn]);

  // While auth is resolving, show nothing to avoid flash
  if (authLoading || !isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Page Header */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-16 overflow-hidden">
          <div className="orb w-[300px] h-[300px] bg-cyan-500/15 -top-10 right-20" />
          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span className="inline-block px-4 py-1.5 rounded-full glass-card text-cyan-400
                            text-xs font-bold tracking-wider uppercase mb-4">
              Skill Test
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              실력 테스트로 나를 객관적으로 파악
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              분야별 테스트를 통해 현재 수준을 진단받고, 딱 맞는 멘토를 추천받으세요.
            </p>
            <div className="mt-5">
              <Link
                href="/tests/results"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl
                           bg-white/10 text-white text-sm font-semibold
                           hover:bg-white/20 transition-colors"
              >
                내 테스트 결과
                <ArrowRight size={14} />
              </Link>
            </div>
          </div>
        </section>

        <section className="max-w-7xl mx-auto px-6 py-12">
          {/* Category Filter */}
          <div className="flex items-center gap-2 overflow-x-auto pb-4 mb-8 scrollbar-hide">
            {CATEGORIES.map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={`px-5 py-2.5 rounded-xl text-sm font-semibold whitespace-nowrap
                            transition-all duration-200 ${
                  selectedCategory === cat
                    ? 'bg-gray-900 text-white shadow-lg shadow-gray-900/10'
                    : 'bg-white text-gray-600 border border-gray-200 hover:border-gray-300 hover:text-gray-900'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

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
                onClick={() => setSelectedCategory(selectedCategory)}
                className="px-5 py-2 rounded-xl bg-gray-900 text-white text-sm font-semibold
                           hover:bg-gray-700 transition-colors"
              >
                다시 시도
              </button>
            </div>
          )}

          {/* Empty State */}
          {!loading && !error && tests.length === 0 && (
            <div className="flex flex-col items-center py-24 gap-3">
              <Code2 size={40} className="text-gray-300" />
              <p className="text-gray-400 font-medium">테스트가 없습니다.</p>
            </div>
          )}

          {/* Test Cards */}
          {!loading && !error && tests.length > 0 && (
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
              {tests.map((test) => (
                <div
                  key={test.id}
                  className="group relative bg-white rounded-2xl border border-gray-100 p-7
                             transition-all duration-300
                             hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50"
                >
                  {/* Icon & Difficulty */}
                  <div className="flex items-center justify-between mb-5">
                    <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-50 to-violet-50
                                    flex items-center justify-center">
                      <Code2 size={22} className="text-blue-600" />
                    </div>
                    <span
                      className={`px-3 py-1 text-xs font-bold rounded-full border ${
                        difficultyColor[test.difficulty] ?? 'bg-gray-50 text-gray-600 border-gray-100'
                      }`}
                    >
                      {difficultyLabel[test.difficulty] ?? test.difficulty}
                    </span>
                  </div>

                  {/* Title */}
                  <h3 className="text-lg font-bold text-gray-900 mb-4">{test.title}</h3>

                  {/* Meta */}
                  <div className="flex items-center gap-4 pt-4 border-t border-gray-50 text-xs text-gray-400">
                    <span className="flex items-center gap-1">
                      <FileText size={14} />
                      {test.questionCount}문항
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock size={14} />
                      {test.timeLimit}분
                    </span>
                    <span className="flex items-center gap-1">
                      <BarChart3 size={14} />
                      {test.category}
                    </span>
                  </div>

                  {/* CTA */}
                  <div className="mt-5">
                    <Link
                      href={`/tests/${test.id}`}
                      className="w-full flex items-center justify-center gap-2 py-3 rounded-xl
                                 text-sm font-semibold text-blue-600 bg-blue-50
                                 group-hover:bg-gradient-to-r group-hover:from-blue-600 group-hover:to-cyan-500
                                 group-hover:text-white transition-all duration-300"
                    >
                      테스트 시작하기
                      <ArrowRight size={14} />
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Info Banner */}
        <section className="max-w-7xl mx-auto px-6 pb-16">
          <div className="bg-gradient-to-r from-gray-900 to-gray-800 rounded-2xl p-8 sm:p-10
                          flex flex-col sm:flex-row items-center justify-between gap-6">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-white/10 flex items-center justify-center">
                <CheckCircle size={24} className="text-cyan-400" />
              </div>
              <div>
                <h3 className="text-white font-bold text-lg">테스트 완료 후 자동 매칭</h3>
                <p className="text-gray-400 text-sm">결과를 바탕으로 최적의 멘토를 추천받으세요</p>
              </div>
            </div>
            <Link
              href="/mentors"
              className="flex items-center gap-2 px-6 py-3 rounded-xl
                         bg-white text-gray-900 font-semibold text-sm
                         hover:bg-gray-100 transition-colors whitespace-nowrap"
            >
              멘토 보러가기
              <ArrowRight size={16} />
            </Link>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}

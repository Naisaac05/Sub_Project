'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { recommendMentors } from '@/lib/matching';
import type { MentorRecommendResponse } from '@/lib/types';
import { Search, Star, ArrowRight, Loader2, Users, Award } from 'lucide-react';

const CATEGORIES = ['전체', 'Java', 'Spring', 'React', 'Python', 'Node.js', 'DevOps'];

const gradients = [
  'from-violet-500 to-pink-500',
  'from-blue-500 to-cyan-400',
  'from-amber-500 to-orange-500',
  'from-emerald-500 to-teal-500',
  'from-rose-500 to-pink-500',
  'from-indigo-500 to-blue-500',
];

const getInitials = (name: string) => name.slice(0, 2);
const getGradient = (id: number) => gradients[id % gradients.length];

const getMatchScoreColor = (score: number) => {
  if (score >= 80) return 'text-emerald-600 bg-emerald-50 border-emerald-200';
  if (score >= 60) return 'text-blue-600 bg-blue-50 border-blue-200';
  if (score >= 40) return 'text-amber-600 bg-amber-50 border-amber-200';
  return 'text-gray-600 bg-gray-50 border-gray-200';
};

const getMatchBarColor = (score: number) => {
  if (score >= 80) return 'bg-emerald-500';
  if (score >= 60) return 'bg-blue-500';
  if (score >= 40) return 'bg-amber-500';
  return 'bg-gray-400';
};

export default function MentorsPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [mentors, setMentors] = useState<MentorRecommendResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.push('/auth/login?redirect=/mentors');
    }
  }, [authLoading, isLoggedIn, router]);

  // Fetch mentors when category changes (only for non-전체)
  useEffect(() => {
    if (!isLoggedIn || selectedCategory === '전체') {
      setMentors([]);
      setError(null);
      return;
    }

    const fetchMentors = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await recommendMentors(selectedCategory);
        if (res.success) {
          setMentors(res.data);
        } else {
          setError(res.message || '멘토 목록을 불러오지 못했습니다.');
        }
      } catch {
        setError('서버와 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
      } finally {
        setLoading(false);
      }
    };

    fetchMentors();
  }, [selectedCategory, isLoggedIn]);

  // Client-side search filter
  const filtered = mentors.filter((m) => {
    const q = search.toLowerCase();
    return (
      m.name.toLowerCase().includes(q) ||
      m.specialty.some((s) => s.toLowerCase().includes(q))
    );
  });

  // Show nothing while auth is loading
  if (authLoading) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-gray-50 flex items-center justify-center">
          <Loader2 size={32} className="animate-spin text-blue-500" />
        </main>
        <Footer />
      </>
    );
  }

  if (!isLoggedIn) return null;

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Page Header */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-16 overflow-hidden">
          <div className="orb w-[400px] h-[400px] bg-blue-600/15 -top-20 -right-20" />
          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span className="inline-block px-4 py-1.5 rounded-full glass-card text-cyan-400
                          text-xs font-bold tracking-wider uppercase mb-4">
              Mentors
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              나에게 맞는 멘토를 찾아보세요
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              전문 분야를 선택하면 AI가 적합한 멘토를 추천해드립니다.
            </p>
          </div>
        </section>

        {/* Filters */}
        <section className="max-w-7xl mx-auto px-6 -mt-6 relative z-20">
          <div className="bg-white rounded-2xl shadow-lg shadow-gray-200/50 border border-gray-100 p-5">
            <div className="flex flex-col gap-4">
              {/* Search */}
              <div className="relative">
                <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="멘토 이름 또는 기술 스택 검색"
                  className="w-full pl-11 pr-4 py-3 rounded-xl bg-gray-50 border border-gray-200
                           text-gray-900 placeholder-gray-400 text-sm
                           focus:outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100
                           transition-all duration-200"
                />
              </div>
              {/* Category buttons */}
              <div className="flex flex-wrap gap-2">
                {CATEGORIES.map((cat) => (
                  <button
                    key={cat}
                    onClick={() => setSelectedCategory(cat)}
                    className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all duration-200
                      ${selectedCategory === cat
                        ? 'bg-blue-600 text-white border-blue-600 shadow-md shadow-blue-100'
                        : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-blue-300 hover:text-blue-600'
                      }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* Content */}
        <section className="max-w-7xl mx-auto px-6 py-12">

          {/* Loading */}
          {loading && (
            <div className="flex flex-col items-center justify-center py-24 gap-4">
              <Loader2 size={36} className="animate-spin text-blue-500" />
              <p className="text-gray-400 text-sm">멘토를 불러오는 중...</p>
            </div>
          )}

          {/* Error */}
          {!loading && error && (
            <div className="flex flex-col items-center justify-center py-24 gap-3">
              <div className="w-14 h-14 rounded-2xl bg-red-50 border border-red-100 flex items-center justify-center">
                <Award size={24} className="text-red-400" />
              </div>
              <p className="text-gray-700 font-semibold">{error}</p>
              <button
                onClick={() => setSelectedCategory(selectedCategory)}
                className="mt-2 px-4 py-2 rounded-xl bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors"
              >
                다시 시도
              </button>
            </div>
          )}

          {/* Prompt to select specialty */}
          {!loading && !error && selectedCategory === '전체' && (
            <div className="flex flex-col items-center justify-center py-24 gap-4">
              <div className="w-16 h-16 rounded-2xl bg-blue-50 border border-blue-100 flex items-center justify-center">
                <Users size={28} className="text-blue-400" />
              </div>
              <p className="text-gray-700 font-semibold text-lg">전문 분야를 선택해주세요</p>
              <p className="text-gray-400 text-sm text-center max-w-xs">
                위의 카테고리 버튼을 클릭하면 해당 분야의 맞춤 멘토를 추천받을 수 있습니다.
              </p>
            </div>
          )}

          {/* Mentor grid */}
          {!loading && !error && selectedCategory !== '전체' && (
            <>
              <p className="text-sm text-gray-500 mb-6">
                <span className="font-semibold text-blue-600">{selectedCategory}</span> 분야&nbsp;·&nbsp;
                총 <span className="font-semibold text-gray-900">{filtered.length}명</span>의 추천 멘토
              </p>

              {filtered.length > 0 ? (
                <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                  {filtered.map((m) => (
                    <div
                      key={m.mentorId}
                      className="group bg-white rounded-2xl border border-gray-100 p-6
                               hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50
                               transition-all duration-300 flex flex-col"
                    >
                      {/* Avatar & Info */}
                      <div className="flex items-center gap-3 mb-4">
                        <div className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${getGradient(m.mentorId)}
                                     flex items-center justify-center text-white font-bold text-sm
                                     group-hover:scale-110 transition-transform duration-300 shrink-0`}>
                          {getInitials(m.name)}
                        </div>
                        <div className="min-w-0">
                          <h3 className="font-bold text-gray-900 truncate">{m.name} 멘토</h3>
                          <p className="text-gray-500 text-xs truncate">
                            {m.company}&nbsp;·&nbsp;경력 {m.careerYears}년차
                          </p>
                        </div>
                      </div>

                      {/* Bio */}
                      <p className="text-gray-500 text-sm leading-relaxed mb-4 line-clamp-2 flex-1">
                        {m.bio || '자기소개가 없습니다.'}
                      </p>

                      {/* Specialty tags */}
                      <div className="flex flex-wrap gap-1.5 mb-4">
                        {m.specialty.map((tag) => (
                          <span
                            key={tag}
                            className="px-2.5 py-1 text-xs rounded-md bg-gray-50 text-gray-600
                                     border border-gray-100 font-medium"
                          >
                            {tag}
                          </span>
                        ))}
                      </div>

                      {/* Match score */}
                      <div className="mb-4">
                        <div className="flex items-center justify-between mb-1.5">
                          <span className="text-xs text-gray-400 font-medium">적합도</span>
                          <span className={`text-xs font-bold px-2 py-0.5 rounded-md border ${getMatchScoreColor(m.matchScore)}`}>
                            {m.matchScore}점
                          </span>
                        </div>
                        <div className="h-1.5 rounded-full bg-gray-100 overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-500 ${getMatchBarColor(m.matchScore)}`}
                            style={{ width: `${m.matchScore}%` }}
                          />
                        </div>
                      </div>

                      {/* Action */}
                      <div className="pt-4 border-t border-gray-50">
                        <Link
                          href={`/matching/recommend?category=${encodeURIComponent(selectedCategory)}`}
                          className="flex items-center justify-center gap-2 w-full py-2.5 rounded-xl
                                   bg-blue-600 text-white text-sm font-semibold
                                   hover:bg-blue-700 active:scale-95
                                   transition-all duration-200 group/btn"
                        >
                          매칭 신청
                          <ArrowRight size={14} className="group-hover/btn:translate-x-0.5 transition-transform" />
                        </Link>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-20">
                  <div className="w-14 h-14 rounded-2xl bg-gray-50 border border-gray-100 flex items-center justify-center mx-auto mb-4">
                    <Star size={22} className="text-gray-300" />
                  </div>
                  <p className="text-gray-500 text-lg font-medium mb-2">
                    {search ? '검색 결과가 없습니다' : '추천 멘토가 없습니다'}
                  </p>
                  <p className="text-gray-400 text-sm">
                    {search ? '다른 검색어를 시도해보세요.' : '다른 전문 분야를 선택해보세요.'}
                  </p>
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

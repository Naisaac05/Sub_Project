'use client';

import { useState } from 'react';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import {
  Search, MessageSquare, Heart, Eye, Clock, ChevronUp, PenSquare,
  TrendingUp, Bookmark, Filter,
} from 'lucide-react';

const categories = ['전체', '질문/답변', '학습 공유', '멘토링 후기', '취업/이직', '자유 게시판'];

const posts = [
  { id: 1, category: '멘토링 후기', title: '네이버 백엔드 멘토님 3개월 후기 - 진짜 달라졌습니다', author: '이정훈', time: '2시간 전', views: 342, likes: 48, comments: 12, liked: false, hot: true },
  { id: 2, category: '질문/답변', title: 'Spring Security JWT 토큰 갱신 관련 질문드립니다', author: '김서연', time: '4시간 전', views: 128, likes: 8, comments: 5, liked: false, hot: false },
  { id: 3, category: '학습 공유', title: 'Java Stream API 정리 노트 공유합니다 (예제 포함)', author: '박도현', time: '6시간 전', views: 567, likes: 92, comments: 23, liked: true, hot: true },
  { id: 4, category: '취업/이직', title: '비전공 프론트엔드 6개월 학습 후 카카오 합격 후기', author: '최유진', time: '8시간 전', views: 1203, likes: 215, comments: 67, liked: false, hot: true },
  { id: 5, category: '질문/답변', title: 'JPA N+1 문제 해결 방법 중 가장 좋은 접근법은?', author: '한지우', time: '12시간 전', views: 234, likes: 19, comments: 8, liked: false, hot: false },
  { id: 6, category: '자유 게시판', title: '개발자 번아웃 극복기 - 1년간의 슬럼프를 이겨낸 방법', author: '윤서준', time: '1일 전', views: 891, likes: 156, comments: 45, liked: true, hot: true },
  { id: 7, category: '학습 공유', title: 'Docker + GitHub Actions으로 CI/CD 구축하기 (실습)', author: '정하은', time: '1일 전', views: 445, likes: 67, comments: 15, liked: false, hot: false },
  { id: 8, category: '멘토링 후기', title: '토스 풀스택 멘토님과 사이드 프로젝트 완성한 후기', author: '송예린', time: '2일 전', views: 678, likes: 98, comments: 31, liked: false, hot: false },
];

function formatCount(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
  return String(n);
}

export default function CommunityPage() {
  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [search, setSearch] = useState('');

  const filtered = posts.filter(p => {
    const matchCategory = selectedCategory === '전체' || p.category === selectedCategory;
    const matchSearch = p.title.toLowerCase().includes(search.toLowerCase());
    return matchCategory && matchSearch;
  });

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Page Header */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-16 overflow-hidden">
          <div className="orb w-[350px] h-[350px] bg-violet-600/15 -top-10 left-20" />
          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span className="inline-block px-4 py-1.5 rounded-full glass-card text-cyan-400
                          text-xs font-bold tracking-wider uppercase mb-4">
              Community
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              함께 성장하는 커뮤니티
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              질문하고, 공유하고, 후기를 남기며 함께 성장하세요.
            </p>
          </div>
        </section>

        <section className="max-w-7xl mx-auto px-6 py-12">
          <div className="flex flex-col lg:flex-row gap-8">
            {/* Main Content */}
            <div className="flex-1">
              {/* Search & Write */}
              <div className="flex gap-3 mb-6">
                <div className="relative flex-1">
                  <Search size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="text"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="게시글 검색"
                    className="w-full pl-11 pr-4 py-3 rounded-xl bg-white border border-gray-200
                             text-gray-900 placeholder-gray-400 text-sm
                             focus:outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100
                             transition-all duration-200"
                  />
                </div>
                <button className="flex items-center gap-2 px-5 py-3 rounded-xl
                               bg-gradient-to-r from-blue-600 to-blue-500
                               text-white font-semibold text-sm shadow-lg shadow-blue-600/20
                               hover:shadow-blue-500/30 transition-all duration-300 whitespace-nowrap">
                  <PenSquare size={16} />
                  글쓰기
                </button>
              </div>

              {/* Category Filter */}
              <div className="flex items-center gap-2 overflow-x-auto pb-4 mb-6 scrollbar-hide">
                {categories.map(cat => (
                  <button
                    key={cat}
                    onClick={() => setSelectedCategory(cat)}
                    className={`px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap
                              transition-all duration-200 ${
                      selectedCategory === cat
                        ? 'bg-gray-900 text-white'
                        : 'bg-white text-gray-500 border border-gray-200 hover:text-gray-900'
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>

              {/* Post List */}
              <div className="space-y-3">
                {filtered.map((post) => (
                  <article
                    key={post.id}
                    className="group bg-white rounded-xl border border-gray-100 p-5
                             hover:border-blue-100 hover:shadow-md hover:shadow-blue-50
                             transition-all duration-200 cursor-pointer"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <span className="px-2.5 py-0.5 text-xs font-semibold rounded-md
                                       bg-gray-50 text-gray-500 border border-gray-100">
                            {post.category}
                          </span>
                          {post.hot && (
                            <span className="px-2 py-0.5 text-xs font-bold rounded-md
                                         bg-red-50 text-red-500 border border-red-100">
                              HOT
                            </span>
                          )}
                        </div>
                        <h3 className="text-base font-semibold text-gray-900 group-hover:text-blue-600
                                   transition-colors truncate">
                          {post.title}
                        </h3>
                        <div className="flex items-center gap-3 mt-2.5 text-xs text-gray-400">
                          <span className="font-medium text-gray-600">{post.author}</span>
                          <span className="flex items-center gap-1"><Clock size={12} />{post.time}</span>
                          <span className="flex items-center gap-1"><Eye size={12} />{formatCount(post.views)}</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-4 text-xs text-gray-400 pt-1">
                        <span className="flex items-center gap-1">
                          <Heart size={14} className={post.liked ? 'text-red-500 fill-red-500' : ''} />
                          {post.likes}
                        </span>
                        <span className="flex items-center gap-1">
                          <MessageSquare size={14} />
                          {post.comments}
                        </span>
                      </div>
                    </div>
                  </article>
                ))}
              </div>

              {filtered.length === 0 && (
                <div className="text-center py-16">
                  <p className="text-gray-400 text-lg">게시글이 없습니다</p>
                </div>
              )}
            </div>

            {/* Sidebar */}
            <aside className="lg:w-80 space-y-6">
              {/* Hot Posts */}
              <div className="bg-white rounded-2xl border border-gray-100 p-6">
                <h3 className="flex items-center gap-2 font-bold text-gray-900 mb-4">
                  <TrendingUp size={18} className="text-red-500" />
                  인기 게시글
                </h3>
                <div className="space-y-3">
                  {posts.filter(p => p.hot).slice(0, 4).map((post, i) => (
                    <div key={post.id} className="flex items-start gap-3 cursor-pointer group/item">
                      <span className="text-sm font-extrabold text-blue-500 font-[Outfit] mt-0.5 w-5">
                        {i + 1}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-gray-700 group-hover/item:text-blue-600
                                   transition-colors truncate font-medium">
                          {post.title}
                        </p>
                        <span className="text-xs text-gray-400">{post.author} &middot; {post.time}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Tags */}
              <div className="bg-white rounded-2xl border border-gray-100 p-6">
                <h3 className="flex items-center gap-2 font-bold text-gray-900 mb-4">
                  <Bookmark size={18} className="text-amber-500" />
                  인기 태그
                </h3>
                <div className="flex flex-wrap gap-2">
                  {['Spring Boot', 'React', 'Java', '면접', '이직', 'JPA', 'Docker', 'TypeScript', '알고리즘', 'AWS'].map(tag => (
                    <span key={tag} className="px-3 py-1.5 text-xs rounded-lg bg-gray-50 text-gray-600
                                            border border-gray-100 font-medium cursor-pointer
                                            hover:bg-blue-50 hover:text-blue-600 hover:border-blue-100
                                            transition-all duration-200">
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            </aside>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}

'use client';

import { useState } from 'react';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import {
  Clock, FileText, BarChart3, ArrowRight, Lock, CheckCircle,
  Code2, Database, Globe, Server, Layout, Cpu,
} from 'lucide-react';

type Difficulty = '입문' | '초급' | '중급' | '고급';

interface TestItem {
  id: number;
  title: string;
  category: string;
  icon: React.ElementType;
  questions: number;
  duration: string;
  difficulty: Difficulty;
  description: string;
  topics: string[];
  available: boolean;
}

const difficultyColor: Record<Difficulty, string> = {
  '입문': 'bg-green-50 text-green-600 border-green-100',
  '초급': 'bg-blue-50 text-blue-600 border-blue-100',
  '중급': 'bg-amber-50 text-amber-600 border-amber-100',
  '고급': 'bg-red-50 text-red-600 border-red-100',
};

const tests: TestItem[] = [
  { id: 1, title: 'Java 기초', category: '백엔드', icon: Code2, questions: 30, duration: '40분', difficulty: '입문', description: 'Java 문법, OOP, 컬렉션 프레임워크 등 기초 역량을 테스트합니다.', topics: ['변수/타입', 'OOP 4대 특성', 'Collection', 'Exception'], available: true },
  { id: 2, title: 'Spring Boot 핵심', category: '백엔드', icon: Server, questions: 25, duration: '35분', difficulty: '초급', description: 'Spring Boot의 핵심 개념과 REST API 설계 역량을 평가합니다.', topics: ['IoC/DI', 'REST API', 'JPA 기초', 'Security'], available: true },
  { id: 3, title: '시스템 설계', category: '아키텍처', icon: Database, questions: 15, duration: '50분', difficulty: '중급', description: '대규모 시스템 설계 능력을 평가합니다. 캐싱, 로드밸런싱, DB 설계 등.', topics: ['캐싱 전략', '로드밸런싱', 'DB 파티셔닝', 'MSA'], available: true },
  { id: 4, title: 'React/Next.js', category: '프론트엔드', icon: Layout, questions: 30, duration: '40분', difficulty: '초급', description: 'React 핵심 개념과 Next.js App Router 기반 개발 역량을 평가합니다.', topics: ['Hooks', 'State 관리', 'SSR/SSG', 'App Router'], available: true },
  { id: 5, title: '알고리즘 & 자료구조', category: '공통', icon: Cpu, questions: 10, duration: '60분', difficulty: '중급', description: '코딩 테스트 대비 알고리즘 문제 풀이 능력을 평가합니다.', topics: ['배열/문자열', '트리/그래프', 'DP', '정렬/탐색'], available: true },
  { id: 6, title: 'DevOps & 클라우드', category: 'DevOps', icon: Globe, questions: 20, duration: '30분', difficulty: '고급', description: 'Docker, Kubernetes, AWS 등 인프라 및 배포 역량을 평가합니다.', topics: ['Docker', 'Kubernetes', 'CI/CD', 'AWS'], available: false },
];

const categories = ['전체', '백엔드', '프론트엔드', '아키텍처', '공통', 'DevOps'];

export default function TestsPage() {
  const [selectedCategory, setSelectedCategory] = useState('전체');

  const filtered = selectedCategory === '전체'
    ? tests
    : tests.filter(t => t.category === selectedCategory);

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
          </div>
        </section>

        <section className="max-w-7xl mx-auto px-6 py-12">
          {/* Category Filter */}
          <div className="flex items-center gap-2 overflow-x-auto pb-4 mb-8 scrollbar-hide">
            {categories.map(cat => (
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

          {/* Test Cards */}
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {filtered.map((test) => (
              <div
                key={test.id}
                className={`group relative bg-white rounded-2xl border border-gray-100 p-7
                          transition-all duration-300
                          ${test.available
                            ? 'hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50 cursor-pointer'
                            : 'opacity-60'}`}
              >
                {!test.available && (
                  <div className="absolute top-4 right-4 flex items-center gap-1 px-3 py-1 rounded-full
                               bg-gray-100 text-gray-500 text-xs font-medium">
                    <Lock size={12} />
                    준비중
                  </div>
                )}

                {/* Icon & Category */}
                <div className="flex items-center justify-between mb-5">
                  <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-50 to-violet-50
                              flex items-center justify-center">
                    <test.icon size={22} className="text-blue-600" />
                  </div>
                  <span className={`px-3 py-1 text-xs font-bold rounded-full border ${difficultyColor[test.difficulty]}`}>
                    {test.difficulty}
                  </span>
                </div>

                {/* Title & Desc */}
                <h3 className="text-lg font-bold text-gray-900 mb-2">{test.title}</h3>
                <p className="text-gray-500 text-sm leading-relaxed mb-4">{test.description}</p>

                {/* Topics */}
                <div className="flex flex-wrap gap-1.5 mb-5">
                  {test.topics.map(topic => (
                    <span key={topic} className="px-2.5 py-1 text-xs rounded-md bg-gray-50 text-gray-500
                                              border border-gray-100">
                      {topic}
                    </span>
                  ))}
                </div>

                {/* Meta */}
                <div className="flex items-center gap-4 pt-4 border-t border-gray-50 text-xs text-gray-400">
                  <span className="flex items-center gap-1">
                    <FileText size={14} />
                    {test.questions}문항
                  </span>
                  <span className="flex items-center gap-1">
                    <Clock size={14} />
                    {test.duration}
                  </span>
                  <span className="flex items-center gap-1">
                    <BarChart3 size={14} />
                    {test.category}
                  </span>
                </div>

                {/* CTA */}
                {test.available && (
                  <div className="mt-5">
                    <button className="w-full flex items-center justify-center gap-2 py-3 rounded-xl
                                   text-sm font-semibold text-blue-600 bg-blue-50
                                   group-hover:bg-gradient-to-r group-hover:from-blue-600 group-hover:to-cyan-500
                                   group-hover:text-white transition-all duration-300">
                      테스트 시작하기
                      <ArrowRight size={14} />
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
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

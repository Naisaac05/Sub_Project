'use client';

import { useState } from 'react';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { Search, Star, Filter, ChevronDown, MapPin, Clock, ArrowRight } from 'lucide-react';

const specialties = ['전체', 'Java/Spring', 'React/Next.js', 'Python/Django', 'Node.js', 'DevOps', '시스템 설계'];
const companies = ['전체', '네이버', '카카오', '토스', '삼성전자', '라인', '쿠팡', '배달의민족'];

const mentorData = [
  { id: 1, name: '김재현', company: '네이버', role: '백엔드', years: 8, tags: ['Java', 'Spring', 'MSA'], rating: 4.9, reviews: 127, initials: 'JK', gradient: 'from-violet-500 to-pink-500', desc: 'Java/Spring 기반 대용량 트래픽 처리 전문. 네이버 검색 시스템 개발 경험.' },
  { id: 2, name: '이수진', company: '카카오', role: '프론트엔드', years: 6, tags: ['React', 'Next.js', 'TypeScript'], rating: 4.8, reviews: 94, initials: 'SJ', gradient: 'from-blue-500 to-cyan-400', desc: 'React/Next.js 전문. 카카오톡 웹 프론트엔드 개발 경험.' },
  { id: 3, name: '박민수', company: '토스', role: '풀스택', years: 7, tags: ['Node.js', 'React', 'AWS'], rating: 4.9, reviews: 108, initials: 'MS', gradient: 'from-amber-500 to-orange-500', desc: '풀스택 개발 및 핀테크 도메인 전문. 토스 결제 시스템 경험.' },
  { id: 4, name: '정하은', company: '삼성전자', role: '백엔드', years: 5, tags: ['Python', 'Django', 'K8s'], rating: 4.7, reviews: 76, initials: 'HE', gradient: 'from-emerald-500 to-teal-500', desc: 'Python/Django 백엔드 및 Kubernetes 인프라 전문.' },
  { id: 5, name: '최동욱', company: '라인', role: '백엔드', years: 9, tags: ['Java', 'Kotlin', 'Redis'], rating: 4.9, reviews: 152, initials: 'DW', gradient: 'from-rose-500 to-pink-500', desc: '메시징 시스템 및 실시간 처리 전문. LINE 메시지 서버 개발 경험.' },
  { id: 6, name: '한지영', company: '쿠팡', role: '프론트엔드', years: 5, tags: ['React', 'Vue', 'GraphQL'], rating: 4.8, reviews: 83, initials: 'JY', gradient: 'from-indigo-500 to-blue-500', desc: '대규모 이커머스 프론트엔드 전문. 쿠팡 상품 페이지 최적화 경험.' },
  { id: 7, name: '윤서준', company: '배달의민족', role: '백엔드', years: 6, tags: ['Java', 'Spring', 'Kafka'], rating: 4.8, reviews: 91, initials: 'SJ', gradient: 'from-cyan-500 to-sky-500', desc: '이벤트 드리븐 아키텍처 및 주문 시스템 전문.' },
  { id: 8, name: '송예린', company: '토스', role: 'DevOps', years: 7, tags: ['AWS', 'Terraform', 'Docker'], rating: 4.9, reviews: 65, initials: 'YR', gradient: 'from-orange-500 to-amber-400', desc: '클라우드 인프라 및 CI/CD 파이프라인 전문. 토스 DevOps 팀.' },
];

export default function MentorsPage() {
  const [search, setSearch] = useState('');
  const [selectedSpecialty, setSelectedSpecialty] = useState('전체');
  const [selectedCompany, setSelectedCompany] = useState('전체');

  const filtered = mentorData.filter((m) => {
    const matchSearch = m.name.includes(search) || m.tags.some(t => t.toLowerCase().includes(search.toLowerCase()));
    const matchSpecialty = selectedSpecialty === '전체' || m.tags.some(t => selectedSpecialty.includes(t));
    const matchCompany = selectedCompany === '전체' || m.company === selectedCompany;
    return matchSearch && matchSpecialty && matchCompany;
  });

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
              검증된 현직 멘토를 만나보세요
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              네이버, 카카오, 토스 등 대기업 현직 개발자가 1:1로 멘토링합니다.
            </p>
          </div>
        </section>

        {/* Filters */}
        <section className="max-w-7xl mx-auto px-6 -mt-6 relative z-20">
          <div className="bg-white rounded-2xl shadow-lg shadow-gray-200/50 border border-gray-100 p-5">
            <div className="flex flex-col md:flex-row gap-4">
              {/* Search */}
              <div className="relative flex-1">
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
              {/* Specialty filter */}
              <div className="relative">
                <Filter size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" />
                <select
                  value={selectedSpecialty}
                  onChange={(e) => setSelectedSpecialty(e.target.value)}
                  className="appearance-none pl-10 pr-10 py-3 rounded-xl bg-gray-50 border border-gray-200
                           text-gray-700 text-sm cursor-pointer
                           focus:outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100
                           transition-all duration-200"
                >
                  {specialties.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                <ChevronDown size={16} className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              </div>
              {/* Company filter */}
              <div className="relative">
                <MapPin size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" />
                <select
                  value={selectedCompany}
                  onChange={(e) => setSelectedCompany(e.target.value)}
                  className="appearance-none pl-10 pr-10 py-3 rounded-xl bg-gray-50 border border-gray-200
                           text-gray-700 text-sm cursor-pointer
                           focus:outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100
                           transition-all duration-200"
                >
                  {companies.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
                <ChevronDown size={16} className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              </div>
            </div>
          </div>
        </section>

        {/* Mentor Grid */}
        <section className="max-w-7xl mx-auto px-6 py-12">
          <p className="text-sm text-gray-500 mb-6">
            총 <span className="font-semibold text-gray-900">{filtered.length}명</span>의 멘토
          </p>

          <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {filtered.map((m) => (
              <div
                key={m.id}
                className="group bg-white rounded-2xl border border-gray-100 p-6
                         hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50
                         transition-all duration-300 cursor-pointer"
              >
                {/* Avatar & Info */}
                <div className="flex items-center gap-3 mb-4">
                  <div className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${m.gradient}
                               flex items-center justify-center text-white font-bold
                               group-hover:scale-110 transition-transform duration-300`}>
                    {m.initials}
                  </div>
                  <div>
                    <h3 className="font-bold text-gray-900">{m.name} 멘토</h3>
                    <p className="text-gray-500 text-xs">{m.company} / {m.role} {m.years}년차</p>
                  </div>
                </div>

                {/* Description */}
                <p className="text-gray-500 text-sm leading-relaxed mb-4 line-clamp-2">{m.desc}</p>

                {/* Tags */}
                <div className="flex flex-wrap gap-1.5 mb-4">
                  {m.tags.map(tag => (
                    <span key={tag} className="px-2.5 py-1 text-xs rounded-md bg-gray-50 text-gray-600
                                            border border-gray-100 font-medium">
                      {tag}
                    </span>
                  ))}
                </div>

                {/* Rating & Reviews */}
                <div className="flex items-center justify-between pt-4 border-t border-gray-50">
                  <div className="flex items-center gap-1">
                    <Star size={14} className="text-amber-400 fill-amber-400" />
                    <span className="text-sm font-semibold text-gray-900">{m.rating}</span>
                    <span className="text-gray-400 text-xs">({m.reviews})</span>
                  </div>
                  <span className="flex items-center gap-1 text-blue-600 text-xs font-semibold
                               group-hover:gap-2 transition-all duration-200">
                    프로필 보기 <ArrowRight size={12} />
                  </span>
                </div>
              </div>
            ))}
          </div>

          {filtered.length === 0 && (
            <div className="text-center py-20">
              <p className="text-gray-400 text-lg mb-2">검색 결과가 없습니다</p>
              <p className="text-gray-400 text-sm">다른 검색어나 필터를 시도해보세요.</p>
            </div>
          )}
        </section>
      </main>
      <Footer />
    </>
  );
}

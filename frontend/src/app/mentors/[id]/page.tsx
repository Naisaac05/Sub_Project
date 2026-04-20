'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { Database, Code2, Layout, Server, Cpu, Smartphone, Layers, Cloud, Users } from 'lucide-react';
import React from 'react';
import { fetchCourse } from '@/lib/courses';
import type { MentoringCourseDetail } from '@/lib/types';

const ICON_MAP: Record<string, React.ComponentType<{ size?: number }>> = {
  Database,
  Code2,
  Layout,
  Server,
  Cpu,
  Smartphone,
  Layers,
  Cloud,
  Users,
};

export default function CourseDetailPage() {
  const params = useParams();
  const router = useRouter();
  const courseKey = params.id as string;

  const [course, setCourse] = useState<MentoringCourseDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchCourse(courseKey)
      .then(setCourse)
      .catch(() => setCourse(null))
      .finally(() => setLoading(false));
  }, [courseKey]);

  if (loading) return <div className="min-h-screen flex items-center justify-center text-white bg-black">로딩 중…</div>;
  if (!course) return <div className="min-h-screen flex items-center justify-center text-white bg-black">코스를 찾을 수 없습니다.</div>;

  const bgGradients = {
    cyan: 'from-cyan-500 to-blue-500',
    blue: 'from-blue-500 to-indigo-500',
    indigo: 'from-indigo-500 to-purple-500',
  };
  const textGradients = {
    cyan: 'text-cyan-400',
    blue: 'text-blue-400',
    indigo: 'text-indigo-400',
  };

  return (
    <>
      <Header />
      <main className="min-h-screen bg-black">
        {/* Hero Section */}
        <section className="relative pt-32 pb-24 overflow-hidden border-b border-white/10" style={{ background: 'radial-gradient(ellipse at top, #0A192F 0%, #000000 100%)' }}>
          <div className="absolute inset-0 opacity-20" style={{ backgroundImage: 'radial-gradient(circle at 2px 2px, white 1px, transparent 0)', backgroundSize: '40px 40px' }}></div>

          <div className="max-w-6xl mx-auto px-6 relative z-10 flex flex-col md:flex-row items-center justify-between">
            <div className="md:w-1/2 text-left mb-10 md:mb-0">
              <h1 className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight mb-4 whitespace-pre-line">
                {course.title}
              </h1>
              <p className="text-gray-300 text-lg leading-relaxed mb-8 max-w-md">
                {course.subtitle}
              </p>
              <button
                onClick={() => router.push('/apply')}
                className="px-8 py-3 bg-[#0066FF] hover:bg-blue-600 text-white font-bold rounded-lg transition-colors text-sm"
              >
                신청하러 가기
              </button>
            </div>

            <div className="md:w-1/2 flex justify-center md:justify-end">
              <div className="w-64 h-64 rounded-3xl bg-gradient-to-br from-[#00A8FF] to-[#0066FF] flex items-center justify-center p-8 shadow-[0_0_50px_rgba(0,102,255,0.4)] relative border-4 border-[#0F2A52]">
                <div className="w-full h-full border-4 border-white/20 rounded-2xl flex items-center justify-center relative overflow-hidden">
                   <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent"></div>
                   <span className="text-8xl font-bold text-white drop-shadow-lg z-10">{course.iconString}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Info Box Section */}
        <section className="border-b border-white/10 bg-[#0A0A0A]">
          <div className="max-w-6xl mx-auto flex flex-col md:flex-row divide-y md:divide-y-0 md:divide-x divide-white/10 text-gray-400 text-sm">

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">대상</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>신입, 경력(2년 이하) 채용자<br/>(이직자 가능)</li>
                <li>개발 경험이 있는 비전공</li>
                <li>전공자 미취업</li>
              </ul>
            </div>

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">멘토링 방식</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>1대1 온라인 화상 방식</li>
                <li>프로젝트 스터디/코드 리뷰</li>
                <li>녹화본/스크립트 제공</li>
                <li>이력서/면접/포트폴리오 코칭</li>
              </ul>
            </div>

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">멤버십 기간</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>기본 4개월 (주 1회 기준)</li>
                <li>개인 맞춤 학습진도 진행</li>
                <li>월 멤버십 연장 가능</li>
              </ul>
            </div>

            <div className="p-8 flex-1">
              <h3 className="text-gray-500 font-bold mb-4 text-center text-xs">진행 시간</h3>
              <ul className="space-y-2 list-disc list-inside">
                <li>주 1회 1시간 화상 멘토링<br/>(상담/진도 체크 위주)</li>
                <li>메신저 상시 소통</li>
                <li>Github 상시 코드 리뷰</li>
              </ul>
            </div>

          </div>
        </section>

        {/* Sticky Nav */}
        <div className="sticky top-16 z-40 bg-black/80 backdrop-blur-md border-b border-white/10 flex justify-center text-xs font-bold text-gray-400">
           <div className="flex w-full max-w-4xl justify-around py-4">
             <span className="text-white">커리큘럼</span>
             <span className="hover:text-white cursor-pointer" onClick={() => {
                document.getElementById('reviews')?.scrollIntoView({ behavior: 'smooth' });
             }}>후기</span>
             <span className="transition-colors hover:text-white cursor-pointer text-[#0066FF]" onClick={() => router.push('/apply')}>신청하기</span>
           </div>
        </div>

        {/* Content Section */}
        <section className="py-24 bg-black">
          <div className="max-w-4xl mx-auto px-6 text-center">

            <h2 className="text-2xl sm:text-3xl font-extrabold text-white mb-6 whitespace-pre-line">
              {course.descriptionTitle}
            </h2>

            <div className="w-12 h-1 bg-gray-800 mx-auto mb-6"></div>

            <p className="text-gray-400 text-sm leading-relaxed mb-16 whitespace-pre-line">
              {course.descriptionText}
            </p>

            <div className="grid md:grid-cols-2 gap-4">
              {course.boxes.map((box, idx) => {
                const Icon = box.icon ? ICON_MAP[box.icon] : null;
                return (
                  <div key={idx} className={`border border-[#0066FF]/30 bg-[#001433]/50 rounded-xl p-6 text-left relative overflow-hidden ${box.isWide ? 'md:col-span-2 mt-4' : ''}`}>
                    <div className={`absolute top-0 left-0 w-full h-1 bg-gradient-to-r ${(bgGradients as any)[box.color ?? 'cyan']}`}></div>
                    <h4 className={`${(textGradients as any)[box.color ?? 'cyan']} text-sm font-bold mb-4 flex items-center gap-2`}>
                      {Icon && <Icon size={16} />}
                      {box.title}
                    </h4>
                    <div className="flex flex-wrap gap-2 mb-4">
                      {box.tags.map((tag: string) => (
                        <span key={tag} className="px-3 py-1 bg-blue-900/30 border border-blue-800 rounded text-gray-300 text-xs">{tag}</span>
                      ))}
                    </div>
                    <p className="text-gray-400 text-xs leading-relaxed">
                      {box.desc}
                    </p>
                  </div>
                );
              })}
            </div>

          </div>
        </section>

        {/* Reviews Section */}
        <section id="reviews" className="py-24 bg-[#050B14] border-t border-white/5">
          <div className="max-w-4xl mx-auto px-6 text-center">

            <h2 className="text-2xl sm:text-3xl font-extrabold text-white mb-12">
              수강생 후기
            </h2>

            <div className="grid md:grid-cols-2 gap-6 text-left">
              <div className="bg-[#0A111E] p-6 rounded-xl border border-white/10">
                <div className="flex items-center gap-1 mb-4 text-[#0066FF]">
                  {'★★★★★'}
                </div>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">
                  "학원에서 수박 겉핥기로 배웠던 부분들을 바닥까지 파헤쳐 볼 수 있었습니다. 진짜 현업에서 고민하는 문제들을 멘토님이 1:1로 지도해주셔서 결국 좋은 핏의 기업에 합격할 수 있었습니다."
                </p>
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-blue-900 text-xs flex justify-center items-center font-bold text-white">익명</div>
                  <span className="text-gray-500 text-xs">최종 합격자 / 주니어</span>
                </div>
              </div>

              <div className="bg-[#0A111E] p-6 rounded-xl border border-white/10">
                <div className="flex items-center gap-1 mb-4 text-[#0066FF]">
                  {'★★★★★'}
                </div>
                <p className="text-gray-300 text-sm leading-relaxed mb-4">
                  "그동안 해왔던 사이드 프로젝트의 아키텍처를 분리하고 동시성 제어까지 적용해보니 시야가 확 트였습니다. 가장 좋았던 것은 이력서 리뷰와 코드 리뷰를 현직자 시선에서 집중적으로 진행해주신 점이었습니다."
                </p>
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-purple-900 text-xs flex justify-center items-center font-bold text-white">익명</div>
                  <span className="text-gray-500 text-xs">이직 성공 / 실무 2년차</span>
                </div>
              </div>
            </div>

            <div className="mt-20 mb-8 max-w-5xl mx-auto">
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {[
                  {
                    id: 'IMMEDIATE',
                    title: '즉시 시작',
                    badge: '시작일 선택 가능',
                    badgeColor: 'bg-blue-900/30 text-blue-400 border-blue-800/50',
                    desc: '결제 즉시 멘토님 매칭 (시작일 선택 가능)',
                    duration: '4개월',
                    originalPrice: 5980000,
                    price: 4680000,
                    monthly: '390,000원 / 12개월(무이자)',
                  },
                  {
                    id: 'EARLY_BIRD_6',
                    title: '6월 시작 얼리버드',
                    badge: '마감 D-13',
                    badgeColor: 'bg-red-900/30 text-red-400 border-red-800/50',
                    desc: '6월 내 멘토님 매칭, 시작일 선택 가능',
                    duration: '4개월',
                    originalPrice: 4980000,
                    price: 4580000,
                    monthly: '381,000원 / 12개월(무이자)',
                  },
                  {
                    id: 'EARLY_BIRD_7',
                    title: '7월 시작 얼리버드',
                    badge: '마감 D-13',
                    badgeColor: 'bg-orange-900/30 text-orange-400 border-orange-800/50',
                    desc: '7월 내 멘토님 매칭, 시작일 선택 가능',
                    duration: '4개월',
                    originalPrice: 4980000,
                    price: 4480000,
                    monthly: '373,000원 / 12개월(무이자)',
                  }
                ].map((plan) => (
                  <div key={plan.id} className="bg-[#0a111a] border border-white/10 rounded-2xl p-8 flex flex-col text-left transition-all hover:border-blue-500/50 hover:shadow-2xl hover:shadow-blue-500/10">
                    <div className="flex justify-between items-start mb-6">
                      <h3 className="text-xl font-bold text-white tracking-tight">{plan.title}</h3>
                      <span className={`px-2.5 py-1 rounded-md text-[10px] font-bold border ${plan.badgeColor}`}>
                        {plan.badge}
                      </span>
                    </div>

                    <div className="space-y-4 mb-8 flex-grow">
                      <div>
                        <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-1">시작</p>
                        <p className="text-sm text-gray-300 font-medium leading-relaxed">{plan.desc}</p>
                      </div>
                      <div>
                        <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-1">기간</p>
                        <p className="text-sm text-gray-300 font-bold">{plan.duration}</p>
                      </div>
                    </div>

                    <div className="pt-6 border-t border-white/5 mb-6">
                      <p className="text-xs text-gray-500 line-through mb-1">{(plan.originalPrice).toLocaleString()}원</p>
                      <p className="text-3xl font-black text-white tracking-tighter">
                        {(plan.price).toLocaleString()}<span className="text-sm font-bold text-gray-400 ml-0.5 tracking-normal">원</span>
                      </p>
                    </div>

                    <div className="p-4 bg-white/5 rounded-xl mb-6">
                      <p className="text-xs text-gray-300 mb-1 flex items-center gap-1">
                        <span className="w-1 h-1 rounded-full bg-blue-500"></span>
                        {plan.monthly}
                      </p>
                      <p className="text-[10px] text-gray-500 leading-tight">
                        *최대 할인 적용시 금액<br/>
                        *12개월 무이자 할부는 일부 카드사만 해당됩니다.
                      </p>
                    </div>

                    <button
                      onClick={() => router.push('/apply')}
                      className="w-full py-4 rounded-xl font-bold text-sm bg-blue-600/20 text-blue-400 border border-blue-500/30 hover:bg-blue-600 hover:text-white transition-all"
                    >
                      해당 플랜으로 지원하기
                    </button>
                  </div>
                ))}
              </div>
            </div>

          </div>
        </section>

      </main>
      <Footer />
    </>
  );
}

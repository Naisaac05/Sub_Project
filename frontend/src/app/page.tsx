'use client';

import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import {
  ArrowRight,
  ClipboardCheck,
  Users,
  CalendarCheck,
  Sparkles,
  TrendingUp,
  Shield,
  MessageSquare,
  ChevronRight,
  Star,
  Zap,
  Target,
} from 'lucide-react';

/* ─────────────────── Hero Section ─────────────────── */
function HeroSection() {
  return (
    <section className="relative min-h-screen hero-gradient grid-pattern overflow-hidden flex items-center">
      {/* Decorative orbs */}
      <div className="orb w-[500px] h-[500px] bg-blue-600/20 -top-40 -left-40" />
      <div className="orb w-[400px] h-[400px] bg-violet-600/15 top-1/3 -right-32" />
      <div className="orb w-[300px] h-[300px] bg-cyan-500/10 bottom-20 left-1/4" />

      <div className="relative z-10 max-w-7xl mx-auto px-6 pt-32 pb-24">
        <div className="max-w-3xl">
          {/* Badge */}
          <div className="animate-fade-in-up inline-flex items-center gap-2 px-4 py-2 rounded-full
                        glass-card text-cyan-400 text-sm font-medium mb-8">
            <Sparkles size={14} />
            <span>실력 기반 멘토 매칭 플랫폼</span>
          </div>

          {/* Headline */}
          <h1 className="animate-fade-in-up delay-100 text-4xl sm:text-5xl lg:text-6xl font-extrabold
                       text-white leading-[1.15] tracking-tight mb-6">
            당신의 실력에 맞는
            <br />
            <span className="text-gradient">최적의 멘토</span>를
            <br />
            매칭합니다
          </h1>

          {/* Subtitle */}
          <p className="animate-fade-in-up delay-200 text-lg sm:text-xl text-gray-400 leading-relaxed mb-10 max-w-xl">
            실력 테스트로 나의 수준을 객관적으로 파악하고,
            현직 개발자 멘토와 1:1 맞춤 멘토링으로 빠르게 성장하세요.
          </p>

          {/* CTAs */}
          <div className="animate-fade-in-up delay-300 flex flex-col sm:flex-row gap-4">
            <Link
              href="/auth/signup"
              className="shimmer inline-flex items-center justify-center gap-2 px-8 py-4 rounded-xl
                       text-white font-bold text-lg
                       bg-gradient-to-r from-blue-600 via-blue-500 to-cyan-500
                       shadow-2xl shadow-blue-600/25
                       hover:shadow-blue-500/40 hover:scale-[1.02]
                       transition-all duration-300"
            >
              무료로 시작하기
              <ArrowRight size={20} />
            </Link>
            <Link
              href="/tests"
              className="inline-flex items-center justify-center gap-2 px-8 py-4 rounded-xl
                       text-gray-300 font-semibold text-lg
                       border border-white/10 hover:border-white/25
                       hover:bg-white/5 hover:text-white
                       transition-all duration-300"
            >
              실력 테스트 해보기
            </Link>
          </div>

          {/* Trust bar */}
          <div className="animate-fade-in-up delay-500 mt-14 flex items-center gap-8 text-gray-500 text-sm">
            <div className="flex items-center gap-2">
              <Shield size={16} className="text-green-500" />
              <span>무료 실력 테스트</span>
            </div>
            <div className="flex items-center gap-2">
              <Star size={16} className="text-amber-500" />
              <span>검증된 현직 멘토</span>
            </div>
            <div className="flex items-center gap-2">
              <Zap size={16} className="text-cyan-500" />
              <span>AI 기반 매칭</span>
            </div>
          </div>
        </div>

        {/* Hero visual - floating cards */}
        <div className="hidden lg:block absolute right-12 top-1/2 -translate-y-1/2">
          <div className="relative w-[380px] h-[420px]">
            {/* Card 1 - Mentor profile */}
            <div className="animate-float absolute top-0 right-0 w-72 glass-card rounded-2xl p-5
                          shadow-2xl shadow-black/20" style={{ animationDelay: '0s' }}>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-11 h-11 rounded-full bg-gradient-to-br from-violet-500 to-pink-500
                             flex items-center justify-center text-white font-bold text-sm">JK</div>
                <div>
                  <p className="text-white font-semibold text-sm">김재현 멘토</p>
                  <p className="text-gray-500 text-xs">네이버 / 8년차 백엔드</p>
                </div>
              </div>
              <div className="flex gap-2 mb-3">
                <span className="px-2.5 py-1 text-xs rounded-md bg-blue-500/10 text-blue-400 font-medium">Java</span>
                <span className="px-2.5 py-1 text-xs rounded-md bg-green-500/10 text-green-400 font-medium">Spring</span>
                <span className="px-2.5 py-1 text-xs rounded-md bg-purple-500/10 text-purple-400 font-medium">MSA</span>
              </div>
              <div className="flex items-center gap-1">
                {[1,2,3,4,5].map(i => (
                  <Star key={i} size={12} className="text-amber-400 fill-amber-400" />
                ))}
                <span className="text-gray-500 text-xs ml-1">4.9 (127)</span>
              </div>
            </div>

            {/* Card 2 - Match result */}
            <div className="animate-float absolute bottom-8 left-0 w-64 glass-card rounded-2xl p-5
                          shadow-2xl shadow-black/20" style={{ animationDelay: '2s' }}>
              <div className="flex items-center gap-2 mb-3">
                <Target size={16} className="text-cyan-400" />
                <p className="text-white font-semibold text-sm">매칭 완료!</p>
              </div>
              <div className="space-y-2">
                <div className="flex justify-between text-xs">
                  <span className="text-gray-500">적합도</span>
                  <span className="text-cyan-400 font-semibold">96%</span>
                </div>
                <div className="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
                  <div className="h-full w-[96%] bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full" />
                </div>
                <p className="text-gray-500 text-xs">Spring Boot 심화 + 시스템 설계</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom fade */}
      <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-white to-transparent" />
    </section>
  );
}

/* ─────────────────── Stats Section ─────────────────── */
function StatsSection() {
  const stats = [
    { value: '2,400+', label: '등록 멘티', color: 'from-blue-500 to-blue-600' },
    { value: '320+', label: '검증된 멘토', color: 'from-violet-500 to-purple-600' },
    { value: '95%', label: '매칭 만족도', color: 'from-cyan-500 to-teal-500' },
    { value: '4.8', label: '평균 평점', suffix: '/ 5.0', color: 'from-amber-500 to-orange-500' },
  ];

  return (
    <section className="relative py-20 bg-white">
      <div className="max-w-7xl mx-auto px-6">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
          {stats.map((stat, i) => (
            <div
              key={stat.label}
              className="stat-card text-center p-8 rounded-2xl bg-gray-50 border border-gray-100"
            >
              <p className={`text-3xl sm:text-4xl font-extrabold bg-gradient-to-br ${stat.color}
                          bg-clip-text text-transparent font-[Outfit]`}>
                {stat.value}
              </p>
              {stat.suffix && (
                <span className="text-gray-400 text-sm font-medium">{stat.suffix}</span>
              )}
              <p className="mt-2 text-gray-500 text-sm font-medium">{stat.label}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────── How It Works ─────────────────── */
function HowItWorksSection() {
  const steps = [
    {
      icon: ClipboardCheck,
      step: '01',
      title: '실력 테스트',
      desc: '분야별 실력 테스트를 통해 현재 나의 수준을 객관적으로 진단받습니다.',
      accent: 'text-blue-500',
      bg: 'bg-blue-50',
      border: 'border-blue-100',
    },
    {
      icon: Users,
      step: '02',
      title: '멘토 매칭',
      desc: '테스트 결과를 바탕으로 나에게 가장 적합한 현직 개발자 멘토를 추천받습니다.',
      accent: 'text-violet-500',
      bg: 'bg-violet-50',
      border: 'border-violet-100',
    },
    {
      icon: CalendarCheck,
      step: '03',
      title: '1:1 멘토링',
      desc: 'Google Meet으로 멘토와 1:1 세션을 진행하며 실력을 키워갑니다.',
      accent: 'text-cyan-500',
      bg: 'bg-cyan-50',
      border: 'border-cyan-100',
    },
  ];

  return (
    <section className="py-24 bg-white">
      <div className="max-w-7xl mx-auto px-6">
        {/* Section header */}
        <div className="text-center mb-16">
          <span className="inline-block px-4 py-1.5 rounded-full bg-blue-50 text-blue-600
                        text-xs font-bold tracking-wider uppercase mb-4">
            How It Works
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-4">
            3단계로 시작하는 성장
          </h2>
          <p className="text-gray-500 text-lg max-w-lg mx-auto">
            복잡한 과정 없이, 테스트부터 멘토링까지 한번에.
          </p>
        </div>

        {/* Steps */}
        <div className="grid md:grid-cols-3 gap-8">
          {steps.map((s, i) => (
            <div key={s.step} className="relative group">
              {/* Connector line */}
              {i < steps.length - 1 && (
                <div className="hidden md:block absolute top-16 left-[60%] w-[calc(100%-20%)] h-[2px]
                             bg-gradient-to-r from-gray-200 to-gray-100 z-0" />
              )}
              <div className={`relative z-10 p-8 rounded-2xl border ${s.border} ${s.bg}/50
                            hover:shadow-xl hover:shadow-gray-100 transition-all duration-300`}>
                <div className={`w-14 h-14 rounded-xl ${s.bg} flex items-center justify-center mb-5`}>
                  <s.icon size={26} className={s.accent} />
                </div>
                <span className={`text-xs font-bold ${s.accent} tracking-wider`}>STEP {s.step}</span>
                <h3 className="text-xl font-bold text-gray-900 mt-2 mb-3">{s.title}</h3>
                <p className="text-gray-500 text-sm leading-relaxed">{s.desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────── Features Section ─────────────────── */
function FeaturesSection() {
  const features = [
    {
      icon: Target,
      title: 'AI 기반 매칭 알고리즘',
      desc: '테스트 결과, 학습 목표, 선호 스타일을 분석하여 최적의 멘토를 자동으로 추천합니다.',
    },
    {
      icon: TrendingUp,
      title: '체계적 실력 분석',
      desc: '알고리즘, 시스템 설계, CS 기초 등 분야별 실력을 세밀하게 진단하고 취약점을 파악합니다.',
    },
    {
      icon: CalendarCheck,
      title: 'Google 캘린더 연동',
      desc: '매칭 후 자동으로 Google Calendar에 일정이 등록되고, Meet 링크가 생성됩니다.',
    },
    {
      icon: Shield,
      title: '검증된 멘토진',
      desc: '네이버, 카카오, 삼성 등 대기업 현직 개발자만 멘토로 활동합니다. 엄격한 심사를 통과합니다.',
    },
    {
      icon: MessageSquare,
      title: '멘토링 후기 & 커뮤니티',
      desc: '실제 멘티들의 솔직한 후기를 확인하고, 커뮤니티에서 함께 성장하세요.',
    },
    {
      icon: Sparkles,
      title: '합리적인 가격',
      desc: '토스페이먼츠 결제로 안전하게. 만족하지 못하면 100% 환불을 보장합니다.',
    },
  ];

  return (
    <section className="py-24 bg-gray-50">
      <div className="max-w-7xl mx-auto px-6">
        <div className="text-center mb-16">
          <span className="inline-block px-4 py-1.5 rounded-full bg-violet-50 text-violet-600
                        text-xs font-bold tracking-wider uppercase mb-4">
            Features
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-4">
            왜 DevMatch인가요?
          </h2>
          <p className="text-gray-500 text-lg max-w-lg mx-auto">
            실력 진단부터 멘토링까지, 성장에 필요한 모든 것.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((f) => (
            <div
              key={f.title}
              className="feature-card p-7 rounded-2xl bg-white border border-gray-100 cursor-default"
            >
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-50 to-violet-50
                          flex items-center justify-center mb-5">
                <f.icon size={22} className="text-blue-600" />
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">{f.title}</h3>
              <p className="text-gray-500 text-sm leading-relaxed">{f.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────── Mentor Preview ─────────────────── */
function MentorPreviewSection() {
  const mentors = [
    { name: '김재현', company: '네이버', role: '백엔드 8년차', tags: ['Java', 'Spring', 'MSA'], initials: 'JK', gradient: 'from-violet-500 to-pink-500' },
    { name: '이수진', company: '카카오', role: '프론트엔드 6년차', tags: ['React', 'Next.js', 'TS'], initials: 'SJ', gradient: 'from-blue-500 to-cyan-400' },
    { name: '박민수', company: '토스', role: '풀스택 7년차', tags: ['Node.js', 'React', 'AWS'], initials: 'MS', gradient: 'from-amber-500 to-orange-500' },
    { name: '정하은', company: '삼성전자', role: '백엔드 5년차', tags: ['Python', 'Django', 'K8s'], initials: 'HE', gradient: 'from-emerald-500 to-teal-500' },
  ];

  return (
    <section className="py-24 bg-white">
      <div className="max-w-7xl mx-auto px-6">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end gap-4 mb-12">
          <div>
            <span className="inline-block px-4 py-1.5 rounded-full bg-cyan-50 text-cyan-600
                          text-xs font-bold tracking-wider uppercase mb-4">
              Mentors
            </span>
            <h2 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight">
              검증된 현직 멘토진
            </h2>
          </div>
          <Link
            href="/mentors"
            className="inline-flex items-center gap-1 text-blue-600 font-semibold text-sm
                     hover:gap-2 transition-all duration-300"
          >
            전체 멘토 보기 <ChevronRight size={16} />
          </Link>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {mentors.map((m) => (
            <div
              key={m.name}
              className="group p-6 rounded-2xl border border-gray-100 bg-white
                       hover:border-blue-100 hover:shadow-xl hover:shadow-blue-50
                       transition-all duration-300 cursor-pointer"
            >
              <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${m.gradient}
                           flex items-center justify-center text-white font-bold text-lg mb-5
                           group-hover:scale-110 transition-transform duration-300`}>
                {m.initials}
              </div>
              <h3 className="text-lg font-bold text-gray-900">{m.name} 멘토</h3>
              <p className="text-gray-500 text-sm mt-1">{m.company} / {m.role}</p>
              <div className="flex flex-wrap gap-1.5 mt-4">
                {m.tags.map(tag => (
                  <span key={tag} className="px-2.5 py-1 text-xs rounded-md bg-gray-50 text-gray-600
                                          border border-gray-100 font-medium">
                    {tag}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────── Testimonials ─────────────────── */
function TestimonialsSection() {
  const reviews = [
    {
      name: '이정훈',
      role: '주니어 백엔드 개발자',
      text: '실력 테스트에서 취약한 부분을 정확히 짚어주고, 그에 맞는 멘토를 매칭해줘서 3개월 만에 네이버 최종 합격했습니다.',
      rating: 5,
    },
    {
      name: '김서연',
      role: '프론트엔드 전환 희망',
      text: '비전공자로 막막했는데, 멘토님이 로드맵부터 모의면접까지 체계적으로 도와주셨어요. 카카오 입사 성공!',
      rating: 5,
    },
    {
      name: '박도현',
      role: '3년차 풀스택 개발자',
      text: '시스템 설계 역량이 부족했는데, 멘토링을 통해 대규모 트래픽 처리 능력을 키워 토스로 이직했습니다.',
      rating: 5,
    },
  ];

  return (
    <section className="py-24 bg-gray-50">
      <div className="max-w-7xl mx-auto px-6">
        <div className="text-center mb-16">
          <span className="inline-block px-4 py-1.5 rounded-full bg-amber-50 text-amber-600
                        text-xs font-bold tracking-wider uppercase mb-4">
            Reviews
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-4">
            멘티들의 실제 후기
          </h2>
        </div>

        <div className="grid md:grid-cols-3 gap-6">
          {reviews.map((r) => (
            <div
              key={r.name}
              className="p-7 rounded-2xl bg-white border border-gray-100
                       hover:shadow-lg hover:shadow-gray-100 transition-all duration-300"
            >
              <div className="flex items-center gap-1 mb-4">
                {Array.from({ length: r.rating }).map((_, i) => (
                  <Star key={i} size={14} className="text-amber-400 fill-amber-400" />
                ))}
              </div>
              <p className="text-gray-700 leading-relaxed text-sm mb-6">&ldquo;{r.text}&rdquo;</p>
              <div className="flex items-center gap-3 pt-4 border-t border-gray-50">
                <div className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center
                             text-gray-600 font-semibold text-xs">
                  {r.name[0]}
                </div>
                <div>
                  <p className="text-sm font-semibold text-gray-900">{r.name}</p>
                  <p className="text-xs text-gray-500">{r.role}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─────────────────── Final CTA ─────────────────── */
function CTASection() {
  return (
    <section className="relative py-28 hero-gradient grid-pattern overflow-hidden">
      <div className="orb w-[400px] h-[400px] bg-blue-600/20 -top-20 -right-20" />
      <div className="orb w-[300px] h-[300px] bg-cyan-500/15 bottom-0 left-10" />

      <div className="relative z-10 max-w-3xl mx-auto px-6 text-center">
        <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-white tracking-tight mb-6">
          지금 바로<br />
          <span className="text-gradient">성장을 시작</span>하세요
        </h2>
        <p className="text-gray-400 text-lg mb-10 max-w-md mx-auto">
          무료 실력 테스트로 나의 수준을 확인하고,
          나에게 딱 맞는 멘토를 만나보세요.
        </p>
        <div className="flex flex-col sm:flex-row gap-4 justify-center">
          <Link
            href="/auth/signup"
            className="shimmer inline-flex items-center justify-center gap-2 px-10 py-4 rounded-xl
                     text-white font-bold text-lg
                     bg-gradient-to-r from-blue-600 via-blue-500 to-cyan-500
                     shadow-2xl shadow-blue-600/25
                     hover:shadow-blue-500/40 hover:scale-[1.02]
                     transition-all duration-300"
          >
            무료로 시작하기
            <ArrowRight size={20} />
          </Link>
          <Link
            href="/tests"
            className="inline-flex items-center justify-center gap-2 px-10 py-4 rounded-xl
                     text-gray-300 font-semibold text-lg
                     border border-white/10 hover:border-white/25
                     hover:bg-white/5 hover:text-white
                     transition-all duration-300"
          >
            실력 테스트 먼저 해보기
          </Link>
        </div>
      </div>
    </section>
  );
}

/* ─────────────────── Page ─────────────────── */
export default function HomePage() {
  return (
    <>
      <Header />
      <main>
        <HeroSection />
        <StatsSection />
        <HowItWorksSection />
        <FeaturesSection />
        <MentorPreviewSection />
        <TestimonialsSection />
        <CTASection />
      </main>
      <Footer />
    </>
  );
}

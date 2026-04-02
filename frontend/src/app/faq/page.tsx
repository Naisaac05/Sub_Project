'use client';

import { useState } from 'react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import Link from 'next/link';
import { ChevronDown, HelpCircle, ArrowRight, MessageCircle, Mail } from 'lucide-react';

interface FAQItem {
  question: string;
  answer: string;
  category: string;
}

const faqs: FAQItem[] = [
  {
    category: '서비스 소개',
    question: 'DevMatch는 어떤 서비스인가요?',
    answer: 'DevMatch는 실력 기반 멘토 매칭 플랫폼입니다. 분야별 실력 테스트를 통해 현재 수준을 객관적으로 진단하고, AI 매칭 알고리즘으로 나에게 가장 적합한 현직 개발자 멘토를 추천해드립니다. 1:1 맞춤 멘토링으로 빠르게 성장할 수 있습니다.',
  },
  {
    category: '서비스 소개',
    question: '멘토는 어떤 분들인가요?',
    answer: '네이버, 카카오, 토스, 삼성전자 등 대기업 현직 개발자들이 멘토로 활동하고 있습니다. 모든 멘토는 경력 확인, 기술 역량 검증, 멘토링 역량 면접 등 엄격한 심사 과정을 거쳐 선발됩니다.',
  },
  {
    category: '실력 테스트',
    question: '실력 테스트는 무료인가요?',
    answer: '네, 실력 테스트는 완전 무료입니다. 회원가입 후 바로 원하는 분야의 테스트를 응시할 수 있습니다. 테스트 결과를 바탕으로 취약점 분석과 멘토 추천을 받으실 수 있습니다.',
  },
  {
    category: '실력 테스트',
    question: '테스트 결과는 어떻게 활용되나요?',
    answer: '테스트 결과는 분야별 점수와 취약점 분석을 제공합니다. 이 결과를 기반으로 AI가 최적의 멘토를 추천하며, 멘토링 시 멘토가 참고하여 맞춤형 커리큘럼을 설계합니다.',
  },
  {
    category: '멘토링',
    question: '멘토링은 어떤 방식으로 진행되나요?',
    answer: '매칭이 완료되면 Google Calendar에 자동으로 일정이 등록되고, Google Meet 링크가 생성됩니다. 정해진 시간에 멘토와 1:1 화상 멘토링을 진행하며, 코드 리뷰, 모의 면접, 기술 상담 등 다양한 형태로 진행됩니다.',
  },
  {
    category: '멘토링',
    question: '멘토링 기간과 횟수는 어떻게 되나요?',
    answer: '기본 멘토링은 주 1회, 4주 패키지로 구성됩니다. 필요에 따라 8주, 12주 장기 패키지도 선택 가능하며, 단회 상담도 가능합니다. 멘토와 협의하여 일정을 유연하게 조정할 수 있습니다.',
  },
  {
    category: '결제/환불',
    question: '결제는 어떻게 하나요?',
    answer: '토스페이먼츠를 통해 안전하게 결제할 수 있습니다. 신용카드, 체크카드, 계좌이체, 간편결제(토스페이, 카카오페이 등)를 지원합니다.',
  },
  {
    category: '결제/환불',
    question: '환불 정책은 어떻게 되나요?',
    answer: '첫 멘토링 세션 진행 전이라면 100% 환불이 가능합니다. 첫 세션 이후에는 남은 세션 수에 비례하여 환불해드립니다. 멘토링 품질에 만족하지 못하시면 별도의 불만족 환불 제도도 운영하고 있습니다.',
  },
  {
    category: '멘토 지원',
    question: '멘토로 활동하고 싶은데 어떻게 지원하나요?',
    answer: '회원가입 시 "멘토" 역할을 선택하거나, 마이페이지에서 멘토 지원서를 작성할 수 있습니다. 3년 이상의 실무 경력이 필요하며, 경력 확인 및 기술 면접을 통과해야 합니다.',
  },
];

const faqCategories = ['전체', '서비스 소개', '실력 테스트', '멘토링', '결제/환불', '멘토 지원'];

export default function FaqPage() {
  const [selectedCategory, setSelectedCategory] = useState('전체');
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  const filtered = selectedCategory === '전체'
    ? faqs
    : faqs.filter(f => f.category === selectedCategory);

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        {/* Page Header */}
        <section className="relative hero-gradient grid-pattern pt-28 pb-16 overflow-hidden">
          <div className="orb w-[350px] h-[350px] bg-blue-600/15 top-10 -right-20" />
          <div className="max-w-7xl mx-auto px-6 relative z-10">
            <span className="inline-block px-4 py-1.5 rounded-full glass-card text-cyan-400
                          text-xs font-bold tracking-wider uppercase mb-4">
              FAQ
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight mb-3">
              자주 묻는 질문
            </h1>
            <p className="text-gray-400 text-lg max-w-xl">
              DevMatch에 대해 궁금한 점을 확인하세요.
            </p>
          </div>
        </section>

        <section className="max-w-3xl mx-auto px-6 py-12">
          {/* Category Filter */}
          <div className="flex items-center gap-2 overflow-x-auto pb-4 mb-8 scrollbar-hide">
            {faqCategories.map(cat => (
              <button
                key={cat}
                onClick={() => { setSelectedCategory(cat); setOpenIndex(null); }}
                className={`px-4 py-2 rounded-lg text-sm font-semibold whitespace-nowrap
                          transition-all duration-200 ${
                  selectedCategory === cat
                    ? 'bg-gray-900 text-white shadow-lg shadow-gray-900/10'
                    : 'bg-white text-gray-500 border border-gray-200 hover:text-gray-900'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          {/* FAQ Accordion */}
          <div className="space-y-3">
            {filtered.map((faq, i) => {
              const isOpen = openIndex === i;
              return (
                <div
                  key={i}
                  className={`bg-white rounded-xl border transition-all duration-300 ${
                    isOpen ? 'border-blue-200 shadow-lg shadow-blue-50' : 'border-gray-100'
                  }`}
                >
                  <button
                    onClick={() => setOpenIndex(isOpen ? null : i)}
                    className="w-full flex items-start gap-4 p-5 text-left"
                  >
                    <HelpCircle size={20} className={`mt-0.5 flex-shrink-0 transition-colors duration-200 ${
                      isOpen ? 'text-blue-500' : 'text-gray-400'
                    }`} />
                    <div className="flex-1">
                      <span className="text-xs font-semibold text-blue-500 mb-1 block">{faq.category}</span>
                      <h3 className={`font-semibold transition-colors duration-200 ${
                        isOpen ? 'text-blue-600' : 'text-gray-900'
                      }`}>
                        {faq.question}
                      </h3>
                    </div>
                    <ChevronDown
                      size={18}
                      className={`mt-1 flex-shrink-0 text-gray-400 transition-transform duration-300 ${
                        isOpen ? 'rotate-180 text-blue-500' : ''
                      }`}
                    />
                  </button>
                  <div className={`overflow-hidden transition-all duration-300 ${
                    isOpen ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'
                  }`}>
                    <div className="px-5 pb-5 pl-14">
                      <p className="text-gray-600 text-sm leading-relaxed">{faq.answer}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* Contact Banner */}
        <section className="max-w-3xl mx-auto px-6 pb-16">
          <div className="bg-white rounded-2xl border border-gray-100 p-8 text-center">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-blue-50 to-violet-50
                        flex items-center justify-center mx-auto mb-5">
              <MessageCircle size={24} className="text-blue-600" />
            </div>
            <h3 className="text-xl font-bold text-gray-900 mb-2">
              원하는 답을 찾지 못하셨나요?
            </h3>
            <p className="text-gray-500 text-sm mb-6">
              고객센터로 문의해주시면 빠르게 답변드리겠습니다.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
              <a
                href="mailto:support@devmatch.kr"
                className="flex items-center gap-2 px-6 py-3 rounded-xl
                         bg-gradient-to-r from-blue-600 to-blue-500
                         text-white font-semibold text-sm shadow-lg shadow-blue-600/20
                         hover:shadow-blue-500/30 transition-all duration-300"
              >
                <Mail size={16} />
                이메일 문의
              </a>
              <Link
                href="/community"
                className="flex items-center gap-2 px-6 py-3 rounded-xl
                         border border-gray-200 text-gray-700 font-semibold text-sm
                         hover:bg-gray-50 transition-all duration-200"
              >
                커뮤니티에 질문하기
                <ArrowRight size={14} />
              </Link>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}

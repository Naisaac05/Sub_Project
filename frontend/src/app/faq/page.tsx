'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import Link from 'next/link';
import { ChevronDown, HelpCircle, ArrowRight, MessageCircle, Mail } from 'lucide-react';
import { fetchPublicFaqs, type Faq, type FaqCategory, CATEGORY_LABEL, CATEGORY_ORDER } from '@/lib/faqs';

type Filter = 'ALL' | FaqCategory;

export default function FaqPage() {
  const [faqs, setFaqs] = useState<Faq[] | null>(null);
  const [error, setError] = useState(false);
  const [selected, setSelected] = useState<Filter>('ALL');
  const [openId, setOpenId] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      setError(false);
      const res = await fetchPublicFaqs();
      setFaqs(res);
    } catch {
      setError(true);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => {
    if (!faqs) return [];
    return selected === 'ALL' ? faqs : faqs.filter((f) => f.category === selected);
  }, [faqs, selected]);

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
            {(['ALL', ...CATEGORY_ORDER] as Filter[]).map((c) => (
              <button
                key={c}
                onClick={() => { setSelected(c); setOpenId(null); }}
                className={`px-4 py-2 rounded-lg text-sm font-semibold whitespace-nowrap
                          transition-all duration-200 ${
                  selected === c
                    ? 'bg-gray-900 text-white shadow-lg shadow-gray-900/10'
                    : 'bg-white text-gray-500 border border-gray-200 hover:text-gray-900'
                }`}
              >
                {c === 'ALL' ? '전체' : CATEGORY_LABEL[c]}
              </button>
            ))}
          </div>

          {/* FAQ Accordion */}
          {error ? (
            <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700">
              <p className="mb-3">FAQ 를 불러오지 못했습니다.</p>
              <button
                type="button"
                onClick={load}
                className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100"
              >
                재시도
              </button>
            </div>
          ) : faqs === null ? (
            <div className="space-y-3">
              {[0,1,2,3].map((i) => (
                <div key={i} className="h-[72px] animate-pulse rounded-xl border border-gray-100 bg-white" />
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <p className="text-center text-sm text-gray-400 py-12">
              표시할 FAQ 가 없습니다.
            </p>
          ) : (
            <div className="space-y-3">
              {filtered.map((faq) => {
                const isOpen = openId === faq.id;
                return (
                  <div
                    key={faq.id}
                    className={`bg-white rounded-xl border transition-all duration-300 ${
                      isOpen ? 'border-blue-200 shadow-lg shadow-blue-50' : 'border-gray-100'
                    }`}
                  >
                    <button
                      onClick={() => setOpenId(isOpen ? null : faq.id)}
                      className="w-full flex items-start gap-4 p-5 text-left"
                    >
                      <HelpCircle size={20} className={`mt-0.5 flex-shrink-0 transition-colors duration-200 ${
                        isOpen ? 'text-blue-500' : 'text-gray-400'
                      }`} />
                      <div className="flex-1">
                        <span className="text-xs font-semibold text-blue-500 mb-1 block">
                          {CATEGORY_LABEL[faq.category]}
                        </span>
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
                        <p className="text-gray-600 text-sm leading-relaxed whitespace-pre-line">
                          {faq.answer}
                        </p>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* Contact Banner — 기존 그대로 */}
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

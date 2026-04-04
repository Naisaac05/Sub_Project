'use client';

import { useState, useEffect, useRef } from 'react';
import { loadPaymentWidget, PaymentWidgetInstance } from '@tosspayments/payment-widget-sdk';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import {
  ArrowRight,
  ArrowLeft,
  Check,
  Sparkles,
  Zap,
  BookOpen,
  Code2,
  Layers,
  Clock,
  CreditCard,
  Tag,
  ChevronDown,
} from 'lucide-react';

/* ───── 가격 정책 상수 (백엔드와 동기화) ───── */
const BASE_PRICE = 990_000;
const RENEWAL_PRICES = [990_000, 990_000, 890_000, 790_000];
const BUNDLE_DISCOUNTS: Record<number, number> = {
  1: 0, 2: 0, 3: 0.05, 4: 0.10, 5: 0.15, 6: 0.20,
};

function getUnitPrice(renewal: number) {
  if (renewal >= RENEWAL_PRICES.length) return RENEWAL_PRICES[RENEWAL_PRICES.length - 1];
  return RENEWAL_PRICES[renewal];
}
function getBundleRate(months: number) {
  if (months >= 6) return 0.20;
  return BUNDLE_DISCOUNTS[months] ?? 0;
}
function formatPrice(n: number) {
  return n.toLocaleString('ko-KR');
}

/* ───── 옵션 데이터 ───── */
const LEVELS = [
  { value: 'BEGINNER', label: '입문', desc: '프로그래밍을 처음 시작하거나, 기초 문법을 배우는 단계', icon: BookOpen, color: 'text-emerald-500', bg: 'bg-emerald-50', border: 'border-emerald-200' },
  { value: 'INTERMEDIATE', label: '중급', desc: '간단한 프로젝트를 만들 수 있고, 기본 개념을 이해하는 단계', icon: Code2, color: 'text-blue-500', bg: 'bg-blue-50', border: 'border-blue-200' },
  { value: 'ADVANCED', label: '심화', desc: '실무 경험이 있으며, 아키텍처와 성능 최적화에 관심 있는 단계', icon: Layers, color: 'text-violet-500', bg: 'bg-violet-50', border: 'border-violet-200' },
];

const STACKS = [
  'Java', 'Spring Boot', 'Python', 'Django', 'JavaScript', 'TypeScript',
  'React', 'Next.js', 'Vue.js', 'Node.js', 'Go', 'Rust',
  'AWS', 'Docker', 'Kubernetes', 'MySQL', 'PostgreSQL', 'MongoDB',
];

const CATEGORIES = [
  { value: 'backend', label: 'Backend 개발' },
  { value: 'frontend', label: 'Frontend 개발' },
  { value: 'fullstack', label: 'Fullstack 개발' },
  { value: 'devops', label: 'DevOps / 인프라' },
  { value: 'mobile', label: '모바일 (iOS/Android)' },
  { value: 'data', label: 'Data / AI / ML' },
];

/* ────────────────────────────────────────────
   STEP 1: 기본 정보 입력
   ──────────────────────────────────────────── */
function StepBasicInfo({
  form, setForm, onNext,
}: {
  form: any; setForm: (f: any) => void; onNext: () => void;
}) {
  const isValid = form.currentLevel && form.targetTechStack.length > 0 && form.careerGoal.trim() && form.category;

  return (
    <div className="animate-fade-in-up">
      {/* 현재 레벨 */}
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">현재 실력 수준</label>
        <div className="grid sm:grid-cols-3 gap-4">
          {LEVELS.map((lv) => {
            const selected = form.currentLevel === lv.value;
            return (
              <button
                key={lv.value}
                onClick={() => setForm({ ...form, currentLevel: lv.value })}
                className={`relative p-5 rounded-2xl border-2 text-left transition-all duration-300
                  ${selected
                    ? `${lv.border} ${lv.bg} shadow-lg scale-[1.02]`
                    : 'border-gray-100 bg-white hover:border-gray-200 hover:shadow-md'
                  }`}
              >
                {selected && (
                  <div className="absolute top-3 right-3 w-6 h-6 rounded-full bg-white shadow flex items-center justify-center">
                    <Check size={14} className={lv.color} />
                  </div>
                )}
                <lv.icon size={24} className={selected ? lv.color : 'text-gray-400'} />
                <p className={`mt-3 font-bold ${selected ? 'text-gray-900' : 'text-gray-700'}`}>{lv.label}</p>
                <p className="mt-1 text-xs text-gray-500 leading-relaxed">{lv.desc}</p>
              </button>
            );
          })}
        </div>
      </div>

      {/* 카테고리 */}
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">희망 코스</label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {CATEGORIES.map((cat) => {
            const selected = form.category === cat.value;
            return (
              <button
                key={cat.value}
                onClick={() => setForm({ ...form, category: cat.value })}
                className={`px-4 py-3 rounded-xl border text-sm font-medium transition-all duration-200
                  ${selected
                    ? 'border-blue-300 bg-blue-50 text-blue-700 shadow-sm'
                    : 'border-gray-100 text-gray-600 hover:border-gray-200 hover:bg-gray-50'
                  }`}
              >
                {cat.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* 기술 스택 */}
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-2">목표 기술 스택</label>
        <p className="text-xs text-gray-500 mb-4">배우고 싶은 기술을 선택해주세요 (복수 선택)</p>
        <div className="flex flex-wrap gap-2">
          {STACKS.map((s) => {
            const selected = form.targetTechStack.includes(s);
            return (
              <button
                key={s}
                onClick={() => {
                  const updated = selected
                    ? form.targetTechStack.filter((t: string) => t !== s)
                    : [...form.targetTechStack, s];
                  setForm({ ...form, targetTechStack: updated });
                }}
                className={`px-3.5 py-2 rounded-lg text-sm font-medium transition-all duration-200
                  ${selected
                    ? 'bg-gradient-to-r from-blue-500 to-cyan-500 text-white shadow-md shadow-blue-500/20'
                    : 'bg-gray-50 text-gray-600 border border-gray-100 hover:border-gray-200 hover:bg-gray-100'
                  }`}
              >
                {s}
              </button>
            );
          })}
        </div>
      </div>

      {/* 목표 커리어 */}
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-2">목표 커리어</label>
        <input
          type="text"
          value={form.careerGoal}
          onChange={(e) => setForm({ ...form, careerGoal: e.target.value })}
          placeholder="예: 네카라쿠배 백엔드 개발자, 스타트업 풀스택 개발자"
          className="w-full px-5 py-4 rounded-xl border border-gray-200 bg-gray-50/50
                   text-gray-900 placeholder:text-gray-400 text-sm
                   focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-300
                   transition-all duration-200"
        />
      </div>

      {/* 다음 */}
      <button
        onClick={onNext}
        disabled={!isValid}
        className={`w-full py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2
                   transition-all duration-300
                   ${isValid
                     ? 'bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 hover:scale-[1.01]'
                     : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                   }`}
      >
        다음 단계로
        <ArrowRight size={18} />
      </button>
    </div>
  );
}

/* ────────────────────────────────────────────
   STEP 2: 코스 & 결제 옵션
   ──────────────────────────────────────────── */
function StepPaymentOptions({
  form, setForm, onNext, onBack,
}: {
  form: any; setForm: (f: any) => void; onNext: () => void; onBack: () => void;
}) {
  const renewalCount = 0; // 신규 유저
  const unitPrice = getUnitPrice(renewalCount);
  const rawTotal = unitPrice * form.desiredMonths;
  const bundleRate = getBundleRate(form.desiredMonths);
  const discountAmount = Math.round(rawTotal * bundleRate);
  const finalPrice = rawTotal - discountAmount;

  return (
    <div className="animate-fade-in-up">
      {/* 수강 방식 */}
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">수강 방식</label>
        <div className="grid sm:grid-cols-2 gap-4">
          {/* 즉시 시작 */}
          <button
            onClick={() => setForm({ ...form, courseType: 'IMMEDIATE' })}
            className={`relative p-6 rounded-2xl border-2 text-left transition-all duration-300
              ${form.courseType === 'IMMEDIATE'
                ? 'border-blue-300 bg-blue-50/50 shadow-lg'
                : 'border-gray-100 bg-white hover:border-gray-200 hover:shadow-md'
              }`}
          >
            {form.courseType === 'IMMEDIATE' && (
              <div className="absolute top-4 right-4 w-6 h-6 rounded-full bg-blue-500 flex items-center justify-center">
                <Check size={14} className="text-white" />
              </div>
            )}
            <Zap size={22} className={form.courseType === 'IMMEDIATE' ? 'text-blue-500' : 'text-gray-400'} />
            <p className="mt-3 font-bold text-gray-900">즉시 시작</p>
            <p className="mt-1 text-xs text-gray-500 leading-relaxed">
              결제 직후 멘토 추천 및 선택이 진행되며,<br />바로 멘토링을 시작할 수 있습니다.
            </p>
          </button>

          {/* 얼리버드 */}
          <button
            onClick={() => setForm({ ...form, courseType: 'EARLY_BIRD' })}
            className={`relative p-6 rounded-2xl border-2 text-left transition-all duration-300
              ${form.courseType === 'EARLY_BIRD'
                ? 'border-violet-300 bg-violet-50/50 shadow-lg'
                : 'border-gray-100 bg-white hover:border-gray-200 hover:shadow-md'
              }`}
          >
            {form.courseType === 'EARLY_BIRD' && (
              <div className="absolute top-4 right-4 w-6 h-6 rounded-full bg-violet-500 flex items-center justify-center">
                <Check size={14} className="text-white" />
              </div>
            )}
            <Clock size={22} className={form.courseType === 'EARLY_BIRD' ? 'text-violet-500' : 'text-gray-400'} />
            <p className="mt-3 font-bold text-gray-900">얼리버드</p>
            <p className="mt-1 text-xs text-gray-500 leading-relaxed">
              다음 달 첫째 주에 시작합니다.<br />전월 20일경 멘토 선택 알림이 발송됩니다.
            </p>
          </button>
        </div>
      </div>

      {/* 수강 기간 (고정) */}
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">수강 기간</label>
        
        <div className="p-5 rounded-2xl bg-blue-50 border border-blue-100 flex items-start gap-4">
          <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0">
            <Tag size={20} className="text-blue-600" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-blue-900 mb-1">최초 등록 시 최소 4개월 과정으로 고정됩니다.</h4>
            <p className="text-xs text-blue-700 leading-relaxed">
              기본기를 확실히 다지고 프로젝트까지 완주하기 위해 4개월 과정으로 시작합니다.<br/>
              (첫 과정 수료 후에는 1개월 단위 추가 연장이 가능합니다.)
            </p>
          </div>
        </div>

        {form.desiredMonths >= 3 && (
          <div className="mt-4 flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-50 border border-emerald-100">
            <Tag size={14} className="text-emerald-600" />
            <span className="text-xs font-semibold text-emerald-700">
              {form.desiredMonths}개월 묶음 할인 {Math.round(bundleRate * 100)}% 적용!
            </span>
          </div>
        )}
      </div>

      {/* 가격 요약 카드 */}
      <div className="mb-8 p-6 rounded-2xl bg-gradient-to-br from-gray-50 to-white border border-gray-100">
        <h3 className="text-sm font-bold text-gray-900 mb-4 flex items-center gap-2">
          <CreditCard size={16} className="text-blue-500" />
          결제 예상 금액
        </h3>

        <div className="space-y-3">
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">월 수강료</span>
            <span className="text-gray-700">{formatPrice(unitPrice)}원</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">수강 기간</span>
            <span className="text-gray-700">× {form.desiredMonths}개월</span>
          </div>
          {discountAmount > 0 && (
            <div className="flex justify-between text-sm">
              <span className="text-emerald-600 font-medium">묶음 할인 ({Math.round(bundleRate * 100)}%)</span>
              <span className="text-emerald-600 font-bold">-{formatPrice(discountAmount)}원</span>
            </div>
          )}
          <div className="pt-3 mt-3 border-t border-gray-100 flex justify-between items-baseline">
            <span className="text-sm font-bold text-gray-900">총 결제 금액</span>
            <div className="text-right">
              {discountAmount > 0 && (
                <p className="text-xs text-gray-400 line-through">{formatPrice(rawTotal)}원</p>
              )}
              <p className="text-2xl font-extrabold text-blue-600 font-[Outfit]">
                {formatPrice(finalPrice)}<span className="text-sm font-bold ml-0.5">원</span>
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* 버튼 */}
      <div className="flex gap-3">
        <button
          onClick={onBack}
          className="px-6 py-4 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm
                   hover:bg-gray-50 transition-all duration-200 flex items-center gap-2"
        >
          <ArrowLeft size={16} />
          이전
        </button>
        <button
          onClick={onNext}
          disabled={!form.courseType}
          className={`flex-1 py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2
                     transition-all duration-300
                     ${form.courseType
                       ? 'bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 hover:scale-[1.01]'
                       : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                     }`}
        >
          결제하기 — {formatPrice(finalPrice)}원
          <ArrowRight size={18} />
        </button>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────
   STEP 3: 토스 결제 위젯
   ──────────────────────────────────────────── */
const clientKey = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const customerKey = 'test_customer_key_123'; // 더미 유저 키

function StepPaymentWidget({ form, finalPrice, onBack }: { form: any, finalPrice: number, onBack: () => void }) {
  const router = useRouter();
  const paymentWidgetRef = useRef<PaymentWidgetInstance | null>(null);
  const paymentMethodsWidgetRef = useRef<any>(null);

  useEffect(() => {
    (async () => {
      const paymentWidget = await loadPaymentWidget(clientKey, customerKey);
      
      const paymentMethodsWidget = paymentWidget.renderPaymentMethods(
        '#payment-widget',
        { value: finalPrice },
        { variantKey: 'DEFAULT' }
      );

      paymentWidget.renderAgreement(
        '#agreement',
        { variantKey: 'AGREEMENT' }
      );

      paymentWidgetRef.current = paymentWidget;
      paymentMethodsWidgetRef.current = paymentMethodsWidget;
    })();
  }, [finalPrice]);

  const handlePayment = async () => {
    const paymentWidget = paymentWidgetRef.current;
    
    try {
      await paymentWidget?.requestPayment({
        orderId: Math.random().toString(36).substring(2, 11),
        orderName: `${CATEGORIES.find(c => c.value === form.category)?.label} 멘토링 (${form.desiredMonths}개월)`,
        successUrl: window.location.origin + '/survey', // 결제 성공 시 바로 성향 조사로 이동
        failUrl: window.location.origin + '/apply',
        customerEmail: 'customer123@gmail.com',
        customerName: '홍길동',
      });
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div className="animate-fade-in-up">
      <div className="mb-4">
        <h2 className="text-xl font-bold text-gray-900 px-4 pt-4">결제 수단 선택</h2>
        <div id="payment-widget" className="w-full" />
      </div>
      <div id="agreement" className="mb-8 w-full" />

      <div className="flex gap-3 px-4 pb-4">
        <button
          onClick={onBack}
          className="px-6 py-4 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm
                   hover:bg-gray-50 transition-all duration-200 flex items-center gap-2"
        >
          <ArrowLeft size={16} />
          이전
        </button>
        <button
          onClick={handlePayment}
          className="flex-1 py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2
                     transition-all duration-300 bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 hover:scale-[1.01]"
        >
          {formatPrice(finalPrice)}원 결제하기
          <ArrowRight size={18} />
        </button>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────
   메인 페이지
   ──────────────────────────────────────────── */
export default function ApplyPage() {
  const [step, setStep] = useState(1);
  const [form, setForm] = useState({
    currentLevel: '',
    targetTechStack: [] as string[],
    careerGoal: '',
    category: '',
    courseType: '',
    desiredMonths: 4, // 최초 4개월로 고정
  });

  const steps = [
    { num: 1, label: '기본 정보' },
    { num: 2, label: '옵션 선택' },
    { num: 3, label: '결제하기' },
  ];

  /* 최종 가격 계산 (Step 3에서 사용) */
  const unitPrice = getUnitPrice(0);
  const rawTotal = unitPrice * form.desiredMonths;
  const bundleRate = getBundleRate(form.desiredMonths);
  const discountAmount = Math.round(rawTotal * bundleRate);
  const finalPrice = rawTotal - discountAmount;

  return (
    <>
      <Header />
      <main className="min-h-screen bg-white pt-24 pb-20">
        <div className="max-w-2xl mx-auto px-6">
          {/* 헤더 */}
          <div className="text-center mb-10">
            <span className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full
                           bg-blue-50 text-blue-600 text-xs font-bold tracking-wider uppercase mb-4">
              <Sparkles size={12} />
              Apply
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-3">
              수강 신청하기
            </h1>
            <p className="text-gray-500">나에게 맞는 코스를 선택하고, 멘토링을 시작하세요.</p>
          </div>

          {/* 스텝 인디케이터 */}
          <div className="flex items-center justify-center gap-0 mb-12">
            {steps.map((s, i) => (
              <div key={s.num} className="flex items-center">
                <div className="flex items-center gap-2">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
                                 transition-all duration-300
                                 ${step >= s.num
                                   ? 'bg-blue-500 text-white shadow-md shadow-blue-500/30'
                                   : 'bg-gray-100 text-gray-400'
                                 }`}>
                    {step > s.num ? <Check size={14} /> : s.num}
                  </div>
                  <span className={`text-sm font-medium hidden sm:block
                                   ${step >= s.num ? 'text-gray-900' : 'text-gray-400'}`}>
                    {s.label}
                  </span>
                </div>
                {i < steps.length - 1 && (
                  <div className={`w-12 sm:w-20 h-0.5 mx-3 rounded-full transition-all duration-500
                                 ${step > s.num ? 'bg-blue-500' : 'bg-gray-200'}`} />
                )}
              </div>
            ))}
          </div>

          {/* 스텝 내용 */}
          {step === 1 && <StepBasicInfo form={form} setForm={setForm} onNext={() => setStep(2)} />}
          {step === 2 && <StepPaymentOptions form={form} setForm={setForm} onNext={() => setStep(3)} onBack={() => setStep(1)} />}
          {step === 3 && <StepPaymentWidget form={form} finalPrice={finalPrice} onBack={() => setStep(2)} />}
        </div>
      </main>
      <Footer />
    </>
  );
}

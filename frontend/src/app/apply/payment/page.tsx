'use client';

import { useState, useEffect, useRef, Suspense } from 'react';
import { loadPaymentWidget, PaymentWidgetInstance } from '@tosspayments/payment-widget-sdk';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { ArrowRight, CreditCard } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

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

const clientKey = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const customerKey = 'test_customer_key_123';

const PLANS = [
  {
    id: 'IMMEDIATE',
    title: '즉시 시작',
    badge: '시작일 선택 가능',
    desc: '결제 즉시 멘토님 매칭 (시작일 선택 가능)',
    duration: '4개월',
    originalPrice: 5980000,
    price: 4680000,
    monthly: '390,000원 / 12개월(무이자)',
    isDiscounted: true
  },
  {
    id: 'EARLY_BIRD_6',
    title: '6월 시작 얼리버드',
    badge: '마감 D-13',
    desc: '6월 내 멘토님 매칭, 시작일 선택 가능',
    duration: '4개월',
    originalPrice: 4980000,
    price: 4580000,
    monthly: '381,000원 / 12개월(무이자)',
    isDiscounted: true
  },
  {
    id: 'EARLY_BIRD_7',
    title: '7월 시작 얼리버드',
    badge: '마감 D-13',
    desc: '7월 내 멘토님 매칭, 시작일 선택 가능',
    duration: '4개월',
    originalPrice: 4980000,
    price: 4480000,
    monthly: '373,000원 / 12개월(무이자)',
    isDiscounted: true
  }
];

function PaymentContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const applicationId = searchParams.get('applicationId');

  const paymentWidgetRef = useRef<PaymentWidgetInstance | null>(null);
  const paymentMethodsWidgetRef = useRef<any>(null);
  const [selectedPlanId, setSelectedPlanId] = useState(PLANS[0].id);
  const [isReady, setIsReady] = useState(false);
  const { user } = useAuth();

  const selectedPlan = PLANS.find(p => p.id === selectedPlanId) || PLANS[0];
  const finalPrice = selectedPlan.price;
  const categoryLabel = "Backend 개발";

  useEffect(() => {
    (async () => {
      const paymentWidget = await loadPaymentWidget(clientKey, customerKey);
      
      const paymentMethodsWidget = await paymentWidget.renderPaymentMethods(
        '#payment-widget',
        { value: finalPrice },
        { variantKey: 'DEFAULT' }
      );
      
      await paymentWidget.renderAgreement(
        '#agreement',
        { variantKey: 'AGREEMENT' }
      );

      paymentWidgetRef.current = paymentWidget;
      paymentMethodsWidgetRef.current = paymentMethodsWidget;
      setIsReady(true);
    })();
  }, []);

  useEffect(() => {
    if (paymentMethodsWidgetRef.current) {
      paymentMethodsWidgetRef.current.updateAmount(finalPrice);
    }
  }, [finalPrice]);

  const handlePayment = async () => {
    const paymentWidget = paymentWidgetRef.current;
    
    try {
      await paymentWidget?.requestPayment({
        orderId: Math.random().toString(36).substring(2, 11),
        orderName: `${categoryLabel} ${selectedPlan.title}`,
        successUrl: window.location.origin + `/payment/success?applicationId=${applicationId}`, 
        failUrl: window.location.origin + `/apply/payment?applicationId=${applicationId}`,
        customerEmail: user?.email || 'customer123@gmail.com',
        customerName: user?.name || '홍길동',
      });
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div className="max-w-6xl mx-auto px-6">
      <div className="text-center mb-12">
        <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-4">
          멘토링 플랜 선택
        </h1>
        <p className="text-gray-500 text-sm sm:text-base">원하시는 시작 시점과 혜택을 선택해주세요.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16">
        {PLANS.map((plan) => (
          <div
            key={plan.id}
            onClick={() => setSelectedPlanId(plan.id)}
            className={`relative bg-white rounded-3xl p-8 cursor-pointer transition-all duration-300 border-2 flex flex-col
              ${selectedPlanId === plan.id 
                ? 'border-blue-500 shadow-2xl scale-[1.02] z-10' 
                : 'border-transparent shadow-sm hover:border-gray-200 hover:shadow-md'}`}
          >
            <div className="flex justify-between items-start mb-8">
              <h3 className="text-xl font-bold text-gray-900 tracking-tight">{plan.title}</h3>
              <span className={`px-2.5 py-1 rounded-md text-[10px] font-bold border
                ${plan.id === 'IMMEDIATE' 
                  ? 'bg-blue-50 text-blue-600 border-blue-100' 
                  : 'bg-red-50 text-red-500 border-red-100'}`}>
                {plan.badge}
              </span>
            </div>

            <div className="space-y-5 mb-10 flex-grow">
              <div>
                <p className="text-[10px] text-gray-400 font-bold uppercase tracking-wider mb-2">시작</p>
                <p className="text-sm text-gray-600 font-medium leading-relaxed">{plan.desc}</p>
              </div>
              <div>
                <p className="text-[10px] text-gray-400 font-bold uppercase tracking-wider mb-2">기간</p>
                <p className="text-sm text-gray-600 font-bold">{plan.duration}</p>
              </div>
            </div>

            <div className="pt-6 border-t border-gray-100 mb-6">
              <p className="text-xs text-gray-400 line-through mb-1 tracking-tight">{formatPrice(plan.originalPrice)}원</p>
              <p className="text-3xl font-black text-blue-600 font-[Outfit] tracking-tighter">
                {formatPrice(plan.price)}<span className="text-sm font-bold text-blue-500/80 ml-0.5 tracking-normal">원</span>
              </p>
            </div>

            <div className="p-4 bg-gray-50 rounded-2xl mb-8">
              <p className="text-xs text-gray-500 mb-1.5 flex items-center gap-1.5">
                <span className="w-1 h-1 rounded-full bg-blue-400"></span>
                {plan.monthly}
              </p>
              <p className="text-[10px] text-gray-400 leading-tight pl-2.5">
                *최대 할인 적용시 금액<br/>
                *12개월 무이자 할부는 일부 카드사만 해당됩니다.
              </p>
            </div>

            <button
              className={`w-full py-4 rounded-xl font-bold text-sm transition-all
                ${selectedPlanId === plan.id 
                  ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/30' 
                  : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
            >
              {selectedPlanId === plan.id ? '선택됨' : '플랜 선택하기'}
            </button>
          </div>
        ))}
      </div>

      <div className="max-w-3xl mx-auto bg-white rounded-3xl p-8 shadow-sm border border-gray-100">
        <div className="mb-8">
          <h2 className="text-xl font-bold text-gray-900 mb-6 flex items-center gap-2">
            <CreditCard className="text-blue-500" />
            결제 정보
          </h2>
          <div id="payment-widget" className="w-full mb-4" />
          <div id="agreement" className="w-full" />
        </div>

        <button
          onClick={handlePayment}
          disabled={!isReady}
          className={`w-full py-5 rounded-2xl font-bold text-lg flex items-center justify-center gap-2
                     transition-all duration-300 shadow-xl
                     ${isReady 
                       ? 'bg-gray-900 text-white hover:bg-black' 
                       : 'bg-gray-200 text-gray-400 cursor-not-allowed'}`}
        >
          {isReady ? `${formatPrice(finalPrice)}원 결제하기` : '결제 시스템 준비 중...'}
          {isReady && <ArrowRight size={20} />}
        </button>
      </div>
    </div>
  );
}

export default function PaymentPage() {
  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50/50 pt-24 pb-20">
        <Suspense fallback={<div className="min-h-screen flex items-center justify-center text-gray-400">Loading...</div>}>
          <PaymentContent />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}


'use client';

import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { loadPaymentWidget, type PaymentWidgetInstance } from '@tosspayments/payment-widget-sdk';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowRight, CreditCard } from 'lucide-react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getEnrollmentPlans } from '@/lib/course-catalog';

const clientKey = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const customerKey = 'test_customer_key_123';

function formatPrice(amount: number) {
  return amount.toLocaleString('ko-KR');
}

function PaymentContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const applicationId = searchParams.get('applicationId');
  const { user } = useAuth();

  const plans = useMemo(() => getEnrollmentPlans(), []);
  const [selectedPlanId, setSelectedPlanId] = useState(plans[0].id);
  const [isReady, setIsReady] = useState(false);
  const paymentWidgetRef = useRef<PaymentWidgetInstance | null>(null);
  const paymentMethodsWidgetRef = useRef<{ updateAmount: (amount: number) => void } | null>(null);

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? plans[0];
  const finalPrice = selectedPlan.price;
  const categoryLabel = '멘토링 코스';

  useEffect(() => {
    const initializeWidget = async () => {
      const paymentWidget = await loadPaymentWidget(clientKey, customerKey);
      const paymentMethodsWidget = await paymentWidget.renderPaymentMethods(
        '#payment-widget',
        { value: finalPrice },
        { variantKey: 'DEFAULT' }
      );

      await paymentWidget.renderAgreement('#agreement', { variantKey: 'AGREEMENT' });

      paymentWidgetRef.current = paymentWidget;
      paymentMethodsWidgetRef.current = paymentMethodsWidget;
      setIsReady(true);
    };

    void initializeWidget();
  }, [finalPrice]);

  useEffect(() => {
    if (paymentMethodsWidgetRef.current) {
      paymentMethodsWidgetRef.current.updateAmount(finalPrice);
    }
  }, [finalPrice]);

  const handlePayment = async () => {
    try {
      await paymentWidgetRef.current?.requestPayment({
        orderId: Math.random().toString(36).slice(2, 11),
        orderName: `${categoryLabel} ${selectedPlan.title}`,
        successUrl: `${window.location.origin}/payment/success?applicationId=${applicationId}`,
        failUrl: `${window.location.origin}/apply/payment?applicationId=${applicationId}`,
        customerEmail: user?.email || 'customer@example.com',
        customerName: user?.name || '수강생',
      });
    } catch (error) {
      console.error(error);
    }
  };

  return (
    <div className="mx-auto max-w-6xl px-6">
      <div className="mb-12 text-center">
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900 sm:text-4xl">
          멘토링 결제 플랜 선택
        </h1>
        <p className="mt-4 text-sm leading-7 text-gray-500 sm:text-base">
          월별 차수와 마감일은 현재 날짜를 기준으로 자동 계산됩니다.
        </p>
      </div>

      <div className="mb-16 grid grid-cols-1 gap-6 md:grid-cols-3">
        {plans.map((plan) => (
          <button
            key={plan.id}
            onClick={() => setSelectedPlanId(plan.id)}
            className={`rounded-3xl border-2 p-8 text-left transition-all duration-300 ${
              selectedPlanId === plan.id
                ? 'scale-[1.02] border-blue-500 bg-white shadow-2xl'
                : 'border-transparent bg-white shadow-sm hover:border-gray-200 hover:shadow-md'
            }`}
          >
            <div className="flex items-start justify-between gap-4">
              <h2 className="break-keep text-xl font-bold tracking-tight text-gray-900">{plan.title}</h2>
              <span
                className={`rounded-full border px-3 py-1 text-[10px] font-bold ${
                  plan.badgeTone === 'blue'
                    ? 'border-blue-200 bg-blue-50 text-blue-600'
                    : plan.badgeTone === 'red'
                      ? 'border-red-200 bg-red-50 text-red-500'
                      : 'border-orange-200 bg-orange-50 text-orange-500'
                }`}
              >
                {plan.badge}
              </span>
            </div>

            <p className="mt-5 break-keep text-sm leading-7 text-gray-600">{plan.desc}</p>

            <div className="mt-6 rounded-2xl bg-gray-50 p-4">
              <p className="text-[10px] font-bold uppercase tracking-wider text-gray-400">진행 기간</p>
              <p className="mt-2 text-sm font-semibold text-gray-700">{plan.duration}</p>
            </div>

            <div className="mt-6 border-t border-gray-100 pt-6">
              <p className="text-xs text-gray-400 line-through">{formatPrice(plan.originalPrice)}원</p>
              <p className="mt-2 text-3xl font-black tracking-tighter text-blue-600">
                {formatPrice(plan.price)}원
              </p>
              <p className="mt-2 text-sm text-gray-500">{plan.monthly}</p>
            </div>

            <div
              className={`mt-8 rounded-2xl px-5 py-4 text-center text-sm font-bold transition-colors ${
                selectedPlanId === plan.id
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-500'
              }`}
            >
              {selectedPlanId === plan.id ? '선택된 플랜' : '이 플랜 선택하기'}
            </div>
          </button>
        ))}
      </div>

      <div className="mx-auto max-w-3xl rounded-3xl border border-gray-100 bg-white p-8 shadow-sm">
        <div className="mb-8">
          <h2 className="mb-6 flex items-center gap-2 text-xl font-bold text-gray-900">
            <CreditCard className="text-blue-500" />
            결제 정보
          </h2>
          <div id="payment-widget" className="mb-4 w-full" />
          <div id="agreement" className="w-full" />
        </div>

        <button
          onClick={handlePayment}
          disabled={!isReady}
          className={`flex w-full items-center justify-center gap-2 rounded-2xl py-5 text-lg font-bold shadow-xl transition-all duration-300 ${
            isReady
              ? 'bg-gray-900 text-white hover:bg-black'
              : 'cursor-not-allowed bg-gray-200 text-gray-400'
          }`}
        >
          {isReady ? `${formatPrice(finalPrice)}원 결제하기` : '결제 위젯 준비 중...'}
          {isReady && <ArrowRight size={20} />}
        </button>

        <button
          onClick={() => router.push('/apply')}
          className="mt-4 w-full rounded-2xl border border-gray-200 py-4 text-sm font-semibold text-gray-600 transition-colors hover:bg-gray-50"
        >
          신청서로 돌아가기
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
        <Suspense fallback={<div className="flex min-h-screen items-center justify-center text-gray-400">Loading...</div>}>
          <PaymentContent />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}

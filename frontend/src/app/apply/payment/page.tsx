'use client';

import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { loadPaymentWidget, type PaymentWidgetInstance } from '@tosspayments/payment-widget-sdk';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowRight, CreditCard } from 'lucide-react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getEnrollmentPlans } from '@/lib/course-catalog';
import WaitingRoom from '@/components/payment/WaitingRoom';
import { createPayment, enterQueue, type PaymentResponse, type QueueStatus } from '@/lib/payment';

// 토스 테스트 키(test_ 접두사) — 샌드박스라 실제 청구가 일어나지 않는다.
const clientKey = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';
const customerKey = 'test_customer_key_123';

/** 현재 모든 플랜이 4개월 집중 과정이다. */
const PLAN_MONTHS = 4;

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

  // 단계: plan(플랜 선택) → queue(대기열) → payment(결제 위젯)
  const [step, setStep] = useState<'plan' | 'queue' | 'payment'>('plan');
  const [queueStatus, setQueueStatus] = useState<QueueStatus | null>(null);
  // 서버가 발급한 결제 건 — orderId 와 실제 청구 금액의 기준이다.
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const paymentWidgetRef = useRef<PaymentWidgetInstance | null>(null);
  const paymentMethodsWidgetRef = useRef<{ updateAmount: (amount: number) => void } | null>(null);

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? plans[0];
  const categoryLabel = '멘토링 코스';

  // 결제 위젯은 결제 건이 생성된 뒤(= 서버 금액이 확정된 뒤)에만 띄운다.
  // 서버가 계산한 금액이 실제 청구 기준이며, 승인 시 서버가 금액 일치를 검증한다.
  useEffect(() => {
    if (step !== 'payment' || !payment) return;
    let disposed = false;

    const initializeWidget = async () => {
      const paymentWidget = await loadPaymentWidget(clientKey, customerKey);
      if (disposed) return;
      const paymentMethodsWidget = await paymentWidget.renderPaymentMethods(
        '#payment-widget',
        { value: payment.amount },
        { variantKey: 'DEFAULT' }
      );
      await paymentWidget.renderAgreement('#agreement', { variantKey: 'AGREEMENT' });
      if (disposed) return;

      paymentWidgetRef.current = paymentWidget;
      paymentMethodsWidgetRef.current = paymentMethodsWidget;
      setIsReady(true);
    };

    void initializeWidget();
    return () => {
      disposed = true;
    };
  }, [step, payment]);

  /** 결제 건 생성 → 결제 단계로 전환. orderId·금액을 서버에서 받아온다. */
  const startPayment = async () => {
    if (!applicationId) {
      setErrorMessage('신청서 정보가 없습니다. 신청서부터 작성해주세요.');
      return;
    }
    try {
      const res = await createPayment({
        applicationId: Number(applicationId),
        // 백엔드는 IMMEDIATE / EARLY_BIRD 두 가지만 구분한다.
        courseType: selectedPlanId.startsWith('EARLY_BIRD') ? 'EARLY_BIRD' : 'IMMEDIATE',
        monthsBundled: PLAN_MONTHS,
      });
      setPayment(res.data);
      setStep('payment');
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setErrorMessage('이미 이 신청서로 결제가 생성되어 있습니다. 결제 내역을 확인해주세요.');
      } else {
        setErrorMessage('결제 정보를 준비하지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
    }
  };

  /** 결제 진행 버튼 — 대기열을 먼저 거친다. */
  const handleProceed = async () => {
    setErrorMessage('');
    setSubmitting(true);
    try {
      const res = await enterQueue();
      if (res.data.active) {
        await startPayment();     // 즉시 입장 (대기열 비활성이거나 이미 입장 상태)
      } else {
        setQueueStatus(res.data); // 대기 화면으로
        setStep('queue');
      }
    } catch {
      setErrorMessage('대기열 진입에 실패했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  /** 토스 결제창 호출 — orderId 는 반드시 서버가 발급한 값을 쓴다. */
  const handlePayment = async () => {
    if (!payment) return;
    try {
      await paymentWidgetRef.current?.requestPayment({
        orderId: payment.orderId,
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

  // 대기열 단계 — 순번이 올 때까지 대기 화면만 보여준다.
  if (step === 'queue' && queueStatus) {
    return (
      <div className="mx-auto max-w-6xl px-6">
        <div className="mb-10 text-center">
          <h1 className="text-2xl font-extrabold tracking-tight text-gray-900 sm:text-3xl">
            결제 대기 중
          </h1>
          <p className="mt-3 text-sm text-gray-500">
            순서가 되면 자동으로 결제 화면으로 이동합니다.
          </p>
        </div>
        <WaitingRoom
          initialStatus={queueStatus}
          onAdmitted={() => void startPayment()}
          onError={(message) => {
            setErrorMessage(message);
            setStep('plan');
          }}
        />
      </div>
    );
  }

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

          {errorMessage && (
            <div className="mb-6 rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-600">
              {errorMessage}
            </div>
          )}

          {payment ? (
            <div className="mb-6 rounded-2xl bg-gray-50 p-5">
              <p className="text-[10px] font-bold uppercase tracking-wider text-gray-400">주문번호</p>
              <p className="mt-1 font-mono text-sm text-gray-600">{payment.orderId}</p>
              <div className="mt-4 flex items-baseline justify-between border-t border-gray-200 pt-4">
                <span className="text-sm font-semibold text-gray-700">결제 금액</span>
                <span className="text-2xl font-black tracking-tight text-gray-900">
                  {formatPrice(payment.amount)}원
                </span>
              </div>
              {payment.discountApplied > 0 && (
                <p className="mt-1 text-right text-xs text-gray-500">
                  묶음 할인 {formatPrice(payment.discountApplied)}원 적용
                </p>
              )}
            </div>
          ) : (
            <p className="mb-6 rounded-2xl bg-gray-50 px-5 py-4 text-sm text-gray-500">
              결제 진행을 누르면 주문번호와 최종 금액이 확정됩니다.
            </p>
          )}

          <div id="payment-widget" className={payment ? 'mb-4 w-full' : 'hidden'} />
          <div id="agreement" className={payment ? 'w-full' : 'hidden'} />
        </div>

        {step === 'payment' ? (
          <button
            onClick={handlePayment}
            disabled={!isReady}
            className={`flex w-full items-center justify-center gap-2 rounded-2xl py-5 text-lg font-bold shadow-xl transition-all duration-300 ${
              isReady
                ? 'bg-gray-900 text-white hover:bg-black'
                : 'cursor-not-allowed bg-gray-200 text-gray-400'
            }`}
          >
            {isReady && payment ? `${formatPrice(payment.amount)}원 결제하기` : '결제 위젯 준비 중...'}
            {isReady && <ArrowRight size={20} />}
          </button>
        ) : (
          <button
            onClick={handleProceed}
            disabled={submitting}
            className={`flex w-full items-center justify-center gap-2 rounded-2xl py-5 text-lg font-bold shadow-xl transition-all duration-300 ${
              submitting
                ? 'cursor-not-allowed bg-gray-200 text-gray-400'
                : 'bg-gray-900 text-white hover:bg-black'
            }`}
          >
            {submitting ? '확인 중...' : '결제 진행하기'}
            {!submitting && <ArrowRight size={20} />}
          </button>
        )}

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

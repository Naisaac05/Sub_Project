'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { confirmApplicationPayment } from '@/lib/application';
import { confirmPayment as confirmTossPayment } from '@/lib/payment';

function SuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const applicationId = searchParams.get('applicationId');
  // 토스가 성공 리다이렉트에 붙여주는 값들 — 승인 요청의 필수 입력이다.
  const paymentKey = searchParams.get('paymentKey');
  const orderId = searchParams.get('orderId');
  const amount = searchParams.get('amount');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!applicationId) {
      router.push('/');
      return;
    }

    const run = async () => {
      try {
        // ① 결제 승인 — 서버에서 분산 락 + 멱등성 체크 + 토스 Idempotency-Key 를 거친다.
        //    새로고침 등으로 이 페이지가 다시 실행돼도 중복 승인되지 않고 기존 결과가 돌아온다.
        if (paymentKey && orderId && amount) {
          await confirmTossPayment({ paymentKey, orderId, amount: Number(amount) });
        }

        // ② 신청서 상태 전이 + 자동 매칭
        await confirmApplicationPayment(Number(applicationId));
        setLoading(false);
        setTimeout(() => {
          router.push('/mypage');
        }, 2000);
      } catch (err) {
        console.error(err);
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 409) {
          // 락 획득 실패 = 같은 결제가 이미 처리 중. 잠시 후 다시 시도하면 된다.
          setError('결제가 이미 처리 중입니다. 잠시 후 결제 내역에서 확인해주세요.');
        } else if (status === 429) {
          setError('대기열 입장이 만료되었습니다. 결제 화면에서 다시 시도해주세요.');
        } else {
          setError('결제 승인 처리에 실패했습니다. 결제 내역을 확인하거나 관리자에게 문의해주세요.');
        }
        setLoading(false);
      }
    };

    void run();
  }, [applicationId, paymentKey, orderId, amount, router]);

  return (
    <div className="mx-4 w-full max-w-md rounded-2xl bg-white p-10 text-center shadow-sm">
      {loading ? (
        <>
          <div className="mx-auto mb-6 h-16 w-16 animate-spin rounded-full border-4 border-blue-100 border-t-blue-500" />
          <h2 className="mb-2 text-2xl font-bold text-gray-900">Confirming payment...</h2>
          <p className="text-gray-500">We are confirming your payment and creating your mentor matching.</p>
        </>
      ) : error ? (
        <>
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-red-100 text-3xl font-bold text-red-500">
            !
          </div>
          <h2 className="mb-2 text-2xl font-bold text-gray-900">Matching failed</h2>
          <p className="mb-6 text-gray-500">{error}</p>
        </>
      ) : (
        <>
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-green-100 text-3xl font-bold text-green-500">
            OK
          </div>
          <h2 className="mb-2 text-2xl font-bold text-gray-900">Matching complete</h2>
          <p className="mb-6 text-gray-500">Your application has been received and a mentor has been matched automatically.</p>
          <p className="text-sm text-gray-400">Moving to My Page...</p>
        </>
      )}
    </div>
  );
}

export default function PaymentSuccessPage() {
  return (
    <>
      <Header />
      <main className="flex min-h-screen items-center justify-center bg-gray-50">
        <Suspense fallback={<div className="text-gray-400">Loading...</div>}>
          <SuccessContent />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}

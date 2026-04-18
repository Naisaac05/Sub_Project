'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';

function SuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const applicationId = searchParams.get('applicationId');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (applicationId) {
      fetch(`http://localhost:8080/api/applications/${applicationId}/confirm-payment`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json',
        },
      })
        .then((response) => {
          if (!response.ok) {
            throw new Error('Payment confirmation failed');
          }
          return response.json();
        })
        .then((data) => {
          console.log('Payment confirmed and mentor assigned:', data);
          setLoading(false);
          setTimeout(() => {
            router.push('/mypage');
          }, 2000);
        })
        .catch((error) => {
          console.error(error);
          setLoading(false);
        });
    } else {
      router.push('/');
    }
  }, [applicationId, router]);

  return (
    <div className="mx-4 w-full max-w-md rounded-2xl bg-white p-10 text-center shadow-sm">
      {loading ? (
        <>
          <div className="mx-auto mb-6 h-16 w-16 animate-spin rounded-full border-4 border-blue-100 border-t-blue-500" />
          <h2 className="mb-2 text-2xl font-bold text-gray-900">결제 확인 중...</h2>
          <p className="text-gray-500">결제 정보를 확인하고 멘토를 배정하고 있습니다.</p>
        </>
      ) : (
        <>
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-green-100 text-3xl text-green-500">
            ✓
          </div>
          <h2 className="mb-2 text-2xl font-bold text-gray-900">신청 완료!</h2>
          <p className="mb-6 text-gray-500">성공적으로 접수되었습니다. 멘토님의 승인을 기다려주세요.</p>
          <p className="text-sm text-gray-400">잠시 후 마이페이지로 이동합니다...</p>
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

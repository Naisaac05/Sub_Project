'use client';

import { useEffect, useState, Suspense } from 'react';
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
      // 서버에 결제 완료 상태 전송 및 멘토 할당 요청
      fetch(`http://localhost:8080/api/applications/${applicationId}/confirm-payment`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      })
      .then(res => {
        if (!res.ok) throw new Error('Payment confirmation failed');
        return res.json();
      })
      .then(data => {
        console.log('Payment confirmed and mentor assigned:', data);
        setLoading(false);
        // 완료 후 사용자의 마이페이지로 이동
        setTimeout(() => {
          router.push('/mypage'); 
        }, 2000);
      })
      .catch(err => {
        console.error(err);
        setLoading(false);
      });
    } else {
      router.push('/');
    }
  }, [applicationId, router]);

  return (
    <div className="bg-white p-10 rounded-2xl shadow-sm text-center max-w-md w-full mx-4">
      {loading ? (
        <>
          <div className="w-16 h-16 mx-auto mb-6 rounded-full border-4 border-blue-100 border-t-blue-500 animate-spin" />
          <h2 className="text-2xl font-bold text-gray-900 mb-2">결제 확인 중...</h2>
          <p className="text-gray-500">결제 정보를 확인하고 멘토를 배정하고 있습니다.</p>
        </>
      ) : (
         <>
          <div className="w-16 h-16 mx-auto mb-6 rounded-full bg-green-100 flex items-center justify-center text-green-500 text-3xl">
            ✓
          </div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">신청 완료!</h2>
          <p className="text-gray-500 mb-6">성공적으로 접수되었습니다. 멘토님의 승인을 기다려주세요.</p>
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
      <main className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Suspense fallback={<div className="text-gray-400">Loading...</div>}>
          <SuccessContent />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}


'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { confirmApplicationPayment } from '@/lib/application';

function SuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const applicationId = searchParams.get('applicationId');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!applicationId) {
      router.push('/');
      return;
    }

    const confirmPayment = async () => {
      try {
        await confirmApplicationPayment(Number(applicationId));
        setLoading(false);
        setTimeout(() => {
          router.push('/mypage');
        }, 2000);
      } catch (err) {
        console.error(err);
        setError('Payment was completed, but automatic matching failed. Please refresh this page or contact an admin.');
        setLoading(false);
      }
    };

    void confirmPayment();
  }, [applicationId, router]);

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

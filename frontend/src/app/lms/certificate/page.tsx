'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Award, Download, CheckCircle2, XCircle } from 'lucide-react';
import { checkEligibility, downloadCertificate } from '@/lib/lms';
import type { CertificateEligibilityResponse } from '@/lib/lms-types';

export default function CertificatePage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [eligibility, setEligibility] = useState<CertificateEligibilityResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    if (!matchingId) return;
    checkEligibility(matchingId)
      .then((res) => setEligibility(res.data.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId]);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      const res = await downloadCertificate(matchingId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = 'certificate.pdf';
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      alert('수료증 다운로드에 실패했습니다.');
    } finally {
      setDownloading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!eligibility) return null;

  const criteria = [
    { label: '진도율', value: eligibility.progressRate, required: eligibility.requiredProgress },
    { label: '출석률', value: eligibility.attendanceRate, required: eligibility.requiredAttendance },
    { label: '과제 제출률', value: eligibility.assignmentSubmitRate, required: eligibility.requiredAssignmentRate },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">수료증</h1>
        <p className="text-gray-400 mt-1">멘토링 수료 자격을 확인하고 수료증을 발급받으세요</p>
      </div>

      <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-8">
        <div className="flex items-center gap-4 mb-6">
          <div className={`w-14 h-14 rounded-2xl flex items-center justify-center ${
            eligibility.eligible
              ? 'bg-gradient-to-br from-blue-500 to-cyan-400'
              : 'bg-white/5'
          }`}>
            <Award size={28} className="text-white" />
          </div>
          <div>
            <h2 className="text-white text-xl font-bold">
              {eligibility.eligible ? '수료 자격 충족!' : '수료 자격 미달'}
            </h2>
            <p className="text-gray-400 text-sm">
              {eligibility.eligible
                ? '축하합니다! 수료증을 다운로드할 수 있습니다.'
                : '아래 기준을 충족하면 수료증을 발급받을 수 있습니다.'}
            </p>
          </div>
        </div>

        <div className="space-y-4">
          {criteria.map((c) => {
            const passed = c.value >= c.required;
            return (
              <div key={c.label} className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  {passed ? (
                    <CheckCircle2 size={18} className="text-green-400" />
                  ) : (
                    <XCircle size={18} className="text-red-400" />
                  )}
                  <span className="text-gray-300">{c.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`font-semibold ${passed ? 'text-green-400' : 'text-red-400'}`}>
                    {c.value}%
                  </span>
                  <span className="text-gray-500 text-sm">/ {c.required}% 이상</span>
                </div>
              </div>
            );
          })}
        </div>

        {eligibility.eligible && (
          <button
            onClick={handleDownload}
            disabled={downloading}
            className="mt-8 w-full py-3 rounded-xl bg-gradient-to-r from-blue-600 to-blue-500 text-white font-semibold
                     hover:from-blue-500 hover:to-blue-400 transition-all duration-300
                     shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30
                     flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <Download size={18} />
            {downloading ? '다운로드 중...' : '수료증 다운로드 (PDF)'}
          </button>
        )}
      </div>
    </div>
  );
}

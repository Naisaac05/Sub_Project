'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import * as api from '@/lib/admin/dashboard';
import { KpiCards } from '@/components/admin/dashboard/KpiCards';
import { SignupTrendChart } from '@/components/admin/dashboard/SignupTrendChart';
import { RevenueTrendChart } from '@/components/admin/dashboard/RevenueTrendChart';
import { ActionQueue } from '@/components/admin/dashboard/ActionQueue';
import { RecentAuditLog } from '@/components/admin/dashboard/RecentAuditLog';
import { SectionError } from '@/components/admin/dashboard/SectionError';

export default function AdminDashboardPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  const [summary, setSummary] = useState<api.DashboardResponse | null>(null);
  const [summaryError, setSummaryError] = useState(false);
  const [auditLog, setAuditLog] = useState<api.AuditLogResponse | null>(null);
  const [auditLogError, setAuditLogError] = useState(false);
  const [summaryReloadKey, setSummaryReloadKey] = useState(0);
  const [auditReloadKey, setAuditReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setSummaryError(false);
    setSummary(null);
    api.fetchDashboard()
      .then((r) => { if (!ignore) setSummary(r); })
      .catch(() => { if (!ignore) setSummaryError(true); });
    return () => { ignore = true; };
  }, [summaryReloadKey]);

  useEffect(() => {
    if (!isSuperAdmin) return;
    let ignore = false;
    setAuditLogError(false);
    setAuditLog(null);
    api.fetchAuditLog()
      .then((r) => { if (!ignore) setAuditLog(r); })
      .catch(() => { if (!ignore) setAuditLogError(true); });
    return () => { ignore = true; };
  }, [isSuperAdmin, auditReloadKey]);

  const retrySummary = useCallback(() => setSummaryReloadKey((k) => k + 1), []);
  const retryAudit = useCallback(() => setAuditReloadKey((k) => k + 1), []);

  const today = new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });

  return (
    <div className="space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">대시보드</h1>
        <p className="mt-1 text-sm text-slate-500">관리자 콘솔 홈 · {today} 기준</p>
      </header>

      {/* KPI */}
      <section>
        {summaryError ? (
          <SectionError onRetry={retrySummary} />
        ) : summary ? (
          <KpiCards kpi={summary.kpi} />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[0,1,2,3].map((i) => (
              <div key={i} className="h-[100px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
            ))}
          </div>
        )}
      </section>

      {/* 차트 */}
      <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {summaryError ? (
          <>
            <SectionError onRetry={retrySummary} />
            <SectionError onRetry={retrySummary} />
          </>
        ) : summary ? (
          <>
            <SignupTrendChart data={summary.signupTrend} />
            <RevenueTrendChart data={summary.revenueTrend} />
          </>
        ) : (
          <>
            <div className="h-[280px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
            <div className="h-[280px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
          </>
        )}
      </section>

      {/* 처리 큐 */}
      <section>
        {summaryError ? null : summary ? (
          <ActionQueue queue={summary.queue} />
        ) : (
          <div className="h-[130px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
        )}
      </section>

      {/* 감사 로그 (SUPER_ADMIN 전용) */}
      {isSuperAdmin && (
        <section>
          {auditLogError ? (
            <SectionError onRetry={retryAudit} />
          ) : auditLog ? (
            <RecentAuditLog items={auditLog.items} />
          ) : (
            <div className="h-[200px] animate-pulse rounded-lg border border-slate-200 bg-slate-50" />
          )}
        </section>
      )}
    </div>
  );
}

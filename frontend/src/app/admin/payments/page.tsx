'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { format } from 'date-fns';
import type { DateRange } from 'react-day-picker';
import { AdminListHeader } from '@/components/admin/AdminListHeader';
import { AdminTabs } from '@/components/admin/AdminTabs';
import { DebouncedSearchInput } from '@/components/admin/DebouncedSearchInput';
import { Pagination } from '@/components/admin/Pagination';
import { AdminDateRangePicker } from '@/components/admin/AdminDateRangePicker';
import { listPayments, getPaymentSummary } from './_api/adminPaymentApi';
import type { PageResponse } from './_api/adminPaymentApi';
import { PaymentSummaryCards } from './_components/PaymentSummaryCards';
import { PaymentListTable } from './_components/PaymentListTable';
import type { AdminPaymentListItem, AdminPaymentSummary, PaymentStatus } from './_types';

const STATUS_TABS: Array<{ value: PaymentStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING', label: '대기' },
  { value: 'CONFIRMED', label: '확정' },
  { value: 'CANCELLED', label: '취소' },
  { value: 'FAILED', label: '실패' },
];

const PAGE_SIZE = 20;

export default function AdminPaymentsPage() {
  const sp = useSearchParams();
  const router = useRouter();

  const initStatus = (sp.get('status') as PaymentStatus | null) ?? 'ALL';
  const initQ = sp.get('q') ?? '';
  const initPage = Number(sp.get('page') ?? 0);
  const initFrom = sp.get('from') ?? '';
  const initTo = sp.get('to') ?? '';

  const [status, setStatus] = useState<PaymentStatus | 'ALL'>(initStatus);
  const [q, setQ] = useState(initQ);
  const [page, setPage] = useState(initPage);
  const [dateRange, setDateRange] = useState<DateRange | undefined>(() => {
    if (initFrom && initTo) {
      return { from: new Date(initFrom), to: new Date(initTo) };
    }
    if (initFrom) {
      return { from: new Date(initFrom), to: undefined };
    }
    return undefined;
  });

  const [data, setData] = useState<PageResponse<AdminPaymentListItem> | null>(null);
  const [summary, setSummary] = useState<AdminPaymentSummary | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fromStr = dateRange?.from ? format(dateRange.from, 'yyyy-MM-dd') : undefined;
  const toStr = dateRange?.to ? format(dateRange.to, 'yyyy-MM-dd') : undefined;

  // Fetch list
  useEffect(() => {
    setLoading(true);
    setError(null);
    listPayments({
      status: status === 'ALL' ? undefined : status,
      q: q || undefined,
      from: fromStr,
      to: toStr,
      page,
      size: PAGE_SIZE,
    })
      .then(setData)
      .catch((e: unknown) => {
        const err = e as { response?: { data?: { message?: string } } };
        setError(err?.response?.data?.message ?? String(e));
      })
      .finally(() => setLoading(false));
  }, [status, q, fromStr, toStr, page]);

  // Fetch summary
  useEffect(() => {
    getPaymentSummary(fromStr, toStr)
      .then(setSummary)
      .catch(() => setSummary(undefined));
  }, [fromStr, toStr]);

  // URL sync
  useEffect(() => {
    const params = new URLSearchParams();
    if (status !== 'ALL') params.set('status', status);
    if (q) params.set('q', q);
    if (fromStr) params.set('from', fromStr);
    if (toStr) params.set('to', toStr);
    if (page > 0) params.set('page', String(page));
    const qs = params.toString();
    router.replace(`/admin/payments${qs ? `?${qs}` : ''}`, { scroll: false });
  }, [status, q, fromStr, toStr, page, router]);

  function handleDateRange(range: DateRange | undefined) {
    setDateRange(range);
    setPage(0);
  }

  return (
    <div className="space-y-4">
      <AdminListHeader
        title="결제 관리"
        description="결제 내역을 조회하고 취소·환불 요청을 처리합니다."
      />

      <PaymentSummaryCards summary={summary} />

      <AdminTabs
        items={STATUS_TABS}
        value={status}
        onChange={(next) => { setStatus(next); setPage(0); }}
        ariaLabel="결제 상태 필터"
        variant="primary"
      />

      <div className="flex items-center gap-2">
        <AdminDateRangePicker
          value={dateRange}
          onChange={handleDateRange}
          placeholder="기간 선택"
        />
        <DebouncedSearchInput
          value={q}
          onChange={(next) => { setQ(next); setPage(0); }}
          placeholder="주문ID·이름·이메일 검색"
        />
      </div>

      {loading && <div className="text-sm text-slate-500 py-6 text-center">불러오는 중…</div>}
      {error && (
        <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-700">
          에러: {error}
        </div>
      )}

      {data && !loading && (
        <>
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <PaymentListTable rows={data.content} />
          </div>
          <Pagination page={data.number} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}

'use client';

import { Suspense, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { ChevronRight, Search, RefreshCw, Inbox } from 'lucide-react';

import {
  listMentorApplications,
  type AdminMentorStatus,
} from '@/lib/admin-mentor';
import type { MentorProfileResponse } from '@/lib/types';

import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import MentorStatusBadge from '@/components/admin/MentorStatusBadge';

const STATUSES: AdminMentorStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];
const STATUS_LABELS: Record<AdminMentorStatus, string> = {
  PENDING: '대기',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
};

function parseStatus(raw: string | null): AdminMentorStatus {
  if (raw === 'APPROVED' || raw === 'REJECTED' || raw === 'PENDING') {
    return raw;
  }
  return 'PENDING';
}

function AdminMentorListInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const currentStatus = parseStatus(searchParams.get('status'));

  const [applications, setApplications] = useState<MentorProfileResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  async function load(status: AdminMentorStatus) {
    setLoading(true);
    setError(null);
    try {
      const data = await listMentorApplications(status);
      setApplications(data);
    } catch (e) {
      const message =
        (e as { response?: { data?: { message?: string } }; message?: string })
          ?.response?.data?.message ??
        (e as Error)?.message ??
        '목록을 불러오지 못했습니다.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(currentStatus);
  }, [currentStatus]);

  function handleStatusChange(next: string) {
    const params = new URLSearchParams(searchParams);
    params.set('status', next);
    router.replace(`/admin/mentor?${params.toString()}`, { scroll: false });
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return applications;
    return applications.filter(
      (m) =>
        m.name.toLowerCase().includes(q) ||
        m.email.toLowerCase().includes(q),
    );
  }, [applications, query]);

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
          멘토 심사 관리
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          신청된 멘토 프로필을 검토하고 승인·반려를 결정합니다.
        </p>
      </div>

      {/* 필터 바 */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Tabs value={currentStatus} onValueChange={handleStatusChange}>
          <TabsList>
            {STATUSES.map((s) => (
              <TabsTrigger key={s} value={s}>
                {STATUS_LABELS[s]}
                {s === currentStatus && applications.length > 0 && !loading ? (
                  <span className="ml-1.5 text-xs text-slate-500">
                    ({applications.length})
                  </span>
                ) : null}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <div className="relative sm:w-72">
          <Search
            className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
            aria-hidden="true"
          />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="이름 또는 이메일로 검색"
            className="pl-8"
          />
        </div>
      </div>

      {/* 상태 */}
      {error ? (
        <Alert variant="destructive">
          <AlertTitle>목록을 불러오지 못했습니다</AlertTitle>
          <AlertDescription className="flex items-center justify-between gap-3">
            <span>{error}</span>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void load(currentStatus)}
              className="shrink-0 gap-1.5"
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              다시 시도
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {/* 테이블 */}
      <div className="rounded-lg border border-slate-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[160px]">신청자</TableHead>
              <TableHead>이메일</TableHead>
              <TableHead>제공 코스</TableHead>
              <TableHead className="w-[80px] text-right">연차</TableHead>
              <TableHead className="w-[100px]">상태</TableHead>
              <TableHead className="w-[120px]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              [...Array(3)].map((_, i) => (
                <TableRow key={`skeleton-${i}`}>
                  {[...Array(6)].map((__, j) => (
                    <TableCell key={j}>
                      <div className="h-4 w-full animate-pulse rounded bg-slate-100" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={6}
                  className="py-12 text-center text-sm text-slate-500"
                >
                  <Inbox
                    className="mx-auto mb-2 h-8 w-8 text-slate-300"
                    aria-hidden="true"
                  />
                  {query.trim()
                    ? '검색 조건에 맞는 신청자가 없습니다.'
                    : `${STATUS_LABELS[currentStatus]} 상태의 신청이 없습니다.`}
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((m) => (
                <TableRow key={m.id}>
                  <TableCell className="font-medium text-slate-900">
                    {m.name}
                  </TableCell>
                  <TableCell className="text-slate-600">{m.email}</TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {m.courses.slice(0, 2).map((c) => (
                        <span
                          key={c.courseKey}
                          className="rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-xs text-slate-700"
                        >
                          {c.title}
                        </span>
                      ))}
                      {m.courses.length > 2 ? (
                        <span className="rounded-md border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-xs text-slate-500">
                          +{m.courses.length - 2}
                        </span>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell className="text-right text-slate-700">
                    {m.careerYears}년
                  </TableCell>
                  <TableCell>
                    <MentorStatusBadge status={m.status} />
                  </TableCell>
                  <TableCell>
                    <Button asChild size="sm" variant="ghost" className="gap-1">
                      <Link href={`/admin/mentor/${m.id}`}>
                        상세 보기
                        <ChevronRight className="h-4 w-4" aria-hidden="true" />
                      </Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* TODO: 서버 페이징이 추가되면 여기에 Pagination 컴포넌트 삽입. 20건 초과 시 보강. */}
    </div>
  );
}

export default function AdminMentorListPage() {
  // useSearchParams 는 Suspense 경계 필요 (Next 15 App Router 권장).
  return (
    <Suspense fallback={<div className="h-8 animate-pulse rounded bg-slate-100" />}>
      <AdminMentorListInner />
    </Suspense>
  );
}

'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { FileSearch, FileX, BadgeCheck } from 'lucide-react';

import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyMentorProfile } from '@/lib/mentor';
import type { MentorProfileResponse } from '@/lib/types';

import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';

type MentorStatus = 'PENDING' | 'REJECTED' | 'APPROVED';

export default function MentorStatusPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading, user } = useAuth();

  const [profile, setProfile] = useState<MentorProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  // 로그인 / 역할 가드
  useEffect(() => {
    if (authLoading) return;
    if (!isLoggedIn) {
      alert('로그인이 필요한 페이지입니다.');
      router.replace('/auth/login?redirect=/mentor/status');
      return;
    }
    if (user && user.role !== 'MENTOR') {
      alert('멘토 계정만 접근할 수 있습니다.');
      router.replace('/');
    }
  }, [authLoading, isLoggedIn, user, router]);

  useEffect(() => {
    if (authLoading || !isLoggedIn) return;
    let cancelled = false;

    (async () => {
      try {
        const p = await getMyMentorProfile();
        if (cancelled) return;
        if (!p) {
          // NEW — 신청 페이지로
          setNotFound(true);
          router.replace('/mentor/apply');
          return;
        }
        setProfile(p);
      } catch (e) {
        console.error('멘토 상태 조회 실패:', e);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [authLoading, isLoggedIn, router]);

  if (authLoading || loading || notFound) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-[#F8FAFC] py-24">
          <div className="mx-auto max-w-2xl px-6">
            <div className="h-64 animate-pulse rounded-xl bg-slate-100" />
          </div>
        </main>
        <Footer />
      </>
    );
  }

  if (!profile) return null;

  const status = profile.status as MentorStatus;

  return (
    <>
      <Header />
      <main className="min-h-screen bg-[#F8FAFC] py-24">
        <div className="mx-auto max-w-2xl px-6">
          <h1 className="mb-8 text-center text-2xl font-extrabold tracking-tight text-slate-900">
            멘토 신청 상태
          </h1>

          {status === 'PENDING' && <PendingCard />}
          {status === 'REJECTED' && (
            <RejectedCard reason={profile.rejectedReason ?? '사유가 기재되지 않았습니다.'} />
          )}
          {status === 'APPROVED' && <ApprovedCard />}
        </div>
      </main>
      <Footer />
    </>
  );
}

/* ─────────── PENDING ─────────── */
function PendingCard() {
  return (
    <Card>
      <CardContent className="px-6 py-12 text-center sm:px-10">
        <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-amber-50 ring-1 ring-amber-200">
          <FileSearch className="h-8 w-8 text-amber-600" />
        </div>
        <Badge className="mb-4 bg-amber-100 text-amber-800 hover:bg-amber-100">검토 중</Badge>
        <h2 className="mb-3 text-xl font-bold text-slate-900">
          신청서가 관리자에게 전달되었습니다
        </h2>
        <p className="mb-8 text-sm leading-relaxed text-slate-500">
          일반적으로 1~3 영업일 내에 검토가 완료됩니다.
          <br />
          결과는 이메일로도 안내드립니다.
        </p>
        <Button variant="outline" asChild>
          <Link href="/">홈으로 돌아가기</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

/* ─────────── REJECTED ─────────── */
function RejectedCard({ reason }: { reason: string }) {
  return (
    <Card>
      <CardContent className="px-6 py-12 text-center sm:px-10">
        <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-red-50 ring-1 ring-red-200">
          <FileX className="h-8 w-8 text-red-600" />
        </div>
        <Badge variant="destructive" className="mb-4">
          반려됨
        </Badge>
        <h2 className="mb-6 text-xl font-bold text-slate-900">
          안타깝게도 신청이 반려되었습니다
        </h2>

        {/* 반려 사유 박스 (목업 승인 디자인) */}
        <div className="mb-8 rounded-lg bg-slate-50 p-5 text-left ring-1 ring-slate-200">
          <div className="mb-2 flex items-center gap-2">
            <span className="inline-block h-2 w-2 rounded-full bg-red-500" />
            <span className="text-[11px] font-bold uppercase tracking-widest text-red-700">
              반려 사유
            </span>
          </div>
          <p className="text-[15px] font-medium leading-relaxed text-slate-900">
            {reason}
          </p>
        </div>

        <p className="mb-8 text-sm text-slate-500">
          내용을 보완한 뒤 다시 신청하실 수 있습니다.
        </p>

        <div className="flex items-center justify-center gap-3">
          <Button variant="outline" asChild>
            <Link href="/">홈</Link>
          </Button>
          <Button asChild>
            <Link href="/mentor/apply">재신청하기</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

/* ─────────── APPROVED ─────────── */
function ApprovedCard() {
  return (
    <Card>
      <CardContent className="px-6 py-12 text-center sm:px-10">
        <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 ring-1 ring-emerald-200">
          <BadgeCheck className="h-8 w-8 text-emerald-600" />
        </div>
        <Badge className="mb-4 bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
          승인됨
        </Badge>
        <h2 className="mb-3 text-xl font-bold text-slate-900">
          멘토 자격이 승인되었습니다 🎉
        </h2>
        <p className="mb-8 text-sm leading-relaxed text-slate-500">
          이제 멘티의 매칭 요청을 수락하고
          <br />
          멘토링을 시작하실 수 있습니다.
        </p>

        <div className="flex items-center justify-center gap-3">
          <Button variant="outline" asChild>
            <Link href="/">홈</Link>
          </Button>
          <Button asChild>
            <Link href="/mentors">멘토 대시보드로</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

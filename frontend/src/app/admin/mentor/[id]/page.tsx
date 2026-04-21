'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, ExternalLink, Check, X, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

import {
  approveMentor,
  listMentorApplications,
  rejectMentor,
} from '@/lib/admin-mentor';
import type { MentorProfileResponse } from '@/lib/types';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import MentorStatusBadge, {
  MENTEE_LEVEL_LABEL,
} from '@/components/admin/MentorStatusBadge';
import RejectMentorDialog from '@/components/admin/RejectMentorDialog';

function DefRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-[120px_1fr] gap-4 py-2">
      <dt className="text-sm font-medium text-slate-500">{label}</dt>
      <dd className="text-sm text-slate-900">{children}</dd>
    </div>
  );
}

export default function AdminMentorDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const profileId = useMemo(() => {
    const parsed = Number(params?.id);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [params?.id]);

  const [profile, setProfile] = useState<MentorProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  const [rejectOpen, setRejectOpen] = useState(false);
  const [actioning, setActioning] = useState(false);

  const load = useCallback(async () => {
    if (profileId === null) {
      setError('잘못된 프로필 ID 입니다.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    setNotFound(false);
    try {
      // 백엔드에 단건 조회 엔드포인트가 없어, 전체 목록에서 id 매칭.
      // 다음 이터레이션에서 GET /api/admin/mentor/{id} 추가 예정.
      const all = await listMentorApplications();
      const found = all.find((m) => m.id === profileId);
      if (!found) {
        setNotFound(true);
      } else {
        setProfile(found);
      }
    } catch (e) {
      const message =
        (e as { response?: { data?: { message?: string } }; message?: string })
          ?.response?.data?.message ??
        (e as Error)?.message ??
        '프로필을 불러오지 못했습니다.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [profileId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleApprove() {
    if (!profile || actioning) return;
    if (!window.confirm(`${profile.name} 님의 신청을 승인하시겠습니까?`)) {
      return;
    }
    setActioning(true);
    try {
      await approveMentor(profile.id);
      toast.success(`${profile.name} 님을 승인했습니다.`);
      router.push('/admin/mentor');
    } catch (e) {
      const message =
        (e as { response?: { data?: { message?: string } }; message?: string })
          ?.response?.data?.message ??
        (e as Error)?.message ??
        '승인 처리 중 오류가 발생했습니다.';
      toast.error(message);
      setActioning(false);
    }
  }

  async function handleReject(reason: string) {
    if (!profile) return;
    await rejectMentor(profile.id, reason);
    toast.success(`${profile.name} 님을 반려했습니다.`);
    setRejectOpen(false);
    router.push('/admin/mentor');
  }

  // ─── 렌더링 분기 ───

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2
          className="h-6 w-6 animate-spin text-slate-400"
          aria-hidden="true"
        />
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="space-y-4">
        <Button asChild variant="ghost" size="sm" className="gap-1">
          <Link href="/admin/mentor">
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            목록
          </Link>
        </Button>
        <Alert variant="destructive">
          <AlertTitle>프로필을 찾을 수 없습니다</AlertTitle>
          <AlertDescription>
            해당 ID의 멘토 신청이 존재하지 않거나 이미 삭제되었습니다.
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="space-y-4">
        <Button asChild variant="ghost" size="sm" className="gap-1">
          <Link href="/admin/mentor">
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            목록
          </Link>
        </Button>
        <Alert variant="destructive">
          <AlertTitle>불러오기 실패</AlertTitle>
          <AlertDescription className="flex items-center justify-between gap-3">
            <span>{error ?? '알 수 없는 오류'}</span>
            <Button size="sm" variant="outline" onClick={() => void load()}>
              다시 시도
            </Button>
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  // ─── 정상 렌더 ───

  const menteeLevelLabel = profile.preferredMenteeLevel
    ? MENTEE_LEVEL_LABEL[profile.preferredMenteeLevel] ??
      profile.preferredMenteeLevel
    : '—';

  return (
    <div className="space-y-6 pb-24">
      {/* 헤더 */}
      <div>
        <Button asChild variant="ghost" size="sm" className="-ml-3 gap-1">
          <Link href="/admin/mentor">
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            목록
          </Link>
        </Button>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
            {profile.name} 님의 신청
          </h1>
          <MentorStatusBadge status={profile.status} />
        </div>
        <p className="mt-1 text-sm text-slate-600">{profile.email}</p>
      </div>

      {/* A. 신청자 */}
      <Card>
        <CardHeader>
          <CardTitle>A. 신청자</CardTitle>
        </CardHeader>
        <CardContent className="divide-y divide-slate-100">
          <DefRow label="이름">{profile.name}</DefRow>
          <DefRow label="이메일">{profile.email}</DefRow>
        </CardContent>
      </Card>

      {/* B. 전문 분야 */}
      <Card>
        <CardHeader>
          <CardTitle>B. 전문 분야</CardTitle>
        </CardHeader>
        <CardContent className="divide-y divide-slate-100">
          <DefRow label="제공 코스">
            <div className="flex flex-wrap gap-1.5">
              {profile.courses.length > 0 ? (
                profile.courses.map((c) => (
                  <span
                    key={c.courseKey}
                    className="rounded-md border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-700"
                  >
                    {c.title}
                  </span>
                ))
              ) : (
                <span className="text-slate-400">—</span>
              )}
            </div>
          </DefRow>
          <DefRow label="기술 스택">
            <div className="flex flex-wrap gap-1.5">
              {profile.techStack && profile.techStack.length > 0 ? (
                profile.techStack.map((t) => (
                  <span
                    key={t}
                    className="rounded-md border border-slate-200 px-2 py-0.5 text-xs text-slate-700"
                  >
                    {t}
                  </span>
                ))
              ) : (
                <span className="text-slate-400">—</span>
              )}
            </div>
          </DefRow>
          <DefRow label="경력 연차">{profile.careerYears}년</DefRow>
          <DefRow label="선호 멘티 레벨">{menteeLevelLabel}</DefRow>
        </CardContent>
      </Card>

      {/* C. 경력 & 자격 */}
      <Card>
        <CardHeader>
          <CardTitle>C. 경력 & 자격</CardTitle>
        </CardHeader>
        <CardContent className="divide-y divide-slate-100">
          <DefRow label="현재 소속">{profile.company || <span className="text-slate-400">—</span>}</DefRow>
          <DefRow label="직무">{profile.jobTitle || <span className="text-slate-400">—</span>}</DefRow>
          <DefRow label="학력">{profile.education || <span className="text-slate-400">—</span>}</DefRow>
          <DefRow label="보유 자격증">
            <div className="flex flex-wrap gap-1.5">
              {profile.certifications && profile.certifications.length > 0 ? (
                profile.certifications.map((c) => (
                  <span
                    key={c}
                    className="rounded-md border border-slate-200 px-2 py-0.5 text-xs text-slate-700"
                  >
                    {c}
                  </span>
                ))
              ) : (
                <span className="text-slate-400">—</span>
              )}
            </div>
          </DefRow>
          <DefRow label="포트폴리오">
            {profile.portfolioUrl ? (
              <a
                href={profile.portfolioUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-slate-900 underline decoration-slate-300 underline-offset-2 hover:decoration-slate-900"
              >
                {profile.portfolioUrl}
                <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
              </a>
            ) : (
              <span className="text-slate-400">—</span>
            )}
          </DefRow>
        </CardContent>
      </Card>

      {/* D. 자기소개 */}
      <Card>
        <CardHeader>
          <CardTitle>D. 자기소개</CardTitle>
        </CardHeader>
        <CardContent>
          {profile.bio ? (
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
              {profile.bio}
            </p>
          ) : (
            <p className="text-sm text-slate-400">자기소개가 비어 있습니다.</p>
          )}
        </CardContent>
      </Card>

      {/* REJECTED 상태: 반려 사유 표시 */}
      {profile.status === 'REJECTED' && profile.rejectedReason ? (
        <Alert variant="destructive">
          <AlertTitle>반려 사유</AlertTitle>
          <AlertDescription className="whitespace-pre-wrap">
            {profile.rejectedReason}
          </AlertDescription>
        </Alert>
      ) : null}

      {/* 푸터 액션 — PENDING 만 노출 */}
      {profile.status === 'PENDING' ? (
        <div className="fixed bottom-0 left-0 right-0 z-10 border-t border-slate-200 bg-white/95 backdrop-blur">
          <div className="mx-auto flex max-w-[1280px] items-center justify-end gap-2 px-6 py-3 md:pl-[244px]">
            <Button
              variant="outline"
              onClick={() => setRejectOpen(true)}
              disabled={actioning}
              className="gap-1.5"
            >
              <X className="h-4 w-4" aria-hidden="true" />
              반려
            </Button>
            <Button
              onClick={handleApprove}
              disabled={actioning}
              className="gap-1.5"
            >
              {actioning ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              ) : (
                <Check className="h-4 w-4" aria-hidden="true" />
              )}
              승인
            </Button>
          </div>
        </div>
      ) : null}

      {/* 반려 모달 */}
      <RejectMentorDialog
        open={rejectOpen}
        onOpenChange={setRejectOpen}
        mentorName={profile.name}
        onConfirm={handleReject}
      />
    </div>
  );
}

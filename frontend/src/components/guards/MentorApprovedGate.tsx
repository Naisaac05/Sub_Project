'use client';

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

/**
 * MENTOR 역할이면서 status !== APPROVED 인 경우 /mentor/status 로 리다이렉트.
 * MENTEE/ADMIN/비로그인 은 통과 (별도 가드가 필요하면 외부에서 추가).
 *
 * 스펙: docs/superpowers/specs/2026-04-18-mentor-application-flow-design.md
 * "접근 권한 매트릭스" 섹션 — LMS, matching 등은 APPROVED 멘토만 이용 가능.
 */
export default function MentorApprovedGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { user, mentorStatus, isLoading } = useAuth();

  // 스펙 매트릭스: MENTOR 는 APPROVED 만 통과. 프로필 없음(null) 포함.
  const blocked =
    !isLoading && user?.role === 'MENTOR' && mentorStatus !== 'APPROVED';

  useEffect(() => {
    if (blocked) {
      toast.error('멘토 승인 후 이용 가능합니다');
      router.replace('/mentor/status');
    }
  }, [blocked, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" aria-hidden="true" />
      </div>
    );
  }

  // 멘토인데 프로필이 아직 없는 경우도 차단 (mentorStatus === null).
  if (user?.role === 'MENTOR' && mentorStatus !== 'APPROVED') {
    return null;
  }

  return <>{children}</>;
}

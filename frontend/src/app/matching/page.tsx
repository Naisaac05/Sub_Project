'use client';

import { useEffect, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMatchingApplication, getMyMatchingsAsMentee, getMyMatchingsAsMentor } from '@/lib/matching';
import type { ApplicationResponse, MatchingResponse } from '@/lib/types';
import {
  AlertCircle,
  ArrowRight,
  CheckCircle,
  Clock,
  FileText,
  Loader2,
  MessageSquare,
  User,
  X,
  XCircle,
} from 'lucide-react';

type TabType = 'mentee' | 'mentor';

const STATUS_CONFIG: Record<MatchingResponse['status'], { label: string; className: string; icon: ReactNode }> = {
  PENDING: {
    label: '대기중',
    className: 'border-amber-200 bg-amber-50 text-amber-700',
    icon: <Clock size={12} />,
  },
  ACCEPTED: {
    label: '매칭완료',
    className: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    icon: <CheckCircle size={12} />,
  },
  REJECTED: {
    label: '거절됨',
    className: 'border-red-200 bg-red-50 text-red-700',
    icon: <XCircle size={12} />,
  },
  CANCELLED: {
    label: '취소됨',
    className: 'border-gray-200 bg-gray-50 text-gray-600',
    icon: <AlertCircle size={12} />,
  },
  TRIAL: {
    label: '체험중',
    className: 'border-cyan-200 bg-cyan-50 text-cyan-700',
    icon: <Clock size={12} />,
  },
};

const LEVEL_LABELS: Record<string, string> = {
  BEGINNER: '입문자',
  INTERMEDIATE: '초급자',
  ADVANCED: '중급자',
};

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

function StatusBadge({ status }: { status: MatchingResponse['status'] }) {
  const config = STATUS_CONFIG[status];

  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold ${config.className}`}>
      {config.icon}
      {config.label}
    </span>
  );
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-gray-100 bg-white p-4">
      <p className="mb-2 text-xs font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <div className="whitespace-pre-wrap break-words text-sm leading-6 text-gray-800">
        {children || '-'}
      </div>
    </div>
  );
}

function ChipList({ items }: { items?: string[] | null }) {
  if (!items || items.length === 0) {
    return <span>-</span>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {items.map((item) => (
        <span key={item} className="rounded-full border border-blue-100 bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
          {item}
        </span>
      ))}
    </div>
  );
}

function ApplicationDetailModal({
  application,
  matching,
  loading,
  error,
  onClose,
}: {
  application: ApplicationResponse | null;
  matching: MatchingResponse | null;
  loading: boolean;
  error: string;
  onClose: () => void;
}) {
  if (!matching) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-8">
      <div className="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-gray-100 px-6 py-5">
          <div>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-3 py-1 text-xs font-bold text-blue-700">
              <FileText size={13} />
              멘티 신청서
            </span>
            <h2 className="mt-2 text-xl font-bold text-gray-900">{matching.menteeName}님의 신청서</h2>
            <p className="mt-1 text-sm text-gray-500">
              Matching #{matching.id} · {matching.category}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-gray-200 text-gray-500 transition-colors hover:bg-gray-50"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <div className="overflow-y-auto bg-gray-50 p-6">
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 size={28} className="animate-spin text-blue-500" />
            </div>
          ) : error ? (
            <div className="rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-600">{error}</div>
          ) : application ? (
            <div className="space-y-6">
              <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                <h3 className="mb-4 text-base font-extrabold text-gray-900">코스 및 목표</h3>
                <div className="grid gap-4 md:grid-cols-2">
                  <DetailRow label="코스">{application.category}</DetailRow>
                  <DetailRow label="수강 유형">{application.courseType}</DetailRow>
                  <DetailRow label="희망 기간">{application.desiredMonths ? `${application.desiredMonths}개월` : '-'}</DetailRow>
                  <DetailRow label="현재 수준">{LEVEL_LABELS[application.currentLevel] ?? application.currentLevel}</DetailRow>
                  <DetailRow label="목표 기술 스택">{application.targetTechStack}</DetailRow>
                  <DetailRow label="커리어 목표">{application.careerGoal}</DetailRow>
                </div>
              </section>

              <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                <h3 className="mb-4 text-base font-extrabold text-gray-900">개발 배경</h3>
                <div className="grid gap-4 md:grid-cols-2">
                  <DetailRow label="사용 언어"><ChipList items={application.languages} /></DetailRow>
                  <DetailRow label="경험 플랫폼"><ChipList items={application.platforms} /></DetailRow>
                  <DetailRow label="컴퓨터 전공 여부">{application.isCsMajor ? '전공자' : '비전공자'}</DetailRow>
                  <DetailRow label="학습 경로"><ChipList items={application.learningPaths} /></DetailRow>
                  <DetailRow label="실무 경력">{application.careerYears}</DetailRow>
                  <DetailRow label="제출일">{application.submittedAt ? formatDate(application.submittedAt) : '-'}</DetailRow>
                </div>
              </section>

              <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                <h3 className="mb-4 text-base font-extrabold text-gray-900">프로젝트 및 학습 가능 시간</h3>
                <div className="grid gap-4 md:grid-cols-2">
                  <DetailRow label="GitHub">
                    {application.githubUrl ? (
                      <a href={application.githubUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 underline underline-offset-2">
                        {application.githubUrl}
                      </a>
                    ) : '-'}
                  </DetailRow>
                  <DetailRow label="프로젝트 경험">{application.projectCount}</DetailRow>
                  <DetailRow label="평일 가능 시간">{application.weekdayStudyHours}</DetailRow>
                  <DetailRow label="주말 가능 시간">{application.weekendStudyHours}</DetailRow>
                  <div className="md:col-span-2">
                    <DetailRow label="프로젝트 설명">{application.projectDescription}</DetailRow>
                  </div>
                </div>
              </section>

              <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                <h3 className="mb-4 text-base font-extrabold text-gray-900">멘티 메모</h3>
                <div className="grid gap-4">
                  <DetailRow label="멘토링 목표">{application.goal}</DetailRow>
                  <DetailRow label="희망 멘토 스타일">{application.personality}</DetailRow>
                  <DetailRow label="자기소개">{application.selfIntroduction}</DetailRow>
                </div>
              </section>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function MenteeCard({ matching }: { matching: MatchingResponse }) {
  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-all hover:border-blue-100 hover:shadow-lg">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400">
            <User size={18} className="text-white" />
          </div>
          <div>
            <p className="font-bold text-gray-900">{matching.mentorName}</p>
            <span className="mt-0.5 inline-block rounded-md border border-blue-100 bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-600">
              {matching.category}
            </span>
          </div>
        </div>
        <StatusBadge status={matching.status} />
      </div>

      {matching.message && (
        <div className="mb-3 flex items-start gap-2 rounded-xl bg-gray-50 p-3">
          <MessageSquare size={14} className="mt-0.5 shrink-0 text-gray-400" />
          <p className="line-clamp-2 text-sm text-gray-600">{matching.message}</p>
        </div>
      )}

      <div className="mt-3 border-t border-gray-50 pt-3">
        <span className="text-xs text-gray-400">{formatDate(matching.createdAt)}</span>
      </div>
    </div>
  );
}

function MentorCard({
  matching,
  onViewApplication,
}: {
  matching: MatchingResponse;
  onViewApplication: (matching: MatchingResponse) => void;
}) {
  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-all hover:border-blue-100 hover:shadow-lg">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-pink-500">
            <User size={18} className="text-white" />
          </div>
          <div>
            <p className="font-bold text-gray-900">{matching.menteeName}</p>
            <span className="mt-0.5 inline-block rounded-md border border-blue-100 bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-600">
              {matching.category}
            </span>
          </div>
        </div>
        <StatusBadge status={matching.status} />
      </div>

      {matching.message && (
        <div className="mb-3 flex items-start gap-2 rounded-xl bg-gray-50 p-3">
          <MessageSquare size={14} className="mt-0.5 shrink-0 text-gray-400" />
          <p className="line-clamp-2 text-sm text-gray-600">{matching.message}</p>
        </div>
      )}

      <div className="mt-3 flex items-center justify-between gap-3 border-t border-gray-50 pt-3">
        <span className="text-xs text-gray-400">{formatDate(matching.createdAt)}</span>
        {matching.applicationId ? (
          <button
            type="button"
            onClick={() => onViewApplication(matching)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-black"
          >
            <FileText size={12} />
            신청서 보기
          </button>
        ) : (
          <span className="text-xs text-gray-400">연결된 신청서 없음</span>
        )}
      </div>
    </div>
  );
}

export default function MatchingPage() {
  const router = useRouter();
  const { user, isLoading: authLoading, isLoggedIn } = useAuth();
  const [activeTab, setActiveTab] = useState<TabType>('mentee');
  const [menteeMatchings, setMenteeMatchings] = useState<MatchingResponse[]>([]);
  const [mentorMatchings, setMentorMatchings] = useState<MatchingResponse[]>([]);
  const [menteeLoading, setMenteeLoading] = useState(false);
  const [mentorLoading, setMentorLoading] = useState(false);
  const [menteeError, setMenteeError] = useState('');
  const [mentorError, setMentorError] = useState('');
  const [selectedApplicationMatching, setSelectedApplicationMatching] = useState<MatchingResponse | null>(null);
  const [selectedApplication, setSelectedApplication] = useState<ApplicationResponse | null>(null);
  const [applicationLoading, setApplicationLoading] = useState(false);
  const [applicationError, setApplicationError] = useState('');

  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.push('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    if (!isLoggedIn || activeTab !== 'mentee') return;

    const load = async () => {
      setMenteeLoading(true);
      setMenteeError('');
      try {
        const res = await getMyMatchingsAsMentee();
        if (res.success) {
          setMenteeMatchings(res.data);
        } else {
          setMenteeError(res.message || '매칭 내역을 불러오지 못했습니다.');
        }
      } catch {
        setMenteeError('매칭 내역을 불러오지 못했습니다.');
      } finally {
        setMenteeLoading(false);
      }
    };

    load();
  }, [activeTab, isLoggedIn]);

  useEffect(() => {
    if (!isLoggedIn || activeTab !== 'mentor' || user?.role !== 'MENTOR') return;

    const load = async () => {
      setMentorLoading(true);
      setMentorError('');
      try {
        const res = await getMyMatchingsAsMentor();
        if (res.success) {
          setMentorMatchings(res.data);
        } else {
          setMentorError(res.message || '멘티 매칭 내역을 불러오지 못했습니다.');
        }
      } catch {
        setMentorError('멘티 매칭 내역을 불러오지 못했습니다.');
      } finally {
        setMentorLoading(false);
      }
    };

    load();
  }, [activeTab, isLoggedIn, user?.role]);

  const handleViewApplication = async (matching: MatchingResponse) => {
    setSelectedApplicationMatching(matching);
    setSelectedApplication(null);
    setApplicationError('');
    setApplicationLoading(true);

    try {
      const res = await getMatchingApplication(matching.id);
      if (res.success) {
        setSelectedApplication(res.data);
      } else {
        setApplicationError(res.message || '신청서를 불러오지 못했습니다.');
      }
    } catch {
      setApplicationError('신청서를 불러오지 못했습니다.');
    } finally {
      setApplicationLoading(false);
    }
  };

  const closeApplicationModal = () => {
    setSelectedApplicationMatching(null);
    setSelectedApplication(null);
    setApplicationError('');
    setApplicationLoading(false);
  };

  if (authLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <Loader2 size={32} className="animate-spin text-blue-500" />
      </div>
    );
  }

  if (!isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="bg-gray-900 pt-28 pb-16">
          <div className="mx-auto max-w-7xl px-6">
            <span className="mb-4 inline-block rounded-full bg-white/10 px-4 py-1.5 text-xs font-bold uppercase tracking-wider text-cyan-300">
              Matching
            </span>
            <h1 className="mb-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">매칭 내역</h1>
            <p className="max-w-xl text-lg text-gray-300">현재 진행 중인 멘토/멘티 매칭을 확인할 수 있습니다.</p>
          </div>
        </section>

        <section className="relative z-20 mx-auto mb-8 -mt-6 max-w-7xl px-6">
          <div className="inline-flex gap-1 rounded-2xl border border-gray-100 bg-white p-2 shadow-lg shadow-gray-200/50">
            <button
              type="button"
              onClick={() => setActiveTab('mentee')}
              className={`rounded-xl px-5 py-2.5 text-sm font-semibold transition-all ${
                activeTab === 'mentee' ? 'bg-gray-900 text-white shadow-sm' : 'border border-gray-200 bg-white text-gray-500 hover:bg-gray-50'
              }`}
            >
              멘티로 보기
            </button>
            {user?.role === 'MENTOR' && (
              <button
                type="button"
                onClick={() => setActiveTab('mentor')}
                className={`rounded-xl px-5 py-2.5 text-sm font-semibold transition-all ${
                  activeTab === 'mentor' ? 'bg-gray-900 text-white shadow-sm' : 'border border-gray-200 bg-white text-gray-500 hover:bg-gray-50'
                }`}
              >
                멘토로 보기
              </button>
            )}
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 pb-20">
          {activeTab === 'mentee' && (
            <>
              {menteeLoading ? (
                <LoadingState />
              ) : menteeError ? (
                <ErrorState message={menteeError} />
              ) : menteeMatchings.length === 0 ? (
                <EmptyState
                  title="아직 매칭 내역이 없습니다"
                  description="멘토링 신청서를 작성하면 승인된 멘토와 자동 매칭됩니다."
                  actionHref="/apply"
                  actionLabel="신청서 작성하기"
                />
              ) : (
                <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                  {menteeMatchings.map((matching) => (
                    <MenteeCard key={matching.id} matching={matching} />
                  ))}
                </div>
              )}
            </>
          )}

          {activeTab === 'mentor' && user?.role === 'MENTOR' && (
            <>
              {mentorLoading ? (
                <LoadingState />
              ) : mentorError ? (
                <ErrorState message={mentorError} />
              ) : mentorMatchings.length === 0 ? (
                <EmptyState
                  title="아직 매칭된 멘티가 없습니다"
                  description="자동 매칭된 멘티가 생기면 이곳에서 신청서와 함께 확인할 수 있습니다."
                  actionHref="/mentor/status"
                  actionLabel="멘토 상태 확인"
                />
              ) : (
                <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                  {mentorMatchings.map((matching) => (
                    <MentorCard key={matching.id} matching={matching} onViewApplication={handleViewApplication} />
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </main>
      <ApplicationDetailModal
        application={selectedApplication}
        matching={selectedApplicationMatching}
        loading={applicationLoading}
        error={applicationError}
        onClose={closeApplicationModal}
      />
      <Footer />
    </>
  );
}

function LoadingState() {
  return (
    <div className="flex items-center justify-center py-24">
      <Loader2 size={32} className="animate-spin text-blue-500" />
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <AlertCircle size={40} className="mb-3 text-red-400" />
      <p className="mb-1 text-lg font-semibold text-gray-700">문제가 발생했습니다</p>
      <p className="text-sm text-gray-400">{message}</p>
    </div>
  );
}

function EmptyState({
  title,
  description,
  actionHref,
  actionLabel,
}: {
  title: string;
  description: string;
  actionHref: string;
  actionLabel: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-gray-100">
        <MessageSquare size={28} className="text-gray-400" />
      </div>
      <p className="mb-1 text-lg font-semibold text-gray-700">{title}</p>
      <p className="mb-6 text-sm text-gray-400">{description}</p>
      <Link
        href={actionHref}
        className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500"
      >
        {actionLabel}
        <ArrowRight size={14} />
      </Link>
    </div>
  );
}

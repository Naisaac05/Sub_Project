'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  ArrowRight,
  Brain,
  CheckCircle2,
  Cloud,
  Database,
  ExternalLink,
  Layers,
  Layout,
  MessageSquarePlus,
  PencilLine,
  Server,
  Smartphone,
  Star,
} from 'lucide-react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyMatchingsAsMentee } from '@/lib/matching';
import {
  getCourseBySlug,
  getEnrollmentPlans,
  matchesCourseCategory,
  type CourseCatalogItem,
  type CourseReview,
} from '@/lib/course-catalog';
import type { MatchingResponse } from '@/lib/types';

const iconMap = {
  server: Server,
  database: Database,
  layout: Layout,
  smartphone: Smartphone,
  cloud: Cloud,
  brain: Brain,
  layers: Layers,
};

const badgeColorMap = {
  blue: 'border-blue-400/30 bg-blue-400/10 text-blue-300',
  red: 'border-red-400/30 bg-red-400/10 text-red-300',
  orange: 'border-orange-400/30 bg-orange-400/10 text-orange-300',
};

type ReviewFormState = {
  rating: number;
  content: string;
};

const REVIEW_LABELS = {
  title: '\uC218\uAC15\uC0DD \uD6C4\uAE30',
  description:
    '\uC2E4\uC81C \uC218\uAC15\uC0DD\uC774 \uC791\uC131\uD55C \uD6C4\uAE30\uB9CC \uB178\uCD9C\uB429\uB2C8\uB2E4.',
  checking: '\uC218\uAC15 \uC774\uB825\uC744 \uD655\uC778\uD558\uB294 \uC911\uC785\uB2C8\uB2E4.',
  noPermission:
    '\uC218\uAC15 \uC774\uB825\uC774 \uC788\uB294 \uBA58\uD2F0\uB9CC \uD6C4\uAE30\uB97C \uC791\uC131\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4. \uC774 \uACFC\uC815\uC5D0 \uB9E4\uCE6D\uB418\uC5C8\uAC70\uB098 \uC218\uAC15\uC744 \uC644\uB8CC\uD55C \uB4A4 \uB2E4\uC2DC \uB0A8\uACA8\uC8FC\uC138\uC694.',
  noPermissionTitle:
    '\uC218\uAC15\uC0DD \uD6C4\uAE30\uB294 \uC2E4\uC81C \uC218\uAC15\uC0DD\uB9CC \uC791\uC131\uD560 \uC218 \uC788\uC5B4\uC694.',
  writeAllowed:
    '\uC774 \uACFC\uC815\uC758 \uC2E4\uC81C \uC218\uAC15 \uC774\uB825\uC774 \uD655\uC778\uB418\uC5B4 \uD6C4\uAE30\uB97C \uC791\uC131\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.',
};

const FALLBACK_COURSE: CourseCatalogItem = {
  slug: 'fallback',
  title: '멘토링 코스',
  shortTitle: '멘토링 코스',
  categoryLabel: 'Mentoring',
  iconKey: 'layout',
  summary: '선택한 과정 정보를 불러오지 못해 기본 안내를 보여주고 있습니다.',
  headline: '과정 정보가 아직 준비되지 않았습니다.',
  description: '코스 목록으로 돌아가 현재 열려 있는 과정을 확인해 주세요.',
  level: '안내',
  durationLabel: '확인 필요',
  matchKeywords: [],
  outcomes: ['현재 열려 있는 코스 확인', '오픈 예정 과정 살펴보기'],
  sections: [
    {
      title: '안내',
      summary: '존재하지 않거나 아직 공개되지 않은 코스입니다.',
      bullets: ['멘토링 코스 목록으로 이동', '오픈된 과정 확인', '준비 중인 과정 살펴보기'],
    },
  ],
  availability: 'upcoming',
  comingSoonNote: '코스 목록으로 돌아가 현재 열려 있는 트랙을 확인해 주세요.',
};

function getStoredReviews(slug: string) {
  if (typeof window === 'undefined') {
    return [];
  }

  const raw = window.localStorage.getItem(`course-reviews:${slug}`);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as CourseReview[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveStoredReviews(slug: string, reviews: CourseReview[]) {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(`course-reviews:${slug}`, JSON.stringify(reviews));
}

export default function CourseDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuth();
  const course = useMemo(() => getCourseBySlug(params?.id) ?? FALLBACK_COURSE, [params?.id]);
  const Icon = iconMap[course.iconKey];
  const enrollmentPlans = useMemo(() => getEnrollmentPlans(), []);

  const [reviews, setReviews] = useState<CourseReview[]>([]);
  const [reviewForm, setReviewForm] = useState<ReviewFormState>({ rating: 5, content: '' });
  const [reviewMessage, setReviewMessage] = useState('');
  const [canWriteReview, setCanWriteReview] = useState(false);
  const [reviewLoading, setReviewLoading] = useState(true);

  useEffect(() => {
    setReviews(getStoredReviews(course.slug));
  }, [course.slug]);

  useEffect(() => {
    const checkReviewPermission = async () => {
      if (authLoading) {
        return;
      }

      if (!user || user.role !== 'MENTEE' || course.availability !== 'open') {
        setCanWriteReview(false);
        setReviewLoading(false);
        return;
      }

      try {
        const response = await getMyMatchingsAsMentee();
        const eligible = response.data.some((matching: MatchingResponse) => {
          const canReviewStatus =
            matching.status === 'ACCEPTED' || matching.status === 'TRIAL' || matching.status === 'CANCELLED';
          return canReviewStatus && matchesCourseCategory(course, matching.category);
        });
        setCanWriteReview(eligible);
      } catch {
        setCanWriteReview(false);
      } finally {
        setReviewLoading(false);
      }
    };

    void checkReviewPermission();
  }, [authLoading, course, user]);

  const existingReview = user ? reviews.find((review) => review.authorId === user.id) : undefined;

  const handleCreateReview = () => {
    if (!user || !canWriteReview) {
      setReviewMessage(REVIEW_LABELS.noPermission);
      return;
    }

    const content = reviewForm.content.trim();
    if (!content) {
      setReviewMessage('후기 내용을 입력해 주세요.');
      return;
    }

    const nextReview: CourseReview = {
      authorId: user.id,
      authorName: user.name,
      rating: reviewForm.rating,
      content,
      createdAt: new Date().toISOString(),
    };

    const nextReviews = [nextReview, ...reviews.filter((review) => review.authorId !== user.id)];
    setReviews(nextReviews);
    saveStoredReviews(course.slug, nextReviews);
    setReviewForm({ rating: 5, content: '' });
    setReviewMessage('후기가 등록되었습니다.');
  };

  return (
    <>
      <Header />
      <main className="min-h-screen bg-[#050B14] pt-24 text-white">
        <section className="border-b border-white/10 bg-[radial-gradient(circle_at_top_left,_rgba(0,102,255,0.18),_transparent_36%),radial-gradient(circle_at_bottom_right,_rgba(0,168,255,0.14),_transparent_28%),#050B14]">
          <div className="mx-auto max-w-6xl px-6 py-16">
            <Link
              href="/mentors"
              className="inline-flex items-center gap-2 text-sm font-medium text-gray-400 transition-colors hover:text-white"
            >
              <ArrowLeft size={16} />
              멘토링 코스 목록으로 돌아가기
            </Link>

            <div className="mt-10 grid items-center gap-10 md:grid-cols-[1.2fr_0.8fr]">
              <div>
                <span className="rounded-full border border-blue-400/20 bg-blue-400/10 px-4 py-1.5 text-xs font-bold tracking-wider text-blue-300">
                  {course.categoryLabel}
                </span>
                <h1 className="mt-6 break-keep text-4xl font-extrabold tracking-tight sm:text-5xl">
                  {course.title}
                </h1>
                <p className="mt-5 break-keep text-xl leading-8 text-gray-300">{course.headline}</p>
                <p className="mt-5 break-keep text-base leading-8 text-gray-400">{course.description}</p>

                <div className="mt-8 flex flex-wrap gap-3">
                  <span className="rounded-full bg-white/5 px-4 py-2 text-sm font-medium text-gray-200">
                    {course.level}
                  </span>
                  <span className="rounded-full bg-white/5 px-4 py-2 text-sm font-medium text-gray-200">
                    {course.durationLabel}
                  </span>
                  <span
                    className={`rounded-full px-4 py-2 text-sm font-medium ${
                      course.availability === 'open'
                        ? 'bg-emerald-400/10 text-emerald-300'
                        : 'bg-amber-400/10 text-amber-300'
                    }`}
                  >
                    {course.availability === 'open' ? '현재 신청 가능' : '오픈 예정'}
                  </span>
                </div>

                <div className="mt-8 flex flex-wrap gap-4">
                  <button
                    onClick={() => router.push(`/apply?course=${course.slug}`)}
                    className="inline-flex items-center gap-2 rounded-2xl bg-blue-600 px-6 py-3 text-sm font-bold text-white transition-colors hover:bg-blue-500"
                  >
                    신청하기 <ArrowRight size={16} />
                  </button>
                  <button
                    onClick={() => document.getElementById('reviews')?.scrollIntoView({ behavior: 'smooth' })}
                    className="inline-flex items-center gap-2 rounded-2xl border border-white/10 bg-white/5 px-6 py-3 text-sm font-semibold text-gray-200 transition-colors hover:bg-white/10"
                  >
                    후기 보기
                  </button>
                </div>
              </div>

              <div className="rounded-[2rem] border border-blue-500/20 bg-gradient-to-br from-blue-500/15 to-cyan-400/10 p-8">
                <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-blue-500/15 text-blue-300">
                  <Icon size={38} />
                </div>
                <h2 className="mt-8 text-2xl font-bold text-white">이 과정을 통해 얻는 것</h2>
                <ul className="mt-6 space-y-4">
                  {course.outcomes.map((outcome) => (
                    <li key={outcome} className="flex items-start gap-3 text-sm leading-6 text-gray-200">
                      <CheckCircle2 size={18} className="mt-1 shrink-0 text-cyan-300" />
                      <span className="break-keep">{outcome}</span>
                    </li>
                  ))}
                </ul>
                {course.comingSoonNote && (
                  <p className="mt-8 rounded-2xl bg-white/5 p-4 text-sm leading-6 text-gray-300">
                    {course.comingSoonNote}
                  </p>
                )}
              </div>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-16">
          <div className="grid gap-6 md:grid-cols-3">
            {course.sections.map((section) => (
              <article key={section.title} className="rounded-3xl border border-white/10 bg-white/[0.03] p-7">
                <h2 className="text-xl font-bold text-white">{section.title}</h2>
                <p className="mt-3 break-keep text-sm leading-7 text-gray-400">{section.summary}</p>
                <ul className="mt-5 space-y-3">
                  {section.bullets.map((bullet) => (
                    <li key={bullet} className="flex items-start gap-3 text-sm leading-6 text-gray-200">
                      <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-400" />
                      <span className="break-keep">{bullet}</span>
                    </li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </section>

        <section id="reviews" className="border-y border-white/5 bg-[#08111d] py-16">
          <div className="mx-auto max-w-6xl px-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 className="text-3xl font-bold text-white">{REVIEW_LABELS.title}</h2>
                <p className="mt-3 break-keep text-sm leading-7 text-gray-400">
                  {REVIEW_LABELS.description}
                </p>
              </div>
              <div className="rounded-2xl bg-white/5 px-4 py-3 text-sm text-gray-300">
                {reviewLoading
                  ? REVIEW_LABELS.checking
                  : canWriteReview
                    ? REVIEW_LABELS.writeAllowed
                    : REVIEW_LABELS.noPermission}
              </div>
            </div>

            <div className="mt-10 grid gap-8 lg:grid-cols-[1fr_380px]">
              <div className="space-y-4">
                {reviews.length === 0 ? (
                  <div className="rounded-3xl border border-dashed border-white/10 bg-white/[0.03] p-8">
                    <p className="text-lg font-semibold text-white">아직 등록된 후기가 없습니다.</p>
                    <p className="mt-3 break-keep text-sm leading-7 text-gray-400">
                      첫 후기는 실제 수강 중이거나 수강 이력이 있는 멘티가 남길 수 있습니다.
                    </p>
                  </div>
                ) : (
                  reviews.map((review) => (
                    <article key={`${review.authorId}-${review.createdAt}`} className="rounded-3xl border border-white/10 bg-white/[0.03] p-7">
                      <div className="flex items-center justify-between gap-4">
                        <div>
                          <p className="text-lg font-semibold text-white">{review.authorName}</p>
                          <p className="text-xs text-gray-500">
                            {new Date(review.createdAt).toLocaleDateString('ko-KR')}
                          </p>
                        </div>
                        <div className="flex items-center gap-1 text-amber-300">
                          {Array.from({ length: review.rating }).map((_, index) => (
                            <Star key={index} size={14} className="fill-current" />
                          ))}
                        </div>
                      </div>
                      <p className="mt-5 break-keep text-sm leading-7 text-gray-300">{review.content}</p>
                    </article>
                  ))
                )}
              </div>

              <aside className="rounded-3xl border border-white/10 bg-white/[0.04] p-7">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-500/15 text-blue-300">
                    <MessageSquarePlus size={20} />
                  </div>
                  <div>
                    <h3 className="text-xl font-bold text-white">후기 작성</h3>
                    <p className="text-sm text-gray-400">가짜 후기는 노출하지 않습니다.</p>
                  </div>
                </div>

                {existingReview && (
                  <div className="mt-6 rounded-2xl border border-emerald-400/20 bg-emerald-400/10 p-4 text-sm text-emerald-200">
                    이미 이 과정에 후기를 남겼습니다. 새로 등록하면 기존 후기가 최신 내용으로 교체됩니다.
                  </div>
                )}

                {!reviewLoading && !canWriteReview && (
                  <div className="mt-6 rounded-2xl border border-amber-400/20 bg-amber-400/10 p-4">
                    <p className="break-keep text-sm font-semibold text-amber-200">{REVIEW_LABELS.noPermissionTitle}</p>
                    <p className="mt-2 break-keep text-sm leading-6 text-amber-100/80">{REVIEW_LABELS.noPermission}</p>
                  </div>
                )}


                <div className="mt-6">
                  <label className="mb-3 block text-sm font-semibold text-gray-200">별점</label>
                  <div className="flex gap-2">
                    {[5, 4, 3, 2, 1].map((rating) => (
                      <button
                        key={rating}
                        onClick={() => setReviewForm((prev) => ({ ...prev, rating }))}
                        className={`rounded-2xl px-3 py-2 text-sm font-semibold transition-colors ${
                          reviewForm.rating === rating
                            ? 'bg-blue-600 text-white'
                            : 'bg-white/5 text-gray-300 hover:bg-white/10'
                        }`}
                        disabled={!canWriteReview}
                      >
                        {rating}점
                      </button>
                    ))}
                  </div>
                </div>

                <div className="mt-6">
                  <label className="mb-3 block text-sm font-semibold text-gray-200">후기 내용</label>
                  <textarea
                    value={reviewForm.content}
                    onChange={(event) => setReviewForm((prev) => ({ ...prev, content: event.target.value }))}
                    placeholder={
                      canWriteReview
                        ? '실제 수강 경험을 바탕으로 도움이 되었던 점을 남겨 주세요.'
                        : '실제 수강 이력이 있는 멘티에게만 후기 작성이 열립니다.'
                    }
                    className="min-h-[180px] w-full rounded-2xl border border-white/10 bg-white/5 p-4 text-sm leading-7 text-white outline-none transition-colors placeholder:text-gray-500 focus:border-blue-500"
                    disabled={!canWriteReview}
                  />
                </div>

                {reviewMessage && (
                  <p className="mt-4 break-keep text-sm leading-6 text-gray-300">{reviewMessage}</p>
                )}

                <button
                  onClick={handleCreateReview}
                  disabled={!canWriteReview}
                  className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-3 text-sm font-bold text-white transition-colors hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-400"
                >
                  <PencilLine size={16} />
                  후기 등록하기
                </button>
              </aside>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-6 py-16">
          <div className="mb-8">
            <h2 className="text-3xl font-bold text-white">신청하기</h2>
            <p className="mt-3 break-keep text-sm leading-7 text-gray-400">
              얼리버드 차수는 현재 날짜를 기준으로 자동 계산되며, 각 차수의 마감일은 해당 월 10일 기준으로 표시됩니다.
            </p>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            {enrollmentPlans.map((plan) => (
              <article key={plan.id} className="flex flex-col rounded-3xl border border-white/10 bg-white/[0.03] p-7">
                <div className="flex items-start justify-between gap-4">
                  <h3 className="break-keep text-2xl font-bold text-white">{plan.title}</h3>
                  <span className={`rounded-full border px-3 py-1 text-xs font-semibold ${badgeColorMap[plan.badgeTone]}`}>
                    {plan.badge}
                  </span>
                </div>
                <p className="mt-4 break-keep text-sm leading-7 text-gray-400">{plan.desc}</p>

                <div className="mt-6 rounded-2xl bg-white/5 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wider text-gray-500">진행 기간</p>
                  <p className="mt-2 text-sm font-medium text-gray-200">{plan.duration}</p>
                </div>

                <div className="mt-6 border-t border-white/5 pt-6">
                  <p className="text-sm text-gray-500 line-through">
                    {plan.originalPrice.toLocaleString('ko-KR')}원
                  </p>
                  <p className="mt-2 text-3xl font-extrabold tracking-tight text-white">
                    {plan.price.toLocaleString('ko-KR')}원
                  </p>
                  <p className="mt-2 text-sm text-gray-400">{plan.monthly}</p>
                </div>

                <button
                  onClick={() => router.push(`/apply?course=${course.slug}`)}
                  className="mt-8 inline-flex items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-4 text-sm font-bold text-white transition-colors hover:bg-blue-500"
                >
                  이 플랜으로 신청하기
                  <ExternalLink size={16} />
                </button>
              </article>
            ))}
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}

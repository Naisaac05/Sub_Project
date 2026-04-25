'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import {
  ArrowRight,
  Brain,
  Cloud,
  Database,
  Layers,
  Layout,
  Loader2,
  Server,
  Smartphone,
} from 'lucide-react';
import { fetchAvailableCourseSummaries, fetchCourseSummaries } from '@/lib/courses';
import { COURSE_CATALOG, getCourseBySlug, type CourseCatalogItem } from '@/lib/course-catalog';
import type { CourseSummary } from '@/lib/types';

const iconMap = {
  server: Server,
  database: Database,
  layout: Layout,
  smartphone: Smartphone,
  cloud: Cloud,
  brain: Brain,
  layers: Layers,
};

function fallbackCourse(course: CourseSummary): CourseCatalogItem {
  return {
    slug: course.courseKey,
    title: course.title,
    shortTitle: course.title,
    categoryLabel: 'Mentoring',
    iconKey: 'layout',
    summary: '승인된 멘토가 배정 가능한 멘토링 코스입니다.',
    headline: course.title,
    description: '멘토링 신청서를 작성하면 이 코스의 승인 멘토와 자동 매칭됩니다.',
    level: 'All levels',
    durationLabel: '4개월',
    matchKeywords: [course.courseKey],
    outcomes: ['코스 담당 멘토 자동 매칭', '멘티 신청서 기반 멘토링 진행'],
    sections: [],
    availability: 'open',
  };
}

function CourseCard({ course }: { course: CourseCatalogItem }) {
  const Icon = iconMap[course.iconKey];

  return (
    <Link
      href={`/mentors/${course.slug}`}
      className="group rounded-3xl border border-white/10 bg-white/[0.03] p-7 transition-all duration-300 hover:border-blue-400/40 hover:bg-white/[0.05]"
    >
      <div className="mb-5 flex items-center justify-between">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-500/10 text-blue-400">
          <Icon size={22} />
        </div>
        <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-xs font-semibold text-emerald-300">
          신청 가능
        </span>
      </div>
      <h2 className="break-keep text-2xl font-bold text-white">{course.title}</h2>
      <p className="mt-3 break-keep text-sm leading-6 text-gray-400">{course.summary}</p>
      <div className="mt-5 flex flex-wrap gap-2">
        {course.outcomes.slice(0, 2).map((item) => (
          <span
            key={item}
            className="rounded-full bg-white/5 px-3 py-1 text-xs font-medium text-gray-300"
          >
            {item}
          </span>
        ))}
      </div>
      <div className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-blue-400">
        코스 자세히 보기 <ArrowRight size={16} className="transition-transform group-hover:translate-x-1" />
      </div>
    </Link>
  );
}

async function withTimeout<T>(promise: Promise<T>, timeoutMs = 5000): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout>;
  const timeout = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error('Course request timed out')), timeoutMs);
  });

  return Promise.race([promise, timeout]).finally(() => clearTimeout(timeoutId));
}

export default function MentorsPage() {
  const [availableSummaries, setAvailableSummaries] = useState<CourseSummary[]>([]);
  const [allSummaries, setAllSummaries] = useState<CourseSummary[]>([]);
  const [availableLoading, setAvailableLoading] = useState(true);

  useEffect(() => {
    withTimeout(fetchAvailableCourseSummaries())
      .then(setAvailableSummaries)
      .catch(() => setAvailableSummaries([]))
      .finally(() => setAvailableLoading(false));

    withTimeout(fetchCourseSummaries())
      .then(setAllSummaries)
      .catch(() => setAllSummaries([]));
  }, []);

  const availableCourses = useMemo(
    () => availableSummaries.map((course) => getCourseBySlug(course.courseKey) ?? fallbackCourse(course)),
    [availableSummaries],
  );
  const upcomingCourses = useMemo(() => {
    const availableKeys = new Set(availableSummaries.map((course) => course.courseKey));
    const allKeys = new Set(allSummaries.map((course) => course.courseKey));
    const inactiveOrUnavailableCourses = allSummaries
      .filter((course) => !availableKeys.has(course.courseKey))
      .map((course) => getCourseBySlug(course.courseKey) ?? fallbackCourse(course));
    const staticOnlyCourses = COURSE_CATALOG.filter(
      (course) => !availableKeys.has(course.slug) && !allKeys.has(course.slug),
    );

    return [...inactiveOrUnavailableCourses, ...staticOnlyCourses];
  }, [allSummaries, availableSummaries]);

  return (
    <>
      <Header />
      <main className="min-h-screen bg-[#060B14] pt-28 text-white">
        <section className="border-b border-white/10 bg-[radial-gradient(circle_at_top_left,_rgba(0,102,255,0.18),_transparent_42%),radial-gradient(circle_at_bottom_right,_rgba(0,168,255,0.14),_transparent_34%),#060B14]">
          <div className="mx-auto max-w-7xl px-6 py-20">
            <span className="rounded-full border border-blue-400/20 bg-blue-400/10 px-4 py-1.5 text-xs font-bold tracking-wider text-blue-300">
              Mentoring Courses
            </span>
            <h1 className="mt-6 max-w-3xl break-keep text-4xl font-extrabold tracking-tight sm:text-5xl">
              멘토가 배정 가능한 코스만 신청할 수 있습니다
            </h1>
            <p className="mt-6 max-w-2xl break-keep text-lg leading-8 text-gray-400">
              승인된 멘토가 담당 중인 코스만 신청 가능한 과정에 표시됩니다. 코스를 선택한 뒤 수강신청을 누르면 신청서에 해당 코스가 자동으로 설정됩니다.
            </p>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 py-16">
          <div className="mb-8">
            <h2 className="text-3xl font-bold text-white">지금 신청 가능한 과정</h2>
            <p className="mt-2 text-sm text-gray-400">실제 승인 멘토가 연결된 코스만 표시됩니다.</p>
          </div>

          {availableLoading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 size={32} className="animate-spin text-blue-400" />
            </div>
          ) : availableCourses.length === 0 ? (
            <div className="rounded-3xl border border-dashed border-white/10 bg-white/[0.03] p-8 text-gray-300">
              현재 신청 가능한 코스가 없습니다. 승인된 멘토가 코스에 연결되면 이곳에 표시됩니다.
            </div>
          ) : (
            <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
              {availableCourses.map((course) => (
                <CourseCard key={course.slug} course={course} />
              ))}
            </div>
          )}
        </section>

        <section className="mx-auto max-w-7xl px-6 pb-20">
          <div className="mb-8">
            <h2 className="text-3xl font-bold text-white">준비 중인 과정</h2>
            <p className="mt-2 text-sm text-gray-400">멘토 배정 준비가 완료되면 신청 가능한 과정으로 이동합니다.</p>
          </div>
          <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
            {upcomingCourses.map((course) => (
              <Link
                key={course.slug}
                href={`/mentors/${course.slug}`}
                className="rounded-3xl border border-dashed border-white/10 bg-white/[0.02] p-7 transition-colors hover:border-white/20 hover:bg-white/[0.04]"
              >
                <div className="mb-5 flex items-center justify-between">
                  <span className="rounded-full bg-white/5 px-3 py-1 text-xs font-semibold text-gray-300">
                    {course.categoryLabel}
                  </span>
                  <span className="rounded-full bg-amber-500/10 px-3 py-1 text-xs font-semibold text-amber-300">
                    준비 중
                  </span>
                </div>
                <h3 className="break-keep text-xl font-bold text-white">{course.title}</h3>
                <p className="mt-3 break-keep text-sm leading-6 text-gray-400">{course.summary}</p>
              </Link>
            ))}
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}

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
  Server,
  Smartphone,
} from 'lucide-react';
import { getOpenCourses, getUpcomingCourses, type CourseCatalogItem } from '@/lib/course-catalog';

const iconMap = {
  server: Server,
  database: Database,
  layout: Layout,
  smartphone: Smartphone,
  cloud: Cloud,
  brain: Brain,
  layers: Layers,
};

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
        <span className="rounded-full border border-white/10 px-3 py-1 text-xs font-semibold text-gray-400">
          {course.categoryLabel}
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
        자세히 보기 <ArrowRight size={16} className="transition-transform group-hover:translate-x-1" />
      </div>
    </Link>
  );
}

export default function MentorsPage() {
  const openCourses = getOpenCourses();
  const upcomingCourses = getUpcomingCourses();

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
              분야별 멘토링 코스를 한 곳에서 보고
              <br />
              지금 열려 있는 과정부터 바로 시작할 수 있습니다.
            </h1>
            <p className="mt-6 max-w-2xl break-keep text-lg leading-8 text-gray-400">
              백엔드, 프론트엔드, 모바일까지 실제 포트폴리오와 코드 리뷰 중심으로 설계한 코스를 모았습니다.
              아직 준비 중인 과정은 오픈 예정 상태로 따로 안내합니다.
            </p>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 py-16">
          <div className="mb-8">
            <h2 className="text-3xl font-bold text-white">지금 신청 가능한 과정</h2>
            <p className="mt-2 text-sm text-gray-400">현재 바로 상세 정보를 확인하고 신청할 수 있는 코스입니다.</p>
          </div>
          <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
            {openCourses.map((course) => (
              <CourseCard key={course.slug} course={course} />
            ))}
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 pb-20">
          <div className="mb-8">
            <h2 className="text-3xl font-bold text-white">오픈 예정 과정</h2>
            <p className="mt-2 text-sm text-gray-400">세부 커리큘럼과 멘토 구성을 마무리하는 중인 과정입니다.</p>
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

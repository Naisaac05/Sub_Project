'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getTests } from '@/lib/test';
import type { TestListResponse } from '@/lib/types';
import {
  ArrowRight,
  BarChart3,
  CheckCircle,
  Clock,
  Code2,
  FileText,
  Loader2,
} from 'lucide-react';

const LABELS = {
  eyebrow: '\uC9C4\uB2E8\uC6A9 Skill Test',
  title: '\uCF54\uC2A4\uBCC4 \uC2E4\uB825 \uD14C\uC2A4\uD2B8',
  description:
    '\uC9C0\uC6D0\uD558\uB824\uB294 \uBA58\uD1A0\uB9C1 \uCF54\uC2A4 \uAE30\uC900\uC73C\uB85C \uD604\uC7AC \uC2E4\uB825\uC744 \uC810\uC218\uD654\uD569\uB2C8\uB2E4. \uC774 \uD14C\uC2A4\uD2B8\uB294 \uD569\uACA9/\uBD88\uD569\uACA9\uBCF4\uB2E4 \uC9C4\uB2E8\uACFC \uBCF5\uC2B5 \uBC29\uD5A5\uC744 \uC815\uD558\uAE30 \uC704\uD55C \uC6A9\uB3C4\uC785\uB2C8\uB2E4.',
  myResults: '\uB0B4 \uD14C\uC2A4\uD2B8 \uACB0\uACFC',
  loading: '\uD14C\uC2A4\uD2B8\uB97C \uBD88\uB7EC\uC624\uB294 \uC911\uC785\uB2C8\uB2E4.',
  error: '\uD14C\uC2A4\uD2B8 \uBAA9\uB85D\uC744 \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.',
  retry: '\uB2E4\uC2DC \uBD88\uB7EC\uC624\uAE30',
  readySection: '\uC9C0\uAE08 \uD480 \uC218 \uC788\uB294 \uC9C4\uB2E8 \uD14C\uC2A4\uD2B8',
  readyDescription: '\uBA58\uD1A0\uB9C1 \uCF54\uC2A4\uBCC4 \uC9C4\uB2E8 \uD14C\uC2A4\uD2B8\uB97C \uC81C\uACF5\uD569\uB2C8\uB2E4.',
  preparingSection: '\uCD94\uAC00 \uC608\uC815 \uCF54\uC2A4',
  preparingDescription:
    '\uBB38\uC81C \uD488\uC9C8\uC744 \uB9DE\uCD98 \uB4A4 \uC21C\uCC28\uC801\uC73C\uB85C \uC5F4\uC5B4\uB450\uACA0\uC2B5\uB2C8\uB2E4.',
  start: '\uD14C\uC2A4\uD2B8 \uC2DC\uC791',
  preparing: '\uC900\uBE44 \uC911',
  questionCount: '\uBB38\uD56D',
  minutes: '\uBD84',
  passingScore: '\uAE30\uC900\uC810\uC218',
  diagnosticNoteTitle: '\uC9C4\uB2E8\uC6A9 \uD14C\uC2A4\uD2B8\uC785\uB2C8\uB2E4',
  diagnosticNote:
    '\uC810\uC218\uB294 \uD604\uC7AC \uC0C1\uD0DC\uB97C \uD655\uC778\uD558\uAE30 \uC704\uD55C \uC2E0\uD638\uC785\uB2C8\uB2E4. \uCD94\uD6C4 \uD2C0\uB9B0 \uBB38\uC81C \uAE30\uBC18 \uAF2C\uB9AC\uC9C8\uBB38\uACFC \uBA58\uD1A0 \uD655\uC778 \uD654\uBA74\uC5D0 \uC5F0\uACB0\uD560 \uC218 \uC788\uAC8C \uD655\uC7A5\uD560 \uC608\uC815\uC785\uB2C8\uB2E4.',
};

const COURSE_ORDER = [
  'java-backend',
  'node-backend',
  'python-backend',
  'frontend',
  'android',
  'ios',
  'flutter',
  'react-native',
  'devops',
  'data-engineer',
  'ml-engineer',
  'game-server',
  'kafka',
  'distributed-lock',
] as const;

const COURSE_META: Record<
  string,
  { title: string; description: string; accent: string; topics: string[] }
> = {
  'java-backend': {
    title: 'Java Backend + AI',
    description: 'Java, Spring, JPA, REST API, \uD2B8\uB79C\uC7AD\uC158',
    accent: 'from-blue-600 to-cyan-500',
    topics: ['Java', 'Spring', 'JPA', 'REST API', '\uD2B8\uB79C\uC7AD\uC158'],
  },
  'node-backend': {
    title: 'Node Backend + AI',
    description: 'TypeScript, Express/Nest, \uBE44\uB3D9\uAE30 \uCC98\uB9AC, API \uC124\uACC4',
    accent: 'from-lime-600 to-emerald-500',
    topics: ['TypeScript', 'Node.js', 'NestJS', '\uBE44\uB3D9\uAE30', 'API'],
  },
  frontend: {
    title: 'Frontend + AI',
    description: 'React, Next.js, \uC0C1\uD0DC \uAD00\uB9AC, \uB80C\uB354\uB9C1, CSS/UI',
    accent: 'from-violet-600 to-fuchsia-500',
    topics: ['React', 'Next.js', '\uC0C1\uD0DC \uAD00\uB9AC', '\uB80C\uB354\uB9C1', 'CSS/UI'],
  },
  'python-backend': {
    title: 'Python Backend + AI',
    description: 'Python, FastAPI/Django, ORM, \uBE44\uB3D9\uAE30, \uB370\uC774\uD130 \uCC98\uB9AC',
    accent: 'from-emerald-600 to-teal-500',
    topics: ['Python', 'FastAPI/Django', 'ORM', '\uBE44\uB3D9\uAE30', '\uB370\uC774\uD130'],
  },
  android: {
    title: 'Android + AI',
    description: 'Kotlin, Android \uC0DD\uBA85\uC8FC\uAE30, \uC0C1\uD0DC, \uB124\uC774\uD2F0\uBE0C \uAE30\uB2A5',
    accent: 'from-green-600 to-lime-500',
    topics: ['Kotlin', 'ViewModel', 'Room', 'Compose', '\uBC30\uD3EC'],
  },
  ios: {
    title: 'iOS + AI',
    description: 'Swift, iOS \uC0DD\uBA85\uC8FC\uAE30, SwiftUI/UIKit, \uBC30\uD3EC',
    accent: 'from-sky-600 to-blue-500',
    topics: ['Swift', 'SwiftUI', 'UIKit', 'URLSession', '\uBC30\uD3EC'],
  },
  flutter: {
    title: 'Flutter + AI',
    description: 'Dart, Flutter Widget, \uC0C1\uD0DC \uAD00\uB9AC, \uB124\uC774\uD2F0\uBE0C \uC5F0\uB3D9',
    accent: 'from-cyan-600 to-sky-500',
    topics: ['Dart', 'Widget', 'State', 'Platform Channel', '\uBC30\uD3EC'],
  },
  'react-native': {
    title: 'React Native + AI',
    description: 'React Native, \uBAA8\uBC14\uC77C \uC0C1\uD0DC, \uB124\uC774\uD2F0\uBE0C \uBAA8\uB4C8, \uBC30\uD3EC',
    accent: 'from-indigo-600 to-blue-500',
    topics: ['React Native', 'FlatList', 'Navigation', 'Native Module', '\uBC30\uD3EC'],
  },
  devops: {
    title: 'DevOps',
    description: 'Docker, CI/CD, Linux, AWS, \uB124\uD2B8\uC6CC\uD06C',
    accent: 'from-slate-700 to-gray-500',
    topics: ['Docker', 'CI/CD', 'Linux', 'AWS', '\uBAA8\uB2C8\uD130\uB9C1'],
  },
  'data-engineer': {
    title: 'Data Engineer + AI',
    description: 'SQL, ETL, \uD30C\uC774\uD504\uB77C\uC778, \uBC30\uCE58/\uC2A4\uD2B8\uB9AC\uBC0D',
    accent: 'from-orange-600 to-amber-500',
    topics: ['SQL', 'ETL', 'Pipeline', 'Batch', 'Streaming'],
  },
  'ml-engineer': {
    title: 'ML Engineer',
    description: '\uBAA8\uB378 \uAE30\uBCF8, \uB370\uC774\uD130 \uC804\uCC98\uB9AC, \uD3C9\uAC00 \uC9C0\uD45C, MLOps',
    accent: 'from-fuchsia-600 to-pink-500',
    topics: ['Model', 'Preprocess', 'Metrics', 'MLOps', 'LLM'],
  },
  'game-server': {
    title: 'Game Server',
    description: '\uC2E4\uC2DC\uAC04 \uD1B5\uC2E0, \uB3D9\uC2DC\uC131, \uC138\uC158, \uC131\uB2A5',
    accent: 'from-red-600 to-rose-500',
    topics: ['Realtime', 'Session', 'Concurrency', 'Matchmaking', 'Performance'],
  },
  kafka: {
    title: 'Kafka Deep Dive',
    description: 'Producer/Consumer, Partition, Offset, Consumer Group, DLQ',
    accent: 'from-zinc-800 to-neutral-600',
    topics: ['Kafka', 'Partition', 'Offset', 'Consumer Group', 'DLQ'],
  },
  'distributed-lock': {
    title: 'Distributed Lock Deep Dive',
    description: '\uBD84\uC0B0 \uB77D, Redis, DB Lock, TTL, \uBA71\uB4F1\uC131',
    accent: 'from-purple-700 to-indigo-500',
    topics: ['Redis Lock', 'DB Lock', 'TTL', 'Idempotency', 'Concurrency'],
  },
};

const PREPARING_COURSES: string[] = [];

function isDiagnosticTest(test: TestListResponse) {
  return COURSE_ORDER.includes(test.category as (typeof COURSE_ORDER)[number])
    && test.difficulty === 'INTERMEDIATE'
    && test.timeLimit === 30
    && test.passingScore === 60
    && test.questionCount === 10;
}

export default function TestsPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const [tests, setTests] = useState<TestListResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    if (authLoading || !isLoggedIn) {
      return;
    }

    let mounted = true;

    const fetchTests = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await getTests();
        if (!mounted) {
          return;
        }
        if (res.success) {
          setTests(res.data);
        } else {
          setError(res.message || LABELS.error);
        }
      } catch {
        if (mounted) {
          setError(LABELS.error);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    fetchTests();
    return () => {
      mounted = false;
    };
  }, [authLoading, isLoggedIn]);

  const diagnosticTests = useMemo(() => {
    return tests
      .filter(isDiagnosticTest)
      .sort((a, b) => COURSE_ORDER.indexOf(a.category as never) - COURSE_ORDER.indexOf(b.category as never));
  }, [tests]);

  if (authLoading || !isLoggedIn) {
    return null;
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="relative overflow-hidden bg-[#07111f] pt-28 pb-16">
          <div className="mx-auto max-w-7xl px-6">
            <span className="inline-flex rounded-full border border-cyan-400/20 bg-cyan-400/10 px-4 py-1.5 text-xs font-bold uppercase tracking-wider text-cyan-300">
              {LABELS.eyebrow}
            </span>
            <div className="mt-5 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
              <div className="max-w-3xl">
                <h1 className="break-keep text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
                  {LABELS.title}
                </h1>
                <p className="mt-4 break-keep text-base leading-7 text-gray-300 sm:text-lg">
                  {LABELS.description}
                </p>
              </div>
              <Link
                href="/tests/results"
                className="inline-flex w-fit items-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-gray-950 transition-colors hover:bg-gray-100"
              >
                {LABELS.myResults}
                <ArrowRight size={16} />
              </Link>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-6 py-12">
          <div className="mb-7">
            <h2 className="text-2xl font-extrabold text-gray-950">{LABELS.readySection}</h2>
            <p className="mt-2 text-sm text-gray-500">{LABELS.readyDescription}</p>
          </div>

          {loading ? (
            <div className="flex flex-col items-center justify-center gap-3 py-24 text-gray-500">
              <Loader2 size={34} className="animate-spin text-blue-500" />
              <p className="text-sm font-medium">{LABELS.loading}</p>
            </div>
          ) : null}

          {!loading && error ? (
            <div className="flex flex-col items-center gap-4 rounded-2xl border border-red-100 bg-white p-10">
              <p className="font-semibold text-red-500">{error}</p>
              <button
                onClick={() => window.location.reload()}
                className="rounded-xl bg-gray-950 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-gray-800"
              >
                {LABELS.retry}
              </button>
            </div>
          ) : null}

          {!loading && !error ? (
            <div className="grid gap-6 lg:grid-cols-3">
              {COURSE_ORDER.map((category) => {
                const test = diagnosticTests.find((item) => item.category === category);
                const meta = COURSE_META[category];

                return (
                  <article
                    key={category}
                    className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-blue-100/60"
                  >
                    <div className={`h-2 bg-gradient-to-r ${meta.accent}`} />
                    <div className="p-7">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-950 text-white">
                          <Code2 size={22} />
                        </div>
                        {test ? (
                          <span className="rounded-full border border-emerald-100 bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600">
                            OPEN
                          </span>
                        ) : (
                          <span className="rounded-full border border-gray-200 bg-gray-50 px-3 py-1 text-xs font-bold text-gray-400">
                            {LABELS.preparing}
                          </span>
                        )}
                      </div>

                      <h3 className="mt-5 text-xl font-extrabold text-gray-950">{meta.title}</h3>
                      <p className="mt-2 min-h-[48px] break-keep text-sm leading-6 text-gray-500">
                        {meta.description}
                      </p>

                      <div className="mt-5 flex flex-wrap gap-2">
                        {meta.topics.map((topic) => (
                          <span
                            key={topic}
                            className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600"
                          >
                            {topic}
                          </span>
                        ))}
                      </div>

                      <div className="mt-6 grid grid-cols-3 gap-3 border-t border-gray-100 pt-5 text-xs text-gray-500">
                        <span className="flex items-center gap-1.5">
                          <FileText size={14} />
                          {test?.questionCount ?? 10}
                          {LABELS.questionCount}
                        </span>
                        <span className="flex items-center gap-1.5">
                          <Clock size={14} />
                          {test?.timeLimit ?? 30}
                          {LABELS.minutes}
                        </span>
                        <span className="flex items-center gap-1.5">
                          <BarChart3 size={14} />
                          {test?.passingScore ?? 60}
                          {LABELS.passingScore}
                        </span>
                      </div>

                      <div className="mt-6">
                        {test ? (
                          <Link
                            href={`/tests/${test.id}`}
                            className={`flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r ${meta.accent} px-5 py-3 text-sm font-bold text-white shadow-lg shadow-blue-500/15 transition-opacity hover:opacity-90`}
                          >
                            {LABELS.start}
                            <ArrowRight size={16} />
                          </Link>
                        ) : (
                          <button
                            disabled
                            className="flex w-full cursor-not-allowed items-center justify-center rounded-xl bg-gray-100 px-5 py-3 text-sm font-bold text-gray-400"
                          >
                            {LABELS.preparing}
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : null}
        </section>

        <section className="mx-auto max-w-7xl px-6 pb-16">
          <div className="rounded-2xl border border-blue-100 bg-blue-50 p-6 sm:p-8">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-600 text-white">
                <CheckCircle size={22} />
              </div>
              <div>
                <h2 className="text-lg font-extrabold text-gray-950">{LABELS.diagnosticNoteTitle}</h2>
                <p className="mt-2 break-keep text-sm leading-6 text-gray-600">{LABELS.diagnosticNote}</p>
              </div>
            </div>
          </div>
        </section>

        {PREPARING_COURSES.length > 0 ? (
          <section className="mx-auto max-w-7xl px-6 pb-20">
            <div className="mb-6">
              <h2 className="text-2xl font-extrabold text-gray-950">{LABELS.preparingSection}</h2>
              <p className="mt-2 text-sm text-gray-500">{LABELS.preparingDescription}</p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {PREPARING_COURSES.map((course) => (
                <div
                  key={course}
                  className="flex items-center justify-between rounded-xl border border-gray-200 bg-white px-4 py-4"
                >
                  <span className="text-sm font-semibold text-gray-700">{course}</span>
                  <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-bold text-gray-400">
                    {LABELS.preparing}
                  </span>
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </main>
      <Footer />
    </>
  );
}

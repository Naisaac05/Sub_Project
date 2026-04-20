'use client';

import { useState, useEffect, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowLeft, Send, TriangleAlert, X, Plus } from 'lucide-react';

import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { fetchCourseSummaries } from '@/lib/courses';
import { applyAsMentor, getMyMentorProfile } from '@/lib/mentor';
import type { CourseSummary, MentorApplyRequest } from '@/lib/types';

import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert';

/* ───────────────────────────────
 * zod schema (backend DTO 매칭)
 * ─────────────────────────────── */
const MENTEE_LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'ANY'] as const;

const schema = z.object({
  bio: z
    .string()
    .max(1000, '자기소개는 1000자 이하여야 합니다.')
    .min(50, '자기소개는 최소 50자 이상 작성해 주세요.'),
  careerYears: z
    .number({ message: '숫자를 입력해 주세요.' })
    .int('정수로 입력해 주세요.')
    .min(1, '경력은 1년 이상이어야 합니다.'),
  courseKeys: z.array(z.string()).min(1, '코스를 1개 이상 선택해 주세요.'),
  techStack: z.array(z.string()).min(1, '기술 스택을 1개 이상 입력해 주세요.'),
  preferredMenteeLevel: z.enum(MENTEE_LEVELS, {
    message: '선호 멘티 수준을 선택해 주세요.',
  }),
  company: z.string().max(100, '소속은 100자 이하로 입력해 주세요.').optional().or(z.literal('')),
  jobTitle: z.string().max(100, '직책은 100자 이하로 입력해 주세요.').optional().or(z.literal('')),
  portfolioUrl: z
    .string()
    .max(500, '포트폴리오 URL은 500자 이하로 입력해 주세요.')
    .optional()
    .or(z.literal('')),
  education: z
    .string()
    .max(200, '학력은 200자 이하로 입력해 주세요.')
    .optional()
    .or(z.literal('')),
  certifications: z.array(z.string()).optional(),
  agreed: z.literal(true, { message: '동의가 필요합니다.' }),
});

type FormValues = z.infer<typeof schema>;

const LEVEL_LABELS: Record<(typeof MENTEE_LEVELS)[number], string> = {
  BEGINNER: '주니어',
  INTERMEDIATE: '미들',
  ADVANCED: '시니어',
  ANY: '무관',
};

/* ───────────────────────────────
 * TagInput (기술 스택 / 자격증용)
 * ─────────────────────────────── */
function TagInput({
  value,
  onChange,
  placeholder,
}: {
  value: string[];
  onChange: (next: string[]) => void;
  placeholder?: string;
}) {
  const [draft, setDraft] = useState('');

  const addTag = () => {
    const trimmed = draft.trim();
    if (!trimmed) return;
    if (value.includes(trimmed)) {
      setDraft('');
      return;
    }
    onChange([...value, trimmed]);
    setDraft('');
  };

  const removeTag = (tag: string) => {
    onChange(value.filter((v) => v !== tag));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      addTag();
    } else if (e.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1));
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        {value.map((tag) => (
          <span
            key={tag}
            className="inline-flex items-center gap-1 rounded-md bg-blue-50 px-2.5 py-1 text-sm font-medium text-blue-700 ring-1 ring-blue-200"
          >
            {tag}
            <button
              type="button"
              onClick={() => removeTag(tag)}
              className="rounded-full text-blue-500 hover:bg-blue-100 hover:text-blue-700"
              aria-label={`${tag} 제거`}
            >
              <X size={14} />
            </button>
          </span>
        ))}
      </div>
      <div className="flex gap-2">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
        />
        <Button type="button" variant="outline" onClick={addTag}>
          <Plus size={16} className="mr-1" />
          추가
        </Button>
      </div>
    </div>
  );
}

/* ───────────────────────────────
 * Page
 * ─────────────────────────────── */
export default function MentorApplyPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading, user } = useAuth();

  const [courses, setCourses] = useState<CourseSummary[]>([]);
  const [profileLoading, setProfileLoading] = useState(true);
  const [rejectedReason, setRejectedReason] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema) as any,
    defaultValues: {
      bio: '',
      careerYears: 1,
      courseKeys: [],
      techStack: [],
      preferredMenteeLevel: 'ANY',
      company: '',
      jobTitle: '',
      portfolioUrl: '',
      education: '',
      certifications: [],
      agreed: false as unknown as true,
    },
  });

  const bioValue = watch('bio') ?? '';

  // 로그인 가드
  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      alert('로그인이 필요한 페이지입니다.');
      router.replace('/auth/login?redirect=/mentor/apply');
    }
  }, [authLoading, isLoggedIn, router]);

  // 역할 가드 (MENTOR 전용)
  useEffect(() => {
    if (!authLoading && user && user.role !== 'MENTOR') {
      alert('멘토 계정만 접근할 수 있습니다.');
      router.replace('/');
    }
  }, [authLoading, user, router]);

  // 코스 목록
  useEffect(() => {
    fetchCourseSummaries()
      .then(setCourses)
      .catch(() => setCourses([]));
  }, []);

  // 현재 프로필 조회 (PENDING/APPROVED 는 /mentor/status, REJECTED 는 prefill)
  useEffect(() => {
    if (authLoading || !isLoggedIn) return;
    let cancelled = false;

    (async () => {
      try {
        const profile = await getMyMentorProfile();
        if (cancelled) return;
        if (!profile) {
          // NEW
          setProfileLoading(false);
          return;
        }
        if (profile.status === 'PENDING' || profile.status === 'APPROVED') {
          router.replace('/mentor/status');
          return;
        }
        // REJECTED — prefill
        setRejectedReason(profile.rejectedReason ?? null);
        reset({
          bio: profile.bio ?? '',
          careerYears: profile.careerYears ?? 1,
          courseKeys: profile.courses?.map((c) => c.courseKey) ?? [],
          techStack: profile.techStack ?? [],
          preferredMenteeLevel: (profile.preferredMenteeLevel ?? 'ANY') as FormValues['preferredMenteeLevel'],
          company: profile.company ?? '',
          jobTitle: profile.jobTitle ?? '',
          portfolioUrl: profile.portfolioUrl ?? '',
          education: profile.education ?? '',
          certifications: profile.certifications ?? [],
          agreed: false as unknown as true,
        });
        setProfileLoading(false);
      } catch (e) {
        console.error('멘토 프로필 조회 실패:', e);
        setProfileLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [authLoading, isLoggedIn, reset, router]);

  const courseKeys = watch('courseKeys');
  const selectedCourseSet = useMemo(() => new Set(courseKeys), [courseKeys]);

  const toggleCourse = (key: string) => {
    if (selectedCourseSet.has(key)) {
      setValue(
        'courseKeys',
        courseKeys.filter((k) => k !== key),
        { shouldValidate: true }
      );
    } else {
      setValue('courseKeys', [...courseKeys, key], { shouldValidate: true });
    }
  };

  const onSubmit = async (values: FormValues) => {
    setSubmitError(null);
    const payload: MentorApplyRequest = {
      courseKeys: values.courseKeys,
      techStack: values.techStack,
      careerYears: values.careerYears,
      company: values.company || undefined,
      jobTitle: values.jobTitle || undefined,
      portfolioUrl: values.portfolioUrl || undefined,
      education: values.education || undefined,
      certifications: values.certifications && values.certifications.length > 0 ? values.certifications : undefined,
      preferredMenteeLevel: values.preferredMenteeLevel,
      bio: values.bio,
    };

    try {
      await applyAsMentor(payload);
      router.push('/mentor/status');
    } catch (e: any) {
      const msg =
        e?.response?.data?.message ||
        e?.message ||
        '신청 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
      setSubmitError(msg);
      console.error('멘토 신청 오류:', e);
    }
  };

  if (authLoading || profileLoading) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-[#F8FAFC] py-24">
          <div className="mx-auto max-w-3xl px-6">
            <div className="h-40 animate-pulse rounded-xl bg-slate-100" />
          </div>
        </main>
        <Footer />
      </>
    );
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-[#F8FAFC] py-24">
        <div className="mx-auto max-w-3xl px-6">
          {/* 헤더 */}
          <div className="mb-8 flex items-start gap-3">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => router.back()}
              className="mt-1"
              aria-label="뒤로가기"
            >
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <div>
              <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">
                멘토 신청
              </h1>
              <p className="mt-2 text-base text-slate-500">
                프로필과 경력을 입력해 멘토링 매칭에 활용됩니다.
              </p>
            </div>
          </div>

          {/* 반려 배너 */}
          {rejectedReason && (
            <div className="mb-8 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              <div className="flex">
                <div className="w-1 shrink-0 bg-red-500" />
                <div className="flex-1 p-5">
                  <div className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-red-50 px-2.5 py-1 text-xs font-bold text-red-700 ring-1 ring-red-200">
                    <TriangleAlert size={12} />
                    이전 신청 반려됨
                  </div>
                  <h3 className="mb-2 text-base font-bold text-slate-900">
                    내용을 보완한 뒤 다시 제출해 주세요.
                  </h3>
                  <div className="rounded-lg bg-slate-50 p-3 ring-1 ring-slate-200">
                    <div className="mb-1 text-[11px] font-bold uppercase tracking-widest text-red-700">
                      반려 사유
                    </div>
                    <p className="text-[15px] font-medium leading-relaxed text-slate-900">
                      {rejectedReason}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            {/* A. 기본 정보 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">A. 기본 정보</CardTitle>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="space-y-2">
                  <Label htmlFor="bio">
                    자기소개 <span className="text-red-500">*</span>
                  </Label>
                  <Textarea
                    id="bio"
                    rows={6}
                    placeholder="멘토링에서 어떤 도움을 줄 수 있는지 작성해 주세요. (최소 50자)"
                    {...register('bio')}
                  />
                  <div className="flex items-center justify-between text-xs">
                    <p className="text-red-600">{errors.bio?.message}</p>
                    <span
                      className={`font-medium ${
                        bioValue.length > 1000 ? 'text-red-600' : 'text-slate-500'
                      }`}
                    >
                      {bioValue.length} / 1000
                    </span>
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="careerYears">
                    경력 연차 <span className="text-red-500">*</span>
                  </Label>
                  <div className="flex items-center gap-2">
                    <Input
                      id="careerYears"
                      type="number"
                      min={1}
                      className="w-32"
                      {...register('careerYears', { valueAsNumber: true })}
                    />
                    <span className="text-sm text-slate-500">년</span>
                  </div>
                  {errors.careerYears && (
                    <p className="text-xs text-red-600">{errors.careerYears.message}</p>
                  )}
                </div>
              </CardContent>
            </Card>

            {/* B. 전문 분야 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">B. 전문 분야</CardTitle>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="space-y-3">
                  <Label>
                    제공 가능한 코스 <span className="text-red-500">*</span>{' '}
                    <span className="text-xs font-normal text-slate-500">(복수 선택)</span>
                  </Label>
                  {courses.length === 0 ? (
                    <p className="text-sm text-slate-500">
                      코스 목록을 불러오는 중입니다…
                    </p>
                  ) : (
                    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                      {courses.map((c) => {
                        const checked = selectedCourseSet.has(c.courseKey);
                        return (
                          <label
                            key={c.courseKey}
                            className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors ${
                              checked
                                ? 'border-blue-500 bg-blue-50/50'
                                : 'border-slate-200 bg-white hover:border-slate-300'
                            }`}
                          >
                            <Checkbox
                              checked={checked}
                              onCheckedChange={() => toggleCourse(c.courseKey)}
                            />
                            <span
                              className={`text-sm ${
                                checked ? 'font-semibold text-blue-900' : 'text-slate-700'
                              }`}
                            >
                              {c.title}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  )}
                  {errors.courseKeys && (
                    <p className="text-xs text-red-600">
                      {errors.courseKeys.message as string}
                    </p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label>
                    기술 스택 <span className="text-red-500">*</span>{' '}
                    <span className="text-xs font-normal text-slate-500">
                      (Enter 또는 쉼표로 추가)
                    </span>
                  </Label>
                  <Controller
                    control={control}
                    name="techStack"
                    render={({ field }) => (
                      <TagInput
                        value={field.value}
                        onChange={field.onChange}
                        placeholder="예: React, TypeScript, Node.js"
                      />
                    )}
                  />
                  {errors.techStack && (
                    <p className="text-xs text-red-600">
                      {errors.techStack.message as string}
                    </p>
                  )}
                </div>

                <div className="space-y-3">
                  <Label>
                    선호 멘티 레벨 <span className="text-red-500">*</span>
                  </Label>
                  <Controller
                    control={control}
                    name="preferredMenteeLevel"
                    render={({ field }) => (
                      <RadioGroup
                        value={field.value}
                        onValueChange={field.onChange}
                        className="grid grid-cols-2 gap-3 sm:grid-cols-4"
                      >
                        {MENTEE_LEVELS.map((lv) => (
                          <label
                            key={lv}
                            className={`flex cursor-pointer items-center gap-2 rounded-lg border p-3 transition-colors ${
                              field.value === lv
                                ? 'border-blue-500 bg-blue-50/50'
                                : 'border-slate-200 bg-white hover:border-slate-300'
                            }`}
                          >
                            <RadioGroupItem value={lv} />
                            <span
                              className={`text-sm ${
                                field.value === lv
                                  ? 'font-semibold text-blue-900'
                                  : 'text-slate-700'
                              }`}
                            >
                              {LEVEL_LABELS[lv]}
                            </span>
                          </label>
                        ))}
                      </RadioGroup>
                    )}
                  />
                  {errors.preferredMenteeLevel && (
                    <p className="text-xs text-red-600">
                      {errors.preferredMenteeLevel.message}
                    </p>
                  )}
                </div>
              </CardContent>
            </Card>

            {/* C. 경력 & 자격 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">C. 경력 &amp; 자격</CardTitle>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="company">현재 소속 (선택)</Label>
                    <Input
                      id="company"
                      placeholder="회사/기관명"
                      {...register('company')}
                    />
                    {errors.company && (
                      <p className="text-xs text-red-600">{errors.company.message}</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="jobTitle">직책 (선택)</Label>
                    <Input id="jobTitle" placeholder="예: 백엔드 엔지니어" {...register('jobTitle')} />
                    {errors.jobTitle && (
                      <p className="text-xs text-red-600">{errors.jobTitle.message}</p>
                    )}
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="education">학력 (선택)</Label>
                  <Input
                    id="education"
                    placeholder="예: ○○대학교 컴퓨터공학과 졸업"
                    {...register('education')}
                  />
                  {errors.education && (
                    <p className="text-xs text-red-600">{errors.education.message}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="portfolioUrl">포트폴리오 URL (선택)</Label>
                  <Input
                    id="portfolioUrl"
                    placeholder="https://github.com/username"
                    {...register('portfolioUrl')}
                  />
                  {errors.portfolioUrl && (
                    <p className="text-xs text-red-600">{errors.portfolioUrl.message}</p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label>보유 자격증 (선택)</Label>
                  <Controller
                    control={control}
                    name="certifications"
                    render={({ field }) => (
                      <TagInput
                        value={field.value ?? []}
                        onChange={field.onChange}
                        placeholder="예: 정보처리기사"
                      />
                    )}
                  />
                </div>
              </CardContent>
            </Card>

            {/* D. 동의 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">D. 동의</CardTitle>
              </CardHeader>
              <CardContent>
                <Controller
                  control={control}
                  name="agreed"
                  render={({ field }) => (
                    <label className="flex cursor-pointer items-start gap-3">
                      <Checkbox
                        checked={!!field.value}
                        onCheckedChange={(v) => field.onChange(v === true)}
                        className="mt-1"
                      />
                      <span className="text-sm text-slate-700">
                        제출한 정보가 사실과 다를 경우 승인이 취소될 수 있음에 동의합니다.
                      </span>
                    </label>
                  )}
                />
                {errors.agreed && (
                  <p className="mt-2 text-xs text-red-600">{errors.agreed.message}</p>
                )}
              </CardContent>
            </Card>

            {/* 서버 에러 */}
            {submitError && (
              <Alert variant="destructive">
                <TriangleAlert className="h-4 w-4" />
                <AlertTitle>제출 실패</AlertTitle>
                <AlertDescription>{submitError}</AlertDescription>
              </Alert>
            )}

            {/* 푸터 버튼 */}
            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => router.back()}
                disabled={isSubmitting}
              >
                취소
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '제출 중…' : '제출하기'}
                {!isSubmitting && <Send className="ml-1 h-4 w-4" />}
              </Button>
            </div>
          </form>
        </div>
      </main>
      <Footer />
    </>
  );
}

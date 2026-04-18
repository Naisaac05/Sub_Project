'use client';

import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { Check, Send, Sparkles } from 'lucide-react';
import { submitApplication } from '@/lib/application';
import { useAuth } from '@/contexts/AuthContext';

const LANGUAGES = ['Java', 'JavaScript', 'Python', 'TypeScript', 'C++', 'Go', 'Rust', 'Swift', 'Kotlin', '기타'];
const PLATFORMS = ['없음', '웹', '안드로이드', 'iOS', '데이터', 'AI', '게임', '임베디드', 'DevOps/인프라', '기타'];
const LEARNING_PATHS = ['대학교', '부트캠프', '국비학원', '온라인 강의', '외부활동', '독학', '배우지 않음'];
const REFERRAL_SOURCES = ['SNS 광고', 'Github', '검색', '지인추천', '블로그', '커뮤니티', '기타'];

export default function ApplyPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [form, setForm] = useState({
    menteeId: 0,
    currentLevel: 'BEGINNER',
    targetTechStack: 'Java, Spring',
    careerGoal: '백엔드 개발자',
    category: 'backend',
    courseType: 'IMMEDIATE',
    desiredMonths: 4,
    languages: [] as string[],
    platforms: [] as string[],
    isCsMajor: null as boolean | null,
    learningPaths: [] as string[],
    careerYears: '',
    githubUrl: '',
    projectCount: '',
    projectDescription: '',
    weekdayStudyHours: '',
    weekendStudyHours: '',
    goal: '',
    personality: '',
    phone: '',
    selfIntroduction: '',
    referralSources: [] as string[],
    referralCode: '',
    termsAgreed: false,
  });

  useEffect(() => {
    if (!isLoading && !isLoggedIn) {
      alert('로그인이 필요한 페이지입니다.');
      router.push('/auth/login?redirect=/apply');
    }
  }, [isLoading, isLoggedIn, router]);

  const toggleArray = (field: keyof typeof form, value: string) => {
    const current = form[field] as string[];
    setForm((prev) => ({
      ...prev,
      [field]: current.includes(value) ? current.filter((item) => item !== value) : [...current, value],
    }));
  };

  const isFormValid = () =>
    form.languages.length > 0 &&
    form.platforms.length > 0 &&
    form.isCsMajor !== null &&
    form.learningPaths.length > 0 &&
    form.careerYears !== '' &&
    form.projectCount !== '' &&
    form.projectDescription.trim() !== '' &&
    form.weekdayStudyHours !== '' &&
    form.weekendStudyHours !== '' &&
    form.goal !== '' &&
    form.personality !== '' &&
    form.phone.trim() !== '' &&
    form.selfIntroduction.trim() !== '' &&
    form.referralSources.length > 0 &&
    form.termsAgreed;

  const handleSubmit = async () => {
    if (!isLoggedIn || !user) {
      alert('로그인이 필요합니다.');
      router.push('/auth/login?redirect=/apply');
      return;
    }

    if (user.role === 'MENTOR') {
      alert('멘토 신청은 현재 비활성화되어 있습니다.');
      return;
    }

    if (!isFormValid()) {
      alert('필수 항목을 모두 입력해주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload = {
        ...form,
        menteeId: user.id,
        isCsMajor: Boolean(form.isCsMajor),
        phone: form.phone,
      };

      const response = await submitApplication(payload);
      const application = response.data ?? response;

      if (application?.id && form.courseType === 'IMMEDIATE') {
        window.location.href = `/apply/payment?applicationId=${application.id}`;
      } else {
        alert('신청서가 저장되었습니다.');
        window.location.href = '/';
      }
    } catch (error: any) {
      console.error(error);
      const message = error?.response?.data?.message || error?.message || '신청서 저장 중 오류가 발생했습니다.';
      alert(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const SectionTitle = ({ children }: { children: ReactNode }) => (
    <h3 className="mb-6 flex items-center gap-2 border-b border-gray-100 pb-3 text-lg font-bold text-gray-900">
      {children}
    </h3>
  );

  const QuestionLabel = ({ children, required = true }: { children: ReactNode; required?: boolean }) => (
    <label className="mb-3 block text-base font-bold text-gray-900">
      <span className="mr-1 text-blue-500">Q.</span> {children}
      {required ? <span className="ml-1 text-red-500">*</span> : null}
    </label>
  );

  const MultiChip = ({ options, field }: { options: string[]; field: keyof typeof form }) => (
    <div className="flex flex-wrap gap-2">
      {options.map((option) => {
        const selected = (form[field] as string[]).includes(option);
        return (
          <button
            key={option}
            type="button"
            onClick={() => toggleArray(field, option)}
            className={`rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-200 ${
              selected
                ? 'border-2 border-blue-200 bg-blue-50 text-blue-700 shadow-sm'
                : 'border-2 border-gray-100 bg-white text-gray-600 hover:border-gray-200 hover:bg-gray-50'
            }`}
          >
            {option}
          </button>
        );
      })}
    </div>
  );

  const RadioGroup = ({ options, field }: { options: string[]; field: keyof typeof form }) => (
    <div className="flex flex-wrap gap-3">
      {options.map((option) => {
        const selected = form[field] === option;
        return (
          <button
            key={option}
            type="button"
            onClick={() => setForm((prev) => ({ ...prev, [field]: option }))}
            className={`flex items-center gap-2 rounded-xl border-2 px-4 py-3 transition-all duration-200 ${
              selected ? 'border-blue-500 bg-blue-50/30' : 'border-gray-100 bg-white hover:border-gray-200'
            }`}
          >
            <div className={`flex h-5 w-5 items-center justify-center rounded-full border-2 ${selected ? 'border-blue-500' : 'border-gray-300'}`}>
              {selected ? <div className="h-2.5 w-2.5 rounded-full bg-blue-500" /> : null}
            </div>
            <span className={`text-sm ${selected ? 'font-bold text-blue-900' : 'font-medium text-gray-700'}`}>{option}</span>
          </button>
        );
      })}
    </div>
  );

  if (isLoading || !user) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-[#F8FAFC] pt-24" />
        <Footer />
      </>
    );
  }

  if (user.role === 'MENTOR') {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-[#F8FAFC] py-24">
          <div className="mx-auto max-w-3xl px-6">
            <div className="rounded-3xl border border-gray-200 bg-white p-10 text-center shadow-sm">
              <span className="inline-flex items-center gap-2 rounded-full bg-blue-100 px-4 py-1.5 text-sm font-bold text-blue-700">
                <Sparkles size={14} />
                DevMatch Apply
              </span>
              <h1 className="mt-5 text-3xl font-extrabold text-gray-900">멘토 신청은 잠시 비활성화되어 있습니다.</h1>
              <p className="mt-3 text-sm leading-6 text-gray-600">
                현재 멘토 신청 연결 작업을 분리해서 진행 중이라 신청서는 멘티 전용으로만 받고 있습니다.
              </p>
            </div>
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
          <div className="mb-12">
            <span className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-blue-100/50 px-4 py-1.5 text-sm font-bold tracking-wider text-blue-700">
              <Sparkles size={14} /> DevMatch Mentoring
            </span>
            <h1 className="mb-4 text-3xl font-extrabold leading-tight tracking-tight text-gray-900 sm:text-4xl">
              시작이 반!
              <br />
              깊이 있는 개발자가 되기 위한 첫걸음.
            </h1>
            <p className="text-lg leading-relaxed text-gray-500">
              작성해주신 내용은 멘티 신청서로 저장되고, 이후 연결 작업은 다른 작업과 합쳐질 수 있게 분리해 두었습니다.
            </p>
          </div>

          <div className="space-y-8 rounded-3xl border border-gray-100 bg-white p-8 shadow-sm sm:p-10">
            <section className="mb-12">
              <SectionTitle>개발 배경</SectionTitle>
              <div className="space-y-8">
                <div>
                  <QuestionLabel>사용해보신 언어를 모두 골라주세요.</QuestionLabel>
                  <MultiChip options={LANGUAGES} field="languages" />
                </div>
                <div>
                  <QuestionLabel>경험해보신 플랫폼을 모두 골라주세요.</QuestionLabel>
                  <MultiChip options={PLATFORMS} field="platforms" />
                </div>
                <div>
                  <QuestionLabel>컴퓨터 전공자이신가요?</QuestionLabel>
                  <div className="flex gap-4">
                    <button
                      type="button"
                      onClick={() => setForm((prev) => ({ ...prev, isCsMajor: true }))}
                      className={`flex-1 rounded-xl border-2 py-4 text-center font-bold ${
                        form.isCsMajor === true ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-100 text-gray-500'
                      }`}
                    >
                      전공자
                    </button>
                    <button
                      type="button"
                      onClick={() => setForm((prev) => ({ ...prev, isCsMajor: false }))}
                      className={`flex-1 rounded-xl border-2 py-4 text-center font-bold ${
                        form.isCsMajor === false ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-100 text-gray-500'
                      }`}
                    >
                      비전공자
                    </button>
                  </div>
                </div>
                <div>
                  <QuestionLabel>어떤 경로로 개발을 배우셨나요?</QuestionLabel>
                  <MultiChip options={LEARNING_PATHS} field="learningPaths" />
                </div>
                <div>
                  <QuestionLabel>개발자로 일한 경력이 있으신가요?</QuestionLabel>
                  <RadioGroup options={['경력 없음', '3년차 미만', '5년차 미만', '10년차 미만']} field="careerYears" />
                </div>
              </div>
            </section>

            <section className="mb-12">
              <SectionTitle>프로젝트 경험</SectionTitle>
              <div className="space-y-8">
                <div>
                  <QuestionLabel required={false}>Github 주소를 입력해주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.githubUrl}
                    onChange={(event) => setForm((prev) => ({ ...prev, githubUrl: event.target.value }))}
                    placeholder="https://github.com/username"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>개발 프로젝트를 몇 개 해보셨나요?</QuestionLabel>
                  <RadioGroup options={['없음', '1개', '2개', '3개', '4개', '5개', '6개 이상']} field="projectCount" />
                </div>
                <div>
                  <QuestionLabel>현재까지 어떤 것들을 개발해보셨나요?</QuestionLabel>
                  <textarea
                    value={form.projectDescription}
                    onChange={(event) => setForm((prev) => ({ ...prev, projectDescription: event.target.value }))}
                    placeholder="학교, 회사, 토이프로젝트 등 자유롭게 적어주세요."
                    rows={5}
                    className="w-full resize-none rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500"
                  />
                </div>
              </div>
            </section>

            <section className="mb-12">
              <SectionTitle>학습 계획 및 성향</SectionTitle>
              <div className="space-y-8">
                <div>
                  <QuestionLabel>희망 기술 스택을 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.targetTechStack}
                    onChange={(event) => setForm((prev) => ({ ...prev, targetTechStack: event.target.value }))}
                    placeholder="예: Java, Spring, React"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>희망 직무를 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.careerGoal}
                    onChange={(event) => setForm((prev) => ({ ...prev, careerGoal: event.target.value }))}
                    placeholder="예: 백엔드 개발자"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>관심 분야 카테고리를 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.category}
                    onChange={(event) => setForm((prev) => ({ ...prev, category: event.target.value }))}
                    placeholder="예: backend, frontend"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>평일 멘토링이 가능한 시간을 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.weekdayStudyHours}
                    onChange={(event) => setForm((prev) => ({ ...prev, weekdayStudyHours: event.target.value }))}
                    placeholder="예: 월/수/금 20:00~22:00"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>주말 멘토링이 가능한 시간을 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.weekendStudyHours}
                    onChange={(event) => setForm((prev) => ({ ...prev, weekendStudyHours: event.target.value }))}
                    placeholder="예: 토요일 14:00~17:00"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>현재 본인이 원하는 방향은 무엇인가요?</QuestionLabel>
                  <RadioGroup options={['취업', '이직', '성장']} field="goal" />
                </div>
                <div>
                  <QuestionLabel>본인의 성격은 어떤 편인가요?</QuestionLabel>
                  <RadioGroup options={['외향적', '내향적']} field="personality" />
                </div>
                <div>
                  <QuestionLabel>연락 가능한 핸드폰 번호를 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.phone}
                    onChange={(event) => setForm((prev) => ({ ...prev, phone: event.target.value }))}
                    placeholder="010-0000-0000"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div>
                  <QuestionLabel>자유롭게 본인을 소개해주세요.</QuestionLabel>
                  <textarea
                    value={form.selfIntroduction}
                    onChange={(event) => setForm((prev) => ({ ...prev, selfIntroduction: event.target.value }))}
                    placeholder="자기소개를 입력해주세요."
                    rows={5}
                    className="w-full resize-none rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500"
                  />
                </div>
              </div>
            </section>

            <section>
              <SectionTitle>기타</SectionTitle>
              <div className="space-y-8">
                <div>
                  <QuestionLabel>DevMatch를 어떤 경로로 알게 되셨나요?</QuestionLabel>
                  <MultiChip options={REFERRAL_SOURCES} field="referralSources" />
                </div>
                <div>
                  <QuestionLabel required={false}>추천 코드가 있다면 적어주세요.</QuestionLabel>
                  <input
                    type="text"
                    value={form.referralCode}
                    onChange={(event) => setForm((prev) => ({ ...prev, referralCode: event.target.value }))}
                    placeholder="선택 입력"
                    className="w-full rounded-xl border-2 border-gray-100 p-4 transition-colors focus:border-blue-500 focus:ring-0"
                  />
                </div>
                <div className="border-t border-gray-100 pt-6">
                  <label className="group flex cursor-pointer items-center gap-3">
                    <div
                      className={`flex h-6 w-6 items-center justify-center rounded border-2 transition-colors ${
                        form.termsAgreed ? 'border-blue-500 bg-blue-500 text-white' : 'border-gray-300 group-hover:border-blue-400'
                      }`}
                    >
                      {form.termsAgreed ? <Check size={16} strokeWidth={3} /> : null}
                    </div>
                    <span className="select-none font-medium text-gray-900">
                      [필수] 서비스 이용 약관 및 개인정보 처리방침에 동의합니다.
                    </span>
                    <input
                      type="checkbox"
                      checked={form.termsAgreed}
                      onChange={(event) => setForm((prev) => ({ ...prev, termsAgreed: event.target.checked }))}
                      className="hidden"
                    />
                  </label>
                </div>
              </div>
            </section>

            <div className="mt-12">
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!isFormValid() || isSubmitting}
                className={`flex w-full items-center justify-center gap-2 rounded-2xl py-5 text-lg font-bold transition-all duration-300 ${
                  isFormValid() && !isSubmitting ? 'bg-gray-900 text-white shadow-xl hover:bg-gray-800' : 'cursor-not-allowed bg-gray-100 text-gray-400'
                }`}
              >
                {isSubmitting ? '제출 중...' : '신청서 저장하기'}
                {isSubmitting ? null : <Send size={20} />}
              </button>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}

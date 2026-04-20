'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { Check, Sparkles, Send } from 'lucide-react';
import { submitApplication } from '@/lib/application';
import { fetchCourseSummaries } from '@/lib/courses';
import type { CourseSummary } from '@/lib/types';
import { useAuth } from '@/contexts/AuthContext';

/* ───── Option Data ───── */
const LANGUAGES = ['Java', 'Javascript', 'Python', 'TypeScript', 'C++', 'Go', 'Rust', 'Swift', 'Kotlin', '기타'];
const PLATFORMS = ['없음', '웹', '안드로이드', 'iOS', '데이터', 'AI', '게임', '임베디드', 'DevOps/인프라', '기타'];
const LEARNING_PATHS = ['대학교', '부트캠프', '국비학원', '온라인 강의', '외부활동', '독학', '배우지 않음'];
const STUDY_HOURS = ['2시간 미만', '2~4시간', '4~6시간', '풀타임'];
const REFERRAL_SOURCES = ['SNS 광고', 'Github', '검색', '지인추천', '블로그', '커뮤니티', '기타'];

export default function ApplyPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [courseOptions, setCourseOptions] = useState<CourseSummary[]>([]);

  useEffect(() => {
    fetchCourseSummaries().then(setCourseOptions).catch(() => setCourseOptions([]));
  }, []);
  
  // 로그인 체크: 로딩 완료 후 로그인 상태가 아니면 로그인 페이지로
  useEffect(() => {
    if (!isLoading && !isLoggedIn) {
      alert("로그인이 필요한 페이지입니다.");
      router.push('/auth/login?redirect=/apply');
    }
  }, [isLoading, isLoggedIn, router]);

  const [form, setForm] = useState({
    menteeId: 0, 
    currentLevel: 'BEGINNER',
    targetTechStack: 'Java, Spring',
    careerGoal: '백엔드 개발자',
    category: '',
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

  const toggleArray = (field: keyof typeof form, value: string) => {
    const arr = form[field] as string[];
    if (arr.includes(value)) {
      setForm({ ...form, [field]: arr.filter(v => v !== value) });
    } else {
      setForm({ ...form, [field]: [...arr, value] });
    }
  };

  const isFormValid = () => {
    return (
      form.category !== '' &&
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
      form.phone !== '' &&
      form.selfIntroduction.trim() !== '' &&
      form.referralSources.length > 0 &&
      form.termsAgreed
    );
  };

  const handleSubmit = async () => {
    if (!isLoggedIn || !user) {
      alert("로그인이 필요합니다.");
      router.push('/auth/login');
      return;
    }

    if (!isFormValid()) {
      alert("필수 항목을 모두 입력해주세요.");
      return;
    }
    
    setIsSubmitting(true);
    try {
      const submissionData = {
        ...form,
        menteeId: user.id
      };

      // @ts-ignore
      const response = await submitApplication(submissionData);
      
      // 백엔드는 ApplicationResponse를 직접 반환 (ApiResponse 래퍼 없음)
      // HTTP 200 + 응답 데이터 존재 = 성공
      const data = response.data ?? response;
      console.log('지원서 제출 응답:', data);
      
      if (data && data.id) {
        // 백엔드에서 autoMatched가 true로 오거나, 프론트에서 IMMEDIATE인 경우 결제로 이동
        if (data.autoMatched === true || form.courseType === 'IMMEDIATE') {
          alert("🎉 지원서가 자동 매칭되어 승인되었습니다! 결제 단계로 이동합니다.");
          window.location.href = `/apply/payment?applicationId=${data.id}`;
        } else {
          alert("제출이 완료되었습니다. 검토 후 결과를 알려드립니다.");
          window.location.href = '/';
        }
      } else {
        alert("제출이 완료되었습니다. 검토 후 결과를 알려드립니다.");
        window.location.href = '/';
      }
    } catch (e: any) {
      console.error('지원서 제출 오류:', e);
      const msg = e?.response?.data?.message || e?.message || "제출 중 오류가 발생했습니다.";
      alert(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const SectionTitle = ({ children }: { children: React.ReactNode }) => (
    <h3 className="text-lg font-bold text-gray-900 mb-6 flex items-center gap-2 border-b border-gray-100 pb-3">
      {children}
    </h3>
  );

  const QuestionLabel = ({ children, required = true }: { children: React.ReactNode, required?: boolean }) => (
    <label className="block text-base font-bold text-gray-900 mb-3">
      <span className="text-blue-500 mr-1">Q.</span> {children}
      {required && <span className="text-red-500 ml-1">*</span>}
    </label>
  );

  const MultiChip = ({ options, field }: { options: string[], field: keyof typeof form }) => (
    <div className="flex flex-wrap gap-2">
      {options.map(opt => {
        const selected = (form[field] as string[]).includes(opt);
        return (
          <button
            key={opt}
            onClick={() => toggleArray(field, opt)}
            className={`px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200
              ${selected 
                ? 'bg-blue-50 text-blue-700 border-2 border-blue-200 shadow-sm' 
                : 'bg-white text-gray-600 border-2 border-gray-100 hover:border-gray-200 hover:bg-gray-50'}`}
          >
            {opt}
          </button>
        )
      })}
    </div>
  );

  const RadioGroup = ({ options, field }: { options: string[], field: keyof typeof form }) => (
    <div className="flex flex-wrap gap-3">
      {options.map(opt => {
        const selected = form[field] === opt;
        return (
          <button
            key={opt}
            onClick={() => setForm({ ...form, [field]: opt })}
            className={`flex items-center gap-2 px-4 py-3 rounded-xl border-2 transition-all duration-200
              ${selected 
                ? 'border-blue-500 bg-blue-50/30' 
                : 'border-gray-100 bg-white hover:border-gray-200'}`}
          >
            <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center
              ${selected ? 'border-blue-500' : 'border-gray-300'}`}>
              {selected && <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />}
            </div>
            <span className={`text-sm ${selected ? 'font-bold text-blue-900' : 'font-medium text-gray-700'}`}>
              {opt}
            </span>
          </button>
        );
      })}
    </div>
  );

  return (
    <>
      <Header />
      <main className="min-h-screen bg-[#F8FAFC] py-24">
        <div className="max-w-3xl mx-auto px-6">
          <div className="mb-12">
            <span className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full bg-blue-100/50 text-blue-700 text-sm font-bold tracking-wider mb-4">
              <Sparkles size={14} /> DevMatch Mentoring
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-4 leading-tight">
              시작이 반!<br/>깊이 있는 개발자가 되기 위한 첫걸음.
            </h1>
            <p className="text-gray-500 text-lg leading-relaxed">
              작성에 들인 노력만큼 잘 맞는 멘토님을 만나실 수 있을 겁니다 :)
            </p>
          </div>

          <div className="space-y-8 bg-white p-8 sm:p-10 rounded-3xl shadow-sm border border-gray-100">
            <section className="mb-12">
              <SectionTitle>멘토링 코스 선택</SectionTitle>
              <div className="space-y-8">
                <div>
                  <QuestionLabel>관심 있는 멘토링 코스를 선택해주세요.</QuestionLabel>
                  <select
                    className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 focus:ring-0 transition-colors bg-white text-gray-900"
                    value={form.category}
                    onChange={e => setForm({ ...form, category: e.target.value })}
                  >
                    <option value="">카테고리 선택…</option>
                    {courseOptions.map(c => (
                      <option key={c.courseKey} value={c.courseKey}>{c.title}</option>
                    ))}
                  </select>
                </div>
              </div>
            </section>

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
                    <button onClick={() => setForm({ ...form, isCsMajor: true })} className={`flex-1 py-4 rounded-xl border-2 text-center font-bold ${form.isCsMajor === true ? 'border-blue-500 text-blue-700 bg-blue-50' : 'border-gray-100 text-gray-500'}`}>전공자</button>
                    <button onClick={() => setForm({ ...form, isCsMajor: false })} className={`flex-1 py-4 rounded-xl border-2 text-center font-bold ${form.isCsMajor === false ? 'border-blue-500 text-blue-700 bg-blue-50' : 'border-gray-100 text-gray-500'}`}>비전공자</button>
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
                  <input type="text" value={form.githubUrl} onChange={e => setForm({...form, githubUrl: e.target.value})} placeholder="https://github.com/username" className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 focus:ring-0 transition-colors" />
                </div>
                <div>
                  <QuestionLabel>개발 프로젝트를 몇 개 해보셨나요?</QuestionLabel>
                  <RadioGroup options={['없음', '1개', '2개', '3개', '4개', '5개', '6개 이상']} field="projectCount" />
                </div>
                <div>
                  <QuestionLabel>현재까지 어떤 것들을 개발해보셨나요?</QuestionLabel>
                  <textarea value={form.projectDescription} onChange={e => setForm({...form, projectDescription: e.target.value})} placeholder="학교나 회사에서 한 것들, 토이프로젝트 무엇이든 좋습니다." rows={5} className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 transition-colors resize-none" />
                </div>
              </div>
            </section>

            <section className="mb-12">
              <SectionTitle>학습 계획 및 성향</SectionTitle>
              <div className="space-y-8">
                <div>
                  <QuestionLabel>평일 멘토링이 가능한 희망 시간대를 적어주세요. (예: 월/수/금 저녁 8시~10시)</QuestionLabel>
                  <input type="text" value={form.weekdayStudyHours} onChange={e => setForm({...form, weekdayStudyHours: e.target.value})} placeholder="자유롭게 기재해주세요." className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 focus:ring-0 transition-colors" />
                </div>
                <div>
                  <QuestionLabel>주말 멘토링이 가능한 희망 시간대를 적어주세요. (예: 토요일 오후 2시~5시)</QuestionLabel>
                  <input type="text" value={form.weekendStudyHours} onChange={e => setForm({...form, weekendStudyHours: e.target.value})} placeholder="자유롭게 기재해주세요." className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 focus:ring-0 transition-colors" />
                </div>
                <div>
                  <QuestionLabel>현재 본인이 원하는 것에 제일 가까운 것은 무엇인가요?</QuestionLabel>
                  <RadioGroup options={['취업', '이직', '성장']} field="goal" />
                </div>
                <div>
                  <QuestionLabel>본인의 성격은 어떤 편인가요?</QuestionLabel>
                  <RadioGroup options={['외향적', '내향적']} field="personality" />
                </div>
                <div>
                  <QuestionLabel>연락 가능한 핸드폰 번호를 적어주세요. (예: 010-1234-5678)</QuestionLabel>
                  <input type="text" value={form.phone} onChange={e => setForm({...form, phone: e.target.value})} placeholder="010-0000-0000" className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 focus:ring-0 transition-colors" />
                </div>
                <div>
                  <QuestionLabel>자유롭게 본인을 소개해주세요.</QuestionLabel>
                  <textarea value={form.selfIntroduction} onChange={e => setForm({...form, selfIntroduction: e.target.value})} placeholder="자기소개를 입력해주세요." rows={5} className="w-full p-4 rounded-xl border-2 border-gray-100 focus:border-blue-500 transition-colors resize-none" />
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
                <div className="pt-6 border-t border-gray-100">
                  <label className="flex items-center gap-3 cursor-pointer group">
                    <div className={`w-6 h-6 rounded border-2 flex items-center justify-center transition-colors
                      ${form.termsAgreed ? 'bg-blue-500 border-blue-500 text-white' : 'border-gray-300 group-hover:border-blue-400'}`}>
                      {form.termsAgreed && <Check size={16} strokeWidth={3} />}
                    </div>
                    <span className="text-gray-900 font-medium select-none">
                      [필수] 서비스 이용 약관 및 개인정보 처리방침에 동의합니다.
                    </span>
                    <input type="checkbox" checked={form.termsAgreed} onChange={e => setForm({...form, termsAgreed: e.target.checked})} className="hidden" />
                  </label>
                </div>
              </div>
            </section>

            <div className="mt-12">
              <button
                onClick={handleSubmit}
                disabled={!isFormValid() || isSubmitting}
                className={`w-full py-5 rounded-2xl font-bold text-lg flex items-center justify-center gap-2 transition-all duration-300
                  ${isFormValid() && !isSubmitting
                    ? 'bg-gray-900 text-white hover:bg-gray-800 shadow-xl'
                    : 'bg-gray-100 text-gray-400 cursor-not-allowed'}`}
              >
                {isSubmitting ? '제출 중...' : '지원서 제출하기'}
                {!isSubmitting && <Send size={20} />}
              </button>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}

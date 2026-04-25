'use client';

import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { confirmApplicationPayment } from '@/lib/application';
import {
  ArrowRight,
  ArrowLeft,
  Check,
  Sparkles,
  MonitorSmartphone,
  Target,
} from 'lucide-react';

/* ───── 설문 옵션 데이터 ───── */
const FEEDBACK_STYLES = [
  { value: 'detailed_review', label: '꼼꼼한 코드리뷰', desc: '한 줄 한 줄 세밀하게 피드백', icon: '🔍' },
  { value: 'direction', label: '방향성 제시', desc: '큰 그림을 보며 방향을 안내', icon: '🧭' },
  { value: 'autonomous', label: '자율적 진행', desc: '스스로 해결하도록 힌트만 제공', icon: '🎯' },
  { value: 'pair_programming', label: '페어 프로그래밍', desc: '함께 코드를 작성하며 학습', icon: '👨‍💻' },
];

const LEARNING_STYLES = [
  { value: 'practice', label: '실습 위주', icon: '⚡' },
  { value: 'theory_practice', label: '이론 + 실습 병행', icon: '📚' },
  { value: 'project', label: '프로젝트 기반', icon: '🚀' },
  { value: 'challenge', label: '코딩 챌린지', icon: '🏆' },
];

const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const DAY_LABELS: Record<string, string> = {
  MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일',
};

const TIME_SLOTS = [
  '09:00-11:00', '11:00-13:00', '14:00-16:00',
  '16:00-18:00', '19:00-21:00', '21:00-23:00',
];

/* ────────────────────────────────────────────
   Step 1: 피드백 & 학습 스타일
   ──────────────────────────────────────────── */
function StepStyle({
  survey, setSurvey, onNext,
}: { survey: any; setSurvey: (s: any) => void; onNext: () => void }) {
  const isValid = survey.feedbackPreference && survey.learningStyle && survey.careerGoal.trim();
  return (
    <div className="animate-fade-in-up">
      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">희망 피드백 스타일</label>
        <div className="grid sm:grid-cols-2 gap-3">
          {FEEDBACK_STYLES.map((fb) => {
            const selected = survey.feedbackPreference === fb.value;
            return (
              <button
                key={fb.value}
                onClick={() => setSurvey({ ...survey, feedbackPreference: fb.value })}
                className={`p-5 rounded-2xl border-2 text-left transition-all duration-300
                  ${selected
                    ? 'border-blue-300 bg-blue-50/50 shadow-md'
                    : 'border-gray-100 bg-white hover:border-gray-200 hover:shadow-sm'
                  }`}
              >
                <div className="flex items-start gap-3">
                  <span className="text-2xl">{fb.icon}</span>
                  <div>
                    <p className={`font-bold text-sm ${selected ? 'text-blue-700' : 'text-gray-900'}`}>{fb.label}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{fb.desc}</p>
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">선호하는 학습 방식</label>
        <div className="grid grid-cols-2 gap-3">
          {LEARNING_STYLES.map((ls) => {
            const selected = survey.learningStyle === ls.value;
            return (
              <button
                key={ls.value}
                onClick={() => setSurvey({ ...survey, learningStyle: ls.value })}
                className={`px-4 py-4 rounded-xl border text-center transition-all duration-200
                  ${selected
                    ? 'border-violet-300 bg-violet-50 text-violet-700 shadow-sm'
                    : 'border-gray-100 text-gray-600 hover:border-gray-200 hover:bg-gray-50'
                  }`}
              >
                <span className="text-xl block mb-1">{ls.icon}</span>
                <span className="text-sm font-semibold">{ls.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-4">멘토링 진행 방식</label>
        <div className="p-5 rounded-2xl bg-violet-50 border border-violet-100 flex items-start gap-4">
          <div className="w-10 h-10 rounded-full bg-violet-100 flex items-center justify-center flex-shrink-0">
            <MonitorSmartphone size={20} className="text-violet-600" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-violet-900 mb-1">주 1회 화상 미팅 & 자율 코드 리뷰</h4>
            <p className="text-xs text-violet-700 leading-relaxed">
              모든 과정은 주 1회 온라인 화상 미팅으로 진행되며,<br />
              코드 리뷰 방식이나 횟수는 담당 멘토님의 재량과 판단 하에 자유롭게 진행됩니다.
            </p>
          </div>
        </div>
      </div>

      <div className="mb-10">
        <label className="block text-sm font-bold text-gray-900 mb-2">목표 커리어</label>
        <input
          type="text"
          value={survey.careerGoal}
          onChange={(e) => setSurvey({ ...survey, careerGoal: e.target.value })}
          placeholder="예: 네카라쿠배 백엔드 개발자"
          className="w-full px-5 py-4 rounded-xl border border-gray-200 bg-gray-50/50
                   text-gray-900 placeholder:text-gray-400 text-sm
                   focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-300
                   transition-all duration-200"
        />
      </div>

      <button
        onClick={onNext}
        disabled={!isValid}
        className={`w-full py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2
                   transition-all duration-300
                   ${isValid
                     ? 'bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 hover:scale-[1.01]'
                     : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                   }`}
      >
        다음: 스케줄 선택
        <ArrowRight size={18} />
      </button>
    </div>
  );
}

/* ────────────────────────────────────────────
   Step 2: 스케줄 선택
   ──────────────────────────────────────────── */
function StepSchedule({
  survey, setSurvey, onNext, onBack,
}: { survey: any; setSurvey: (s: any) => void; onNext: () => void; onBack: () => void }) {
  const toggleSlot = (day: string, time: string) => {
    const key = `${day}:${time}`;
    const updated = survey.preferredSchedule.includes(key)
      ? survey.preferredSchedule.filter((s: string) => s !== key)
      : [...survey.preferredSchedule, key];
    setSurvey({ ...survey, preferredSchedule: updated });
  };
  const isValid = survey.preferredSchedule.length >= 1;
  return (
    <div className="animate-fade-in-up">
      <div className="mb-6">
        <label className="block text-sm font-bold text-gray-900 mb-2">멘토링 가능한 시간대</label>
        <p className="text-xs text-gray-500 mb-6">가능한 시간대를 최소 1개 이상 선택해주세요. 많이 선택할수록 매칭 확률이 높아집니다.</p>
        <div className="overflow-x-auto -mx-2 px-2">
          <div className="min-w-[600px]">
            <div className="grid grid-cols-8 gap-1.5 mb-2">
              <div className="text-xs text-gray-400 font-medium py-2 text-center">시간</div>
              {DAYS.map((d) => (
                <div key={d} className="text-xs text-gray-700 font-bold py-2 text-center">
                  {DAY_LABELS[d]}
                </div>
              ))}
            </div>
            {TIME_SLOTS.map((time) => (
              <div key={time} className="grid grid-cols-8 gap-1.5 mb-1.5">
                <div className="text-xs text-gray-400 py-2 text-center whitespace-nowrap">
                  {time.split('-')[0]}
                </div>
                {DAYS.map((day) => {
                  const key = `${day}:${time}`;
                  const selected = survey.preferredSchedule.includes(key);
                  return (
                    <button
                      key={key}
                      onClick={() => toggleSlot(day, time)}
                      className={`py-2.5 rounded-lg text-xs font-medium transition-all duration-200
                        ${selected
                          ? 'bg-blue-500 text-white shadow-sm shadow-blue-500/20'
                          : 'bg-gray-50 text-gray-400 hover:bg-gray-100 hover:text-gray-600 border border-gray-100'
                        }`}
                    >
                      {selected ? <Check size={12} className="mx-auto" /> : ''}
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="flex gap-3 mt-8">
        <button onClick={onBack} className="px-6 py-4 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm hover:bg-gray-50 transition-all duration-200 flex items-center gap-2"><ArrowLeft size={16} />이전</button>
        <button onClick={onNext} disabled={!isValid} className={`flex-1 py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2 transition-all duration-300 ${isValid ? 'bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30' : 'bg-gray-100 text-gray-400'}`}>멘토 추천 받기 <Sparkles size={16} /></button>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────
   Step 3: 멘토 추천 결과
   ──────────────────────────────────────────── */
const DUMMY_MENTORS = [
  { id: 1, name: '김재현', company: '네이버', role: '백엔드 8년차', tags: ['Java', 'Spring', 'MSA'], matchScore: 96, reason: '기술 스택이 정확히 일치합니다.', gradient: 'from-violet-500 to-pink-500', initials: 'JK' },
  { id: 2, name: '이수진', company: '카카오', role: '프론트엔드 6년차', tags: ['React', 'Next.js', 'TS'], matchScore: 91, reason: '관련 기술 경험이 풍부합니다.', gradient: 'from-blue-500 to-cyan-400', initials: 'SJ' },
  { id: 3, name: '박민수', company: '토스', role: '풀스택 7년차', tags: ['Node.js', 'React', 'AWS'], matchScore: 88, reason: '토스 현직자입니다.', gradient: 'from-amber-500 to-orange-500', initials: 'MS' },
];

function StepRecommendation({ onBack }: { onBack: () => void }) {
  const router = useRouter();
  const [selectedMentor, setSelectedMentor] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), 1500);
    return () => clearTimeout(timer);
  }, []);
  if (isLoading) return <div className="text-center py-20"><div className="w-12 h-12 mx-auto mb-4 border-4 border-blue-500 border-t-transparent animate-spin rounded-full"></div><p>분석 중...</p></div>;
  return (
    <div className="animate-fade-in-up">
      <div className="space-y-4 mb-8">
        {DUMMY_MENTORS.map((m) => (
          <button key={m.id} onClick={() => setSelectedMentor(m.id)} className={`w-full p-5 rounded-2xl border-2 text-left transition-all ${selectedMentor === m.id ? 'border-blue-500 bg-blue-50/20' : 'border-gray-100 bg-white'}`}>
            <div className="flex items-center gap-4">
              <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${m.gradient} flex items-center justify-center text-white font-bold`}>{m.initials}</div>
              <div><h3 className="font-bold text-gray-900">{m.name} 멘토</h3><p className="text-xs text-gray-500">{m.company} / {m.role}</p></div>
              <div className="ml-auto text-xl font-black text-blue-500">{m.matchScore}</div>
            </div>
          </button>
        ))}
      </div>
      <button onClick={() => selectedMentor && router.push('/mypage')} disabled={!selectedMentor} className={`w-full py-4 rounded-xl font-bold ${selectedMentor ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-400'}`}>선택 완료</button>
    </div>
  );
}

function SurveyContent() {
  const searchParams = useSearchParams();
  const applicationId = searchParams.get('applicationId');
  useEffect(() => {
    if (applicationId) {
      confirmApplicationPayment(Number(applicationId)).catch(console.error);
    }
  }, [applicationId]);
  const [step, setStep] = useState(1);
  const [survey, setSurvey] = useState({ feedbackPreference: '', learningStyle: '', careerGoal: '', preferredSchedule: [] as string[] });
  return (
    <div className="max-w-2xl mx-auto px-6">
      <div className="text-center mb-10">
        <h1 className="text-3xl font-extrabold text-gray-900 mb-2">성향 조사</h1>
        <p className="text-gray-500">나에게 딱 맞는 멘토를 찾기 위한 간단한 설문입니다.</p>
      </div>
      {step === 1 && <StepStyle survey={survey} setSurvey={setSurvey} onNext={() => setStep(2)} />}
      {step === 2 && <StepSchedule survey={survey} setSurvey={setSurvey} onNext={() => setStep(3)} onBack={() => setStep(1)} />}
      {step === 3 && <StepRecommendation onBack={() => setStep(2)} />}
    </div>
  );
}

export default function SurveyPage() {
  return (
    <>
      <Header />
      <main className="min-h-screen bg-white pt-24 pb-20">
        <Suspense fallback={<div>Loading...</div>}>
          <SurveyContent />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}



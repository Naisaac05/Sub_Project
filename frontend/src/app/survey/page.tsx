'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import {
  ArrowRight,
  ArrowLeft,
  Check,
  Sparkles,
  MessageSquareText,
  MonitorSmartphone,
  Lightbulb,
  Compass,
  BookOpen,
  ChevronRight,
  Star,
  Users,
  Clock,
  Target,
  Zap,
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
      {/* 피드백 스타일 */}
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

      {/* 학습 스타일 */}
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

      {/* 멘토링 방식 (고정) */}
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

      {/* 목표 커리어 */}
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

        {/* 스케줄 그리드 */}
        <div className="overflow-x-auto -mx-2 px-2">
          <div className="min-w-[600px]">
            {/* 요일 헤더 */}
            <div className="grid grid-cols-8 gap-1.5 mb-2">
              <div className="text-xs text-gray-400 font-medium py-2 text-center">시간</div>
              {DAYS.map((d) => (
                <div key={d} className="text-xs text-gray-700 font-bold py-2 text-center">
                  {DAY_LABELS[d]}
                </div>
              ))}
            </div>

            {/* 시간 슬롯 */}
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

        {survey.preferredSchedule.length > 0 && (
          <p className="mt-4 text-xs text-blue-600 font-medium">
            ✓ {survey.preferredSchedule.length}개 시간대 선택됨
          </p>
        )}
      </div>

      {/* 버튼 */}
      <div className="flex gap-3 mt-8">
        <button
          onClick={onBack}
          className="px-6 py-4 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm
                   hover:bg-gray-50 transition-all duration-200 flex items-center gap-2"
        >
          <ArrowLeft size={16} />
          이전
        </button>
        <button
          onClick={onNext}
          disabled={!isValid}
          className={`flex-1 py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2
                     transition-all duration-300
                     ${isValid
                       ? 'bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 hover:scale-[1.01]'
                       : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                     }`}
        >
          멘토 추천 받기
          <Sparkles size={16} />
        </button>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────
   Step 3: 멘토 추천 결과
   ──────────────────────────────────────────── */
// 더미 멘토 데이터 (실제에서는 API에서 가져옴)
const DUMMY_MENTORS = [
  { id: 1, name: '김재현', company: '네이버', role: '백엔드 8년차', tags: ['Java', 'Spring', 'MSA'], matchScore: 96, reason: '기술 스택이 정확히 일치합니다. 학습 수준에 최적화된 멘토링을 제공합니다. 시간대가 잘 맞습니다.', gradient: 'from-violet-500 to-pink-500', initials: 'JK' },
  { id: 2, name: '이수진', company: '카카오', role: '프론트엔드 6년차', tags: ['React', 'Next.js', 'TS'], matchScore: 91, reason: '관련 기술 경험이 풍부합니다. 시간대가 잘 맞습니다. 카카오 현직자입니다.', gradient: 'from-blue-500 to-cyan-400', initials: 'SJ' },
  { id: 3, name: '박민수', company: '토스', role: '풀스택 7년차', tags: ['Node.js', 'React', 'AWS'], matchScore: 88, reason: '학습 수준에 최적화된 멘토링을 제공합니다. 토스 현직자입니다.', gradient: 'from-amber-500 to-orange-500', initials: 'MS' },
  { id: 4, name: '정하은', company: '삼성전자', role: '백엔드 5년차', tags: ['Python', 'Django', 'K8s'], matchScore: 85, reason: '관련 기술 경험이 풍부합니다. 삼성전자 현직자입니다.', gradient: 'from-emerald-500 to-teal-500', initials: 'HE' },
  { id: 5, name: '최영호', company: '라인', role: '인프라 9년차', tags: ['AWS', 'Docker', 'K8s'], matchScore: 82, reason: '현업 경험이 풍부하여 실무형 피드백을 제공합니다. 라인 현직자입니다.', gradient: 'from-red-500 to-rose-500', initials: 'YH' },
  { id: 6, name: '한지민', company: '쿠팡', role: '백엔드 4년차', tags: ['Java', 'Spring', 'Redis'], matchScore: 79, reason: '트렌디한 기술 스택과 탁월한 소통 능력을 갖추고 있습니다. 쿠팡 현직자입니다.', gradient: 'from-indigo-500 to-blue-500', initials: 'JM' },
];

function StepRecommendation({ onBack }: { onBack: () => void }) {
  const router = useRouter();
  const [selectedMentor, setSelectedMentor] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 로딩 시뮬레이션
  useState(() => {
    const timer = setTimeout(() => setIsLoading(false), 2000);
    return () => clearTimeout(timer);
  });

  if (isLoading) {
    return (
      <div className="animate-fade-in text-center py-20">
        <div className="w-16 h-16 mx-auto mb-6 rounded-full border-4 border-blue-100 border-t-blue-500 animate-spin" />
        <p className="text-gray-900 font-bold text-lg mb-2">멘토를 분석하고 있습니다</p>
        <p className="text-gray-500 text-sm">성향 데이터를 기반으로 최적의 멘토를 찾고 있어요...</p>
      </div>
    );
  }

  return (
    <div className="animate-fade-in-up">
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-emerald-50 text-emerald-600 text-xs font-bold mb-3">
          <Target size={12} />
          총 {DUMMY_MENTORS.length}명의 멘토를 추천합니다
        </div>
        <p className="text-gray-500 text-sm">나의 성향과 기술 스택에 가장 적합한 멘토를 선택해주세요.</p>
      </div>

      <div className="space-y-4 mb-8">
        {DUMMY_MENTORS.map((mentor, idx) => {
          const selected = selectedMentor === mentor.id;
          return (
            <button
              key={mentor.id}
              onClick={() => setSelectedMentor(mentor.id)}
              className={`w-full p-5 rounded-2xl border-2 text-left transition-all duration-300
                ${selected
                  ? 'border-blue-300 bg-blue-50/30 shadow-lg shadow-blue-500/10'
                  : 'border-gray-100 bg-white hover:border-gray-200 hover:shadow-md'
                }`}
            >
              <div className="flex items-start gap-4">
                {/* 아바타 */}
                <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${mentor.gradient}
                              flex items-center justify-center text-white font-bold text-lg flex-shrink-0
                              ${selected ? 'scale-110' : ''} transition-transform duration-300`}>
                  {mentor.initials}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-bold text-gray-900">{mentor.name} 멘토</h3>
                    {idx === 0 && (
                      <span className="px-2 py-0.5 rounded-md bg-amber-50 text-amber-600 text-xs font-bold">
                        Best Match
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-gray-500 mb-2">{mentor.company} / {mentor.role}</p>

                  {/* 태그 */}
                  <div className="flex flex-wrap gap-1.5 mb-3">
                    {mentor.tags.map(tag => (
                      <span key={tag} className="px-2 py-0.5 text-xs rounded-md bg-gray-50 text-gray-600 border border-gray-100 font-medium">
                        {tag}
                      </span>
                    ))}
                  </div>

                  {/* 추천 사유 */}
                  <p className="text-xs text-gray-500 leading-relaxed">{mentor.reason}</p>
                </div>

                {/* 점수 */}
                <div className="flex-shrink-0 text-right">
                  <div className={`text-2xl font-extrabold font-[Outfit] ${selected ? 'text-blue-600' : 'text-gray-300'}`}>
                    {mentor.matchScore}
                  </div>
                  <div className="text-xs text-gray-400 mt-0.5">적합도</div>
                </div>
              </div>
            </button>
          );
        })}
      </div>

      {/* 버튼 */}
      <div className="flex gap-3">
        <button
          onClick={onBack}
          className="px-6 py-4 rounded-xl border border-gray-200 text-gray-600 font-semibold text-sm
                   hover:bg-gray-50 transition-all duration-200 flex items-center gap-2"
        >
          <ArrowLeft size={16} />
          이전
        </button>
        <button
          onClick={() => {
            if (selectedMentor) {
              router.push('/mypage');
            }
          }}
          disabled={!selectedMentor}
          className={`flex-1 py-4 rounded-xl font-bold text-base flex items-center justify-center gap-2
                     transition-all duration-300
                     ${selectedMentor
                       ? 'bg-gradient-to-r from-blue-600 via-blue-500 to-cyan-500 text-white shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 hover:scale-[1.01]'
                       : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                     }`}
        >
          이 멘토와 시작하기
          <ArrowRight size={18} />
        </button>
      </div>

      {/* 무료 체험 안내 */}
      <div className="mt-6 p-4 rounded-xl bg-gradient-to-r from-blue-50 to-cyan-50 border border-blue-100">
        <p className="text-xs text-blue-700 leading-relaxed">
          <span className="font-bold">💡 무료 체험 안내:</span> 멘토 선택 후 7일간 무료 체험 기간이 제공됩니다.
          첫 멘토님과 성향이 맞지 않다고 판단될 경우, 멘토링 횟수 차감 없이 멘토 변경이 가능합니다.
        </p>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────
   메인 페이지
   ──────────────────────────────────────────── */
export default function SurveyPage() {
  const [step, setStep] = useState(1);
  const [survey, setSurvey] = useState({
    feedbackPreference: '',
    learningStyle: '',
    careerGoal: '',
    preferredSchedule: [] as string[],
  });

  const steps = [
    { num: 1, label: '학습 성향' },
    { num: 2, label: '스케줄' },
    { num: 3, label: '멘토 추천' },
  ];

  return (
    <>
      <Header />
      <main className="min-h-screen bg-white pt-24 pb-20">
        <div className="max-w-2xl mx-auto px-6">
          {/* 헤더 */}
          <div className="text-center mb-10">
            <span className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full
                           bg-violet-50 text-violet-600 text-xs font-bold tracking-wider uppercase mb-4">
              <Sparkles size={12} />
              Mentor Matching
            </span>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight mb-3">
              성향 조사 & 멘토 추천
            </h1>
            <p className="text-gray-500">나에게 딱 맞는 멘토를 찾기 위한 간단한 설문입니다.</p>
          </div>

          {/* 스텝 인디케이터 */}
          <div className="flex items-center justify-center gap-0 mb-12">
            {steps.map((s, i) => (
              <div key={s.num} className="flex items-center">
                <div className="flex items-center gap-2">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
                                 transition-all duration-300
                                 ${step >= s.num
                                   ? 'bg-violet-500 text-white shadow-md shadow-violet-500/30'
                                   : 'bg-gray-100 text-gray-400'
                                 }`}>
                    {step > s.num ? <Check size={14} /> : s.num}
                  </div>
                  <span className={`text-sm font-medium hidden sm:block
                                   ${step >= s.num ? 'text-gray-900' : 'text-gray-400'}`}>
                    {s.label}
                  </span>
                </div>
                {i < steps.length - 1 && (
                  <div className={`w-12 sm:w-20 h-0.5 mx-3 rounded-full transition-all duration-500
                                 ${step > s.num ? 'bg-violet-500' : 'bg-gray-200'}`} />
                )}
              </div>
            ))}
          </div>

          {/* 스텝 내용 */}
          {step === 1 && <StepStyle survey={survey} setSurvey={setSurvey} onNext={() => setStep(2)} />}
          {step === 2 && <StepSchedule survey={survey} setSurvey={setSurvey} onNext={() => setStep(3)} onBack={() => setStep(1)} />}
          {step === 3 && <StepRecommendation onBack={() => setStep(2)} />}
        </div>
      </main>
      <Footer />
    </>
  );
}

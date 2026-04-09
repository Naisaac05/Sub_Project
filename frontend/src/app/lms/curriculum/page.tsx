'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { CheckCircle2, Circle, ExternalLink } from 'lucide-react';
import { getCurriculum, toggleWeekComplete } from '@/lib/lms';
import type { CurriculumResponse } from '@/lib/lms-types';

export default function CurriculumPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [curriculum, setCurriculum] = useState<CurriculumResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchCurriculum = () => {
    if (!matchingId) return;
    getCurriculum(matchingId)
      .then((res) => setCurriculum(res.data.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchCurriculum(); }, [matchingId]);

  const handleToggle = async (weekId: number) => {
    await toggleWeekComplete(weekId);
    fetchCurriculum();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!curriculum) {
    return (
      <div className="text-center py-20">
        <p className="text-gray-400">아직 커리큘럼이 등록되지 않았습니다.</p>
        <p className="text-gray-500 text-sm mt-1">멘토가 커리큘럼을 설정하면 여기에 표시됩니다.</p>
      </div>
    );
  }

  const completedCount = curriculum.weeks.filter(w => w.isCompleted).length;
  const progressPercent = curriculum.totalWeeks > 0
    ? Math.round((completedCount / curriculum.totalWeeks) * 100) : 0;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">{curriculum.title}</h1>
        {curriculum.description && (
          <p className="text-gray-400 mt-1">{curriculum.description}</p>
        )}
        <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
          <span>{curriculum.startDate} ~ {curriculum.endDate}</span>
          <span>{completedCount}/{curriculum.totalWeeks}주 완료</span>
        </div>
      </div>

      {/* 프로그레스 바 */}
      <div className="bg-white/5 rounded-full h-3 overflow-hidden">
        <div
          className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full transition-all duration-500"
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      {/* 주차 목록 */}
      <div className="space-y-4">
        {curriculum.weeks.map((week) => (
          <div
            key={week.id}
            className={`bg-[#0f1420] border rounded-2xl p-6 transition-all ${
              week.isCompleted ? 'border-blue-500/30' : 'border-white/5'
            }`}
          >
            <div className="flex items-start gap-4">
              <button
                onClick={() => handleToggle(week.id)}
                className="mt-0.5 shrink-0"
              >
                {week.isCompleted ? (
                  <CheckCircle2 size={22} className="text-blue-400" />
                ) : (
                  <Circle size={22} className="text-gray-600 hover:text-gray-400 transition-colors" />
                )}
              </button>
              <div className="flex-1">
                <h3 className="text-white font-semibold">
                  Week {week.weekNumber}: {week.title}
                </h3>
                {week.description && (
                  <p className="text-gray-400 text-sm mt-1">{week.description}</p>
                )}
                {week.topics.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-3">
                    {week.topics.map((topic, i) => (
                      <span key={i} className="px-2.5 py-0.5 rounded-md text-xs font-medium bg-blue-500/10 text-blue-400">
                        {topic}
                      </span>
                    ))}
                  </div>
                )}
                {week.resources.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-2">
                    {week.resources.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-blue-400 transition-colors">
                        <ExternalLink size={12} /> 참고 자료 {i + 1}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

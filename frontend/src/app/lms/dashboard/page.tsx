'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { TrendingUp, Calendar, ClipboardList, Clock, MessageCircle, Video } from 'lucide-react';
import StatCard from '@/components/lms/StatCard';
import ActivityFeed from '@/components/lms/ActivityFeed';
import { getDashboard } from '@/lib/lms';
import type { DashboardResponse } from '@/lib/lms-types';

export default function DashboardPage() {
  const searchParams = useSearchParams();
  const matchingId = Number(searchParams.get('matchingId'));
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!matchingId) return;
    getDashboard(matchingId)
      .then((res) => setData(res.data.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [matchingId]);

  if (!matchingId) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-gray-400">매칭 ID가 필요합니다. 매칭 내역에서 LMS에 접근해주세요.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">대시보드</h1>
        <p className="text-gray-400 mt-1">멘토링 진행 현황을 한눈에 확인하세요</p>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="진도율" value={`${data.progressRate}%`} icon={TrendingUp} color="blue" />
        <StatCard label="출석률" value={`${data.attendanceRate}%`} icon={Calendar} color="green" />
        <StatCard label="과제" value={`${data.assignmentStats.submitted}/${data.assignmentStats.total}`} icon={ClipboardList} color="purple" />
        <StatCard label="D-Day" value={`D-${data.dDay}`} icon={Clock} color="orange" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 다음 세션 */}
        <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4">다음 멘토링</h3>
          {data.nextSession ? (
            <div className="space-y-3">
              <p className="text-gray-300">{data.nextSession.category}</p>
              <p className="text-gray-400 text-sm">
                {data.nextSession.date} {data.nextSession.startTime} ~ {data.nextSession.endTime}
              </p>
              <a
                href={data.nextSession.meetLink}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-colors text-sm"
              >
                <Video size={16} />
                화상 회의 참여
              </a>
            </div>
          ) : (
            <p className="text-gray-500 text-sm">예정된 세션이 없습니다</p>
          )}
        </div>

        {/* 최근 활동 */}
        <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4">최근 활동</h3>
          <ActivityFeed activities={data.recentActivities} />
        </div>

        {/* 소통 링크 */}
        <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4">소통</h3>
          <div className="space-y-3">
            <div>
              <p className="text-gray-400 text-xs mb-1">멘토</p>
              <p className="text-white">{data.mentorInfo.name}</p>
              <p className="text-gray-500 text-sm">{data.mentorInfo.email}</p>
            </div>
            {data.communicationLinks.discord && (
              <a
                href={data.communicationLinks.discord}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-[#5865F2]/10 text-[#5865F2] hover:bg-[#5865F2]/20 transition-colors text-sm"
              >
                <MessageCircle size={16} />
                Discord 참여
              </a>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

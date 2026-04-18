'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { 
  Check, 
  X, 
  Loader2, 
  ArrowLeft, 
  FileText, 
  Calendar, 
  Clock, 
  Users,
  AlertCircle,
  Briefcase
} from 'lucide-react';

export default function AssignmentsPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading } = useAuth();
  const [assignments, setAssignments] = useState<any[]>([]);
  const [isPageLoading, setIsPageLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [isLoading, isLoggedIn, router]);

  useEffect(() => {
    if (isLoggedIn && user?.role === 'MENTOR') {
      fetchAssignments();
    } else if (!isLoading && user?.role !== 'MENTOR') {
      // 멘토가 아니면 접근 불가
      setError('멘토만 접근 가능한 페이지입니다.');
      setIsPageLoading(false);
    }
  }, [isLoggedIn, user?.role, isLoading]);

  const fetchAssignments = async () => {
    setIsPageLoading(true);
    try {
      const res = await fetch(`http://localhost:8080/api/applications/my-assignments`, {
        headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
      });
      if (res.ok) {
        const data = await res.json();
        setAssignments(data);
      } else {
        setError('배정 목록이 없습니다.');
      }
    } catch (e) {
      console.error(e);
      setError('배정 목록이 없습니다.');
    } finally {
      setIsPageLoading(false);
    }
  };

  const handleAssignmentAction = async (id: number, action: 'approve' | 'reject') => {
    try {
      const res = await fetch(`http://localhost:8080/api/applications/${id}/${action}`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
      });
      if (res.ok) {
        alert(action === 'approve' ? '매칭이 수락되었습니다!' : '매칭을 거절했습니다.');
        fetchAssignments();
      } else {
        alert('처리 중 오류가 발생했습니다.');
      }
    } catch (e) {
      console.error(e);
      alert('서버와 통신 중 오류가 발생했습니다.');
    }
  };

  if (isLoading || isPageLoading) {
    return (
      <>
        <Header />
        <main className="min-h-screen hero-gradient grid-pattern flex items-center justify-center pt-16">
          <Loader2 size={32} className="animate-spin text-blue-400" />
        </main>
      </>
    );
  }

  return (
    <>
      <Header />
      <main className="min-h-screen hero-gradient grid-pattern pt-28 pb-20">
        <div className="orb w-[400px] h-[400px] bg-blue-600/15 -top-20 -left-20" />
        <div className="orb w-[300px] h-[300px] bg-violet-600/10 bottom-20 right-10" />

        <div className="relative z-10 max-w-4xl mx-auto px-6">
          {/* Header */}
          <div className="mb-10">
            <Link
              href="/mypage"
              className="inline-flex items-center gap-2 text-gray-500 hover:text-gray-300 text-sm mb-6 transition-colors"
            >
              <ArrowLeft size={16} />
              마이페이지로 돌아가기
            </Link>
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center shadow-lg shadow-blue-500/20">
                <FileText size={24} className="text-white" />
              </div>
              <div>
                <h1 className="text-3xl font-bold text-white tracking-tight">배정 목록</h1>
                <p className="text-gray-400 mt-1">새로 배정된 멘티 신청서를 확인하고 매칭을 진행하세요.</p>
              </div>
            </div>
          </div>

          {error ? (
            <div className="glass-card rounded-2xl p-12 text-center border-red-500/20">
              <AlertCircle size={40} className="text-red-400 mx-auto mb-4" />
              <p className="text-white font-medium">{error}</p>
              <button 
                onClick={() => router.push('/')}
                className="mt-6 px-6 py-2.5 rounded-xl bg-white/5 border border-white/10 text-white hover:bg-white/10 transition-all"
              >
                홈으로 이동
              </button>
            </div>
          ) : assignments.length === 0 ? (
            <div className="glass-card rounded-2xl p-20 text-center">
              <div className="w-20 h-20 rounded-3xl bg-white/3 flex items-center justify-center mx-auto mb-6">
                <Briefcase size={36} className="text-gray-600" />
              </div>
              <h2 className="text-xl font-bold text-white mb-2">대기 중인 신청서가 없습니다</h2>
              <p className="text-gray-500">새로운 멘티가 배정되면 여기에 표시됩니다.</p>
            </div>
          ) : (
            <div className="grid gap-6">
              {assignments.map((app) => (
                <div 
                  key={app.id} 
                  className="glass-card rounded-2xl p-6 sm:p-8 border border-white/5 hover:border-white/10 transition-all duration-300 shadow-xl"
                >
                  <div className="flex flex-col lg:flex-row gap-8">
                    {/* Left: Info */}
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-4">
                        <span className="px-3 py-1 rounded-full bg-blue-500/10 text-blue-400 text-xs font-bold uppercase tracking-wider">
                          {app.category}
                        </span>
                        <span className="px-3 py-1 rounded-full bg-violet-500/10 text-violet-400 text-xs font-bold uppercase tracking-wider">
                          {app.courseType}
                        </span>
                      </div>
                      
                      <h3 className="text-xl font-bold text-white mb-4">
                        {app.userName} 멘티의 신청서
                      </h3>

                      <div className="grid sm:grid-cols-2 gap-4 mb-6">
                        <div className="p-4 rounded-xl bg-white/3 border border-white/5">
                          <p className="text-gray-500 text-xs mb-1">목표 커리어</p>
                          <p className="text-white text-sm font-medium">{app.careerGoal}</p>
                        </div>
                        <div className="p-4 rounded-xl bg-white/3 border border-white/5">
                          <p className="text-gray-500 text-xs mb-1">현재 실력 레벨</p>
                          <p className="text-white text-sm font-medium">{app.currentLevel}</p>
                        </div>
                      </div>

                      <div className="p-4 rounded-xl bg-black/20 border border-white/5">
                        <p className="text-gray-300 font-bold text-sm mb-3 flex items-center gap-2">
                          <Clock size={16} className="text-blue-400" />
                          희망 학습 시간
                        </p>
                        <div className="grid grid-cols-2 gap-4">
                          <div>
                            <p className="text-gray-500 text-[10px] uppercase font-bold tracking-widest mb-1">평일</p>
                            <p className="text-gray-300 text-sm">{app.weekdayStudyHours}</p>
                          </div>
                          <div>
                            <p className="text-gray-500 text-[10px] uppercase font-bold tracking-widest mb-1">주말</p>
                            <p className="text-gray-300 text-sm">{app.weekendStudyHours}</p>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Right: Actions */}
                    <div className="lg:w-48 flex flex-col gap-3 justify-center shrink-0 border-t lg:border-t-0 lg:border-l border-white/5 pt-6 lg:pt-0 lg:pl-8">
                      <button 
                        onClick={() => handleAssignmentAction(app.id, 'approve')}
                        className="w-full py-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30 transition-all flex items-center justify-center gap-2"
                      >
                        <Check size={18} />
                        수락 및 매칭
                      </button>
                      <button 
                        onClick={() => handleAssignmentAction(app.id, 'reject')}
                        className="w-full py-4 rounded-xl bg-white/5 hover:bg-red-500/10 text-gray-400 hover:text-red-400 font-bold text-sm border border-white/10 hover:border-red-500/20 transition-all flex items-center justify-center gap-2"
                      >
                        <X size={18} />
                        거절
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
      <Footer />
    </>
  );
}

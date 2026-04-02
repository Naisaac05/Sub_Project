'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import { useAuth } from '@/contexts/AuthContext';
import { updateMyProfile } from '@/lib/auth';
import { User, Mail, Shield, Calendar, Edit3, Save, X, Loader2, ArrowLeft } from 'lucide-react';

export default function MyPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading, refreshUser } = useAuth();

  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({ name: '', password: '', passwordConfirm: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // 비로그인 시 로그인 페이지로 리다이렉트
  useEffect(() => {
    if (!isLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [isLoading, isLoggedIn, router]);

  // 편집 모드 시작 시 현재 값으로 초기화
  const startEditing = () => {
    setEditForm({ name: user?.name || '', password: '', passwordConfirm: '' });
    setIsEditing(true);
    setError('');
    setSuccess('');
  };

  const cancelEditing = () => {
    setIsEditing(false);
    setError('');
  };

  const handleSave = async () => {
    setError('');
    setSuccess('');

    // 유효성 검증
    if (editForm.name && (editForm.name.length < 2 || editForm.name.length > 50)) {
      setError('이름은 2~50자로 입력해주세요.');
      return;
    }
    if (editForm.password) {
      if (editForm.password.length < 8 || editForm.password.length > 20) {
        setError('비밀번호는 8~20자로 입력해주세요.');
        return;
      }
      if (editForm.password !== editForm.passwordConfirm) {
        setError('비밀번호가 일치하지 않습니다.');
        return;
      }
    }

    // 변경 사항 확인
    const updateData: { name?: string; password?: string } = {};
    if (editForm.name && editForm.name !== user?.name) {
      updateData.name = editForm.name;
    }
    if (editForm.password) {
      updateData.password = editForm.password;
    }

    if (Object.keys(updateData).length === 0) {
      setError('변경된 내용이 없습니다.');
      return;
    }

    setIsSaving(true);
    try {
      const res = await updateMyProfile(updateData);
      if (res.success) {
        await refreshUser();
        setIsEditing(false);
        setSuccess('프로필이 성공적으로 수정되었습니다.');
      }
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setError(axiosError.response?.data?.message || '프로필 수정에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  // 역할 라벨
  const roleLabel = user?.role === 'MENTOR' ? '멘토' : user?.role === 'ADMIN' ? '관리자' : '멘티';
  const roleColor = user?.role === 'MENTOR' ? 'text-violet-400 bg-violet-500/10' :
    user?.role === 'ADMIN' ? 'text-amber-400 bg-amber-500/10' : 'text-blue-400 bg-blue-500/10';

  // 로딩 중
  if (isLoading || !user) {
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
      <main className="min-h-screen hero-gradient grid-pattern pt-16">
        <div className="orb w-[400px] h-[400px] bg-blue-600/20 -top-20 -left-20" />
        <div className="orb w-[300px] h-[300px] bg-violet-600/15 bottom-20 right-10" />

        <div className="relative z-10 max-w-2xl mx-auto px-6 py-12">
          {/* 뒤로가기 */}
          <Link
            href="/"
            className="inline-flex items-center gap-2 text-gray-500 hover:text-gray-300
                     text-sm mb-8 transition-colors"
          >
            <ArrowLeft size={16} />
            홈으로 돌아가기
          </Link>

          {/* 프로필 카드 */}
          <div className="glass-card rounded-2xl overflow-hidden shadow-2xl shadow-black/20">
            {/* 상단 배너 */}
            <div className="h-28 bg-gradient-to-r from-blue-600/30 via-violet-600/20 to-cyan-600/30 relative">
              <div className="absolute -bottom-10 left-8">
                <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-blue-500 to-cyan-400
                             flex items-center justify-center text-white font-bold text-2xl
                             border-4 border-[#0a0e1a] shadow-lg">
                  {user.name[0].toUpperCase()}
                </div>
              </div>
            </div>

            {/* 프로필 정보 */}
            <div className="pt-14 px-8 pb-8">
              <div className="flex items-start justify-between mb-6">
                <div>
                  <h1 className="text-2xl font-bold text-white">{user.name}</h1>
                  <span className={`inline-block mt-1 px-2.5 py-0.5 rounded-md text-xs font-medium ${roleColor}`}>
                    {roleLabel}
                  </span>
                </div>
                {!isEditing && (
                  <button
                    onClick={startEditing}
                    className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm
                             text-gray-400 hover:text-white border border-white/10
                             hover:border-white/20 hover:bg-white/5 transition-all duration-200"
                  >
                    <Edit3 size={14} />
                    수정
                  </button>
                )}
              </div>

              {/* 성공 메시지 */}
              {success && (
                <div className="mb-5 p-3.5 rounded-xl bg-green-500/10 border border-green-500/20">
                  <p className="text-green-400 text-sm text-center">{success}</p>
                </div>
              )}

              {/* 에러 메시지 */}
              {error && (
                <div className="mb-5 p-3.5 rounded-xl bg-red-500/10 border border-red-500/20">
                  <p className="text-red-400 text-sm text-center">{error}</p>
                </div>
              )}

              {/* 정보 목록 */}
              <div className="space-y-4">
                {/* 이메일 */}
                <div className="flex items-center gap-4 p-4 rounded-xl bg-white/3 border border-white/5">
                  <div className="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center">
                    <Mail size={18} className="text-blue-400" />
                  </div>
                  <div className="flex-1">
                    <p className="text-gray-500 text-xs mb-0.5">이메일</p>
                    <p className="text-white text-sm">{user.email}</p>
                  </div>
                </div>

                {/* 이름 */}
                <div className="flex items-center gap-4 p-4 rounded-xl bg-white/3 border border-white/5">
                  <div className="w-10 h-10 rounded-lg bg-violet-500/10 flex items-center justify-center">
                    <User size={18} className="text-violet-400" />
                  </div>
                  <div className="flex-1">
                    <p className="text-gray-500 text-xs mb-0.5">이름</p>
                    {isEditing ? (
                      <input
                        type="text"
                        value={editForm.name}
                        onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                        className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10
                                 text-white text-sm focus:outline-none focus:border-blue-500/50
                                 focus:ring-1 focus:ring-blue-500/25 transition-all"
                      />
                    ) : (
                      <p className="text-white text-sm">{user.name}</p>
                    )}
                  </div>
                </div>

                {/* 역할 */}
                <div className="flex items-center gap-4 p-4 rounded-xl bg-white/3 border border-white/5">
                  <div className="w-10 h-10 rounded-lg bg-cyan-500/10 flex items-center justify-center">
                    <Shield size={18} className="text-cyan-400" />
                  </div>
                  <div className="flex-1">
                    <p className="text-gray-500 text-xs mb-0.5">역할</p>
                    <p className="text-white text-sm">{roleLabel}</p>
                  </div>
                </div>

                {/* 가입일 */}
                <div className="flex items-center gap-4 p-4 rounded-xl bg-white/3 border border-white/5">
                  <div className="w-10 h-10 rounded-lg bg-amber-500/10 flex items-center justify-center">
                    <Calendar size={18} className="text-amber-400" />
                  </div>
                  <div className="flex-1">
                    <p className="text-gray-500 text-xs mb-0.5">가입일</p>
                    <p className="text-white text-sm">
                      {new Date(user.createdAt).toLocaleDateString('ko-KR', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                      })}
                    </p>
                  </div>
                </div>

                {/* 비밀번호 변경 (편집 모드) */}
                {isEditing && (
                  <>
                    <div className="p-4 rounded-xl bg-white/3 border border-white/5 space-y-3">
                      <p className="text-gray-400 text-xs font-medium">비밀번호 변경 (선택)</p>
                      <input
                        type="password"
                        value={editForm.password}
                        onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
                        placeholder="새 비밀번호 (8~20자)"
                        className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10
                                 text-white placeholder-gray-600 text-sm focus:outline-none
                                 focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/25 transition-all"
                      />
                      <input
                        type="password"
                        value={editForm.passwordConfirm}
                        onChange={(e) => setEditForm({ ...editForm, passwordConfirm: e.target.value })}
                        placeholder="비밀번호 확인"
                        className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10
                                 text-white placeholder-gray-600 text-sm focus:outline-none
                                 focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/25 transition-all"
                      />
                    </div>

                    {/* 편집 버튼 */}
                    <div className="flex gap-3 pt-2">
                      <button
                        onClick={cancelEditing}
                        className="flex-1 flex items-center justify-center gap-2 py-3 rounded-xl
                                 text-gray-400 text-sm font-medium border border-white/10
                                 hover:border-white/20 hover:bg-white/5 transition-all duration-200"
                      >
                        <X size={16} />
                        취소
                      </button>
                      <button
                        onClick={handleSave}
                        disabled={isSaving}
                        className="flex-1 flex items-center justify-center gap-2 py-3 rounded-xl
                                 text-white font-bold text-sm
                                 bg-gradient-to-r from-blue-600 to-blue-500
                                 shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30
                                 hover:scale-[1.01] transition-all duration-300
                                 disabled:opacity-60 disabled:cursor-not-allowed"
                      >
                        {isSaving ? (
                          <Loader2 size={16} className="animate-spin" />
                        ) : (
                          <Save size={16} />
                        )}
                        {isSaving ? '저장 중...' : '저장'}
                      </button>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </main>
    </>
  );
}

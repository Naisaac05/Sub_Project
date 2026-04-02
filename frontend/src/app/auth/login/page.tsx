'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import { useAuth } from '@/contexts/AuthContext';
import { Eye, EyeOff, Mail, Lock, ArrowRight, Loader2 } from 'lucide-react';

export default function LoginPage() {
  const router = useRouter();
  const { login, isLoggedIn, isLoading: authLoading } = useAuth();

  const [showPassword, setShowPassword] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 이미 로그인 상태이면 홈으로 리다이렉트
  useEffect(() => {
    if (!authLoading && isLoggedIn) {
      router.replace('/');
    }
  }, [authLoading, isLoggedIn, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email || !password) {
      setError('이메일과 비밀번호를 모두 입력해주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      await login(email, password);
      router.push('/');
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      if (axiosError.response?.data?.message) {
        setError(axiosError.response.data.message);
      } else {
        setError('로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <Header />
      <main className="min-h-screen hero-gradient grid-pattern flex items-center justify-center px-6 pt-16">
        {/* Decorative orbs */}
        <div className="orb w-[400px] h-[400px] bg-blue-600/20 -top-20 -left-20" />
        <div className="orb w-[300px] h-[300px] bg-violet-600/15 bottom-20 right-10" />

        <div className="relative z-10 w-full max-w-md">
          {/* Card */}
          <div className="glass-card rounded-2xl p-8 sm:p-10 shadow-2xl shadow-black/20">
            {/* Header */}
            <div className="text-center mb-8">
              <Link href="/" className="inline-flex items-center gap-2.5 mb-6">
                <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-blue-500 to-cyan-400
                             flex items-center justify-center">
                  <span className="text-white font-bold text-sm font-[Outfit]">D</span>
                </div>
                <span className="text-white font-bold text-xl tracking-tight font-[Outfit]">
                  Dev<span className="text-cyan-400">Match</span>
                </span>
              </Link>
              <h1 className="text-2xl font-extrabold text-white mb-2">로그인</h1>
              <p className="text-gray-500 text-sm">계정에 로그인하고 멘토링을 시작하세요</p>
            </div>

            {/* Error Message */}
            {error && (
              <div className="mb-5 p-3.5 rounded-xl bg-red-500/10 border border-red-500/20">
                <p className="text-red-400 text-sm text-center">{error}</p>
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Email */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-2">이메일</label>
                <div className="relative">
                  <Mail size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-600" />
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@example.com"
                    disabled={isSubmitting}
                    className="w-full pl-11 pr-4 py-3.5 rounded-xl bg-white/5 border border-white/10
                             text-white placeholder-gray-600 text-sm
                             focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/25
                             transition-all duration-200 disabled:opacity-50"
                  />
                </div>
              </div>

              {/* Password */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-2">비밀번호</label>
                <div className="relative">
                  <Lock size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-600" />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="비밀번호를 입력하세요"
                    disabled={isSubmitting}
                    className="w-full pl-11 pr-12 py-3.5 rounded-xl bg-white/5 border border-white/10
                             text-white placeholder-gray-600 text-sm
                             focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/25
                             transition-all duration-200 disabled:opacity-50"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-600 hover:text-gray-400
                             transition-colors"
                  >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </div>

              {/* Remember & Forgot */}
              <div className="flex items-center justify-between">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" className="w-4 h-4 rounded bg-white/5 border-white/10
                                                   text-blue-500 focus:ring-blue-500/25" />
                  <span className="text-gray-500 text-sm">로그인 유지</span>
                </label>
                <Link href="#" className="text-blue-400 text-sm hover:text-blue-300 transition-colors">
                  비밀번호 찾기
                </Link>
              </div>

              {/* Submit */}
              <button
                type="submit"
                disabled={isSubmitting}
                className="shimmer w-full flex items-center justify-center gap-2 py-3.5 rounded-xl
                         text-white font-bold text-sm
                         bg-gradient-to-r from-blue-600 via-blue-500 to-cyan-500
                         shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30
                         hover:scale-[1.01] transition-all duration-300
                         disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:scale-100"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 size={16} className="animate-spin" />
                    로그인 중...
                  </>
                ) : (
                  <>
                    로그인
                    <ArrowRight size={16} />
                  </>
                )}
              </button>
            </form>

            {/* Divider */}
            <div className="flex items-center gap-3 my-6">
              <div className="flex-1 h-px bg-white/10" />
              <span className="text-gray-600 text-xs">또는</span>
              <div className="flex-1 h-px bg-white/10" />
            </div>

            {/* Google OAuth */}
            <button className="w-full flex items-center justify-center gap-3 py-3.5 rounded-xl
                           border border-white/10 hover:border-white/20 hover:bg-white/5
                           text-gray-300 text-sm font-medium transition-all duration-200">
              <svg className="w-5 h-5" viewBox="0 0 24 24">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
              Google로 로그인
            </button>

            {/* Sign up link */}
            <p className="text-center text-gray-500 text-sm mt-6">
              아직 계정이 없으신가요?{' '}
              <Link href="/auth/signup" className="text-blue-400 hover:text-blue-300 font-semibold transition-colors">
                회원가입
              </Link>
            </p>
          </div>
        </div>
      </main>
    </>
  );
}

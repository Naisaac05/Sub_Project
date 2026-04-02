'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import { useAuth } from '@/contexts/AuthContext';
import { Eye, EyeOff, Mail, Lock, User, ArrowRight, GraduationCap, Briefcase, Loader2 } from 'lucide-react';

type Role = 'MENTEE' | 'MENTOR';

export default function SignupPage() {
  const router = useRouter();
  const { signup, isLoggedIn, isLoading: authLoading } = useAuth();

  const [showPassword, setShowPassword] = useState(false);
  const [role, setRole] = useState<Role>('MENTEE');
  const [form, setForm] = useState({ name: '', email: '', password: '', passwordConfirm: '' });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [agreeTerms, setAgreeTerms] = useState(false);

  // 이미 로그인 상태이면 홈으로 리다이렉트
  useEffect(() => {
    if (!authLoading && isLoggedIn) {
      router.replace('/');
    }
  }, [authLoading, isLoggedIn, router]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const validate = (): string | null => {
    if (!form.name || !form.email || !form.password || !form.passwordConfirm) {
      return '모든 필드를 입력해주세요.';
    }
    if (form.name.length < 2 || form.name.length > 50) {
      return '이름은 2~50자로 입력해주세요.';
    }
    if (form.password.length < 8 || form.password.length > 20) {
      return '비밀번호는 8~20자로 입력해주세요.';
    }
    if (form.password !== form.passwordConfirm) {
      return '비밀번호가 일치하지 않습니다.';
    }
    if (!agreeTerms) {
      return '이용약관 및 개인정보처리방침에 동의해주세요.';
    }
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setIsSubmitting(true);
    try {
      await signup(form.name, form.email, form.password);
      router.push('/auth/login?signup=success');
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      if (axiosError.response?.data?.message) {
        setError(axiosError.response.data.message);
      } else {
        setError('회원가입에 실패했습니다. 다시 시도해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <Header />
      <main className="min-h-screen hero-gradient grid-pattern flex items-center justify-center px-6 pt-16 pb-12">
        <div className="orb w-[400px] h-[400px] bg-violet-600/20 -top-20 right-10" />
        <div className="orb w-[300px] h-[300px] bg-cyan-500/15 bottom-20 -left-10" />

        <div className="relative z-10 w-full max-w-md">
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
              <h1 className="text-2xl font-extrabold text-white mb-2">회원가입</h1>
              <p className="text-gray-500 text-sm">DevMatch에서 성장을 시작하세요</p>
            </div>

            {/* Error Message */}
            {error && (
              <div className="mb-5 p-3.5 rounded-xl bg-red-500/10 border border-red-500/20">
                <p className="text-red-400 text-sm text-center">{error}</p>
              </div>
            )}

            {/* Role Select */}
            <div className="grid grid-cols-2 gap-3 mb-6">
              <button
                type="button"
                onClick={() => setRole('MENTEE')}
                className={`flex items-center justify-center gap-2 py-3.5 rounded-xl text-sm font-semibold
                          transition-all duration-200 ${
                  role === 'MENTEE'
                    ? 'bg-blue-500/15 border-blue-500/40 text-blue-400 border'
                    : 'bg-white/5 border border-white/10 text-gray-500 hover:border-white/20'
                }`}
              >
                <GraduationCap size={18} />
                멘티
              </button>
              <button
                type="button"
                onClick={() => setRole('MENTOR')}
                className={`flex items-center justify-center gap-2 py-3.5 rounded-xl text-sm font-semibold
                          transition-all duration-200 ${
                  role === 'MENTOR'
                    ? 'bg-violet-500/15 border-violet-500/40 text-violet-400 border'
                    : 'bg-white/5 border border-white/10 text-gray-500 hover:border-white/20'
                }`}
              >
                <Briefcase size={18} />
                멘토
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Name */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-2">이름</label>
                <div className="relative">
                  <User size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-600" />
                  <input
                    type="text"
                    name="name"
                    value={form.name}
                    onChange={handleChange}
                    placeholder="홍길동"
                    disabled={isSubmitting}
                    className="w-full pl-11 pr-4 py-3.5 rounded-xl bg-white/5 border border-white/10
                             text-white placeholder-gray-600 text-sm
                             focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/25
                             transition-all duration-200 disabled:opacity-50"
                  />
                </div>
              </div>

              {/* Email */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-2">이메일</label>
                <div className="relative">
                  <Mail size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-600" />
                  <input
                    type="email"
                    name="email"
                    value={form.email}
                    onChange={handleChange}
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
                    name="password"
                    value={form.password}
                    onChange={handleChange}
                    placeholder="8자 이상 입력하세요"
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

              {/* Password Confirm */}
              <div>
                <label className="block text-sm font-medium text-gray-400 mb-2">비밀번호 확인</label>
                <div className="relative">
                  <Lock size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-600" />
                  <input
                    type="password"
                    name="passwordConfirm"
                    value={form.passwordConfirm}
                    onChange={handleChange}
                    placeholder="비밀번호를 다시 입력하세요"
                    disabled={isSubmitting}
                    className="w-full pl-11 pr-4 py-3.5 rounded-xl bg-white/5 border border-white/10
                             text-white placeholder-gray-600 text-sm
                             focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/25
                             transition-all duration-200 disabled:opacity-50"
                  />
                </div>
              </div>

              {/* Terms */}
              <label className="flex items-start gap-2.5 cursor-pointer pt-1">
                <input
                  type="checkbox"
                  checked={agreeTerms}
                  onChange={(e) => setAgreeTerms(e.target.checked)}
                  className="w-4 h-4 mt-0.5 rounded bg-white/5 border-white/10
                                                 text-blue-500 focus:ring-blue-500/25"
                />
                <span className="text-gray-500 text-xs leading-relaxed">
                  <Link href="#" className="text-blue-400 hover:underline">이용약관</Link> 및{' '}
                  <Link href="#" className="text-blue-400 hover:underline">개인정보처리방침</Link>에 동의합니다.
                </span>
              </label>

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
                    가입 중...
                  </>
                ) : (
                  <>
                    가입하기
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
              Google로 가입하기
            </button>

            {/* Login link */}
            <p className="text-center text-gray-500 text-sm mt-6">
              이미 계정이 있으신가요?{' '}
              <Link href="/auth/login" className="text-blue-400 hover:text-blue-300 font-semibold transition-colors">
                로그인
              </Link>
            </p>
          </div>
        </div>
      </main>
    </>
  );
}

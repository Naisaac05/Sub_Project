'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import AdminHeader from '@/components/admin/AdminHeader';
import AdminSidebar from '@/components/admin/AdminSidebar';
import NotAuthorized from '@/components/admin/NotAuthorized';

/**
 * /admin/** 전용 레이아웃.
 *
 * 접근 제어:
 *  - isLoading: 스피너 (초기 auth 복원 중)
 *  - 비로그인: /auth/login 으로 리다이렉트 (redirect 쿼리 동봉)
 *  - 로그인했지만 ADMIN 아님: 403 렌더 (리다이렉트 X)
 *  - ADMIN: Header + Sidebar + children
 */
export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading, isLoggedIn } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (isLoading) return;
    if (!isLoggedIn) {
      const redirect = encodeURIComponent(pathname ?? '/admin');
      router.replace(`/auth/login?redirect=${redirect}`);
    }
  }, [isLoading, isLoggedIn, pathname, router]);

  // 로딩
  if (isLoading || !isLoggedIn) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-slate-900" />
      </div>
    );
  }

  // 로그인했지만 관리자가 아님 → 403
  if (user?.role !== 'ADMIN' && user?.role !== 'SUPER_ADMIN') {
    return (
      <div className="min-h-screen bg-slate-50">
        <NotAuthorized />
      </div>
    );
  }

  // 관리자
  return (
    <div className="min-h-screen bg-slate-50">
      <AdminHeader />
      <div className="mx-auto flex max-w-[1280px]">
        <AdminSidebar />
        <main className="min-w-0 flex-1 px-6 py-8">{children}</main>
      </div>
    </div>
  );
}

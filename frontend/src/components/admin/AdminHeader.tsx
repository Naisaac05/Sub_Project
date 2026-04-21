'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { LogOut } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { Button } from '@/components/ui/button';

export default function AdminHeader() {
  const { user, logout } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push('/');
  }

  return (
    <header className="border-b border-slate-200 bg-slate-100">
      <div className="mx-auto flex h-14 max-w-[1280px] items-center justify-between px-6">
        <div className="flex items-center gap-3">
          <Link href="/" className="text-base font-bold text-slate-900">
            DevMatch
          </Link>
          <span className="text-slate-300">·</span>
          <span className="text-sm font-medium text-slate-600">
            관리자 콘솔
          </span>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600">
            {user?.name}
            <span className="ml-1 text-slate-400">({user?.email})</span>
          </span>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            className="gap-1.5 text-slate-600 hover:text-slate-900"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            로그아웃
          </Button>
        </div>
      </div>
    </header>
  );
}

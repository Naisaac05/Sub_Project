'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LayoutDashboard, UserCheck, Users, CreditCard, FileText, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

const NAV_ITEMS: Array<{
  href: string;
  label: string;
  icon: typeof Users;
  match: (pathname: string) => boolean;
  requireSuperAdmin?: boolean;
}> = [
  {
    href: '/admin/dashboard',
    label: '대시보드',
    icon: LayoutDashboard,
    match: (p) => p === '/admin/dashboard' || p.startsWith('/admin/dashboard/'),
  },
  {
    href: '/admin/mentor',
    label: '멘토 심사',
    icon: UserCheck,
    match: (p) => p === '/admin/mentor' || p.startsWith('/admin/mentor/'),
  },
  {
    href: '/admin/users',
    label: '회원 관리',
    icon: Users,
    match: (p) => p === '/admin/users' || p.startsWith('/admin/users/'),
  },
  {
    href: '/admin/payments',
    label: '결제 관리',
    icon: CreditCard,
    match: (p) => p === '/admin/payments' || p.startsWith('/admin/payments/'),
  },
  {
    href: '/admin/posts',
    label: '게시물 관리',
    icon: FileText,
    match: (p) => p === '/admin/posts' || p.startsWith('/admin/posts/'),
  },
  {
    href: '/admin/admins',
    label: '관리자 계정',
    icon: ShieldCheck,
    match: (p) => p === '/admin/admins' || p.startsWith('/admin/admins/'),
    requireSuperAdmin: true,
  },
];

export default function AdminSidebar() {
  const pathname = usePathname();
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';
  const items = NAV_ITEMS.filter((it) => !it.requireSuperAdmin || isSuperAdmin);

  return (
    <aside className="hidden w-[220px] shrink-0 border-r border-slate-200 bg-white md:block">
      <nav className="py-6">
        <ul className="space-y-1 px-3">
          {items.map((item) => {
            const isActive = item.match(pathname ?? '');
            const Icon = item.icon;
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className={
                    'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors ' +
                    (isActive
                      ? 'bg-slate-900 text-white'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900')
                  }
                  aria-current={isActive ? 'page' : undefined}
                >
                  <Icon className="h-4 w-4" aria-hidden="true" />
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
    </aside>
  );
}

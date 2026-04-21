'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Users } from 'lucide-react';

const NAV_ITEMS: Array<{
  href: string;
  label: string;
  icon: typeof Users;
  match: (pathname: string) => boolean;
}> = [
  {
    href: '/admin/mentor',
    label: '멘토 심사',
    icon: Users,
    match: (p) => p === '/admin/mentor' || p.startsWith('/admin/mentor/'),
  },
];

export default function AdminSidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden w-[220px] shrink-0 border-r border-slate-200 bg-white md:block">
      <nav className="py-6">
        <ul className="space-y-1 px-3">
          {NAV_ITEMS.map((item) => {
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

'use client';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { LayoutDashboard, BookOpen, Video, ClipboardList, NotebookPen, Briefcase, Award } from 'lucide-react';

const menuItems = [
  { label: '대시보드', href: '/lms/dashboard', icon: LayoutDashboard },
  { label: '커리큘럼', href: '/lms/curriculum', icon: BookOpen },
  { label: '멘토링 세션', href: '/lms/sessions', icon: Video },
  { label: '과제 / 코드리뷰', href: '/lms/assignments', icon: ClipboardList },
  { label: '학습 노트', href: '/lms/notes', icon: NotebookPen },
  { label: '취업 지원', href: '/lms/career', icon: Briefcase },
  { label: '수료증', href: '/lms/certificate', icon: Award },
];

export default function LmsSidebar() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const matchingId = searchParams.get('matchingId') || '';

  return (
    <aside className="w-64 min-h-screen bg-[#0b101e] border-r border-white/5 flex flex-col">
      <div className="px-6 py-5 border-b border-white/5">
        <Link href="/" className="text-white font-bold text-lg tracking-tight font-[Outfit]">
          Dev<span className="text-cyan-400">Match</span>
          <span className="text-gray-500 text-sm font-normal ml-2">LMS</span>
        </Link>
      </div>
      <nav className="flex-1 px-3 py-4 space-y-1">
        {menuItems.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;
          const href = matchingId ? `${item.href}?matchingId=${matchingId}` : item.href;
          return (
            <Link key={item.href} href={href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 ${
                isActive ? 'bg-blue-500/10 text-blue-400' : 'text-gray-400 hover:text-white hover:bg-white/5'
              }`}>
              <Icon size={18} />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}

'use client';

import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  Briefcase,
  ChevronDown,
  FileText,
  LogOut,
  Menu,
  ShieldCheck,
  User,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

const LABELS = {
  courses: '\uBA58\uD1A0\uB9C1 \uCF54\uC2A4',
  tests: '\uC2E4\uB825 \uD14C\uC2A4\uD2B8',
  community: '\uCEE4\uBBA4\uB2C8\uD2F0',
  pending: '\uC2B9\uC778 \uB300\uAE30\uC911',
  rejected: '\uBC18\uB824\uB428',
  mentor: '\uBA58\uD1A0',
  admin: '\uAD00\uB9AC\uC790',
  mentee: '\uBA58\uD2F0',
  mypage: '\uB9C8\uC774\uD398\uC774\uC9C0',
  testResults: '\uD14C\uC2A4\uD2B8 \uACB0\uACFC',
  matching: '\uB9E4\uCE6D \uB0B4\uC5ED',
  mentorStatus: '\uBA58\uD1A0 \uC2E0\uCCAD/\uC0C1\uD0DC',
  assignments: '\uBC30\uC815 \uBAA9\uB85D',
  adminConsole: '\uAD00\uB9AC\uC790 \uCF58\uC194',
  logout: '\uB85C\uADF8\uC544\uC6C3',
  login: '\uB85C\uADF8\uC778',
  start: '\uC2DC\uC791\uD558\uAE30',
  menuToggle: '\uBA54\uB274 \uD1A0\uAE00',
  careerCourses: '\uCDE8\uC5C5/\uC774\uC9C1 \uBA58\uD1A0\uB9C1',
  shortTerm: '\uB2E8\uAE30 \uCDE8\uC5C5/\uC774\uC9C1',
  expertHeading: '5\uB144\uCC28 \uC774\uC0C1 \uBA58\uD1A0\uB9C1',
  expertMsa: 'Kotlin/MSA \uCD5C\uACE0\uAE09 \uACFC\uC815',
  distributedLock: '\uBD84\uC0B0 \uB77D Deep Dive',
};

const navItems = [
  {
    label: LABELS.courses,
    href: '/mentors',
    hasMegamenu: true,
  },
  { label: LABELS.tests, href: '/tests' },
  { label: 'LMS', href: '/lms/dashboard' },
  { label: LABELS.community, href: '/community' },
  { label: 'FAQ', href: '/faq' },
];

const MENTOR_STATUS_BADGE: Record<'PENDING' | 'REJECTED', { label: string; className: string }> = {
  PENDING: {
    label: LABELS.pending,
    className: 'bg-amber-500/15 text-amber-300',
  },
  REJECTED: {
    label: LABELS.rejected,
    className: 'bg-red-500/15 text-red-300',
  },
};

export default function Header() {
  const router = useRouter();
  const { user, mentorStatus, isLoggedIn, isLoading, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = async () => {
    setDropdownOpen(false);
    setMobileOpen(false);
    await logout();
    router.push('/');
  };

  const userInitial = user?.name ? user.name[0].toUpperCase() : '?';
  const roleLabel =
    user?.role === 'MENTOR'
      ? LABELS.mentor
      : user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN'
        ? LABELS.admin
        : LABELS.mentee;

  return (
    <header className="fixed left-0 right-0 top-0 z-50 transition-all duration-300">
      <div className="border-b border-white/5 bg-[#0a0e1a]/80 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
          <Link href="/" className="group flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-cyan-400 transition-shadow duration-300 group-hover:shadow-lg group-hover:shadow-blue-500/25">
              <span className="font-[Outfit] text-sm font-bold text-white">D</span>
            </div>
            <span className="font-[Outfit] text-lg font-bold tracking-tight text-white">
              Dev<span className="text-cyan-400">Match</span>
            </span>
          </Link>

          <nav className="group/nav hidden items-center gap-1 md:flex">
            {navItems.map((item) => (
              <div key={item.label} className={`relative ${item.hasMegamenu ? 'group/mega' : ''}`}>
                <Link
                  href={item.href}
                  className="flex items-center gap-1 px-4 py-6 text-sm font-semibold text-gray-300 transition-all duration-200 hover:text-white"
                >
                  {item.label}
                  {item.hasMegamenu ? (
                    <ChevronDown
                      size={14}
                      className="opacity-70 transition-transform group-hover/mega:rotate-180"
                    />
                  ) : null}
                </Link>

                {item.hasMegamenu ? (
                  <div className="invisible absolute left-[-200px] top-full w-screen max-w-[1280px] cursor-default border-y border-white/10 bg-[#373c44] opacity-0 shadow-xl transition-all duration-300 group-hover/mega:visible group-hover/mega:opacity-100">
                    <div className="flex max-w-7xl gap-20 px-6 py-10 text-sm">
                      <div className="space-y-4">
                        <h3 className="mb-2 font-bold text-white">{LABELS.careerCourses}</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/java-backend" className="hover:text-white">Java/Kotlin Backend + AI</Link></li>
                          <li><Link href="/mentors/node-backend" className="hover:text-white">Node.js Backend + AI</Link></li>
                          <li><Link href="/mentors/python-backend" className="hover:text-white">Python Backend + AI</Link></li>
                          <li><Link href="/mentors/frontend" className="hover:text-white">Frontend + AI</Link></li>
                          <li><Link href="/mentors/android" className="hover:text-white">Android + AI</Link></li>
                          <li><Link href="/mentors/ios" className="hover:text-white">iOS + AI</Link></li>
                          <li><Link href="/mentors/flutter" className="hover:text-white">Flutter + AI</Link></li>
                        </ul>
                      </div>

                      <div className="space-y-4 pt-9">
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/react-native" className="hover:text-white">React Native + AI</Link></li>
                          <li><Link href="/mentors/devops" className="hover:text-white">DevOps</Link></li>
                          <li><Link href="/mentors/data-engineer" className="hover:text-white">Data Engineer + AI</Link></li>
                          <li><Link href="/mentors/ml-engineer" className="hover:text-white">ML Engineer</Link></li>
                          <li><Link href="/mentors/game-server" className="hover:text-white">Game Server</Link></li>
                          <li><Link href="/mentors/short-term" className="hover:text-white">{LABELS.shortTerm}</Link></li>
                        </ul>
                      </div>

                      <div className="space-y-4">
                        <h3 className="mb-2 font-bold text-white">First Step</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/firststep" className="hover:text-white">Java Backend + AI</Link></li>
                        </ul>
                      </div>

                      <div className="space-y-4">
                        <h3 className="mb-2 font-bold text-white">F5: Deep Dive</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/distributed-lock" className="hover:text-white">{LABELS.distributedLock}</Link></li>
                          <li><Link href="/mentors/kafka" className="hover:text-white">Kafka Deep Dive</Link></li>
                        </ul>
                      </div>

                      <div className="space-y-4">
                        <h3 className="mb-2 font-bold text-white">{LABELS.expertHeading}</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/expert-msa" className="hover:text-white">{LABELS.expertMsa}</Link></li>
                        </ul>
                      </div>
                    </div>
                  </div>
                ) : null}
              </div>
            ))}
          </nav>

          <div className="hidden items-center gap-3 md:flex">
            {isLoading ? (
              <div className="h-9 w-24" />
            ) : isLoggedIn && user ? (
              <div className="relative" ref={dropdownRef}>
                <button
                  onClick={() => setDropdownOpen(!dropdownOpen)}
                  className="flex items-center gap-2.5 rounded-xl px-3 py-1.5 transition-all duration-200 hover:bg-white/5"
                >
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 text-xs font-bold text-white">
                    {userInitial}
                  </div>
                  <span className="max-w-[100px] truncate text-sm font-medium text-gray-300">{user.name}</span>
                  <ChevronDown
                    size={14}
                    className={`text-gray-500 transition-transform duration-200 ${dropdownOpen ? 'rotate-180' : ''}`}
                  />
                </button>

                {dropdownOpen ? (
                  <div className="animate-fade-in absolute right-0 top-full mt-2 w-56 overflow-hidden rounded-xl border border-white/10 bg-[#0f1420] shadow-2xl shadow-black/40">
                    <div className="border-b border-white/5 px-4 py-3">
                      <p className="text-sm font-semibold text-white">{user.name}</p>
                      <p className="mt-0.5 text-xs text-gray-500">{user.email}</p>
                      <div className="mt-1.5 flex items-center gap-1.5">
                        <span className="inline-block rounded-md bg-blue-500/10 px-2 py-0.5 text-xs font-medium text-blue-400">
                          {roleLabel}
                        </span>
                        {user.role === 'MENTOR' && mentorStatus && mentorStatus !== 'APPROVED' ? (
                          <span className={`inline-block rounded-md px-2 py-0.5 text-xs font-medium ${MENTOR_STATUS_BADGE[mentorStatus].className}`}>
                            {MENTOR_STATUS_BADGE[mentorStatus].label}
                          </span>
                        ) : null}
                      </div>
                    </div>

                    <div className="py-1">
                      <DropdownLink href="/mypage" icon={<User size={16} />} onClick={() => setDropdownOpen(false)}>
                        {LABELS.mypage}
                      </DropdownLink>
                      <DropdownLink href="/tests/results" icon={<FileText size={16} />} onClick={() => setDropdownOpen(false)}>
                        {LABELS.testResults}
                      </DropdownLink>
                      <DropdownLink href="/matching" icon={<Users size={16} />} onClick={() => setDropdownOpen(false)}>
                        {LABELS.matching}
                      </DropdownLink>
                      {user.role === 'MENTOR' ? (
                        <>
                          <DropdownLink href="/mentor/status" icon={<Briefcase size={16} />} onClick={() => setDropdownOpen(false)}>
                            {LABELS.mentorStatus}
                          </DropdownLink>
                          <DropdownLink href="/lms/assignments" icon={<FileText size={16} />} onClick={() => setDropdownOpen(false)}>
                            {LABELS.assignments}
                          </DropdownLink>
                        </>
                      ) : null}
                      {user.role === 'ADMIN' || user.role === 'SUPER_ADMIN' ? (
                        <DropdownLink href="/admin/mentor" icon={<ShieldCheck size={16} />} onClick={() => setDropdownOpen(false)}>
                          {LABELS.adminConsole}
                        </DropdownLink>
                      ) : null}

                      <button
                        onClick={handleLogout}
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-sm text-gray-400 transition-colors hover:bg-white/5 hover:text-red-400"
                      >
                        <LogOut size={16} />
                        {LABELS.logout}
                      </button>
                    </div>
                  </div>
                ) : null}
              </div>
            ) : (
              <>
                <Link
                  href="/auth/login"
                  className="px-4 py-2 text-sm text-gray-300 transition-colors duration-200 hover:text-white"
                >
                  {LABELS.login}
                </Link>
                <Link
                  href="/auth/signup"
                  className="rounded-lg bg-gradient-to-r from-blue-600 to-blue-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition-all duration-300 hover:from-blue-500 hover:to-blue-400 hover:shadow-blue-500/30"
                >
                  {LABELS.start}
                </Link>
              </>
            )}
          </div>

          <button
            onClick={() => setMobileOpen(!mobileOpen)}
            className="p-2 text-gray-400 transition-colors hover:text-white md:hidden"
            aria-label={LABELS.menuToggle}
          >
            {mobileOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </div>

      {mobileOpen ? (
        <div className="animate-fade-in border-b border-white/5 bg-[#0a0e1a]/95 backdrop-blur-xl md:hidden">
          <div className="space-y-1 px-6 py-4">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileOpen(false)}
                className="block rounded-lg px-4 py-3 text-gray-300 transition-colors duration-200 hover:bg-white/5 hover:text-white"
              >
                {item.label}
              </Link>
            ))}

            <div className="flex flex-col gap-2 border-t border-white/10 pt-4">
              {isLoggedIn && user ? (
                <>
                  <div className="flex items-center gap-3 px-4 py-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 text-xs font-bold text-white">
                      {userInitial}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-white">{user.name}</p>
                      <div className="flex items-center gap-1.5">
                        <p className="text-xs text-gray-500">{roleLabel}</p>
                        {user.role === 'MENTOR' && mentorStatus && mentorStatus !== 'APPROVED' ? (
                          <span className={`inline-block rounded-md px-1.5 py-0.5 text-[10px] font-medium ${MENTOR_STATUS_BADGE[mentorStatus].className}`}>
                            {MENTOR_STATUS_BADGE[mentorStatus].label}
                          </span>
                        ) : null}
                      </div>
                    </div>
                  </div>
                  <MobileLink href="/mypage" onClick={() => setMobileOpen(false)}>{LABELS.mypage}</MobileLink>
                  <MobileLink href="/tests/results" onClick={() => setMobileOpen(false)}>{LABELS.testResults}</MobileLink>
                  <MobileLink href="/matching" onClick={() => setMobileOpen(false)}>{LABELS.matching}</MobileLink>
                  {user.role === 'MENTOR' ? (
                    <>
                      <MobileLink href="/mentor/status" onClick={() => setMobileOpen(false)}>{LABELS.mentorStatus}</MobileLink>
                      <MobileLink href="/lms/assignments" onClick={() => setMobileOpen(false)}>{LABELS.assignments}</MobileLink>
                    </>
                  ) : null}
                  {user.role === 'ADMIN' || user.role === 'SUPER_ADMIN' ? (
                    <MobileLink href="/admin/mentor" onClick={() => setMobileOpen(false)}>{LABELS.adminConsole}</MobileLink>
                  ) : null}
                  <button
                    onClick={handleLogout}
                    className="rounded-lg px-4 py-3 text-center text-red-400 transition-colors hover:bg-white/5 hover:text-red-300"
                  >
                    {LABELS.logout}
                  </button>
                </>
              ) : (
                <>
                  <MobileLink href="/auth/login" onClick={() => setMobileOpen(false)}>{LABELS.login}</MobileLink>
                  <Link
                    href="/auth/signup"
                    onClick={() => setMobileOpen(false)}
                    className="rounded-lg bg-gradient-to-r from-blue-600 to-blue-500 px-4 py-3 text-center font-semibold text-white"
                  >
                    {LABELS.start}
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </header>
  );
}

function DropdownLink({
  href,
  icon,
  onClick,
  children,
}: {
  href: string;
  icon: ReactNode;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <Link
      href={href}
      onClick={onClick}
      className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400 transition-colors hover:bg-white/5 hover:text-white"
    >
      {icon}
      {children}
    </Link>
  );
}

function MobileLink({
  href,
  onClick,
  children,
}: {
  href: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <Link
      href={href}
      onClick={onClick}
      className="rounded-lg px-4 py-3 text-center text-gray-300 transition-colors hover:bg-white/5 hover:text-white"
    >
      {children}
    </Link>
  );
}

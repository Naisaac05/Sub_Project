'use client';

import { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Menu, X, User, LogOut, ChevronDown, FileText, Users, GraduationCap, Briefcase, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';

const navItems = [
  { 
    label: '멘토링 코스', 
    href: '/mentors',
    hasMegamenu: true
  },
  { label: '실력 테스트', href: '/tests' },
  { label: '수강 신청', href: '/apply' },
  { label: 'LMS', href: '/lms/dashboard' },
  { label: '커뮤니티', href: '/community' },
  { label: 'FAQ', href: '/faq' },
];

export default function Header() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
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

  // 사용자 이니셜 (첫 글자)
  const userInitial = user?.name ? user.name[0].toUpperCase() : '?';

  // 역할 라벨
  const roleLabel = user?.role === 'MENTOR' ? '멘토' : user?.role === 'ADMIN' ? '관리자' : '멘티';

  return (
    <header className="fixed top-0 left-0 right-0 z-50 transition-all duration-300">
      <div className="bg-[#0a0e1a]/80 backdrop-blur-xl border-b border-white/5">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center
                          group-hover:shadow-lg group-hover:shadow-blue-500/25 transition-shadow duration-300">
              <span className="text-white font-bold text-sm font-[Outfit]">D</span>
            </div>
            <span className="text-white font-bold text-lg tracking-tight font-[Outfit]">
              Dev<span className="text-cyan-400">Match</span>
            </span>
          </Link>

          {/* Desktop Nav */}
          <nav className="hidden md:flex items-center gap-1 group/nav">
            {navItems.map((item) => (
              <div 
                key={item.label} 
                className={`relative ${item.hasMegamenu ? 'group/mega' : ''}`}
              >
                <Link
                  href={item.href}
                  className="px-4 py-6 text-sm font-semibold text-gray-300 hover:text-white transition-all duration-200 flex items-center gap-1"
                >
                  {item.label}
                  {item.hasMegamenu && <ChevronDown size={14} className="opacity-70 group-hover/mega:rotate-180 transition-transform" />}
                </Link>

                {/* Desktop Mega Menu for Course */}
                {item.hasMegamenu && (
                  <div className="absolute top-[100%] left-[-200px] w-screen max-w-[1280px] bg-[#373c44] border-y border-white/10 shadow-xl opacity-0 invisible group-hover/mega:opacity-100 group-hover/mega:visible transition-all duration-300 cursor-default">
                    <div className="max-w-7xl px-6 py-10 flex gap-20 text-sm">
                      {/* Column 1 */}
                      <div className="space-y-4">
                        <h3 className="font-bold text-white mb-2">취준생/5년차 미만 멘토링</h3>
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
                      {/* Column 2 */}
                      <div className="space-y-4 pt-9">
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/react-native" className="hover:text-white">React Native + AI</Link></li>
                          <li><Link href="/mentors/devops" className="hover:text-white">Devops</Link></li>
                          <li><Link href="/mentors/data-engineer" className="hover:text-white">Data Engineer + AI</Link></li>
                          <li><Link href="/mentors/ml-engineer" className="hover:text-white">ML Engineer</Link></li>
                          <li><Link href="/mentors/game-server" className="hover:text-white">Game Server</Link></li>
                          <li><Link href="/mentors/short-term" className="hover:text-white">단기 취업/이직</Link></li>
                        </ul>
                      </div>
                      {/* Column 3 */}
                      <div className="space-y-4">
                        <h3 className="font-bold text-white mb-2">First Step</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/firststep" className="hover:text-white">Java Backend + AI</Link></li>
                        </ul>
                      </div>
                      {/* Column 4 */}
                      <div className="space-y-4">
                        <h3 className="font-bold text-white mb-2">F5: Deep Dive</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/distributed-lock" className="hover:text-white">분산 락 Deep Dive</Link></li>
                          <li><Link href="/mentors/kafka" className="hover:text-white">Kafka Deep Dive</Link></li>
                        </ul>
                      </div>
                      {/* Column 5 */}
                      <div className="space-y-4">
                        <h3 className="font-bold text-white mb-2">5년차 이상 멘토링</h3>
                        <ul className="space-y-3 font-semibold text-gray-300">
                          <li><Link href="/mentors/expert-msa" className="hover:text-white">Kotlin/MSA 최고급 과정</Link></li>
                        </ul>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </nav>

          {/* Auth - Desktop */}
          <div className="hidden md:flex items-center gap-3">
            {isLoading ? (
              // 초기 로딩 중은 빈 상태
              <div className="w-24 h-9" />
            ) : isLoggedIn && user ? (
              // ── 로그인 상태: 유저 드롭다운 ──
              <div className="relative" ref={dropdownRef}>
                <button
                  onClick={() => setDropdownOpen(!dropdownOpen)}
                  className="flex items-center gap-2.5 px-3 py-1.5 rounded-xl
                           hover:bg-white/5 transition-all duration-200"
                >
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400
                               flex items-center justify-center text-white font-bold text-xs">
                    {userInitial}
                  </div>
                  <span className="text-gray-300 text-sm font-medium max-w-[100px] truncate">
                    {user.name}
                  </span>
                  <ChevronDown
                    size={14}
                    className={`text-gray-500 transition-transform duration-200 ${
                      dropdownOpen ? 'rotate-180' : ''
                    }`}
                  />
                </button>

                {/* 드롭다운 메뉴 */}
                {dropdownOpen && (
                  <div className="absolute right-0 top-full mt-2 w-56 rounded-xl overflow-hidden
                               bg-[#0f1420] border border-white/10 shadow-2xl shadow-black/40
                               animate-fade-in">
                    {/* 유저 정보 */}
                    <div className="px-4 py-3 border-b border-white/5">
                      <p className="text-white text-sm font-semibold">{user.name}</p>
                      <p className="text-gray-500 text-xs mt-0.5">{user.email}</p>
                      <span className="inline-block mt-1.5 px-2 py-0.5 rounded-md text-xs font-medium
                                     bg-blue-500/10 text-blue-400">
                        {roleLabel}
                      </span>
                    </div>
                    {/* 메뉴 항목 */}
                    <div className="py-1">
                      <Link
                        href="/mypage"
                        onClick={() => setDropdownOpen(false)}
                        className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                 hover:text-white hover:bg-white/5 transition-colors"
                      >
                        <User size={16} />
                        마이페이지
                      </Link>
                      <Link
                        href="/tests/results"
                        onClick={() => setDropdownOpen(false)}
                        className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                 hover:text-white hover:bg-white/5 transition-colors"
                      >
                        <FileText size={16} />
                        테스트 결과
                      </Link>
                      <Link
                        href="/matching"
                        onClick={() => setDropdownOpen(false)}
                        className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                 hover:text-white hover:bg-white/5 transition-colors"
                      >
                        <Users size={16} />
                        매칭 내역
                      </Link>
                      {user.role === 'MENTOR' && (
                        <>
                          <Link
                            href="/mentor/status"
                            onClick={() => setDropdownOpen(false)}
                            className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                     hover:text-white hover:bg-white/5 transition-colors"
                          >
                            <Briefcase size={16} />
                            멘토 신청/상태
                          </Link>
                          <Link
                            href="/lms/assignments"
                            onClick={() => setDropdownOpen(false)}
                            className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                     hover:text-white hover:bg-white/5 transition-colors"
                          >
                            <FileText size={16} />
                            배정 목록
                          </Link>
                        </>
                      )}
                      {user.role === 'ADMIN' && (
                        <Link
                          href="/admin/mentor"
                          onClick={() => setDropdownOpen(false)}
                          className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                   hover:text-white hover:bg-white/5 transition-colors"
                        >
                          <ShieldCheck size={16} />
                          관리자 콘솔
                        </Link>
                      )}
                      <button
                        onClick={handleLogout}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
                                 hover:text-red-400 hover:bg-white/5 transition-colors"
                      >
                        <LogOut size={16} />
                        로그아웃
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              // ── 비로그인 상태: 로그인/시작하기 ──
              <>
                <Link
                  href="/auth/login"
                  className="px-4 py-2 text-sm text-gray-300 hover:text-white transition-colors duration-200"
                >
                  로그인
                </Link>
                <Link
                  href="/auth/signup"
                  className="px-5 py-2.5 text-sm font-semibold text-white rounded-lg
                           bg-gradient-to-r from-blue-600 to-blue-500
                           hover:from-blue-500 hover:to-blue-400
                           shadow-lg shadow-blue-600/20 hover:shadow-blue-500/30
                           transition-all duration-300"
                >
                  시작하기
                </Link>
              </>
            )}
          </div>

          {/* Mobile Menu Toggle */}
          <button
            onClick={() => setMobileOpen(!mobileOpen)}
            className="md:hidden p-2 text-gray-400 hover:text-white transition-colors"
            aria-label="메뉴 토글"
          >
            {mobileOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </div>

      {/* Mobile Nav */}
      {mobileOpen && (
        <div className="md:hidden bg-[#0a0e1a]/95 backdrop-blur-xl border-b border-white/5 animate-fade-in">
          <div className="px-6 py-4 space-y-1">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileOpen(false)}
                className="block px-4 py-3 text-gray-300 hover:text-white hover:bg-white/5
                         rounded-lg transition-colors duration-200"
              >
                {item.label}
              </Link>
            ))}
            <div className="pt-4 border-t border-white/10 flex flex-col gap-2">
              {isLoggedIn && user ? (
                <>
                  {/* 모바일 로그인 상태 */}
                  <div className="px-4 py-2 flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400
                                 flex items-center justify-center text-white font-bold text-xs">
                      {userInitial}
                    </div>
                    <div>
                      <p className="text-white text-sm font-semibold">{user.name}</p>
                      <p className="text-gray-500 text-xs">{roleLabel}</p>
                    </div>
                  </div>
                  <Link
                    href="/mypage"
                    onClick={() => setMobileOpen(false)}
                    className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg
                             hover:bg-white/5 transition-colors"
                  >
                    마이페이지
                  </Link>
                  <Link
                    href="/tests/results"
                    onClick={() => setMobileOpen(false)}
                    className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg
                             hover:bg-white/5 transition-colors"
                  >
                    테스트 결과
                  </Link>
                  <Link
                    href="/matching"
                    onClick={() => setMobileOpen(false)}
                    className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg
                             hover:bg-white/5 transition-colors"
                  >
                    매칭 내역
                  </Link>
                  {user.role === 'MENTOR' && (
                    <>
                      <Link
                        href="/mentor/status"
                        onClick={() => setMobileOpen(false)}
                        className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg
                                 hover:bg-white/5 transition-colors"
                      >
                        멘토 신청/상태
                      </Link>
                      <Link
                        href="/lms/assignments"
                        onClick={() => setMobileOpen(false)}
                        className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg
                                 hover:bg-white/5 transition-colors"
                      >
                        배정 목록
                      </Link>
                    </>
                  )}
                  {user.role === 'ADMIN' && (
                    <Link
                      href="/admin/mentor"
                      onClick={() => setMobileOpen(false)}
                      className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg
                               hover:bg-white/5 transition-colors"
                    >
                      관리자 콘솔
                    </Link>
                  )}
                  <button
                    onClick={handleLogout}
                    className="px-4 py-3 text-red-400 hover:text-red-300 text-center rounded-lg
                             hover:bg-white/5 transition-colors"
                  >
                    로그아웃
                  </button>
                </>
              ) : (
                <>
                  <Link
                    href="/auth/login"
                    onClick={() => setMobileOpen(false)}
                    className="px-4 py-3 text-gray-300 hover:text-white text-center rounded-lg"
                  >
                    로그인
                  </Link>
                  <Link
                    href="/auth/signup"
                    onClick={() => setMobileOpen(false)}
                    className="px-4 py-3 text-white text-center font-semibold rounded-lg
                             bg-gradient-to-r from-blue-600 to-blue-500"
                  >
                    시작하기
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
}

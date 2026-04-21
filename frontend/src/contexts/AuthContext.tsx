'use client';

import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import type { UserResponse } from '@/lib/types';
import * as authService from '@/lib/auth';
import { getMyMentorProfile } from '@/lib/mentor';

export type MentorStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

interface AuthContextType {
  user: UserResponse | null;
  mentorStatus: MentorStatus | null; // MENTOR 가 아니거나 아직 신청 전이면 null
  isLoading: boolean;
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (name: string, email: string, password: string, role: 'MENTEE' | 'MENTOR') => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  refreshMentorStatus: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

async function fetchMentorStatus(role: UserResponse['role']): Promise<MentorStatus | null> {
  if (role !== 'MENTOR') return null;
  try {
    const profile = await getMyMentorProfile();
    return profile?.status ?? null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [mentorStatus, setMentorStatus] = useState<MentorStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 앱 최초 로드 시 토큰이 있으면 사용자 정보 복원
  useEffect(() => {
    const initAuth = async () => {
      const token = authService.getAccessToken();
      if (token) {
        try {
          const res = await authService.getMyProfile();
          if (res.success) {
            setUser(res.data);
            setMentorStatus(await fetchMentorStatus(res.data.role));
          }
        } catch {
          // 토큰 만료 등 — 조용히 실패
          authService.clearTokens();
        }
      }
      setIsLoading(false);
    };
    initAuth();
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const tokenRes = await authService.login({ email, password });
    if (tokenRes.success) {
      const profileRes = await authService.getMyProfile();
      if (profileRes.success) {
        setUser(profileRes.data);
        setMentorStatus(await fetchMentorStatus(profileRes.data.role));
      }
    }
  }, []);

  const signup = useCallback(async (name: string, email: string, password: string, role: 'MENTEE' | 'MENTOR') => {
    await authService.signup({ email, password, name, role });
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
    setMentorStatus(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const res = await authService.getMyProfile();
      if (res.success) {
        setUser(res.data);
        setMentorStatus(await fetchMentorStatus(res.data.role));
      }
    } catch {
      // 실패 시 무시
    }
  }, []);

  const refreshMentorStatus = useCallback(async () => {
    if (!user) return;
    setMentorStatus(await fetchMentorStatus(user.role));
  }, [user]);

  return (
    <AuthContext.Provider
      value={{
        user,
        mentorStatus,
        isLoading,
        isLoggedIn: !!user,
        login,
        signup,
        logout,
        refreshUser,
        refreshMentorStatus,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

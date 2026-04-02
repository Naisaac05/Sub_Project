'use client';

import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import type { UserResponse } from '@/lib/types';
import * as authService from '@/lib/auth';

interface AuthContextType {
  user: UserResponse | null;
  isLoading: boolean;
  isLoggedIn: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
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
      }
    }
  }, []);

  const signup = useCallback(async (name: string, email: string, password: string) => {
    await authService.signup({ email, password, name });
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const res = await authService.getMyProfile();
      if (res.success) {
        setUser(res.data);
      }
    } catch {
      // 실패 시 무시
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isLoggedIn: !!user,
        login,
        signup,
        logout,
        refreshUser,
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

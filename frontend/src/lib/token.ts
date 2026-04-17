/**
 * Token Storage Utilities
 * Access token만 localStorage에 저장합니다.
 * Refresh token은 HttpOnly 쿠키(/api/auth, SameSite=Strict)로 서버가 관리합니다.
 */

const ACCESS_TOKEN_KEY = 'accessToken';

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(accessToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

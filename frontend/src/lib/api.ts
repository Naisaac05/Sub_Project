import axios from 'axios';
import { getAccessToken, setAccessToken, clearAccessToken } from './token';
import type { ApiResponse, TokenResponse } from './types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || '/api';
const normalizedApiBaseUrl = API_BASE_URL.replace(/\/$/, '');

/**
 * DevMatch API 클라이언트
 * Spring Boot 백엔드와 /api 프리픽스로 통신하며, refresh token은 HttpOnly 쿠키로
 * 서버가 관리합니다 (클라이언트는 access token만 직접 들고 있음).
 */
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터: JWT 토큰 자동 첨부
apiClient.interceptors.request.use(
  (config) => {
    if (typeof window !== 'undefined') {
      const token = getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 응답 인터셉터: 401 에러 시 토큰 갱신 시도
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
}> = [];

const processQueue = (error: unknown | null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(undefined);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login')
    ) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then(() => {
        originalRequest.headers.Authorization = `Bearer ${getAccessToken()}`;
        return apiClient(originalRequest);
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const res = await axios.post<ApiResponse<TokenResponse>>(
        `${normalizedApiBaseUrl}/auth/refresh`,
        null,
        { withCredentials: true }
      );
      if (res.data.success && res.data.data) {
        setAccessToken(res.data.data.accessToken);
        processQueue(null);
        originalRequest.headers.Authorization = `Bearer ${res.data.data.accessToken}`;
        return apiClient(originalRequest);
      }
      throw new Error('Refresh failed');
    } catch (refreshError) {
      processQueue(refreshError);
      clearAccessToken();
      if (typeof window !== 'undefined') {
        window.location.href = '/auth/login';
      }
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);

export default apiClient;

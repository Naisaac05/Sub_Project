import axios from 'axios';
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token';
import type { ApiResponse, TokenResponse } from './types';

/**
 * DevMatch API 클라이언트
 * Spring Boot 백엔드 (localhost:8080)와 통신합니다.
 * next.config.js의 rewrite로 /api/* → localhost:8080/api/* 프록시됩니다.
 */
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
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

    // 401이 아니거나, 이미 재시도한 요청이거나, refresh/login 요청 자체인 경우 바로 reject
    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login')
    ) {
      return Promise.reject(error);
    }

    // 이미 refresh 진행 중이면 큐에 대기
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
      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        throw new Error('No refresh token');
      }

      const res = await axios.post<ApiResponse<TokenResponse>>('/api/auth/refresh', { refreshToken });
      if (res.data.success && res.data.data) {
        setTokens(res.data.data.accessToken, res.data.data.refreshToken);
        processQueue(null);
        originalRequest.headers.Authorization = `Bearer ${res.data.data.accessToken}`;
        return apiClient(originalRequest);
      }
      throw new Error('Refresh failed');
    } catch (refreshError) {
      processQueue(refreshError);
      clearTokens();
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

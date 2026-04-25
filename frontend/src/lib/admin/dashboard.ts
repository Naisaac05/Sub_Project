import apiClient from '../api';
import type { ApiResponse } from '../types';

// ─── 응답 타입 (backend DTO 와 동일 구조) ───

export interface MetricWithDelta {
  current: number;
  deltaFromLastMonth: number;
  deltaPercent: number | null;
}

export interface MatchingMetric {
  current: number;
  newThisMonth: number;
}

export interface MentorMetric {
  current: number;
  pending: number;
}

export interface DashboardKpi {
  totalActiveUsers: MetricWithDelta;
  currentMonthRevenue: MetricWithDelta;
  totalAcceptedMatchings: MatchingMetric;
  approvedMentors: MentorMetric;
}

export interface SignupTrendPoint {
  date: string; // YYYY-MM-DD
  count: number;
}

export interface RevenueTrendPoint {
  month: string; // YYYY-MM
  grossRevenue: number;
  refundAmount: number;
  netRevenue: number;
}

export interface DashboardQueue {
  pendingMentorCount: number;
  failedPaymentCount: number;
}

export interface DashboardResponse {
  kpi: DashboardKpi;
  signupTrend: SignupTrendPoint[];
  revenueTrend: RevenueTrendPoint[];
  queue: DashboardQueue;
}

export type AuditActionType =
  | 'USER_ROLE_CHANGE' | 'USER_DEACTIVATE' | 'USER_REACTIVATE' | 'USER_DELETE'
  | 'USER_PASSWORD_RESET' | 'USER_MENTOR_SWAP' | 'ADMIN_CREATE'
  | 'PAYMENT_REFUND' | 'POST_DELETE' | 'COMMENT_DELETE'
  | 'MENTOR_APPROVE' | 'MENTOR_REJECT';

export interface AuditLogItem {
  id: number;
  adminName: string;
  actionType: AuditActionType;
  description: string;
  targetHref: string;
  createdAt: string;
}

export interface AuditLogResponse {
  items: AuditLogItem[];
}

// ─── API ───

export async function fetchDashboard(): Promise<DashboardResponse> {
  const res = await apiClient.get<ApiResponse<DashboardResponse>>('/admin/dashboard');
  return res.data.data;
}

export async function fetchAuditLog(): Promise<AuditLogResponse> {
  const res = await apiClient.get<ApiResponse<AuditLogResponse>>('/admin/dashboard/audit-log');
  return res.data.data;
}

import apiClient from './api';
import type { ApiResponse } from './types';

export interface ApplicationRequest {
  menteeId: number;
  currentLevel: string;
  targetTechStack: string;
  careerGoal: string;
  category: string;
  courseType: string;
  desiredMonths: number;
  languages: string[];
  platforms: string[];
  isCsMajor: boolean;
  learningPaths: string[];
  careerYears: string;
  githubUrl: string;
  projectCount: string;
  projectDescription: string;
  weekdayStudyHours: string;
  weekendStudyHours: string;
  goal: string;
  personality: string;
  selfIntroduction: string;
  referralSources: string[];
  referralCode: string;
  termsAgreed: boolean;
}

export async function submitApplication(data: ApplicationRequest): Promise<ApiResponse<any>> {
  const res = await apiClient.post<ApiResponse<any>>('/applications/submit', data);
  return res.data;
}

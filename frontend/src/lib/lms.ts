import apiClient from './api';
import type { ApiResponse } from './types';
import type {
  DashboardResponse, EnrollmentResponse, CurriculumResponse, CurriculumCreateRequest,
  AssignmentResponse, AssignmentCreateRequest, SubmissionRequest, FeedbackRequest,
  NoteResponse, NoteCreateRequest, NoteCommentRequest, ResumeResponse,
  ResumeFeedbackRequest, MockInterviewResponse, MockInterviewCreateRequest,
  CertificateEligibilityResponse,
} from './lms-types';

export const getDashboard = (matchingId: number) =>
  apiClient.get<ApiResponse<DashboardResponse>>(`/lms/dashboard/${matchingId}`);
export const getEnrollment = (matchingId: number) =>
  apiClient.get<ApiResponse<EnrollmentResponse>>(`/lms/enrollment/${matchingId}`);
export const createCurriculum = (data: CurriculumCreateRequest) =>
  apiClient.post<ApiResponse<CurriculumResponse>>('/lms/curriculum', data);
export const getCurriculum = (matchingId: number) =>
  apiClient.get<ApiResponse<CurriculumResponse>>(`/lms/curriculum/${matchingId}`);
export const updateCurriculum = (id: number, data: Partial<CurriculumCreateRequest>) =>
  apiClient.put<ApiResponse<CurriculumResponse>>(`/lms/curriculum/${id}`, data);
export const toggleWeekComplete = (weekId: number) =>
  apiClient.put<ApiResponse<void>>(`/lms/curriculum/weeks/${weekId}/complete`);
export const createAssignment = (data: AssignmentCreateRequest) =>
  apiClient.post<ApiResponse<AssignmentResponse>>('/lms/assignments', data);
export const getAssignments = (matchingId: number, type?: string) =>
  apiClient.get<ApiResponse<AssignmentResponse[]>>('/lms/assignments', { params: { matchingId, type } });
export const getAssignment = (id: number) =>
  apiClient.get<ApiResponse<AssignmentResponse>>(`/lms/assignments/${id}`);
export const submitAssignment = (id: number, data: SubmissionRequest) =>
  apiClient.post<ApiResponse<AssignmentResponse>>(`/lms/assignments/${id}/submit`, data);
export const feedbackAssignment = (id: number, data: FeedbackRequest) =>
  apiClient.post<ApiResponse<AssignmentResponse>>(`/lms/assignments/${id}/feedback`, data);
export const createNote = (data: NoteCreateRequest) =>
  apiClient.post<ApiResponse<NoteResponse>>('/lms/notes', data);
export const getNotes = (matchingId: number, type?: string) =>
  apiClient.get<ApiResponse<NoteResponse[]>>('/lms/notes', { params: { matchingId, type } });
export const getNote = (id: number) =>
  apiClient.get<ApiResponse<NoteResponse>>(`/lms/notes/${id}`);
export const updateNote = (id: number, data: Partial<NoteCreateRequest>) =>
  apiClient.put<ApiResponse<NoteResponse>>(`/lms/notes/${id}`, data);
export const addNoteComment = (noteId: number, data: NoteCommentRequest) =>
  apiClient.post<ApiResponse<NoteResponse>>(`/lms/notes/${noteId}/comments`, data);
export const uploadResume = (matchingId: number, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('matchingId', matchingId.toString());
  return apiClient.post<ApiResponse<ResumeResponse>>('/lms/resumes', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const getResumes = (matchingId: number) =>
  apiClient.get<ApiResponse<ResumeResponse[]>>('/lms/resumes', { params: { matchingId } });
export const feedbackResume = (id: number, data: ResumeFeedbackRequest) =>
  apiClient.post<ApiResponse<ResumeResponse>>(`/lms/resumes/${id}/feedback`, data);
export const createMockInterview = (data: MockInterviewCreateRequest) =>
  apiClient.post<ApiResponse<MockInterviewResponse>>('/lms/mock-interviews', data);
export const getMockInterviews = (matchingId: number) =>
  apiClient.get<ApiResponse<MockInterviewResponse[]>>('/lms/mock-interviews', { params: { matchingId } });
export const checkEligibility = (matchingId: number) =>
  apiClient.get<ApiResponse<CertificateEligibilityResponse>>(`/lms/certificate/eligibility/${matchingId}`);
export const downloadCertificate = (matchingId: number) =>
  apiClient.get(`/lms/certificate/${matchingId}/download`, { responseType: 'blob' });

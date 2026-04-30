import apiClient from './api';
import type { ApiResponse, CourseSummary, MentoringCourseDetail } from './types';

export async function fetchCourses(): Promise<MentoringCourseDetail[]> {
  const res = await apiClient.get<ApiResponse<MentoringCourseDetail[]>>('/courses');
  return res.data.data;
}

export async function fetchAvailableCourses(): Promise<MentoringCourseDetail[]> {
  const res = await apiClient.get<ApiResponse<MentoringCourseDetail[]>>('/courses/available');
  return res.data.data;
}

export async function fetchCourse(courseKey: string): Promise<MentoringCourseDetail> {
  const res = await apiClient.get<ApiResponse<MentoringCourseDetail>>(`/courses/${courseKey}`);
  return res.data.data;
}

export async function fetchCourseSummaries(): Promise<CourseSummary[]> {
  const courses = await fetchCourses();
  return courses.map(c => ({
    courseKey: c.courseKey,
    title: c.title,
    iconString: c.iconString,
  }));
}

export async function fetchAvailableCourseSummaries(): Promise<CourseSummary[]> {
  const courses = await fetchAvailableCourses();
  return courses.map(c => ({
    courseKey: c.courseKey,
    title: c.title,
    iconString: c.iconString,
  }));
}

export async function fetchMentorCount(courseKey: string): Promise<number> {
  const res = await apiClient.get<ApiResponse<{ mentorCount: number }>>(`/courses/${courseKey}/mentor-count`);
  return res.data.data.mentorCount;
}

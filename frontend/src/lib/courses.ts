import apiClient from './api';
import type { ApiResponse, CourseSummary, MentoringCourseDetail } from './types';

export async function fetchCourses(): Promise<MentoringCourseDetail[]> {
  const res = await apiClient.get<ApiResponse<MentoringCourseDetail[]>>('/courses');
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

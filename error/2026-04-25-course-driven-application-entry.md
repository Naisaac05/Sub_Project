# Course-driven application entry showed unavailable courses

- Date: 2026-04-25
- Area: frontend / backend
- Severity: medium

## Symptoms

The main navigation still exposed a standalone application entry, and the application form asked users to choose a mentoring course again. This made it possible to start an application without coming from a course page, while the mentoring course list also showed courses that had no approved mentor.

## Cause

The frontend treated course selection as a form field instead of preserving the selected course from the mentoring course detail route. The course list API returned all active courses, so the "available courses" section could not distinguish courses with approved mentors.

## Fix

- Removed the standalone application navigation item in `frontend/src/components/layout/Header.tsx:9`.
- Changed course detail apply buttons to route to `/apply?course=<course-slug>` in `frontend/src/app/mentors/[id]/page.tsx:227` and `frontend/src/app/mentors/[id]/page.tsx:436`.
- Removed the application form course question and read the selected course from the query string in `frontend/src/app/apply/page.tsx:34` and `frontend/src/app/apply/page.tsx:387`.
- Added `/api/courses/available` and filtered courses by approved mentor assignment in `backend/src/main/java/com/devmatch/controller/CourseController.java:30` and `backend/src/main/java/com/devmatch/service/CourseService.java:30`.
- Updated the mentoring courses page to render the available list from the new API in `frontend/src/app/mentors/page.tsx:91`.
- Updated the preparing-course list to show every course that is not currently available, instead of only static `upcoming` courses, in `frontend/src/app/mentors/page.tsx:101`.
- Split the available-course loading state and added a timeout fallback so the page does not keep an indefinite spinner if one course API request stalls in `frontend/src/app/mentors/page.tsx:85`.

## Prevention / Notes

Course application should stay course-driven: entry point is the mentoring course detail page, and the application page should not let users free-select or overwrite the course key. When adding new course listings, use the available-courses API for any user-facing "can apply" surface.

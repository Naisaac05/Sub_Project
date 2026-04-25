# Course-specific auto matching did not filter mentor courses

## Symptoms

- A mentee application could be auto-matched to any mentor, regardless of the mentoring course selected in the application.
- For example, an application with `category=game-server` could still consider mentors that did not offer the `game-server` course.

## Cause

- `backend/src/main/java/com/devmatch/service/ApplicationService.java` selected candidates with `userRepository.findByRole(Role.MENTOR)`.
- That only checked the user's role and did not verify:
  - the mentor profile was approved
  - the mentor profile included a course whose `courseKey` matched the application `category`

## Fix

- Changed auto-matching to load approved mentor profiles from `MentorProfileRepository`.
- Candidate mentors are now filtered by `profile.getCourses()` where `course.getCourseKey().equals(application.getCategory())`.
- The existing least-active matching selection is kept after course filtering.
- If no approved mentor offers the selected course, the application is marked as `MATCHING_FAILED`.

Related file:

- `backend/src/main/java/com/devmatch/service/ApplicationService.java`

## Prevention / Notes

- Local DB check on 2026-04-25 showed no approved mentor profile with the `game-server` course.
- In that state, a `game-server` application will not auto-match until a mentor profile with that course is created or approved.
- Verification: `./gradlew.bat compileJava --no-daemon` completed successfully.

# Mentor could not review matched mentee application and unavailable courses were still submittable

## Symptoms

- A mentee could select a mentoring course that had no approved mentor available.
- After auto matching, mentors needed to review who the mentee was, but the matching detail view did not reliably show the linked application in a readable application form.

## Cause

- The application page already called the mentor count endpoint, but submission was not blocked when the selected course had zero approved mentors.
- The matching page had an application detail modal path, but the new visible labels were corrupted and the UI was still carrying mentor approval/rejection actions that no longer fit the auto-matching flow.

## Fix

- The application page now blocks submission when the selected course has no approved mentor.
- The application page shows a clear availability message after course selection.
- The mentor matching card now exposes a `View application` action when the matching has an `applicationId`.
- The application detail opens in a full modal with grouped read-only fields so the mentor can review the mentee application.
- Removed the old mentor accept/reject UI from the matching card surface because the current flow is automatic matching.

Related files:

- `frontend/src/app/apply/page.tsx`
- `frontend/src/app/matching/page.tsx`
- `frontend/src/lib/matching.ts`
- `backend/src/main/java/com/devmatch/controller/MatchingController.java`
- `backend/src/main/java/com/devmatch/service/MatchingService.java`

## Prevention / Notes

- Verification: `npm.cmd run build` completed successfully.
- The backend must be restarted for the matching application endpoint and course-specific matching behavior to be available to the running app.
- If a local course still shows zero mentors, seed or approve a mentor profile with that course before testing the full auto-match path.

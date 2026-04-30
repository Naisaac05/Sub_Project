# Admin dashboard audit action switch missed mentor change actions

- Date: 2026-04-25
- Area: backend
- Severity: medium

## Symptoms

`./gradlew compileJava` failed with `the switch expression does not cover all possible input values` in `AdminDashboardService.formatDescription`.

## Cause

`AdminActionType` added `MENTOR_CHANGE_APPROVE` and `MENTOR_CHANGE_REJECT`, but the dashboard audit feed description switch still only handled the older action types.

## Fix

Added `MENTOR_CHANGE_APPROVE` and `MENTOR_CHANGE_REJECT` cases to `backend/src/main/java/com/devmatch/service/AdminDashboardService.java:162`.

## Prevention / Notes

When adding a new `AdminActionType`, update all exhaustive switches that render audit log labels, especially dashboard feed formatting.

# Apply page course availability guide was shown in English and time entry was too free-form

## Symptoms

- The mentee application page showed the course mentor availability guide in English.
- Study time input was either free text or a timetable-style grid, which was not the desired input style.

## Cause

- The course availability messages were added with English copy during the auto-matching flow updates.
- Study time was stored as a string, so the first UI update kept that string field and rendered a timetable-style selector.

## Fix

- Replaced the course availability guide and alert copy with Korean text.
- Replaced the timetable-style selector with day-based `select` inputs.
- Time options are now split into 1-hour blocks and still serialize into the existing string fields:
  - `weekdayStudyHours`
  - `weekendStudyHours`

Related file:

- `frontend/src/app/apply/page.tsx`

## Prevention / Notes

- Verification: `npm.cmd run build` completed successfully.
- Backend changes were not required because the existing API already accepts study time as strings.

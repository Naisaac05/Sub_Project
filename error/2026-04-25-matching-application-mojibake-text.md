# Matching application detail text rendered as mojibake

- Date: 2026-04-25
- Area: frontend
- Severity: low

## Symptoms

After adding the matched mentee application detail UI, parts of the matching page showed broken text such as mojibake instead of readable labels. The page stylesheet was still served correctly, so the issue looked like text-only or malformed text in the frontend rather than a missing CSS file.

## Cause

Some Korean UI strings added to `frontend/src/app/matching/page.tsx` were saved as mojibake. The malformed strings were inside the new application detail modal, the `View application` button area, and the application loading error messages.

## Fix

Replaced the corrupted labels in the new matching application detail UI with safe ASCII labels so the modal renders readable text and keeps the same layout.

- `frontend/src/app/matching/page.tsx:114` - replaced corrupted modal labels with readable application detail labels
- `frontend/src/app/matching/page.tsx:414` - replaced corrupted card button label with `View application`
- `frontend/src/app/matching/page.tsx:584` - replaced corrupted application load error messages

Verification:

```text
cd frontend
npm.cmd run build
Compiled successfully
```

## Prevention / Notes

When adding Korean copy in this repository, verify the saved bytes with a UTF-8 read before building. If the terminal or patch path corrupts Korean text, use ASCII labels or escaped strings until the editor encoding is confirmed.

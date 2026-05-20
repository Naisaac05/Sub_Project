# Backend bootRun failed because port 8080 was already in use

- Date: 2026-05-16
- Area: backend
- Severity: low

## Symptoms

Running `.\gradlew.bat bootRun` failed with:

```text
Web server failed to start. Port 8080 was already in use.
```

Gradle reported `:bootRun FAILED` because the Spring Boot process exited with code 1.

## Cause

Another Java/Spring Boot process was already listening on port 8080 when `bootRun` was started. After the failed run, port 8080 was free again, which indicates the conflicting process had been stopped or exited by the time we rechecked.

During verification, a test `bootRun` process was started and reached the 8080 listening state. Its child Java process remained after the Gradle wrapper was stopped, so it was explicitly terminated.

## Fix

- Checked port 8080 listener state before rerunning `bootRun`.
- Verified `bootRun` could start and bind to port 8080 after the port was free.
- Stopped the verification Java process and confirmed no listener remained on port 8080.

## Prevention / Notes

Before restarting the backend, stop the old Spring Boot terminal with `Ctrl+C`. If `bootRun` reports port 8080 in use again, identify the listener and stop it:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
Get-Process -Id <PID>
Stop-Process -Id <PID>
```

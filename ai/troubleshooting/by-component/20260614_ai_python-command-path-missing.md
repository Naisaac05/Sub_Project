---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI Python 명령 PATH 누락 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI Python 명령 PATH 누락

- 발생 일시: 2026-06-14
- 영역: AI / test environment
- 심각도: low

## 증상

PowerShell에서 `python -m unittest`를 실행하면 `python` 명령을 찾을 수 없어 테스트가 시작되지 않았다.

## 원인

현재 셸 PATH에 Python 실행 파일이 등록되지 않았지만, 프로젝트 전용 실행 파일은 `ai/.venv/Scripts/python.exe`에 존재했다.

## 해결 방법

AI 테스트와 preparation 스크립트를 `ai/.venv/Scripts/python.exe`로 실행했다.

## 재발 방지·메모

AI 관련 명령은 저장소 루트의 일반 `python` 대신 `ai/.venv/Scripts/python.exe`를 우선 사용한다.

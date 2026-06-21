---
type: report
category: ollama
status: active
updated: 2026-06-18
description: "로컬 AI를 사용하는 방법입니다"

---

# Ollama Local AI Run Guide

DevMatch 스마트 복습에서 `qwen3:4b-q4_K_M` 로컬 AI를 사용하는 방법입니다.

## 1. Ollama 설치

Windows에서 Ollama를 설치합니다.

- 다운로드: https://ollama.com/download

설치가 끝나면 VSCode를 완전히 종료했다가 다시 실행합니다.

## 2. 설치 확인

VSCode PowerShell 또는 Windows PowerShell에서 확인합니다.

```powershell
ollama --version
```

버전이 나오면 설치가 정상입니다.

## 3. VSCode PowerShell에서 ollama 명령어가 안 잡힐 때

설치했는데 아래처럼 나오면 PATH가 아직 갱신되지 않은 상태입니다.

```text
ollama : 'ollama' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다.
```

먼저 VSCode를 완전히 껐다가 다시 켭니다.

그래도 안 되면 Windows PowerShell을 새로 열고 확인합니다.

```powershell
ollama --version
```

Windows PowerShell에서는 되는데 VSCode에서만 안 되면 VSCode PATH 갱신 문제입니다. VSCode 재실행 또는 PC 재부팅으로 해결되는 경우가 많습니다.

## 4. 설치 경로 직접 확인

아래 명령어로 Ollama 실행 파일이 있는지 확인합니다.

```powershell
Test-Path "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe"
```

`True`가 나오면 직접 실행할 수 있습니다.

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" --version
```

## 5. VSCode PowerShell에서 임시 PATH 등록

VSCode 터미널에서 바로 쓰고 싶으면 현재 터미널에만 PATH를 임시로 추가합니다.

```powershell
$env:Path += ";$env:LOCALAPPDATA\Programs\Ollama"
ollama --version
```

이 방식은 현재 터미널에만 적용됩니다. 새 터미널을 열면 다시 설정해야 할 수 있습니다.

## 6. Qwen 모델 다운로드

Ollama 명령어가 인식되면 모델을 다운로드합니다.

```powershell
ollama pull qwen3:4b-q4_K_M
```

만약 `ollama` 명령어가 아직 안 잡히면 직접 경로로 실행합니다.

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" pull qwen3:4b-q4_K_M
```

## 7. 모델 실행 테스트

모델이 정상 동작하는지 확인합니다.

```powershell
ollama run qwen3:4b-q4_K_M
```

프롬프트가 뜨면 테스트로 입력합니다.

```text
Next.js use client가 뭐야?
```

답변이 나오면 모델 준비가 끝난 것입니다.

종료는 `/bye`를 입력하거나 `Ctrl + C`를 누릅니다.

## 8. Ollama 서버 실행

보통 Ollama는 설치 후 백그라운드에서 자동 실행됩니다.

직접 서버를 켜야 한다면 별도 터미널에서 실행합니다.

```powershell
ollama serve
```

이미 실행 중이면 포트 사용 중이라는 메시지가 나올 수 있습니다. 그 경우 이미 켜져 있는 것이므로 괜찮습니다.

기본 주소는 아래입니다.

```text
http://localhost:11434
```

## 9. Python AI 서버 실행

이제 Java 백엔드가 Ollama를 직접 호출하지 않고, `ai/` 폴더의 Python 서버를 통해 호출합니다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai

python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt

uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

정상 확인:

```text
http://localhost:8001/health
```

## 10. 백엔드에서 Python AI 모드 켜기

백엔드 실행 터미널에서 환경변수를 설정합니다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\backend

$env:AI_REVIEW_PROVIDER="PYTHON"
$env:PYTHON_AI_ENABLED="true"
$env:PYTHON_AI_BASE_URL="http://localhost:8001"
```

그다음 백엔드를 실행합니다.

```powershell
.\gradlew.bat bootRun
```

## 11. CMD에서 실행할 때

CMD에서는 PowerShell의 `$env:` 문법을 사용할 수 없습니다.

CMD를 쓰는 경우:

```cmd
cd C:\Users\User\Desktop\Sub_Project\backend

set AI_REVIEW_PROVIDER=PYTHON
set PYTHON_AI_ENABLED=true
set PYTHON_AI_BASE_URL=http://localhost:8001

gradlew.bat bootRun
```

## 12. 프론트에서 확인

1. 프론트와 백엔드를 실행합니다.
2. 로그인합니다.
3. 실력 테스트를 풉니다.
4. 틀린 문제가 있는 결과에서 `스마트 복습`을 누릅니다.
5. 복습 화면에서 아래 버튼을 사용합니다.

```text
확인 질문에 답하기
궁금한 점 질문하기
다음 문제로
```

Ollama가 정상 연결되어 있으면 자유 질문에 대해 더 자연스러운 답변이 나옵니다.

## 13. 동작 방식

현재 DevMatch 스마트 복습은 아래 순서로 동작합니다.

```text
AI_REVIEW_PROVIDER=PYTHON
-> Spring Boot backend
-> Python FastAPI AI server
-> Ollama qwen3:4b-q4_K_M 호출
-> 실패하면 규칙 기반 답변으로 fallback
```

즉 Ollama가 꺼져 있어도 앱은 터지지 않고 기존 규칙 기반 복습으로 동작합니다.

## 14. 자주 나는 문제

### ollama 명령어를 못 찾음

원인:

- VSCode가 설치 전 PATH를 계속 들고 있음
- Ollama 설치 후 터미널을 새로 열지 않음
- PATH 등록이 아직 안 됨

해결:

```powershell
$env:Path += ";$env:LOCALAPPDATA\Programs\Ollama"
ollama --version
```

또는:

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" --version
```

### 모델이 없다고 나옴

모델을 다시 받습니다.

```powershell
ollama pull qwen3:4b-q4_K_M
```

### AI 답변이 안 나오고 규칙 기반처럼 보임

확인할 것:

```powershell
ollama --version
ollama run qwen3:4b-q4_K_M
```

Python AI 서버가 켜져 있는지도 확인합니다.

```text
http://localhost:8001/health
```

백엔드 실행 터미널에 환경변수가 들어갔는지도 확인합니다.

```powershell
echo $env:AI_REVIEW_PROVIDER
echo $env:PYTHON_AI_BASE_URL
```

정상값:

```text
PYTHON
http://localhost:8001
```

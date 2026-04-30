# DevMatch AI Service

DevMatch 스마트 복습용 Python AI 서버입니다.

Java Spring Boot 백엔드는 이 서버로 요청을 보내고, Python 서버는 로컬 Ollama `qwen2.5:1.5b` 모델을 호출합니다.

## 실행 순서

### 1. Ollama 모델 준비

```powershell
ollama pull qwen2.5:1.5b
```

Ollama가 켜져 있는지 확인합니다.

```powershell
ollama run qwen2.5:1.5b
```

### 2. Python 가상환경 생성

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai

python -m venv .venv
.\.venv\Scripts\activate
```

### 3. 패키지 설치

```powershell
pip install -r requirements.txt
```

### 4. AI 서버 실행

```powershell
uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

정상 확인:

```text
http://localhost:8001/health
```

### 5. 백엔드 실행

백엔드 실행 터미널에서:

```powershell
cd C:\Users\User\Desktop\Sub_Project\backend

$env:AI_REVIEW_PROVIDER="PYTHON"
$env:PYTHON_AI_ENABLED="true"
$env:PYTHON_AI_BASE_URL="http://localhost:8001"

.\gradlew.bat bootRun
```

CMD에서는:

```cmd
set AI_REVIEW_PROVIDER=PYTHON
set PYTHON_AI_ENABLED=true
set PYTHON_AI_BASE_URL=http://localhost:8001
gradlew.bat bootRun
```

## 구조

```text
frontend
  -> backend Spring Boot
    -> ai Python FastAPI
      -> Ollama qwen2.5:1.5b
```

Ollama 또는 Python AI 서버가 실패하면 백엔드는 기존 규칙 기반 복습으로 fallback합니다.


# DevMatch AI Service

DevMatch 스마트 복습용 Python AI 서버입니다.

Java Spring Boot 백엔드는 이 서버로 요청을 보내고, Python 서버는 로컬 Ollama `qwen3:4b-q4_K_M` 모델을 호출합니다.

## 실행 순서

### 1. Ollama 모델 준비

```powershell
ollama pull qwen3:4b-q4_K_M
```

Ollama가 켜져 있는지 확인합니다.

```powershell
ollama run qwen3:4b-q4_K_M
```

### 2. Python 가상환경 생성

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai

python -m venv .venv
.\.venv\Scripts\activate
```

Anaconda를 쓰고 있고 RAG 선택 의존성까지 설치할 계획이면 Python 3.11 환경을 권장합니다. Python 3.13에서는 `chromadb` 하위 의존성인 `chroma-hnswlib`가 C++ 빌드를 요구할 수 있습니다.

```powershell
conda create -n devmatch-ai python=3.11 -y
conda activate devmatch-ai
cd C:\Users\User\Desktop\Sub_Project\ai
python -m pip install --upgrade pip
```

FastAPI 서버만 실행할 때는 기존 `(base)` 환경을 써도 됩니다.

### 3. 패키지 설치

```powershell
pip install -r requirements.txt
```

`requirements.txt`는 FastAPI 서버 실행에 필요한 최소 의존성만 설치합니다. Chroma/LangChain 기반 RAG 의존성은 선택 설치입니다.

```powershell
pip install -r requirements-rag.txt
```

Windows에서 `requirements-rag.txt` 설치 중 `Microsoft Visual C++ 14.0 or greater is required` 오류가 나면 Microsoft C++ Build Tools를 설치하거나 Python 3.11/3.12 환경에서 다시 시도합니다. 현재 Phase 1 fallback retriever는 `requirements-rag.txt` 없이도 동작합니다.

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
      -> Ollama qwen3:4b-q4_K_M
```

Ollama 또는 Python AI 서버가 실패하면 백엔드는 기존 규칙 기반 복습으로 fallback합니다.


# Sub_Project

## 실행 명령어 요약

각 서비스는 별도 터미널에서 실행합니다. 로컬 AI 답변 기능을 쓰려면 `Ollama -> AI 서버 -> 백엔드 -> 프론트엔드` 순서로 켜는 것을 권장합니다.

### 1. Ollama

처음 한 번만 모델을 받습니다.

```powershell
ollama pull qwen3:4b-q4_K_M
```

Ollama 서버가 꺼져 있으면 실행합니다.

```powershell
ollama serve
```

이미 Ollama가 백그라운드에서 실행 중이면 생략해도 됩니다.

### 2. AI 서버

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

가상환경이 없거나 패키지를 다시 설치해야 할 때:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

FastAPI 서버만 실행할 때는 기존 `(base)` 환경을 써도 됩니다. RAG 선택 의존성까지 설치할 경우에는 Python 3.11 가상환경을 권장합니다.

Anaconda 사용 시:

```powershell
conda create -n devmatch-ai python=3.11 -y
conda activate devmatch-ai
cd C:\Users\User\Desktop\Sub_Project\ai
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Chroma/LangChain 기반 RAG 의존성은 선택 설치입니다. Windows에서 `chroma-hnswlib` 빌드 오류가 나면 Microsoft C++ Build Tools가 필요하거나 Python 3.11/3.12 환경을 사용해야 합니다.

```powershell
pip install -r requirements-rag.txt
```

상태 확인:

```text
http://localhost:8001/health
```

### 3. 백엔드

```powershell
cd C:\Users\User\Desktop\Sub_Project\backend
.\gradlew.bat bootRun
```

백엔드는 기본적으로 `http://localhost:8080`에서 실행됩니다. AI 관련 값은 루트 `.env`와 `backend/src/main/resources/application.yml`의 `app.ai-review` 설정을 사용합니다.

### 4. 프론트엔드

```powershell
cd C:\Users\User\Desktop\Sub_Project\frontend
npm.cmd install
npm.cmd run dev
```

프론트엔드는 기본적으로 `http://localhost:3000`에서 실행됩니다.

로컬 개발에서는 AI 복습처럼 오래 걸릴 수 있는 요청이 Next.js rewrite proxy를 타지 않도록 `frontend/.env.local`에 백엔드 주소를 직접 지정합니다.

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

이 값을 바꾼 뒤에는 프론트엔드 dev 서버를 재시작해야 합니다.

## AI 응답 속도 관련 설정

루트 `.env`에서 로컬 AI 속도 관련 값을 조정합니다.

```env
AI_REVIEW_PROVIDER=OLLAMA
OLLAMA_MODEL=qwen3:4b-q4_K_M
OLLAMA_MAX_TOKENS=80
OLLAMA_NUM_CTX=256
OLLAMA_READ_TIMEOUT_SECONDS=0
OLLAMA_REQUEST_TIMEOUT_SECONDS=0
OLLAMA_KEEP_ALIVE=30m
OLLAMA_WARMUP_ENABLED=true
```

10초대를 넘기는 경우 프론트엔드는 즉시 치명적인 오류로 막지 않고, 세션을 다시 조회해 늦게 저장된 AI 답변을 대화에 반영합니다.

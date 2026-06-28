# DevMatch AI 서버 (FastAPI + RAG + Ollama 클라이언트)
FROM python:3.11-slim
WORKDIR /app

# 빌드 도구(일부 RAG 의존성 컴파일에 필요)
RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements-rag.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-rag.txt

COPY . .

# Chroma 인덱스를 이미지에 baking (실패해도 빌드는 진행 — 볼륨/런타임 재생성 가능)
RUN python scripts/reindex_knowledge.py || true

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]

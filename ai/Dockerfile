# DevMatch AI 서버 (FastAPI + RAG + Ollama 클라이언트)
FROM python:3.11-slim
WORKDIR /app

# 빌드 도구(arm64에서 kiwipiepy 등 소스 컴파일에 필요: g++, cmake)
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements-rag.txt ./
# arm64에는 kiwipiepy 휠이 없어 소스 빌드됨. 그 setup.py 가 import numpy 하므로
# numpy를 먼저 깔고, kiwipiepy는 빌드격리 없이(메인 env의 numpy 사용) 빌드한 뒤
# 나머지 의존성을 설치한다(이미 깔린 kiwipiepy는 재빌드되지 않음).
RUN pip install --no-cache-dir --upgrade pip setuptools wheel \
 && pip install --no-cache-dir numpy \
 && pip install --no-cache-dir --no-build-isolation kiwipiepy==0.20.4 \
 && pip install --no-cache-dir -r requirements.txt -r requirements-rag.txt

COPY . .
# Chroma 인덱스는 app/vectorstore/ 에 사전 빌드되어 git 추적됨 → 위 COPY 로 이미지에 포함된다.
# (재생성 스크립트 scripts/ 는 ai/.dockerignore 로 제외되므로 런타임/빌드에 의존하지 않음)
# 참고: 쿼리 임베딩(bge-m3)은 첫 호출 시 런타임 다운로드됨(EC2 네트워크 필요).

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]

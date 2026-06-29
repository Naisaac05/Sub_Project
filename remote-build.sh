#!/bin/bash
# EC2(ARM)에서 실행되는 빌드·기동 스크립트. deploy.ps1 이 stdin 으로 흘려보낸다.
# 비밀값 없음(.env.prod 는 별도 전송). buildx 구버전 회피 위해 클래식 빌더 사용.
set -e
cd ~/devmatch

echo "== build (DOCKER_BUILDKIT=0: AL2023 buildx 구버전 회피) =="
export DOCKER_BUILDKIT=0
docker build -t devmatch-backend ./backend
docker build -t devmatch-frontend --build-arg BACKEND_URL=http://backend:8080 ./frontend
docker build -t devmatch-ai ./ai

echo "== up -d =="
docker compose -f docker-compose.prod.yml up -d

echo "== status =="
docker compose -f docker-compose.prod.yml ps --format 'table {{.Service}}\t{{.State}}\t{{.Status}}'

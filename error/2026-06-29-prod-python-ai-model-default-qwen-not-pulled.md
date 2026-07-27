# 운영(prod)에서 백엔드가 서버에 없는 qwen3:1.7b 모델로 AI 생성을 요청해 RAG 답변 품질/지연 저하

- 발생 일시: 2026-06-29
- 영역: infra / docker (배포 환경변수)
- 심각도: high

## 증상

AWS 배포 후, AI 복습(꼬리질문/자유질문) 부분에서 "ollama의 qwen이 RAG를 활용해 잘 동작하지 않는다"는 증상. RAG를 쓰는 답변이 느리거나 fallback/template으로 떨어지거나 품질이 낮음.

## 원인

모델 이름이 세 계층에서 어긋나 있었음.

- 백엔드 provider 기본값은 `PYTHON`이고([application.yml:80](backend/src/main/resources/application.yml:80)), `.env.prod`에 `AI_REVIEW_PROVIDER`/`PYTHON_AI_MODEL`이 없어서 백엔드는 Python AI에 `python.model` 기본값 **`qwen3:1.7b`** 를 실어 보냄([application.yml:90](backend/src/main/resources/application.yml:90), [PythonAiReviewClient.java:94](backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java:94)).
- 그런데 운영 배포 스크립트 `deploy.ps1`은 **`exaone3.5:2.4b` + `bge-m3`만** pull함([deploy.ps1:58-59](deploy.ps1:58)). 즉 `qwen3:1.7b`(및 직접경로 기본 `qwen3:4b-q4_K_M`)는 서버에 존재하지 않음.
- Python 서비스의 자체 기본/Fallback 모델은 이미 `exaone3.5:2.4b`임([ollama/client.py:19-20](ai/app/ollama/client.py:19)). 따라서 RAG를 쓰는 free-question은 서버에 없는 `qwen3:1.7b` 호출 → 실패 후 exaone fallback(지연·타임아웃 시 template) 또는 누군가 수동으로 받은 작은 qwen이 낮은 품질로 응답.
- 꼬리질문(follow-up)은 설계상 RAG를 끄고([nodes.py:60-63](ai/app/workflow/nodes.py:60)) `FALLBACK_MODEL`(=exaone) 직접 사용([nodes.py:929-932](ai/app/workflow/nodes.py:929))이라 정상. 사용자도 "follow-up은 exaone이 맞다"고 확정. 문제는 **백엔드 기본값 qwen ↔ 실제 pull된 exaone 불일치**.
- 보조 원인: 배포 가이드가 잘못된 모델을 받게 안내([deploy-guide.md Task 4.3]에서 `qwen3:4b-q4_K_M` pull) → 불일치를 키움.

## 해결 방법

운영 모델을 `deploy.ps1`이 pull하는 `exaone3.5:2.4b`로 일원화.

- `.env.prod`에 `PYTHON_AI_MODEL=exaone3.5:2.4b`, `OLLAMA_MODEL=exaone3.5:2.4b` 추가([.env.prod:12-15](.env.prod:12)). `deploy.ps1:52`가 이 파일을 서버로 scp하므로 재배포 시 반영됨.
- 템플릿 동기화: [.env.prod.example](.env.prod.example)에 동일 항목 + 주석("pull 모델과 반드시 일치") 추가.
- 잘못된 배포 안내 수정: [docs/deploy-guide.md Task 4.3](docs/deploy-guide.md)을 `exaone3.5:2.4b` + `bge-m3` pull 및 "PYTHON_AI_MODEL과 일치" 경고로 교체.

적용 후 경로: 백엔드 → Python `request.model = exaone3.5:2.4b`(pull됨) → free-question/first-question/follow-up 모두 exaone 단일 모델로 동작, 실패-후-fallback 왕복 제거.

## 재발 방지 / 메모

- **불변식**: `.env.prod`의 `PYTHON_AI_MODEL`/`OLLAMA_MODEL` == `deploy.ps1`이 pull하는 모델. 한쪽만 바꾸면 동일 증상 재발.
- 미검증 잔여: 본 수정은 **재배포(`deploy.ps1`) + AI 컨테이너 재기동** 후에야 서버에 반영됨. 반영 확인 필요:
  - `docker compose -f docker-compose.prod.yml exec -T ollama ollama list` → `exaone3.5:2.4b`, `bge-m3` 존재 확인
  - AI 로그에서 `model_used=exaone3.5:2.4b`, `fallback_used=false` 확인([deploy-runbook.md:221-231](docs/deploy-runbook.md:221))
- 만약 서버에 이미 `qwen3:1.7b`를 수동으로 pull해둔 상태였다면 호출 자체는 성공하나 1.7B 소형 모델의 한국어/RAG 품질이 낮음 — 이 경우에도 동일 수정(exaone 고정)으로 해결.

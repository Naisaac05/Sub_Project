---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AI 시스템 보호를 위한 보안 가드레일 정책 및 아키텍처 명세서"

---

# AI 리뷰 보안 가드레일 설계

## 목표

Spring Boot에서 FastAPI로 전달되는 AI 리뷰 호출에 첫 번째 보안·가드레일 계층을 추가합니다. 이 계층은 서비스 토큰 인증, 프롬프트 인젝션 무력화, 입력 길이 정책, 확대된 개인정보(PII) 마스킹으로 구성됩니다.

## 아키텍처

FastAPI는 `AI_REVIEW_SERVICE_TOKEN`이 설정된 경우에만 `X-AI-Service-Token`을 검증합니다. Spring Boot는 `app.ai-review.python.service-token`에 설정된 값을 동일한 헤더로 전송합니다. 따라서 로컬 개발 편의성을 유지하면서 프로덕션 환경의 보안을 강화할 수 있습니다.

입력 텍스트는 프롬프트, 검색 처리, 로그에 전달되기 전 Python 요청 정규화 단계에서 정제됩니다. 정제 과정은 개인정보를 마스킹하고, 일반적인 프롬프트 인젝션 문구를 무력화하며, 긴 필드를 `AI_REVIEW_MAX_INPUT_LENGTH`(기본값 700)까지 잘라냅니다.

## 범위

이 단계에서는 AI 리뷰 시스템의 경계와 프롬프트 입력을 보호합니다. 사용자에게 노출되는 콘텐츠 조정 결정이나 완전한 DLP 엔진은 추가하지 않습니다.

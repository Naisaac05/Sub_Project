"""
Ollama API 디버그용: 최소 요청 → 옵션 추가 단계별 테스트.
어느 단계에서 400 에러가 나는지 격리합니다.

사용법:
    python ai/experiments/prompt_v2_comparison/debug_ollama.py
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request


OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("COMPARE_MODEL", "qwen3:4b-q4_K_M")


def call(body: dict, label: str) -> bool:
    print(f"\n[{label}] 요청 본문:")
    print(json.dumps(body, ensure_ascii=False, indent=2))
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
        ans = str(payload.get("response", "")).strip()
        print(f"  ✅ 성공. 응답 일부: {ans[:80]}...")
        return True
    except urllib.error.HTTPError as exc:
        body_text = ""
        try:
            body_text = exc.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        print(f"  ❌ HTTP {exc.code} {exc.reason}")
        print(f"  응답 본문: {body_text}")
        return False
    except Exception as exc:
        print(f"  ❌ {type(exc).__name__}: {exc}")
        return False


def main() -> int:
    print(f"=== Ollama 디버그 (model: {MODEL}, base: {OLLAMA_BASE_URL}) ===")

    # 1단계: 가장 최소 요청
    minimal = {"model": MODEL, "prompt": "안녕", "stream": False}
    if not call(minimal, "1. 최소 요청"):
        print("\n💡 최소 요청도 실패 → 모델명/URL 확인 필요")
        return 1

    # 2단계: options 추가
    with_options = {
        "model": MODEL,
        "prompt": "안녕",
        "stream": False,
        "options": {"temperature": 0.4, "num_predict": 50, "num_ctx": 2048, "num_thread": 4},
    }
    if not call(with_options, "2. options 추가"):
        print("\n💡 options 추가 시 실패 → options 키 중 하나가 문제")
        return 1

    # 3단계: think 추가
    with_think = {**with_options, "think": False}
    if not call(with_think, "3. think:False 추가"):
        print("\n💡 think 필드가 문제 → Ollama 버전이 think 미지원일 수 있음")
        return 1

    # 4단계: keep_alive 추가
    with_keep_alive = {**with_think, "keep_alive": -1}
    if not call(with_keep_alive, "4. keep_alive 추가"):
        print("\n💡 keep_alive 값이 문제")
        return 1

    # 5단계: 추가 옵션
    full = {
        **with_keep_alive,
        "options": {
            **with_keep_alive["options"],
            "repeat_penalty": 1.1,
            "top_p": 0.9,
            "top_k": 40,
        },
    }
    if not call(full, "5. 전체 옵션"):
        print("\n💡 추가 옵션 중 하나가 문제 (repeat_penalty/top_p/top_k)")
        return 1

    print("\n✅ 모든 단계 성공. compare.py 본 실행이 가능합니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

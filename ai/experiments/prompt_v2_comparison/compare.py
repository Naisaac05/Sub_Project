"""
v1 vs v2 프롬프트 A/B 비교 실행 스크립트 (RAG 포함 버전).

운영 흐름(ai/app/workflow/nodes.py)과 동일한 단계로 진행합니다:
1. 샘플 입력으로 RAG 쿼리 구성 (운영의 _full_context_query 와 동일)
2. retrieve_context 로 개념 카드 검색 (limit=3)
3. score >= 5.0 인 카드만 통과 (운영의 MIN_WORKFLOW_CONTEXT_SCORE)
4. _context_text 와 동일한 방식으로 컨텍스트 결합
5. build_prompt 에 컨텍스트 주입 → Ollama 호출

사용법:
    python ai/experiments/prompt_v2_comparison/compare.py

전제:
    - Ollama 서버가 http://localhost:11434 에서 실행 중
    - 사용할 모델이 미리 pull 되어 있음 (기본: qwen3:1.7b)

환경 변수:
    OLLAMA_BASE_URL    (기본: http://localhost:11434)
    COMPARE_MODEL      (기본: qwen3:1.7b)
    COMPARE_TIMEOUT    (기본: 60초)
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
SAMPLES_PATH = SCRIPT_DIR / "samples.jsonl"
RESULTS_BASE = SCRIPT_DIR / "results"
V2_PROMPT_PATH = (
    SCRIPT_DIR.parent.parent / "app" / "knowledge" / "prompts" / "follow_up_v2.prompt"
)


def _resolve_results_dir(model: str, match_production: bool) -> Path:
    """모델/모드별 자동 폴더 분리. results/{model}_{mode}/ 형태.

    덮어쓰기 방지 + 여러 실험 결과 동시 보존을 위해 자동 명명.
    환경변수 RESULTS_DIR 로 명시 지정도 가능.
    """
    override = os.getenv("RESULTS_DIR")
    if override:
        return Path(override)
    safe_model = model.replace(":", "_").replace("/", "_")
    mode_tag = "no_rag" if match_production else "with_rag"
    return RESULTS_BASE / f"{safe_model}__{mode_tag}"

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("COMPARE_MODEL", "qwen3:1.7b")
TIMEOUT = int(os.getenv("COMPARE_TIMEOUT", "60"))

# 운영의 MIN_WORKFLOW_CONTEXT_SCORE 와 동일하게 유지.
MIN_CONTEXT_SCORE = 5.0
RAG_LIMIT = 3

# 2026-05-27 운영 변경 반영: follow-up 모드는 RAG 스킵 (ai/app/workflow/nodes.py:30-33).
# 이 플래그가 true 면 retrieve_rag 가 빈 컨텍스트를 반환해 새 운영 동작과 일치시킨다.
MATCH_PRODUCTION_FOLLOWUP = os.getenv("MATCH_PRODUCTION_FOLLOWUP", "false").lower() in {"1", "true", "yes"}

# v1 프롬프트는 운영 코드(ai/app/prompts.py)에서 직접 가져옵니다.
# 이렇게 하면 v1이 항상 최신 운영 버전과 동기화됩니다.
APP_ROOT = SCRIPT_DIR.parent.parent
sys.path.insert(0, str(APP_ROOT))


def build_rag_query(sample: dict) -> str:
    """운영의 _full_context_query (nodes.py:233) 와 동일한 쿼리 빌딩."""
    parts = (
        sample.get("question", ""),
        sample.get("correct_answer", ""),
        sample.get("selected_answer", ""),
        sample.get("user_answer", ""),
        sample.get("evaluation", ""),
    )
    return " ".join(part for part in parts if part)


def retrieve_rag(sample: dict) -> tuple[str, list[dict]]:
    """운영과 동일하게 RAG 검색 → 점수 필터 → 텍스트 결합.

    Returns:
        (context_text, contexts_info)
        - context_text: build_prompt 에 넘길 문자열
        - contexts_info: 결과 표시용 (concept_id, title, score)
    """
    # 운영 정합 모드: follow-up 은 RAG 스킵 (운영 nodes.py:30-33 동일).
    if MATCH_PRODUCTION_FOLLOWUP:
        return "", []

    from app.rag.retriever import retrieve_context

    query = build_rag_query(sample)
    raw_contexts = retrieve_context(query, limit=RAG_LIMIT)
    filtered = [c for c in raw_contexts if c.score >= MIN_CONTEXT_SCORE]

    # 운영의 _context_text (nodes.py:379) 와 동일.
    context_text = "\n\n".join(c.content for c in filtered)
    contexts_info = [
        {
            "concept_id": c.concept_id,
            "title": c.title,
            "score": round(c.score, 2),
            "passed_filter": c.score >= MIN_CONTEXT_SCORE,
        }
        for c in raw_contexts
    ]
    return context_text, contexts_info


def build_v1_prompt(sample: dict, context_text: str) -> str:
    """기존 코드(app/prompts.py)의 build_prompt 를 그대로 사용. 운영의 컨텍스트 주입 동일."""
    from app.prompts import build_prompt
    from app.schemas import AiGenerateRequest

    request = AiGenerateRequest(
        question=sample["question"],
        options=sample.get("options", []),
        selected_answer=sample.get("selected_answer", ""),
        correct_answer=sample.get("correct_answer", ""),
        user_answer=sample.get("user_answer", ""),
        evaluation=sample.get("evaluation", ""),
        step=sample.get("step", 1),
    )
    return build_prompt("follow-up", request, context=context_text)


def build_v2_prompt(sample: dict, context_text: str) -> str:
    """follow_up_v2.prompt 템플릿을 읽어서 {{변수}} 치환. RAG 컨텍스트도 주입."""
    template = V2_PROMPT_PATH.read_text(encoding="utf-8")
    options_text = "\n".join(
        f"{i + 1}. {opt}" for i, opt in enumerate(sample.get("options", []))
    )
    # 운영 build_prompt 가 컨텍스트 있을 때 붙이는 형식과 동일하게 맞춤.
    context_block = f"\n\n[Retrieved Context]\n{context_text}" if context_text else ""
    replacements = {
        "{{question}}": sample.get("question", ""),
        "{{options}}": options_text,
        "{{selected_answer}}": sample.get("selected_answer", ""),
        "{{correct_answer}}": sample.get("correct_answer", ""),
        "{{user_answer}}": sample.get("user_answer", ""),
        "{{evaluation}}": sample.get("evaluation", ""),
        "{{step}}": str(sample.get("step", 1)),
        "{{context_block}}": context_block,
    }
    result = template
    for key, value in replacements.items():
        result = result.replace(key, value)
    return result


def call_ollama(prompt: str) -> tuple[str, int]:
    """Ollama generate API 호출. (응답 텍스트, 경과 ms) 반환."""
    body = {
        "model": MODEL,
        "prompt": prompt,
        "stream": False,
        "think": False,
        "keep_alive": -1,  # 정수로 보냄. 문자열 "-1"은 Ollama가 Go duration 으로 파싱하다 400 (error/2026-05-27 참고)
        "options": {
            "temperature": 0.4,
            "num_predict": 256,
            "num_ctx": 2048,
            "num_thread": 4,
            "repeat_penalty": 1.1,
            "top_p": 0.9,
            "top_k": 40,
        },
    }
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        f"{OLLAMA_BASE_URL}/api/generate",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = ""
        try:
            body = exc.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        return (
            f"[오류] HTTP {exc.code} {exc.reason}\n응답 본문: {body}",
            int((time.perf_counter() - started) * 1000),
        )
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        return f"[오류] {type(exc).__name__}: {exc}", int((time.perf_counter() - started) * 1000)
    elapsed_ms = int((time.perf_counter() - started) * 1000)
    return str(payload.get("response", "")).strip(), elapsed_ms


def load_samples() -> list[dict]:
    samples = []
    with SAMPLES_PATH.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                samples.append(json.loads(line))
    return samples


def write_jsonl(path: Path, items: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as f:
        for item in items:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")


def format_rag_summary(contexts_info: list[dict]) -> str:
    if not contexts_info:
        return "(검색된 카드 없음)"
    lines = []
    for c in contexts_info:
        mark = "✅" if c["passed_filter"] else "⚠️ 점수미달"
        lines.append(f"- {mark} `{c['concept_id']}` ({c['title']}) — score: {c['score']}")
    return "\n".join(lines)


def write_comparison_md(samples: list[dict], v1_results: list[dict], v2_results: list[dict], results_dir: Path) -> None:
    rag_note = (
        "운영 정합 모드: follow-up 은 RAG 스킵 (운영 nodes.py:30-33 동일)"
        if MATCH_PRODUCTION_FOLLOWUP
        else f"RAG 포함: 검색 (limit={RAG_LIMIT}, score≥{MIN_CONTEXT_SCORE}) → 프롬프트 컨텍스트 주입"
    )
    title_suffix = "(운영 정합 — RAG 없음)" if MATCH_PRODUCTION_FOLLOWUP else "(RAG 포함)"
    lines = [
        f"# v1 vs v2 프롬프트 비교 결과 {title_suffix}",
        f"",
        f"- 모델: `{MODEL}`",
        f"- 샘플 수: {len(samples)}",
        f"- 생성 시각: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- {rag_note}",
        f"",
        f"## 평가 방법",
        f"각 샘플별로 v1/v2 출력을 나란히 보고, 아래 기준으로 직접 채점하세요:",
        f"- **자연스러움**: 한국어 톤이 자연스러운가? (영어 단어 섞임/번역체 없음)",
        f"- **진단 적합성**: 학습자의 상태에 맞는 꼬리질문인가?",
        f"- **형식 준수**: 3문장 이내 + 물음표로 끝남",
        f"- **컨텍스트 활용**: RAG 카드 내용을 답변에 잘 녹였는가?",
        f"",
        f"---",
        f"",
    ]
    for sample, v1, v2 in zip(samples, v1_results, v2_results):
        lines.extend([
            f"## 샘플 {sample['id']} — {sample['scenario']}",
            f"",
            f"**문제**: {sample['question']}",
            f"**학습자 답변**: {sample['user_answer']}",
            f"",
            f"**RAG 검색 결과**:",
            format_rag_summary(v1["contexts_info"]),
            f"",
            f"### v1 출력 ({v1['elapsed_ms']}ms)",
            f"```",
            v1["output"],
            f"```",
            f"",
            f"### v2 출력 ({v2['elapsed_ms']}ms)",
            f"```",
            v2["output"],
            f"```",
            f"",
            f"### 채점 (직접 작성)",
            f"- 자연스러움: v1 _/5, v2 _/5",
            f"- 진단 적합성: v1 _/5, v2 _/5",
            f"- 컨텍스트 활용: v1 _/5, v2 _/5",
            f"- 메모: ",
            f"",
            f"---",
            f"",
        ])
    (results_dir / "comparison.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    results_dir = _resolve_results_dir(MODEL, MATCH_PRODUCTION_FOLLOWUP)
    results_dir.mkdir(parents=True, exist_ok=True)
    samples = load_samples()
    print(f"[info] {len(samples)}개 샘플 로드. 모델: {MODEL}")
    print(f"[info] Ollama URL: {OLLAMA_BASE_URL}")
    if MATCH_PRODUCTION_FOLLOWUP:
        print(f"[info] 운영 정합 모드: follow-up 은 RAG 스킵 (운영 nodes.py:30-33 동일)")
    else:
        print(f"[info] RAG 포함: 검색(limit={RAG_LIMIT}, score≥{MIN_CONTEXT_SCORE}) → 프롬프트 주입 → Ollama")
    print(f"[info] 각 샘플당 v1, v2 두 번 호출합니다 (총 {len(samples) * 2}회)")
    print()

    v1_results = []
    v2_results = []
    for i, sample in enumerate(samples, 1):
        print(f"[{i}/{len(samples)}] {sample['id']} - {sample['scenario']}")
        # RAG 검색은 v1/v2 공통 — 같은 쿼리 → 같은 컨텍스트로 공정 비교
        context_text, contexts_info = retrieve_rag(sample)
        if MATCH_PRODUCTION_FOLLOWUP:
            print(f"  RAG: 스킵 (운영 정합 모드)")
        else:
            passed = [c for c in contexts_info if c["passed_filter"]]
            print(f"  RAG: {len(contexts_info)}개 후보 검색, {len(passed)}개 통과(score≥{MIN_CONTEXT_SCORE})")

        v1_prompt = build_v1_prompt(sample, context_text)
        v1_out, v1_ms = call_ollama(v1_prompt)
        print(f"  v1 완료 ({v1_ms}ms)")
        v1_results.append({
            "id": sample["id"],
            "prompt": v1_prompt,
            "output": v1_out,
            "elapsed_ms": v1_ms,
            "contexts_info": contexts_info,
        })

        v2_prompt = build_v2_prompt(sample, context_text)
        v2_out, v2_ms = call_ollama(v2_prompt)
        print(f"  v2 완료 ({v2_ms}ms)")
        v2_results.append({
            "id": sample["id"],
            "prompt": v2_prompt,
            "output": v2_out,
            "elapsed_ms": v2_ms,
            "contexts_info": contexts_info,
        })

    write_jsonl(results_dir / "v1_outputs.jsonl", v1_results)
    write_jsonl(results_dir / "v2_outputs.jsonl", v2_results)
    write_comparison_md(samples, v1_results, v2_results, results_dir)

    print()
    print(f"[완료] 결과 저장 위치: {results_dir}")
    print(f"  - {results_dir / 'comparison.md'} (사람이 보기 좋은 비교표)")
    print(f"  - {results_dir / 'v1_outputs.jsonl'} (v1 원본 출력 + 검색 정보)")
    print(f"  - {results_dir / 'v2_outputs.jsonl'} (v2 원본 출력 + 검색 정보)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

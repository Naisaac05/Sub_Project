# -*- coding: utf-8 -*-
"""
LLM-리랭커 실측 — bge top-3 후보를 로컬 LLM이 재정렬하면 recall@1이 오르나?

흐름: bge로 top-3 후보 추림(임베딩 캐시라 즉시) → LLM에게 "이 중 질문에 가장 맞는 카드 하나" 선택
      → 그 선택을 새 top-1로 보고 recall@1 비교.
리랭킹의 천장 = recall@3(후보 안에 정답이 있어야 올릴 수 있음).

지표: bge recall@1 vs rerank recall@1, 그리고 rescued(bge 틀린 걸 살림)/demoted(bge 맞은 걸 망침).

실행:  PYTHONUTF8=1 python evals/retrieval_poc/rerank_llm.py
"""
import json
import os
import pathlib
import urllib.request

from corpus_scaling import DENSE, DIVERSE, card_text, embed, queries_for

HERE = pathlib.Path(__file__).parent
REPORT = HERE / "REPORT_rerank_llm.md"
N = 3
MODEL = os.getenv("POC_LLM_MODEL", "qwen2.5:3b")
BASE = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")


def make_bge(docs):
    vecs = {i: embed(docs[i]) for i in docs}

    def topn(query, n):
        q = embed(query)
        ranked = sorted(((i, sum(a * b for a, b in zip(q, vecs[i]))) for i in docs),
                        key=lambda x: x[1], reverse=True)
        return [i for i, _ in ranked[:n]]
    return topn


def llm_pick(query, cand_ids, docs):
    lines = [f"{i + 1}. {docs[cid]}" for i, cid in enumerate(cand_ids)]
    prompt = (f'질문: "{query}"\n'
              "아래 카드 중 이 질문에 가장 잘 맞는 것 하나의 번호를 골라라.\n"
              + "\n".join(lines)
              + '\n번호만 JSON으로 답해라: {"pick": 번호}')
    body = json.dumps({
        "model": MODEL, "prompt": prompt, "stream": False, "format": "json",
        "think": False, "options": {"temperature": 0, "num_predict": 20},
    }).encode("utf-8")
    req = urllib.request.Request(f"{BASE}/api/generate", data=body, headers={"Content-Type": "application/json"})
    try:
        resp = json.loads(urllib.request.urlopen(req, timeout=120).read().decode("utf-8"))
        pick = int(json.loads(resp.get("response", "{}")).get("pick"))
        if 1 <= pick <= len(cand_ids):
            return cand_ids[pick - 1]
    except Exception:  # noqa: BLE001  (파싱/네트워크 실패 → bge top-1 유지)
        pass
    return cand_ids[0]


def run(specs):
    docs = {s[0]: card_text(s) for s in specs}
    topn = make_bge(docs)
    qs = [(q, s[0]) for s in specs for q, _qt in queries_for(s)]
    n = len(qs)
    bge_hit = rer_hit = rescued = demoted = ceiling = 0
    for q, gold in qs:
        cands = topn(q, N)
        bge_top = cands[0]
        pick = llm_pick(q, cands, docs)
        bge_ok = bge_top == gold
        rer_ok = pick == gold
        bge_hit += bge_ok
        rer_hit += rer_ok
        rescued += (not bge_ok and rer_ok)
        demoted += (bge_ok and not rer_ok)
        ceiling += (gold in cands)
    return {"n": n, "bge": bge_hit / n, "rerank": rer_hit / n,
            "rescued": rescued, "demoted": demoted, "ceiling": ceiling / n}


def main():
    corpora = {"DENSE (근접개념)": DENSE, "DIVERSE (다양)": DIVERSE}
    results = {name: run(specs) for name, specs in corpora.items()}

    out = []
    out.append(f"# LLM-리랭커 실측 — bge top-{N} 재정렬 (모델 {MODEL})\n")
    out.append("> 자동 생성. `python evals/retrieval_poc/rerank_llm.py`\n")
    out.append(f"리랭킹 천장 = recall@{N}(후보 안에 정답이 있어야 올림). rescued=bge 틀린 걸 살림, demoted=bge 맞은 걸 망침.\n")
    out.append("| 코퍼스 | bge recall@1 | **rerank recall@1** | Δ | 천장(recall@3) | rescued | demoted |")
    out.append("|---|---:|---:|---:|---:|---:|---:|")
    for name, r in results.items():
        out.append(f"| {name} | {r['bge']:.1%} | {r['rerank']:.1%} | {r['rerank'] - r['bge']:+.1%} | "
                   f"{r['ceiling']:.1%} | {r['rescued']} | {r['demoted']} |")
    out.append("")
    for name, r in results.items():
        d = r["rerank"] - r["bge"]
        verdict = ("리랭킹이 bge보다 높음 → 도움됨!" if d > 0.001 else
                   "리랭킹 ≈ bge" if abs(d) <= 0.001 else "리랭킹이 bge보다 낮음 → 손해(demoted가 rescued보다 많음)")
        out.append(f"- **{name}**: {verdict} (rescued {r['rescued']} − demoted {r['demoted']} = 순이득 {r['rescued'] - r['demoted']})")
    out.append("")
    REPORT.write_text("\n".join(out), encoding="utf-8")

    for name, r in results.items():
        print(f"{name}: bge={r['bge']:.3f} rerank={r['rerank']:.3f} delta={r['rerank']-r['bge']:+.3f} "
              f"ceiling={r['ceiling']:.3f} rescued={r['rescued']} demoted={r['demoted']}")
    print(f"report -> {REPORT}")


if __name__ == "__main__":
    main()

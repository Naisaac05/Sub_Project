# -*- coding: utf-8 -*-
"""
검색 비교 PoC — bm25 / bge / hybrid 를 recall@1 / recall@3 / MRR 로 비교하고 REPORT 생성.

데이터셋의 각 행: question + expected_concepts(+ 선택 query_type).
query_type 이 있으면 '질의 유형별 분해'를 추가로 출력한다(하이브리드가 어디서 값을 하나).

평가 대상: expected_concepts 가 코퍼스 카드에 존재하는 행만.
recall@k = 정답 개념 중 하나라도 상위 k 안에 있으면 hit. MRR = 첫 정답 개념 순위의 역수.

실행:
  PYTHONUTF8=1 python evals/retrieval_poc/evaluate.py                                  # golden_dataset
  PYTHONUTF8=1 python evals/retrieval_poc/evaluate.py --dataset evals/retrieval_poc/hybrid_queries.jsonl --out evals/retrieval_poc/REPORT_hybrid.md
"""
import argparse
import json
import pathlib

from retrievers import ALL_IDS, N_CARDS, RETRIEVERS

HERE = pathlib.Path(__file__).parent
KS = (1, 3)
NAMES = ("bm25", "bge", "hybrid")
LABEL = {"bm25": "bm25 (lexical, 단어)", "bge": "bge (임베딩, 의미)", "hybrid": "hybrid (RRF 융합)"}


def load_rows(path):
    cardset = set(ALL_IDS)
    rows = [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]
    out = []
    for r in rows:
        gold = {c for c in r.get("expected_concepts", []) if c in cardset}
        if gold:
            out.append({"id": r.get("id", ""), "question": r["question"], "gold": gold,
                        "query_type": r.get("query_type", "all")})
    return out, len(rows)


def first_rank(ranked, gold):
    for i, cid in enumerate(ranked):
        if cid in gold:
            return i + 1
    return None


def metrics(records):
    n = len(records)
    if n == 0:
        return {"n": 0, "recall": {k: 0.0 for k in KS}, "mrr": 0.0}
    hits = {k: sum(1 for r in records if r["rank"] and r["rank"] <= k) for k in KS}
    mrr = sum((1.0 / r["rank"]) if r["rank"] else 0.0 for r in records) / n
    return {"n": n, "recall": {k: hits[k] / n for k in KS}, "mrr": mrr}


def run(fn, rows):
    recs = []
    for row in rows:
        ranked = fn(row["question"], k=N_CARDS)
        recs.append({"rank": first_rank(ranked, row["gold"]), "query_type": row["query_type"],
                     "question": row["question"], "gold": sorted(row["gold"]), "top3": ranked[:3]})
    return recs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", type=pathlib.Path, default=HERE.parent / "golden_dataset.jsonl")
    ap.add_argument("--out", type=pathlib.Path, default=HERE / "REPORT.md")
    args = ap.parse_args()

    rows, total = load_rows(args.dataset)
    results = {name: run(fn, rows) for name, fn in RETRIEVERS.items()}
    qtypes = sorted({r["query_type"] for r in rows})

    out = []
    out.append(f"# 검색 비교 PoC 리포트 — `{args.dataset.name}`\n")
    out.append("> 자동 생성. `python evals/retrieval_poc/evaluate.py --dataset ...`\n")
    out.append(f"- 코퍼스: 지식카드 {N_CARDS}장 / 평가 질의 {len(rows)}개 (전체 {total})")
    out.append("- recall@k = 정답 개념 중 하나라도 상위 k 안에 있으면 hit. MRR = 첫 정답 순위 역수.\n")

    out.append("## 1. 전체 결과\n")
    out.append("| 리트리버 | recall@1 | recall@3 | MRR |")
    out.append("|---|---:|---:|---:|")
    for name in NAMES:
        m = metrics(results[name])
        out.append(f"| {LABEL[name]} | {m['recall'][1]:.1%} | {m['recall'][3]:.1%} | {m['mrr']:.3f} |")
    out.append("")

    if qtypes != ["all"]:
        out.append("## 2. 질의 유형별 recall@1 (하이브리드가 어디서 값을 하나)\n")
        out.append("| query_type | n | bm25 | bge | hybrid |")
        out.append("|---|---:|---:|---:|---:|")
        for qt in qtypes:
            n_qt = sum(1 for r in rows if r["query_type"] == qt)
            cells = [f"{metrics([r for r in results[name] if r['query_type'] == qt])['recall'][1]:.0%}"
                     for name in NAMES]
            out.append(f"| {qt} | {n_qt} | {cells[0]} | {cells[1]} | {cells[2]} |")
        out.append("")

    out.append("## 3. recall@3 에서 놓친 질의\n")
    for name in NAMES:
        ms = [r for r in results[name] if not r["rank"] or r["rank"] > 3]
        out.append(f"### {LABEL[name]} — {len(ms)}건")
        for r in ms:
            out.append(f"- `{r['question']}` ({r['query_type']}) → 정답 {r['gold']}, top3={r['top3']}")
        out.append("")

    args.out.write_text("\n".join(out), encoding="utf-8")

    for name in NAMES:
        m = metrics(results[name])
        print(f"{name:8s} recall@1={m['recall'][1]:.3f} recall@3={m['recall'][3]:.3f} mrr={m['mrr']:.3f}")
    if qtypes != ["all"]:
        for qt in qtypes:
            line = " ".join(
                f"{name}={metrics([r for r in results[name] if r['query_type']==qt])['recall'][1]:.2f}"
                for name in NAMES)
            print(f"  [{qt:11s}] {line}")
    print(f"rows={len(rows)} cards={N_CARDS} report -> {args.out}")


if __name__ == "__main__":
    main()

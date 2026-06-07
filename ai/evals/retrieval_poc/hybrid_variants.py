# -*- coding: utf-8 -*-
"""
"bge를 안 깎는(혹은 살리는) hybrid가 존재하는가?" 측정.

DENSE / DIVERSE 코퍼스에서 질의별 bm25·bge 랭킹을 한 번 계산하고, 그 위에서 변형들을 비교:
  - bge / bm25 / rrf 1:1 (기준)
  - 가중 RRF 1:2, 1:3, 1:5 (bge 가중치를 높임)
  - 캐스케이드: bge top1 마진(top1-top2 코사인)이 τ 미만일 때만 RRF로 폴백
  - oracle: 질의마다 bge 또는 bm25 중 하나라도 top1을 맞히면 정답 (= 모든 fusion 의 이론적 천장)

핵심: oracle 이 bge 보다 의미 있게 높지 않으면 → 어떤 hybrid 도 bge 를 못 넘는다.
임베딩은 corpus_scaling 의 디스크 캐시를 재사용(2회차부터 즉시).

실행:  PYTHONUTF8=1 python evals/retrieval_poc/hybrid_variants.py
"""
import pathlib

from corpus_scaling import DENSE, DIVERSE, BM25, card_text, embed, queries_for

HERE = pathlib.Path(__file__).parent
REPORT = HERE / "REPORT_hybrid_variants.md"


def bge_scored(docs):
    vecs = {i: embed(docs[i]) for i in docs}

    def rank(query):
        q = embed(query)
        return sorted(((i, sum(a * b for a, b in zip(q, vecs[i]))) for i in docs),
                      key=lambda x: x[1], reverse=True)
    return rank


def weighted_rrf(bm_ranked, bge_ranked, w_bm, w_bge, k=60):
    score = {}
    for r, c in enumerate(bm_ranked):
        score[c] = score.get(c, 0.0) + w_bm / (k + r + 1)
    for r, c in enumerate(bge_ranked):
        score[c] = score.get(c, 0.0) + w_bge / (k + r + 1)
    return sorted(score, key=lambda c: score[c], reverse=True)


def analyze(specs):
    docs = {s[0]: card_text(s) for s in specs}
    bm = BM25(docs)
    bgs = bge_scored(docs)
    qs = [(q, s[0]) for s in specs for q, _qt in queries_for(s)]

    per = []
    for q, gold in qs:
        bm_ranked = bm.rank(q)
        bge_sc = bgs(q)
        bge_ranked = [i for i, _ in bge_sc]
        margin = (bge_sc[0][1] - bge_sc[1][1]) if len(bge_sc) > 1 else 1.0
        per.append({"gold": gold, "bm": bm_ranked, "bge": bge_ranked, "margin": margin})

    n = len(per)

    def r1(pick_fn):
        return sum(1 for p in per if pick_fn(p) == p["gold"]) / n

    variants = {}
    variants["bge"] = r1(lambda p: p["bge"][0])
    variants["bm25"] = r1(lambda p: p["bm"][0])
    variants["rrf 1:1"] = r1(lambda p: weighted_rrf(p["bm"], p["bge"], 1, 1)[0])
    for wb in (2, 3, 5):
        variants[f"rrf 1:{wb}"] = r1(lambda p, wb=wb: weighted_rrf(p["bm"], p["bge"], 1, wb)[0])
    for tau in (0.03, 0.05, 0.08):
        variants[f"cascade τ={tau}"] = r1(
            lambda p, tau=tau: p["bge"][0] if p["margin"] >= tau else weighted_rrf(p["bm"], p["bge"], 1, 1)[0])

    both = bge_only = bm_only = neither = 0
    bm_only_q = []
    for (q, gold), p in zip(qs, per):
        bg = p["bge"][0] == gold
        bmh = p["bm"][0] == gold
        if bg and bmh:
            both += 1
        elif bg:
            bge_only += 1
        elif bmh:
            bm_only += 1
            bm_only_q.append(q)
        else:
            neither += 1
    oracle = (both + bge_only + bm_only) / n
    return variants, {"n": n, "both": both, "bge_only": bge_only, "bm_only": bm_only,
                      "neither": neither, "oracle": oracle, "bm_only_q": bm_only_q}


def main():
    corpora = {"DENSE (근접개념)": DENSE, "DIVERSE (다양)": DIVERSE}
    out = []
    out.append("# hybrid 변형 비교 — bge를 안 깎는 hybrid가 있는가?\n")
    out.append("> 자동 생성. `python evals/retrieval_poc/hybrid_variants.py`\n")
    out.append("recall@1 기준. oracle = 질의마다 bge·bm25 중 하나라도 top1 맞히면 정답(모든 fusion의 천장).\n")

    console = []
    for cname, specs in corpora.items():
        variants, orc = analyze(specs)
        out.append(f"## {cname} — 질의 {orc['n']}개\n")
        out.append("| 변형 | recall@1 | bge 대비 |")
        out.append("|---|---:|---:|")
        bge_v = variants["bge"]
        for name, v in variants.items():
            delta = "" if name == "bge" else f"{v - bge_v:+.1%}"
            out.append(f"| {name} | {v:.1%} | {delta} |")
        out.append("")
        out.append("**오라클 분해 (recall@1):**\n")
        out.append(f"- 둘 다 맞음: {orc['both']} / bge만 맞음: {orc['bge_only']} / "
                   f"**bm25만 맞음(= hybrid가 살릴 여지): {orc['bm_only']}** / 둘 다 틀림: {orc['neither']}")
        out.append(f"- **oracle 천장 = {orc['oracle']:.1%}** (bge 단독 {bge_v:.1%})")
        gap = orc["oracle"] - bge_v
        if orc["bm_only"] == 0:
            out.append(f"- → bm25-only 정답이 0개. **어떤 fusion도 bge({bge_v:.1%})를 못 넘는다.**")
        else:
            out.append(f"- → bm25-only 정답 {orc['bm_only']}개 존재. 완벽 fusion이면 +{gap:.1%}까지 가능. "
                       f"살릴 질의: {orc['bm_only_q']}")
        out.append("")
        console.append((cname, variants, orc, bge_v))

    REPORT.write_text("\n".join(out), encoding="utf-8")

    for cname, variants, orc, bge_v in console:
        print(f"== {cname} (n={orc['n']}) ==")
        for name, v in variants.items():
            print(f"  {name:14s} r@1={v:.3f}" + ("" if name == "bge" else f"  ({v-bge_v:+.3f} vs bge)"))
        print(f"  oracle={orc['oracle']:.3f}  bm25_only={orc['bm_only']}  bge_only={orc['bge_only']}")
    print(f"report -> {REPORT}")


if __name__ == "__main__":
    main()

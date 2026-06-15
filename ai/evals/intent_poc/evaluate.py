# -*- coding: utf-8 -*-
"""
dataset.jsonl 을 선택한 분류기로 평가하고 REPORT.md 를 생성한다.

이 파일은 app 을 직접 import 하지 않는다. 앱 결합은 classifiers.py 한 곳에만 있다.
분류기 교체:  --classifier current | phase1

실행:
  PYTHONUTF8=1 python evals/intent_poc/evaluate.py                 # 기본 current
  PYTHONUTF8=1 python evals/intent_poc/evaluate.py --classifier phase1
"""
import argparse
import json
import pathlib
from collections import Counter, defaultdict

from classifiers import ABBR, CLASSES, TARGET_DESC, get_classifier, mapping_rows

HERE = pathlib.Path(__file__).parent
DEFAULT_DATA = HERE / "dataset.jsonl"
DEFAULT_REPORT = HERE / "REPORT.md"


def filter_rows_by_split(rows, split):
    if split is None:
        return list(rows)
    return [row for row in rows if row.get("split") == split]


def prf(support, predicted_total, correct):
    recall = correct / support if support else 0.0
    precision = correct / predicted_total if predicted_total else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
    return precision, recall, f1


def main():
    ap = argparse.ArgumentParser(description="의도분류 PoC 평가기")
    ap.add_argument("--classifier", default="current", help="classifiers.py 의 분류기 이름 (current|phase1)")
    ap.add_argument("--dataset", type=pathlib.Path, default=DEFAULT_DATA)
    ap.add_argument("--split", choices=("dev", "holdout"), default=None,
                    help="dataset.jsonl split filter. omitted means all rows.")
    ap.add_argument("--out", type=pathlib.Path, default=DEFAULT_REPORT)
    args = ap.parse_args()

    predict = get_classifier(args.classifier)
    all_rows = [json.loads(l) for l in args.dataset.read_text(encoding="utf-8").splitlines() if l.strip()]
    rows = filter_rows_by_split(all_rows, args.split)

    confusion = defaultdict(Counter)
    support = Counter()
    predicted_total = Counter()
    correct = Counter()
    by_vtype = defaultdict(lambda: [0, 0])
    by_split = defaultdict(lambda: [0, 0])
    misses = []

    for r in rows:
        exp = r["expected_intent"]
        try:
            pred = predict(r["question"])
        except Exception as exc:  # noqa: BLE001
            pred = f"(error:{type(exc).__name__})"
        confusion[exp][pred] += 1
        support[exp] += 1
        predicted_total[pred] += 1
        ok = pred == exp
        correct[exp] += 1 if ok else 0
        by_vtype[r["variation_type"]][1] += 1
        by_vtype[r["variation_type"]][0] += 1 if ok else 0
        by_split[r["split"]][1] += 1
        by_split[r["split"]][0] += 1 if ok else 0
        if not ok:
            misses.append((exp, pred, r["variation_type"], r["question"]))

    total = len(rows)
    total_correct = sum(correct.values())
    overall_acc = total_correct / total if total else 0.0

    out = []
    out.append(f"# 의도분류 PoC 리포트 — classifier=`{args.classifier}`\n")
    out.append("> 자동 생성 파일. `python evals/intent_poc/evaluate.py` 로 재생성된다.\n")
    out.append(f"- 데이터셋: `{args.dataset.name}` ({total}행, 의도당 {total // len(CLASSES)}개, dev/holdout 50:50)")
    out.append(f"- 측정 대상: {TARGET_DESC.get(args.classifier, args.classifier)}")
    out.append("- 비교 기준: Phase 1 의 10-class taxonomy\n")

    out.append("## 1. 한눈에 보는 결과\n")
    out.append(f"- **전체 정확도: {overall_acc:.1%}** ({total_correct}/{total})")
    if by_split["dev"][1] and by_split["holdout"][1]:
        out.append(f"- dev 정확도: {by_split['dev'][0] / by_split['dev'][1]:.1%}  /  "
                   f"holdout 정확도: {by_split['holdout'][0] / by_split['holdout'][1]:.1%}")
    macro_f1 = sum(prf(support[c], predicted_total[c], correct[c])[2] for c in CLASSES) / len(CLASSES)
    if args.split:
        out.append(f"- split filter: `{args.split}`")
    out.append(f"- macro-F1: {macro_f1:.3f}\n")

    out.append("## 2. 클래스별 정밀도/재현율/F1\n")
    out.append("| 의도 | support | 맞춘 수 | precision | recall | F1 |")
    out.append("|---|---:|---:|---:|---:|---:|")
    for c in CLASSES:
        p, rc, f1 = prf(support[c], predicted_total[c], correct[c])
        out.append(f"| {c} | {support[c]} | {correct[c]} | {p:.2f} | {rc:.2f} | {f1:.2f} |")
    out.append("")

    out.append("## 3. 변형 축별 정확도 (규칙이 어떤 변형에서 무너지나)\n")
    out.append("| variation_type | 정확도 | (맞춘/전체) |")
    out.append("|---|---:|---|")
    for vt in ("seed", "typo", "filler", "paraphrase"):
        c, t = by_vtype[vt]
        if t:
            out.append(f"| {vt} | {c / t:.1%} | {c}/{t} |")
    out.append("")

    out.append("## 4. 혼동 행렬 (행=정답, 열=예측)\n")
    legend = ", ".join(f"{ABBR[c]}={c}" for c in CLASSES)
    out.append(f"약어: {legend}\n")
    out.append("| 정답\\예측 | " + " | ".join(ABBR[c] for c in CLASSES) + " |")
    out.append("|" + "---|" * (len(CLASSES) + 1))
    for exp in CLASSES:
        cells = [str(confusion[exp][pred]) if confusion[exp][pred] else "." for pred in CLASSES]
        out.append(f"| **{ABBR[exp]}** | " + " | ".join(cells) + " |")
    extra = sorted(set(p for cnt in confusion.values() for p in cnt) - set(CLASSES))
    if extra:
        out.append(f"\n예외 예측 라벨(매핑 외): {extra}")
    out.append("")

    out.append("## 5. 자동 진단 — 어디서 무너지는가\n")
    zero_recall = [c for c in CLASSES if support[c] and correct[c] == 0]
    if zero_recall:
        out.append(f"- **재현율 0% 클래스: {', '.join(zero_recall)}**")
    flow = Counter()
    for exp in CLASSES:
        for pred, n in confusion[exp].items():
            if pred != exp and n:
                flow[(exp, pred)] += n
    if flow:
        out.append("- 가장 흔한 오분류 방향 Top 5:")
        for (exp, pred), n in flow.most_common(5):
            out.append(f"  - `{exp}` → `{pred}` : {n}건")
    out.append("")

    rmap = mapping_rows(args.classifier)
    if rmap:
        out.append("## 부록 A. 사용한 매핑 (현재 출력 → 10-class)\n")
        out.append("| 현재 (intent, sub_intent) | 10-class |")
        out.append("|---|---|")
        for (i, s), v in rmap:
            out.append(f"| ({i}, {s}) | {v} |")
        out.append("| 그 외 intent 기본값 | concept_definition→DEF, wrong_answer_explanation→WAR, follow_up→FU, general_question→UNK |")
        out.append("")

    out.append("## 부록 B. 오분류 샘플 (최대 25개)\n")
    out.append("| 정답 | 예측 | variation | 질문 |")
    out.append("|---|---|---|---|")
    for exp, pred, vt, q in misses[:25]:
        out.append(f"| {exp} | {pred} | {vt} | {q} |")
    out.append("")

    args.out.write_text("\n".join(out), encoding="utf-8")

    print(f"classifier={args.classifier} total={total} acc={overall_acc:.3f} macro_f1={macro_f1:.3f} "
          f"split={args.split or 'all'} dev={by_split['dev'][0]}/{by_split['dev'][1]} "
          f"holdout={by_split['holdout'][0]}/{by_split['holdout'][1]}")
    print("zero_recall=", zero_recall)
    print("by_vtype=", {k: f"{v[0]}/{v[1]}" for k, v in by_vtype.items()})
    print(f"report -> {args.out}")


if __name__ == "__main__":
    main()

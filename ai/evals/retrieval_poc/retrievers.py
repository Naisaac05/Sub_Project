# -*- coding: utf-8 -*-
"""
검색 비교 PoC 의 리트리버 3종 — **앱 결합을 이 파일 한 곳에만** 둔다.

- bm25   : 앱의 실제 BM25 어댑터(순수 파이썬, 단어 매칭)
- bge    : 로컬 Ollama bge-m3 임베딩 최근접(의미 매칭). 카드 임베딩은 1회 계산 후 캐시.
- hybrid : bm25 + bge 결과를 RRF(Reciprocal Rank Fusion)로 순위 합침.

각 리트리버: query -> 관련 concept_id 랭킹 리스트(상위→하위).
코퍼스는 앱의 지식카드(load_concept_cards). 외부 API/torch 미사용(임베딩은 Ollama).
"""
import json
import math
import os
import pathlib
import sys
import urllib.request

AI_ROOT = pathlib.Path(__file__).resolve().parents[2]  # ai/
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from app.rag.documents import load_concept_cards          # noqa: E402
from app.rag.retriever import select_retriever_adapter    # noqa: E402

_CARDS = load_concept_cards()
ALL_IDS = [c.concept_id for c in _CARDS]
_CARD_TEXT = {c.concept_id: c.searchable_text for c in _CARDS}
N_CARDS = len(ALL_IDS)


# --- ① lexical: 앱의 BM25 어댑터 (단어 매칭) --------------------------------
_BM25_ADAPTER = None


def bm25(query, k=N_CARDS):
    global _BM25_ADAPTER
    if _BM25_ADAPTER is None:
        _BM25_ADAPTER = select_retriever_adapter("bm25")
    return [r.concept_id for r in _BM25_ADAPTER.retrieve(query, limit=k)]


# --- ② bge: 로컬 Ollama bge-m3 임베딩 최근접 (의미 매칭) ---------------------
_EMB_CACHE = {}
_CARD_VECS = None


def _embed(text):
    if text in _EMB_CACHE:
        return _EMB_CACHE[text]
    base = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    model = os.getenv("POC_EMBED_MODEL", "bge-m3")
    body = json.dumps({"model": model, "prompt": text}).encode("utf-8")
    req = urllib.request.Request(f"{base}/api/embeddings", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        v = json.loads(r.read().decode("utf-8"))["embedding"]
    norm = math.sqrt(sum(x * x for x in v)) or 1.0
    v = [x / norm for x in v]
    _EMB_CACHE[text] = v
    return v


def _ensure_card_vecs():
    global _CARD_VECS
    if _CARD_VECS is None:
        _CARD_VECS = {cid: _embed(_CARD_TEXT[cid]) for cid in ALL_IDS}
    return _CARD_VECS


def bge(query, k=N_CARDS):
    vecs = _ensure_card_vecs()
    q = _embed(query)
    ranked = sorted(ALL_IDS, key=lambda cid: sum(a * b for a, b in zip(q, vecs[cid])), reverse=True)
    return ranked[:k]


# --- ③ hybrid: RRF(Reciprocal Rank Fusion) ---------------------------------
def _rrf(rank_lists, k=60):
    scores = {}
    for ids in rank_lists:
        for rank, cid in enumerate(ids):
            scores[cid] = scores.get(cid, 0.0) + 1.0 / (k + rank + 1)
    return sorted(scores, key=lambda c: scores[c], reverse=True)


def hybrid(query, k=N_CARDS):
    return _rrf([bm25(query), bge(query)])[:k]


RETRIEVERS = {"bm25": bm25, "bge": bge, "hybrid": hybrid}

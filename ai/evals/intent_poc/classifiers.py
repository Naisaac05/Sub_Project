# -*- coding: utf-8 -*-
"""
PoC 평가 대상 분류기 레지스트리 — **앱(app) 결합을 이 파일 한 곳에만** 둔다.

경계 규칙
- 의존성은 항상 PoC -> app 단방향. app 은 이 PoC 를 절대 import 하지 않는다.
- evaluate.py 는 app 을 직접 import 하지 않고, 여기서 고른 classifier(callable)만 쓴다.
- 새 분류기(예: Phase 1 의 10-class Intent Extractor)는 여기 함수 하나 추가 + 레지스트리 등록으로 끝난다.

각 classifier 는 `question: str -> 10-class label(str)` 시그니처를 따른다.
"""
import pathlib
import sys

# app 패키지를 import 할 수 있도록 ai/ 루트를 경로에 올린다(결합을 이 파일에 가둔다).
AI_ROOT = pathlib.Path(__file__).resolve().parents[2]  # ai/
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

CLASSES = [
    "ANSWER_REASON", "WRONG_ANSWER_REASON", "CONCEPT_DEFINITION", "COMPARISON",
    "EXAMPLE_REQUEST", "PRACTICAL_USAGE", "DEBUG_OR_ERROR", "FOLLOW_UP",
    "OFF_TOPIC", "UNKNOWN",
]
ABBR = {
    "ANSWER_REASON": "AR", "WRONG_ANSWER_REASON": "WAR", "CONCEPT_DEFINITION": "DEF",
    "COMPARISON": "CMP", "EXAMPLE_REQUEST": "EX", "PRACTICAL_USAGE": "PRAC",
    "DEBUG_OR_ERROR": "DBG", "FOLLOW_UP": "FU", "OFF_TOPIC": "OFF", "UNKNOWN": "UNK",
}

# --- current: 현재 규칙기반 (4-intent) 을 10-class 로 환산 ---------------------
# 현재 분류기에 없는 의도(ANSWER_REASON 등)로는 절대 매핑되지 않는다 -> 구조적 recall 0.
CURRENT_SUB_MAP = {
    ("concept_definition", "comparison"): "COMPARISON",
    ("concept_definition", "practical"): "PRACTICAL_USAGE",
    ("concept_definition", "definition"): "CONCEPT_DEFINITION",
    ("concept_definition", "related"): "CONCEPT_DEFINITION",
    ("wrong_answer_explanation", "explanation"): "WRONG_ANSWER_REASON",
    ("follow_up", "follow_up"): "FOLLOW_UP",
}
CURRENT_INTENT_DEFAULT = {
    "concept_definition": "CONCEPT_DEFINITION",
    "wrong_answer_explanation": "WRONG_ANSWER_REASON",
    "follow_up": "FOLLOW_UP",
    "general_question": "UNKNOWN",
}


def _current(question):
    # app import 는 이 함수 안에서만 일어난다(phase1 만 쓸 땐 app 불필요).
    from app.workflow.intent import classify_free_question_rule_based

    r = classify_free_question_rule_based(question)
    mapped = CURRENT_SUB_MAP.get((r.intent, r.sub_intent))
    if mapped is None:
        mapped = CURRENT_INTENT_DEFAULT.get(r.intent, "UNKNOWN")
    return mapped


def _phase1(question):
    raise NotImplementedError(
        "Phase 1 의 10-class Intent Extractor 가 아직 없습니다. "
        "구현 후 이 함수에서 호출해 10-class label 을 그대로 반환하세요."
    )


# --- rule10: PoC 참조 구현 — 목적에 맞춰 짠 10-class 규칙 분류기 ----------------
# 트리거 신호(차이->COMPARISON, 예시->EXAMPLE 등)만으로 작성. 우선순위(위->아래) 중요.
# app 과 무관(순수 PoC). Phase 1 production 추출기의 참조/출발점으로 쓸 수 있다.
import re  # noqa: E402

_FU_PHRASE = ("다시쉽게", "다시설명", "쉽게설명", "쉽게말", "쉽게풀", "더쉽게", "한번만더",
              "무슨말", "무슨뜻", "무슨소리", "무슨의미", "말인지모")
_OFF = ("점심", "밥", "먹", "영화", "주말", "날씨", "축구", "연봉", "월급", "돈많이", "벌어")
_DEBUG = ("에러", "오류", "예외", "안돼", "안되", "안됨", "안돌아", "돌아가", "주입이안",
          "npe", "nullpointer", "작동안")
_COMPARE = ("차이", "비교", "vs", "뭐가달라", "뭐가다", "차이점", "다른점")
_EXAMPLE = ("예시", "예제", "예를", "사례", "코드로", "케이스", "예하나", "예보여", "예좀")
_PRACTICAL = ("실무", "현업", "언제써", "언제쓰", "많이써", "많이쓰", "자주쓰", "많이들",
              "실제로많이", "실제로자주", "어떤상황")
_DEF = ("뭐야", "뭔데", "뭐임", "머야", "머임", "뭔가요", "뜻", "정의", "개념", "무엇",
        "뭔지", "뭐예요", "뭐죠")
_VAGUE = ("그거", "있잖아", "뭐냐", "뭐였", "뭐더라", "뭐시기", "막그런", "막이것", "그런거",
          "이것저것", "관련그런")


def _norm10(q):
    return re.sub(r"\s+", "", q).lower()


def _is_garbled(n):
    if not n:
        return True
    has_syllable = any("가" <= c <= "힣" for c in n)
    has_jamo = any("ㄱ" <= c <= "ㅣ" for c in n)
    if has_jamo and not has_syllable:          # 자모만/자모 섞임 (ㅁㄴㅇㄹ, ㅋㅋ)
        return True
    if not has_syllable and re.fullmatch(r"[a-z]+", n):   # asdf, zzzz
        return True
    if re.fullmatch(r"[^가-힣a-z0-9]+", n):     # 기호만 (......)
        return True
    return False


def _rule10(question):
    n = _norm10(question)
    if _is_garbled(n):
        return "UNKNOWN"
    if any(k in n for k in _FU_PHRASE):                 # 다시 설명/무슨 말 → 직전 답 의존
        return "FOLLOW_UP"
    if any(k in n for k in _OFF):
        return "OFF_TOPIC"
    if any(k in n for k in _DEBUG):
        return "DEBUG_OR_ERROR"
    if "틀" in n or "오답" in n:                         # 내 오답 이유
        return "WRONG_ANSWER_REASON"
    if ("정답" in n or "맞" in n) and any(k in n for k in ("왜", "이유", "근거", "까닭")):
        return "ANSWER_REASON"
    if any(k in n for k in _COMPARE):
        return "COMPARISON"
    if any(k in n for k in _EXAMPLE):
        return "EXAMPLE_REQUEST"
    if any(k in n for k in _PRACTICAL):
        return "PRACTICAL_USAGE"
    if any(k in n for k in _DEF):
        return "CONCEPT_DEFINITION"
    if "왜" in n:                                        # 개념 없는 bare 왜 → 후속 되묻기
        return "FOLLOW_UP"
    if any(k in n for k in _VAGUE):
        return "UNKNOWN"
    return "UNKNOWN"


# --- llm: 로컬 Ollama LLM 분류기 (외부 API 미사용, 제약 준수) -------------------
_LLM_PROMPT = """너는 학습 복습 서비스의 질문 의도 분류기다. 학습자 질문을 아래 10개 의도 중 정확히 하나로 분류한다.

- ANSWER_REASON: 정답이 왜 정답인지
- WRONG_ANSWER_REASON: 내가 고른 오답이 왜 틀렸는지
- CONCEPT_DEFINITION: 한 개념의 뜻/정의
- COMPARISON: 두 개념의 차이/비교
- EXAMPLE_REQUEST: 구체적 예시/코드 요청
- PRACTICAL_USAGE: 실무에서 언제/어떻게 쓰는지
- DEBUG_OR_ERROR: 에러/코드가 안 되는 이유
- FOLLOW_UP: 직전 답변에 의존한 되묻기(왜요?, 다시 설명, 무슨 말)
- OFF_TOPIC: 복습/개발과 무관한 잡담
- UNKNOWN: 의도를 알 수 없음(깨진 입력/모호)

예시:
질문: "세션이랑 JWT 차이가 뭐야?" -> {"intent":"COMPARISON"}
질문: "이거 왜 자꾸 안 되지?" -> {"intent":"DEBUG_OR_ERROR"}
질문: "점심 뭐 먹지?" -> {"intent":"OFF_TOPIC"}

질문: "__Q__"
JSON만 출력:"""


def _llm(question):
    import json
    import os
    import urllib.request

    model = os.getenv("POC_LLM_MODEL", "qwen2.5:3b")
    base = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    prompt = _LLM_PROMPT.replace("__Q__", question.replace('"', "'"))
    body = json.dumps({
        "model": model,
        "prompt": prompt,
        "stream": False,
        "format": "json",      # Ollama 강제 JSON
        "think": False,
        "options": {"temperature": 0, "num_predict": 40},
    }).encode("utf-8")
    req = urllib.request.Request(f"{base}/api/generate", data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            payload = json.loads(r.read().decode("utf-8"))
        obj = json.loads(payload.get("response", "{}"))
        label = str(obj.get("intent", "")).strip().upper()
    except Exception:  # noqa: BLE001  (네트워크/파싱 실패는 UNKNOWN 으로)
        return "UNKNOWN"
    return label if label in CLASSES else "UNKNOWN"


_DATASET_PATH = pathlib.Path(__file__).parent / "dataset.jsonl"


def _load_dev():
    """학습/적합용 dev split 만 읽는다 (holdout 은 절대 학습에 안 쓴다)."""
    import json
    rows = [json.loads(l) for l in _DATASET_PATH.read_text(encoding="utf-8").splitlines() if l.strip()]
    dev = [r for r in rows if r["split"] == "dev"]
    return [r["question"] for r in dev], [r["expected_intent"] for r in dev]


# --- ml: 학습형 분류기 — 규칙을 사람이 안 쓰고 데이터로 학습 -----------------
# TF-IDF 문자 n-gram(2~5) + 로지스틱 회귀. 문자 n-gram 이라 오타에도 비교적 강하다.
# dev(120) 로 학습하고 holdout(120) 으로 평가한다 -> dev 정확도는 '학습 정확도'라 부풀려져 있음.
_ML_MODEL = None


def _ml_fit():
    global _ML_MODEL
    if _ML_MODEL is not None:
        return _ML_MODEL
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import Pipeline

    X, y = _load_dev()
    pipe = Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), min_df=1)),
        ("clf", LogisticRegression(max_iter=2000, C=10.0)),
    ])
    pipe.fit(X, y)
    _ML_MODEL = pipe
    return pipe


def _ml(question):
    pred = _ml_fit().predict([question])[0]
    return pred if pred in CLASSES else "UNKNOWN"


# --- embed: 임베딩 최근접 중심 — 규칙 대신 '예시 추가'로 분류 -----------------
# bge-m3(Ollama)로 dev 예시를 임베딩해 의도별 중심(centroid)을 만들고,
# 질문 임베딩과 코사인 최대인 의도로 분류한다. 의미 기반이라 오타/말바꿈에 강하다.
_EMBED_CENTROIDS = None
_EMBED_CACHE_PATH = pathlib.Path(__file__).parent / ".embed_cache.json"
_EMBED_CACHE = None


def _embed_cache_key(model, text):
    import hashlib

    return hashlib.sha256(f"{model}\0{text}".encode("utf-8")).hexdigest()


def _load_embed_cache():
    global _EMBED_CACHE
    if _EMBED_CACHE is not None:
        return _EMBED_CACHE
    import json

    if _EMBED_CACHE_PATH.exists():
        try:
            _EMBED_CACHE = json.loads(_EMBED_CACHE_PATH.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            _EMBED_CACHE = {}
    else:
        _EMBED_CACHE = {}
    return _EMBED_CACHE


def _save_embed_cache(cache):
    import json

    _EMBED_CACHE_PATH.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")


def _fetch_ollama_embedding(model, text):
    import json
    import os
    import urllib.request

    base = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    body = json.dumps({"model": model, "prompt": text}).encode("utf-8")
    req = urllib.request.Request(f"{base}/api/embeddings", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))["embedding"]


def _ollama_embed(text):
    import os

    model = os.getenv("POC_EMBED_MODEL", "bge-m3")
    cache = _load_embed_cache()
    key = _embed_cache_key(model, text)
    if key in cache:
        return cache[key]
    embedding = _fetch_ollama_embedding(model, text)
    cache[key] = embedding
    _save_embed_cache(cache)
    return embedding


def _embed_fit():
    global _EMBED_CENTROIDS
    if _EMBED_CENTROIDS is not None:
        return _EMBED_CENTROIDS
    from collections import defaultdict
    import numpy as np

    X, y = _load_dev()
    buckets = defaultdict(list)
    for q, label in zip(X, y):
        v = np.asarray(_ollama_embed(q), dtype="float32")
        v /= np.linalg.norm(v) + 1e-9
        buckets[label].append(v)
    centroids = {}
    for label, vs in buckets.items():
        c = np.mean(vs, axis=0)
        centroids[label] = c / (np.linalg.norm(c) + 1e-9)
    _EMBED_CENTROIDS = centroids
    return centroids


def _embed(question):
    import numpy as np

    centroids = _embed_fit()
    try:
        v = np.asarray(_ollama_embed(question), dtype="float32")
    except Exception:  # noqa: BLE001
        return "UNKNOWN"
    v /= np.linalg.norm(v) + 1e-9
    best, best_sim = "UNKNOWN", -1.0
    for label, c in centroids.items():
        sim = float(np.dot(v, c))
        if sim > best_sim:
            best_sim, best = sim, label
    return best


CLASSIFIERS = {
    "current": _current,
    "phase1": _phase1,
    "rule10": _rule10,
    "llm": _llm,
    "ml": _ml,
    "embed": _embed,
}

# 리포트 헤더에 쓰는 대상 설명
TARGET_DESC = {
    "current": "PoC 비교용 `ai/app/workflow/intent.py` 의 `classify_free_question_rule_based` (4-intent 규칙기반, 운영 미사용)",
    "phase1": "Phase 1 10-class Intent Extractor (미구현)",
    "rule10": "PoC 참조 구현 `rule10` — 트리거 신호 기반 10-class 규칙 분류기 (순수 PoC, app 무관)",
    "llm": "로컬 Ollama LLM 분류기 (기본 qwen2.5:3b, format=json, temperature=0)",
    "ml": "학습형 — TF-IDF 문자 n-gram + 로지스틱 회귀 (dev 120 학습, 규칙 미작성)",
    "embed": "임베딩 최근접중심 — bge-m3(Ollama) dev 예시 centroid, 코사인 분류 (예시만 추가)",
}


def get_classifier(name):
    if name not in CLASSIFIERS:
        raise SystemExit(f"unknown classifier '{name}'. available: {list(CLASSIFIERS)}")
    return CLASSIFIERS[name]


def mapping_rows(name):
    """리포트 부록용 매핑 표. 해당 없으면 None."""
    if name == "current":
        return list(CURRENT_SUB_MAP.items())
    return None

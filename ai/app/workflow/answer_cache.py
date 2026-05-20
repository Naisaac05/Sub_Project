from collections import OrderedDict

from app.schemas import AiGenerateRequest
from app.workflow.intent import normalize_question


MAX_CACHE_ITEMS = 128
_CACHE: OrderedDict[str, str] = OrderedDict()


def cache_key_for(mode: str, request: AiGenerateRequest) -> str:
    parts = (
        mode,
        request.model,
        request.question,
        request.correct_answer,
        request.selected_answer,
        request.user_answer,
        request.evaluation,
    )
    return "|".join(normalize_question(part) for part in parts)


def get_cached_answer(key: str) -> str | None:
    answer = _CACHE.get(key)
    if answer is None:
        return None
    _CACHE.move_to_end(key)
    return answer


def put_cached_answer(key: str, answer: str) -> None:
    if not answer:
        return
    _CACHE[key] = answer
    _CACHE.move_to_end(key)
    while len(_CACHE) > MAX_CACHE_ITEMS:
        _CACHE.popitem(last=False)


def clear_answer_cache() -> None:
    _CACHE.clear()

# -*- coding: utf-8 -*-
"""
시드(seeds.jsonl) -> 증강 데이터셋(dataset.jsonl) 생성기.

설계 원칙
- 라벨 상속: 변종의 expected_intent 는 시드에서 그대로 상속한다(변종은 표면형만 흔든다).
- char-level 오타: 결정론적으로 생성. 새 단어를 넣지 않으므로 의도가 바뀔 수 없다 -> 사람 검수 불필요.
- word-level 변형(구어체/유의어/줄임말/어순): 의도 드리프트 위험이 있어 사람이 큐레이션한
  MANUAL 목록만 사용한다(자동 생성 금지).
- 시드당 정확히 8행을 목표로 하며, 우선순위는 seed > 오타 > filler > 큐레이션 변형 순.

재현성: 난수를 쓰지 않는다. 같은 입력 -> 같은 dataset.jsonl.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).parent
SEEDS_PATH = HERE / "seeds.jsonl"
OUT_PATH = HERE / "dataset.jsonl"

ROWS_PER_SEED = 8
MAX_TYPOS_PER_SEED = 3

# 한글 음절 분해/조합 (NFC 완성형 가나다 영역)
SBASE = 0xAC00
SEND = 0xD7A3
# 시각적으로 헷갈리는 중성(jung) 인덱스 쌍: ㅐ<->ㅔ, ㅒ<->ㅖ, ㅚ<->ㅙ
VOWEL_CONF = {1: 5, 5: 1, 3: 7, 7: 3, 11: 10, 10: 11}


def _decompose(ch):
    code = ord(ch)
    if SBASE <= code <= SEND:
        s = code - SBASE
        return s // 588, (s % 588) // 28, s % 28
    return None


def _compose(cho, jung, jong):
    return chr(SBASE + (cho * 21 + jung) * 28 + jong)


def typo_drop_jongseong(text):
    """첫 번째 받침 있는 음절의 받침을 떨어뜨린다(종성 탈락 오타)."""
    out = []
    done = False
    for ch in text:
        d = _decompose(ch)
        if d and d[2] != 0 and not done:
            out.append(_compose(d[0], d[1], 0))
            done = True
        else:
            out.append(ch)
    return "".join(out) if done else None


def typo_swap_vowel(text):
    """첫 번째 헷갈리는 모음을 치환한다(모음 혼동 오타)."""
    out = []
    done = False
    for ch in text:
        d = _decompose(ch)
        if d and d[1] in VOWEL_CONF and not done:
            out.append(_compose(d[0], VOWEL_CONF[d[1]], d[2]))
            done = True
        else:
            out.append(ch)
    return "".join(out) if done else None


def typo_spacing(text):
    """띄어쓰기 오타: 공백이 있으면 첫 공백 제거, 없으면 2번째 글자 뒤에 공백 삽입."""
    if " " in text:
        return text.replace(" ", "", 1)
    if len(text) >= 3:
        return text[:2] + " " + text[2:]
    return None


def make_typos(text):
    seen, typos = set(), []
    for fn in (typo_drop_jongseong, typo_swap_vowel, typo_spacing):
        t = fn(text)
        if t and t != text and t not in seen:
            seen.add(t)
            typos.append(t)
    return typos


def make_filler(text):
    return "혹시 " + text


# ---------------------------------------------------------------------------
# 사람이 큐레이션한 word-level 변형 (구어체/유의어/줄임말/어순).
# 규율: 의도(expected_intent)를 바꾸는 표현 금지. '왜/차이/예시/에러' 등 의도-신호어를
#       새로 끼워넣지 않는다(시드의 의도가 유지되는 표현만 둔다).
# ---------------------------------------------------------------------------
MANUAL = {
    "ar-a": ["이거 정답 왜 3번임?", "왜 3번이 답이야?", "3번이 정답인 이유 뭐야", "이 문제 답이 왜 3번이에요?"],
    "ar-b": ["정답 B인 이유가 뭐임?", "왜 B가 정답이에요?", "B가 답인 까닭이 뭐죠?", "정답이 왜 B예요?"],
    "ar-c": ["이게 왜 맞는 거임?", "이거 맞는 이유가 뭐야?", "이게 정답인 근거 뭐예요?", "이게 왜 맞아?"],
    "war-a": ["내가 고른 2번 왜 틀림?", "2번 고른 게 왜 오답이야?", "제가 선택한 2번은 왜 틀렸나요?", "내 답 2번이 왜 틀려?"],
    "war-b": ["제 답 오답인 이유 뭐예요?", "내 답이 왜 오답임?", "제가 쓴 답이 틀린 이유가 뭐죠?", "왜 제 답이 오답이에요?"],
    "war-c": ["이거 나 왜 틀림?", "내가 이거 왜 틀렸어?", "이 문제 제가 왜 틀린 거예요?", "나 이거 왜 틀린 거야"],
    "def-a": ["트랜잭션 뭐임?", "트랜잭션이 뭔데?", "트랜잭션 뜻이 뭐야?", "트랜잭션이 무엇인가요?"],
    "def-b": ["REST API 정의 좀 알려줘", "REST API가 뭐예요?", "REST API 뜻 알려줄래?", "REST API의 개념 설명해줘"],
    "def-c": ["인덱스 개념 좀 설명해줘", "인덱스가 뭔지 알려줘", "인덱스 뜻 좀 잡아줄래?", "인덱스 개념 정리해줘"],
    "cmp-a": ["세션이랑 JWT 뭐가 달라?", "세션이랑 JWT 차이 뭐임?", "세션과 JWT의 차이점이 뭔가요?", "JWT랑 세션 비교 좀"],
    "cmp-b": ["프로세스랑 스레드 비교해줘", "Process랑 Thread 차이가 뭐야?", "프로세스 vs 스레드 뭐가 달라요?", "프로세스와 스레드 비교 좀 해줄래?"],
    "cmp-c": ["List랑 Set 차이가 뭐야?", "리스트랑 셋 뭐가 다름?", "List와 Set의 차이점은?", "Set이랑 List 비교해줘"],
    "ex-a": ["N+1 문제 예시 좀 들어줄래?", "N+1 문제 예 하나만", "N+1 예시 보여줘", "N+1 문제 사례 하나 알려줘"],
    "ex-b": ["옵저버 패턴 코드 예시 보여줘", "옵저버 패턴 코드로 좀 보여줘", "옵저버 패턴 예제 코드 있어?", "옵저버 패턴 코드 짜서 보여줄 수 있어?"],
    "ex-c": ["이거 실제 예시로 설명해줘", "실제 사례 하나 들어서 설명해줄래?", "예를 들어서 설명해줘", "구체적인 예시로 보여줘"],
    "prac-a": ["인덱스 실무에서 언제 씀?", "인덱스 실제로 언제 써요?", "현업에서 인덱스 언제 쓰는 거야?", "인덱스는 어떤 상황에 실무에서 써?"],
    "prac-b": ["트랜잭션 격리수준 실무에서 어떻게 정해?", "현업에선 격리수준 어떻게 잡아요?", "트랜잭션 격리수준 실제로 어떻게 결정해?", "격리수준 현업에서 보통 뭐로 해요?"],
    "prac-c": ["이거 실무에서 많이 써요?", "이거 현업에서 자주 쓰나?", "실제로 많이들 쓰는 거예요?", "이거 진짜 실무에서 써?"],
    "dbg-a": ["이 코드 왜 NPE 나?", "코드에서 NullPointerException 왜 떠?", "이거 왜 자꾸 NPE 떠요?", "왜 NullPointerException 발생해?"],
    "dbg-b": ["스프링 빈 주입이 안 돼요 왜죠?", "빈 주입이 왜 안 되지?", "스프링에서 의존성 주입이 안 되는데 왜?", "빈이 주입이 안 되는데 이유가 뭐예요?"],
    "dbg-c": ["이거 왜 안 됨?", "이게 왜 자꾸 안 돼요?", "왜 계속 안 되는 거지?", "이거 왜 안 돌아가?"],
    "fu-a": ["왜죠?", "왜에요?", "왜 그런 거예요?", "왜 그래요?", "왜?", "왜인가요?"],
    "fu-b": ["다시 쉽게 설명해줘", "좀 더 쉽게 말해줄래?", "다시 쉽게 알려주세요", "쉽게 풀어서 설명해줄 수 있어?", "한 번만 더 쉽게요", "더 쉽게 설명 가능해요?"],
    "fu-c": ["그게 무슨 말이야?", "방금 그거 무슨 뜻이에요?", "그 말이 무슨 뜻이죠?", "그게 무슨 소리야?", "방금 한 말 무슨 의미예요?", "그게 무슨 말인지 모르겠어요"],
    "off-a": ["점심 뭐 먹을까?", "오늘 점심 뭐 먹지ㅋㅋ", "밥 뭐 먹지?", "점심 메뉴 추천 좀"],
    "off-b": ["주말에 볼 영화 추천해줘", "이번 주말에 무슨 영화 보지?", "영화 뭐 재밌는 거 없어?", "주말에 영화나 볼까?"],
    "off-c": ["개발자 연봉 많이 받나요?", "개발자 되면 돈 많이 벌어?", "개발자 월급 어느 정도예요?", "개발자 연봉 높아요?"],
    "unk-a": ["ㅋㅋㅋㅋ", "ㅇㅇ", "asdf asdf", "zzzz", "........", "ㅁㅁㅁ"],
    "unk-b": ["그거 그거 있잖아", "그 뭐냐 그거", "아 그거 뭐였지", "그거 좀 해줘", "그거 있지 않나", "그 뭐더라"],
    "unk-c": ["자바 막 그런 거 있잖아", "그 자바 뭐시기", "자바 그거 뭐였더라", "자바 막 이것저것", "자바 그 뭐냐 그거", "자바 관련 그런 거"],
}


def build_rows_for_seed(seed):
    """우선순위(seed > 오타 > filler > 큐레이션)대로 채워 최대 8행을 만든다."""
    q = seed["question"]
    intent = seed["expected_intent"]
    candidates = [(q, "seed")]
    for t in make_typos(q)[:MAX_TYPOS_PER_SEED]:
        candidates.append((t, "typo"))
    candidates.append((make_filler(q), "filler"))
    for m in MANUAL.get(seed["seed_id"], []):
        candidates.append((m, "paraphrase"))

    rows, seen = [], set()
    for text, vtype in candidates:
        if len(rows) >= ROWS_PER_SEED:
            break
        if text in seen:
            continue
        seen.add(text)
        idx = len(rows)
        rows.append(
            {
                "id": f"{intent}-{seed['seed_id']}-{idx:02d}",
                "seed_id": seed["seed_id"],
                "question": text,
                "expected_intent": intent,
                "variation_type": vtype,
                # 같은 시드 내에서 짝/홀로 dev/holdout 을 갈라 균형 유지
                "split": "dev" if idx % 2 == 0 else "holdout",
            }
        )
    return rows


def main():
    seeds = [json.loads(line) for line in SEEDS_PATH.read_text(encoding="utf-8").splitlines() if line.strip()]
    all_rows, per_seed = [], {}
    for seed in seeds:
        rows = build_rows_for_seed(seed)
        per_seed[seed["seed_id"]] = len(rows)
        all_rows.extend(rows)

    with OUT_PATH.open("w", encoding="utf-8") as f:
        for row in all_rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    # ASCII 안전 요약 (Windows 콘솔 인코딩 회피)
    short = sum(1 for n in per_seed.values() if n < ROWS_PER_SEED)
    print(f"seeds={len(seeds)} rows={len(all_rows)} dev={sum(1 for r in all_rows if r['split']=='dev')} "
          f"holdout={sum(1 for r in all_rows if r['split']=='holdout')} seeds_under_8={short}")
    if short:
        for sid, n in per_seed.items():
            if n < ROWS_PER_SEED:
                print(f"  UNDER: {sid} -> {n}")


if __name__ == "__main__":
    main()

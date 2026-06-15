with open('ai/app/rag/retriever.py', 'r', encoding='utf-8') as f:
    code = f.read()

# 기존 import 정리
old_imports = "from app.rag.documents import ConceptCard, load_concept_cards"
new_imports = "from app.rag.documents import load_concept_cards\nfrom app.schemas.rag_card import RagCard"
code = code.replace(old_imports, new_imports)

code = code.replace('list[ConceptCard]', 'list[RagCard]')
code = code.replace('card: ConceptCard', 'card: RagCard')

# score_card keyword_text
old_score_kw = 'keyword_text = card.sections.get("평가 키워드", "")'
new_score_kw = 'keyword_text = "\\n".join(p.content for p in card.payloads.model_dump().values() if getattr(p, "intent", None) and p.intent.value == "CONCEPT_DEFINITION" and getattr(p, "payload_status", None) and p.payload_status.value == "approved")'
code = code.replace(old_score_kw, new_score_kw)

# _has_exact_phrase_match keyword_tokens
old_match_kw = 'keyword_tokens = set(tokenize(card.sections.get("평가 키워드", "")))'
new_match_kw = 'keyword_tokens = set(tokenize("\\n".join(p.content for p in card.payloads.model_dump().values() if getattr(p, "intent", None) and p.intent.value == "CONCEPT_DEFINITION" and getattr(p, "payload_status", None) and p.payload_status.value == "approved")))'
code = code.replace(old_match_kw, new_match_kw)


# _format_card_context 함수 전체 교체
old_format_func = '''def _format_card_context(card: ConceptCard) -> str:
    sections = "\\n\\n".join(
        f"## {name}\\n{content}" for name, content in card.sections.items()
    )
    return f"# {card.title}\\n\\n{sections}".strip()'''

new_format_func = '''def _format_card_context(card: RagCard) -> str:
    sections = "\\n\\n".join(
        f"## {p.intent.value}\\n{p.content}"
        for p in card.payloads.model_dump().values()
        if p and getattr(p, 'payload_status', None) and p.payload_status.value == "approved"
    )
    title = getattr(card, 'title', card.term if hasattr(card, 'term') else card.card_id)
    return f"# {title}\\n\\n{sections}".strip()'''

code = code.replace(old_format_func, new_format_func)

with open('ai/app/rag/retriever.py', 'w', encoding='utf-8') as f:
    f.write(code)

print("retriever.py updated")

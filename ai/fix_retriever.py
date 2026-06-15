import re

filepath = "ai/app/rag/retriever.py"

with open(filepath, "r", encoding="utf-8") as f:
    code = f.read()

# Import 수정
code = re.sub(
    r"from app\.rag\.documents import ConceptCard, load_concept_cards",
    'from app.rag.documents import load_concept_cards\nfrom app.schemas.rag_card import RagCard',
    code,
    flags=re.MULTILINE
)

code = code.replace("list[ConceptCard]", "list[RagCard]")
code = code.replace("card: ConceptCard", "card: RagCard")

# _format_card_context 함수 전체 교체
new_func = """def _format_card_context(card: RagCard) -> str:
    \"\"\"RagCard의 approved payload만 사용하여 컨텍스트 생성\"\"\"
    approved_sections = []
    for p in getattr(card, 'payloads', []):
        if getattr(p, 'payload_status', None) and getattr(p.payload_status, 'value', None) == "approved":
            intent = getattr(getattr(p, 'intent', None), 'value', str(getattr(p, 'intent', 'UNKNOWN')))
            content = getattr(p, 'content', '')
            approved_sections.append(f"## {intent}\\n{content}")
    
    sections = "\\n\\n".join(approved_sections)
    title = getattr(card, 'term', getattr(card, 'card_id', 'Unknown'))
    return f"# {title}\\n\\n{sections}".strip()
"""

code = re.sub(
    r"def _format_card_context\(.*?\)(?:.|\n)*?return.*\.strip\(\)",
    new_func,
    code,
    flags=re.DOTALL | re.MULTILINE
)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(code)

print("✅ retriever.py 복구 완료")
print("파일 길이:", len(code), "자")

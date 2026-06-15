import os
import re
import json

SQL_PATH = r"C:\Users\User\Desktop\Sub_Project\backend\data\devmatch-data-only.sql"
OUT_DIR = r"C:\Users\User\Desktop\Sub_Project\ai\app\knowledge\concepts"

CATEGORIES = {
    1: "java",
    2: "java",
    3: "java",
    4: "spring",
    5: "spring",
    6: "cs",
}

INTENTS = [
    "ANSWER_REASON",
    "WRONG_ANSWER_REASON",
    "CONCEPT_DEFINITION",
    "COMPARISON",
    "EXAMPLE_REQUEST",
    "PRACTICAL_USAGE",
    "DEBUG_OR_ERROR"
]

def slugify(term: str) -> str:
    slug = term.lower()
    slug = re.sub(r'[^a-z0-9가-힣]+', '-', slug)
    return slug.strip('-')

def extract_term(content: str) -> str:
    if '정수형 변수' in content: return 'primitive-type'
    if '문자열을 비교' in content: return 'string-comparison'
    if '기본 자료형' in content: return 'primitive-type'
    if '배열의 길이' in content: return 'array-length'
    if 'x++' in content or '출력 결과' in content: return 'operator'
    if '상수' in content: return 'final-keyword'
    if '반복문' in content: return 'loop'
    if '상속' in content: return 'inheritance'
    if '접근 제어자' in content: return 'access-modifier'
    if 'main 메서드' in content: return 'main-method'
    if 'ArrayList' in content: return 'arraylist-linkedlist'
    if '제네릭' in content or 'extends Number' in content: return 'generic'
    if '함수형 인터페이스' in content: return 'functional-interface'
    if 'Stream' in content: return 'stream-api'
    if 'exception' in content.lower(): return 'exception'
    if 'SOLID' in content: return 'solid-principle'
    if 'HashMap' in content: return 'hashmap'
    if 'Optional' in content: return 'optional'
    if 'default 메서드' in content: return 'default-method'
    if 'JVM' in content: return 'jvm-memory'
    if 'G1 GC' in content: return 'g1-gc'
    if 'volatile' in content: return 'volatile'
    if 'synchronized' in content or 'ReentrantLock' in content: return 'synchronization'
    if '싱글턴' in content: return 'singleton-pattern'
    if 'ConcurrentHashMap' in content: return 'concurrenthashmap'
    if '리플렉션' in content: return 'reflection'
    if 'CompletableFuture' in content: return 'completablefuture'
    if '클래스 로더' in content: return 'classloader'
    if 'String이 불변' in content: return 'string-immutable'
    if 'IoC' in content: return 'ioc'
    if 'application.properties' in content or 'application.yml' in content: return 'spring-properties'
    if '@Controller' in content: return 'controller'
    if '의존성 주입' in content or 'DI' in content: return 'dependency-injection'
    if '@Autowired' in content: return 'autowired'
    if '내장 서버' in content: return 'embedded-server'
    if '@RequestMapping' in content: return 'requestmapping'
    if 'Bean' in content: return 'bean-scope'
    if '@Service' in content: return 'service-annotation'
    if 'MVC' in content: return 'mvc-lifecycle'
    if '@ManyToOne' in content: return 'jpa-manytoone'
    if '@Transactional' in content: return 'transactional'
    if 'Spring Security' in content: return 'spring-security'
    if 'N+1' in content: return 'jpa-n-plus-one'
    
    eng_words = re.findall(r'[A-Za-z]+', content)
    if eng_words: return eng_words[0].lower()
    return 'general'

def run():
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(SQL_PATH, 'r', encoding='utf-8') as f:
        data = f.read()
    
    lines = [line for line in data.splitlines() if line.startswith("INSERT INTO `questions`")]
    
    parsed = []
    for line in lines:
        match = re.search(r"VALUES\s*\((.*)\);$", line)
        if not match: continue
        val_str = match.group(1)
        
        try:
            # id is everything before the first comma
            first_comma = val_str.find(',')
            q_id = int(val_str[:first_comma].strip())
            
            # test_id is everything after the last comma
            last_comma = val_str.rfind(',')
            test_id = int(val_str[last_comma+1:].strip())
            
            # content is inside the first string, e.g. 1,'Java에서...',0,...
            content_match = re.search(r"^\d+\s*,\s*'(.*?)'\s*,", val_str)
            if content_match:
                content = content_match.group(1)
            else:
                content = 'Unknown'
                
            parsed.append({"id": q_id, "content": content, "test_id": test_id})
        except Exception as e:
            print(f"Error parsing row: {val_str} -> {e}")

    cards = {}
    for q in parsed:
        if 'E2E' in q['content'].upper():
            continue
        
        cat = CATEGORIES.get(q['test_id'], 'cs')
        term = extract_term(q['content'])
        slug = slugify(term)
        card_id = f"{cat}-{slug}"
        
        if card_id not in cards:
            cards[card_id] = {
                "card_id": card_id,
                "category": cat,
                "term": slug,
                "source_question_ids": [],
                "payloads": []
            }
            for intent in INTENTS:
                cards[card_id]["payloads"].append({
                    "intent": intent,
                    "content": f"Auto-generated draft for {intent} related to {slug}.",
                    "payload_status": "draft"
                })
        
        cards[card_id]["source_question_ids"].append(q['id'])
        
    for card_id, card_data in cards.items():
        cat = card_data["category"]
        os.makedirs(os.path.join(OUT_DIR, cat), exist_ok=True)
        file_path = os.path.join(OUT_DIR, cat, f"{card_data['term']}.json")
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(card_data, f, ensure_ascii=False, indent=2)
            
    print(f"Generated {len(cards)} unique cards from {len(parsed)} questions.")

if __name__ == "__main__":
    run()

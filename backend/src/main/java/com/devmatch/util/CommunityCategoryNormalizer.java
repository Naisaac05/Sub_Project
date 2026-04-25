package com.devmatch.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CommunityCategoryNormalizer {

    public static final String QUESTION = "질문/답변";
    public static final String STUDY = "학습 공유";
    public static final String REVIEW = "멘토링 후기";
    public static final String CAREER = "취업/이직";
    public static final String FREE = "자유게시판";

    private static final Map<String, String> LEGACY_CATEGORY_MAP = Map.of(
            "ï§žëˆÐ¦/?ë“¬?", QUESTION,
            "?ìˆˆë’¿ æ€¨ë“­ì‘€", STUDY,
            "ï§Žì„‘ë„—ï§??ê¾§ë¦°", REVIEW,
            "ç—â‘¥ë¾½/?ëŒì­…", CAREER,
            "?ë¨¯ì‘€å¯ƒëš¯ë–†??", FREE
    );

    private static final Map<String, String> CATEGORY_CODE_MAP = Map.of(
            "question", QUESTION,
            "study", STUDY,
            "review", REVIEW,
            "career", CAREER,
            "free", FREE
    );

    private static final Set<String> VALID_CATEGORIES = Set.of(
            QUESTION,
            STUDY,
            REVIEW,
            CAREER,
            FREE
    );

    private CommunityCategoryNormalizer() {
    }

    public static String normalize(String category) {
        if (category == null) {
            return FREE;
        }

        String trimmed = category.trim();
        if (trimmed.isEmpty()) {
            return FREE;
        }

        String categoryCode = CATEGORY_CODE_MAP.get(trimmed.toLowerCase(Locale.ROOT));
        if (categoryCode != null) {
            return categoryCode;
        }

        String legacy = LEGACY_CATEGORY_MAP.get(trimmed);
        if (legacy != null) {
            return legacy;
        }

        if (VALID_CATEGORIES.contains(trimmed)) {
            return trimmed;
        }

        return FREE;
    }
}

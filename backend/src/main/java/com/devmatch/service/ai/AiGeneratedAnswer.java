package com.devmatch.service.ai;

import java.util.List;
import java.util.Map;

public record AiGeneratedAnswer(
        String answer,
        String route,
        String resolvedQuery,
        String correctionType,
        String matchedConceptId,
        String answerStyle,
        List<String> qualityFlags,
        String candidateId,
        Integer latencyMs,
        List<Map<String, Object>> observabilityEvents
) {
    public static AiGeneratedAnswer plain(String answer) {
        return new AiGeneratedAnswer(answer, null, null, null, null, null, List.of(), null, null, List.of());
    }
}

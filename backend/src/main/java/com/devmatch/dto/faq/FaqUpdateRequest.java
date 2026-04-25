package com.devmatch.dto.faq;

import com.devmatch.entity.FaqCategory;
import jakarta.validation.constraints.Size;

/**
 * 부분 수정용 — null 인 필드는 변경 안 함.
 * orderIndex 도 포함 (인접 swap 호출용).
 */
public record FaqUpdateRequest(
        FaqCategory category,
        @Size(max = 200) String question,
        @Size(max = 5000) String answer,
        Integer orderIndex,
        Boolean published
) {
}

package com.devmatch.dto.faq;

import com.devmatch.entity.Faq;
import com.devmatch.entity.FaqCategory;

import java.time.LocalDateTime;

public record FaqResponse(
        Long id,
        FaqCategory category,
        String question,
        String answer,
        int orderIndex,
        boolean published,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getOrderIndex(),
                faq.isPublished(),
                faq.getCreatedAt(),
                faq.getUpdatedAt()
        );
    }
}

package com.devmatch.dto.faq;

import com.devmatch.entity.FaqCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaqCreateRequest(
        @NotNull FaqCategory category,
        @NotBlank @Size(max = 200) String question,
        @NotBlank @Size(max = 5000) String answer,
        Boolean published
) {
    public boolean publishedOrDefault() {
        return published == null || published;
    }
}

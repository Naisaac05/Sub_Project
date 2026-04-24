package com.devmatch.dto.admin.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPostDeleteRequest(
        @NotBlank(message = "사유를 입력하세요")
        @Size(min = 10, max = 500, message = "사유는 10~500자로 입력하세요")
        String reason
) {}

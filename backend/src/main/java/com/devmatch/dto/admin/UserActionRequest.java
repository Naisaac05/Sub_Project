package com.devmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UserActionRequest {

    @NotBlank(message = "사유는 필수입니다")
    @Size(min = 10, max = 500, message = "사유는 10~500자여야 합니다")
    private String reason;
}

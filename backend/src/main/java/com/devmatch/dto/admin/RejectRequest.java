package com.devmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequest {

    @NotBlank(message = "반려 사유를 입력해주세요")
    @Size(min = 10, max = 500, message = "반려 사유는 10자 이상 500자 이하로 입력해주세요")
    private String reason;
}

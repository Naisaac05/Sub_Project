package com.devmatch.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequest {

    @Size(min = 2, max = 50, message = "이름은 2~50자여야 합니다")
    private String name;

    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다")
    private String password;
}

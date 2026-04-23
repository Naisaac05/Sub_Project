package com.devmatch.dto.admin;

import com.devmatch.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminCreateResponse {
    private UserResponse user;
    private String temporaryPassword;
}

package com.devmatch.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordResetResponse {
    private String temporaryPassword;
    private boolean mustChangePassword;
}

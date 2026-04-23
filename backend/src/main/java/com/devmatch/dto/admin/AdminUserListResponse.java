package com.devmatch.dto.admin;

import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserListResponse {
    private Long id;
    private String email;
    private String name;
    private String role;
    private String status;
    private String jobTitle;
    private LocalDateTime createdAt;

    public static AdminUserListResponse from(User user) {
        return new AdminUserListResponse(
                user.getId(),
                user.getEmail(),
                UserDisplay.displayName(user),
                user.getRole().name(),
                user.getStatus().name(),
                user.getJobTitle(),
                user.getCreatedAt()
        );
    }
}

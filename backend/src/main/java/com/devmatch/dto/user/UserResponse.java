package com.devmatch.dto.user;

import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private String name;
    private String role;
    private String status;
    private String jobTitle;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                UserDisplay.displayName(user),
                user.getRole().name(),
                user.getStatus().name(),
                user.getJobTitle(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getCreatedAt()
        );
    }
}

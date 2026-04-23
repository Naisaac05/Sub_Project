package com.devmatch.dto.admin;

import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminUserDetailResponse {
    private Long id;
    private String email;
    private String name;
    private String role;
    private String status;
    private String jobTitle;
    private String provider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private long paymentCount;
    private long postCount;
    private Long mentorProfileId;

    public static AdminUserDetailResponse from(User user, long paymentCount, long postCount,
                                               Long mentorProfileId) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                UserDisplay.displayName(user),
                user.getRole().name(),
                user.getStatus().name(),
                user.getJobTitle(),
                user.getProvider(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                paymentCount,
                postCount,
                mentorProfileId
        );
    }
}

package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.mentor.MentorApplyRequest;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mentor", description = "멘토 API")
@RestController
@RequestMapping("/api/mentor")
@RequiredArgsConstructor
public class MentorController {

    private final MentorService mentorService;

    @Operation(summary = "멘토 신청")
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<MentorProfileResponse>> apply(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MentorApplyRequest request) {
        MentorProfileResponse response = mentorService.apply(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("멘토 신청이 완료되었습니다", response));
    }

    @Operation(summary = "내 멘토 프로필 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MentorProfileResponse>> getMyMentorProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MentorProfileResponse response = mentorService.getMyMentorProfile(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

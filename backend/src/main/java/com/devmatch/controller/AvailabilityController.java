package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.session.AvailabilityRequest;
import com.devmatch.dto.session.AvailabilityResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Availability", description = "멘토 가용 시간 API")
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @Operation(summary = "가용 시간 추가", description = "멘토 전용")
    @PostMapping
    public ResponseEntity<ApiResponse<AvailabilityResponse>> addAvailability(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AvailabilityRequest request) {
        AvailabilityResponse response = availabilityService.addAvailability(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("가용 시간이 추가되었습니다", response));
    }

    @Operation(summary = "내 가용 시간 목록", description = "멘토 전용")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getMyAvailabilities(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<AvailabilityResponse> response = availabilityService.getMyAvailabilities(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "특정 멘토의 가용 시간 조회")
    @GetMapping("/mentor/{mentorId}")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getMentorAvailabilities(
            @PathVariable Long mentorId) {
        List<AvailabilityResponse> response = availabilityService.getMentorAvailabilities(mentorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "가용 시간 삭제", description = "멘토 전용")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAvailability(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        availabilityService.deleteAvailability(userDetails.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("가용 시간이 삭제되었습니다", null));
    }
}

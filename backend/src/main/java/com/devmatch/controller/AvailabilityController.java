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

@Tag(name = "Availability", description = "멘토 가용 시간 관리 API")
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @Operation(summary = "가용 시간 추가", description = "멘토가 가용 시간(요일/시간)을 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AvailabilityResponse>> addAvailability(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody AvailabilityRequest request
    ) {
        AvailabilityResponse response = availabilityService.addAvailability(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("가용 시간이 등록되었습니다", response));
    }

    @Operation(summary = "내 가용 시간 조회", description = "멘토 본인의 가용 시간 목록을 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getMyAvailability(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        List<AvailabilityResponse> list = availabilityService.getMyAvailability(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "멘토 가용 시간 조회", description = "멘티가 특정 멘토의 가용 시간을 조회합니다.")
    @GetMapping("/mentor/{mentorId}")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getMentorAvailability(
            @PathVariable Long mentorId
    ) {
        List<AvailabilityResponse> list = availabilityService.getMentorAvailability(mentorId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "가용 시간 삭제", description = "멘토가 등록한 가용 시간을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAvailability(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        availabilityService.deleteAvailability(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("가용 시간이 삭제되었습니다", null));
    }
}

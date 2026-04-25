package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.menteechange.MentorChangeRequestResponse;
import com.devmatch.dto.menteechange.MentorChangeRequestSubmitRequest;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.MentorChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mentor Change (Mentee)", description = "멘티의 멘토 교체 신청 API")
@RestController
@RequestMapping("/api/mentee/mentor-change-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTEE')")
public class MentorChangeRequestController {

    private final MentorChangeRequestService service;

    @Operation(summary = "멘토 교체 신청 제출")
    @PostMapping
    public ResponseEntity<ApiResponse<MentorChangeRequestResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MentorChangeRequestSubmitRequest request
    ) {
        MentorChangeRequestResponse res = service.submit(user.getUserId(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("멘토 교체 신청이 접수되었습니다", res));
    }

    @Operation(summary = "본인 최근 신청 조회 (없으면 null)")
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<MentorChangeRequestResponse>> latest(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getLatest(user.getUserId())));
    }

    @Operation(summary = "본인 신청 상세")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MentorChangeRequestResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getOwn(user.getUserId(), id)));
    }

    @Operation(summary = "본인 PENDING 신청 취소")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        service.cancel(user.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}

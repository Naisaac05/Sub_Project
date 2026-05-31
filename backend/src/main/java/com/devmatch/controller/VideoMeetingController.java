package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.video.VideoMeetingRequest;
import com.devmatch.dto.video.VideoMeetingResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.VideoMeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/video-meetings")
@RequiredArgsConstructor
public class VideoMeetingController {
    private final VideoMeetingService videoMeetingService;

    @PostMapping
    public ResponseEntity<ApiResponse<VideoMeetingResponse>> createOrUpdate(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody VideoMeetingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("화상회의 정보가 저장되었습니다.", videoMeetingService.createOrUpdate(user.getUserId(), request))
        );
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<VideoMeetingResponse>> getBySession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(videoMeetingService.findBySessionId(user.getUserId(), sessionId)));
    }
}

package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.video.VideoMeetingRequest;
import com.devmatch.dto.video.VideoMeetingResponse;
import com.devmatch.service.VideoMeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/video-meetings")
@RequiredArgsConstructor
public class VideoMeetingController {
    private final VideoMeetingService videoMeetingService;

    @PostMapping
    public ResponseEntity<ApiResponse<VideoMeetingResponse>> createOrUpdate(@RequestBody VideoMeetingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("화상회의 정보가 저장되었습니다.", videoMeetingService.createOrUpdate(request))
        );
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<VideoMeetingResponse>> getBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(videoMeetingService.findBySessionId(sessionId)));
    }
}

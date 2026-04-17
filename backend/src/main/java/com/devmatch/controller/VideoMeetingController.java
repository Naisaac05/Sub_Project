package com.devmatch.controller;

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
    public ResponseEntity<VideoMeetingResponse> createOrUpdate(@RequestBody VideoMeetingRequest request) {
        return ResponseEntity.ok(videoMeetingService.createOrUpdate(request));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<VideoMeetingResponse> getBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(videoMeetingService.findBySessionId(sessionId));
    }
}

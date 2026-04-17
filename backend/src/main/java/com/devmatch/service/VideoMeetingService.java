package com.devmatch.service;

import com.devmatch.dto.video.VideoMeetingRequest;
import com.devmatch.dto.video.VideoMeetingResponse;
import com.devmatch.entity.VideoMeeting;
import com.devmatch.repository.VideoMeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoMeetingService {
    private final VideoMeetingRepository videoMeetingRepository;

    @Transactional
    public VideoMeetingResponse createOrUpdate(VideoMeetingRequest request) {
        VideoMeeting vm = videoMeetingRepository.findBySessionId(request.getSessionId())
                .orElse(VideoMeeting.builder().sessionId(request.getSessionId()).build());

        vm.setPlatform(request.getPlatform());
        vm.setUrl(request.getUrl());

        videoMeetingRepository.save(vm);
        return VideoMeetingResponse.from(vm);
    }

    public VideoMeetingResponse findBySessionId(Long sessionId) {
        VideoMeeting vm = videoMeetingRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Video meeting not found for session: " + sessionId));
        return VideoMeetingResponse.from(vm);
    }
}

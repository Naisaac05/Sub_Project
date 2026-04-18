package com.devmatch.service;

import com.devmatch.dto.video.VideoMeetingRequest;
import com.devmatch.dto.video.VideoMeetingResponse;
import com.devmatch.entity.VideoMeeting;
import com.devmatch.repository.MentoringSessionRepository;
import com.devmatch.repository.VideoMeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoMeetingService {
    private final VideoMeetingRepository videoMeetingRepository;
    private final MentoringSessionRepository sessionRepository;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public VideoMeetingResponse createOrUpdate(VideoMeetingRequest request) {
        VideoMeeting vm = videoMeetingRepository.findBySessionId(request.getSessionId())
                .orElse(VideoMeeting.builder().sessionId(request.getSessionId()).build());

        vm.setPlatform(request.getPlatform());
        vm.setTitle(request.getTitle());
        vm.setUrl(request.getUrl());

        videoMeetingRepository.save(vm);

        // MentoringSession의 meetLink와 동기화
        sessionRepository.findById(request.getSessionId()).ifPresent(s -> {
            s.updateMeetLink(request.getUrl());
            s.updateTitle(request.getTitle());
            sessionRepository.save(s);

            // Google Calendar 업데이트도 시도
            if (s.getCalendarEventId() != null) {
                googleCalendarService.updateEvent(
                        s.getCalendarEventId(),
                        request.getTitle(),
                        s.getSessionDate(),
                        s.getStartTime(),
                        s.getEndTime()
                );
            }
        });

        return VideoMeetingResponse.from(vm);
    }

    public VideoMeetingResponse findBySessionId(Long sessionId) {
        VideoMeeting vm = videoMeetingRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Video meeting not found for session: " + sessionId));
        return VideoMeetingResponse.from(vm);
    }
}

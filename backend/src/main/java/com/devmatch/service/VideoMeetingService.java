package com.devmatch.service;

import com.devmatch.dto.video.VideoMeetingRequest;
import com.devmatch.dto.video.VideoMeetingResponse;
import com.devmatch.entity.MentoringSession;
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
    public VideoMeetingResponse createOrUpdate(Long userId, VideoMeetingRequest request) {
        MentoringSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new com.devmatch.exception.ForbiddenOperationException("해당 멘토링 세션을 찾을 수 없습니다."));
        assertParticipant(session, userId);

        VideoMeeting vm = videoMeetingRepository.findBySessionId(request.getSessionId())
                .orElse(VideoMeeting.builder().sessionId(request.getSessionId()).build());

        vm.setPlatform(request.getPlatform());
        vm.setTitle(request.getTitle());
        vm.setUrl(request.getUrl());

        videoMeetingRepository.save(vm);

        // MentoringSession의 meetLink와 동기화
        session.updateMeetLink(request.getUrl());
        session.updateTitle(request.getTitle());
        sessionRepository.save(session);

        // Google Calendar 업데이트도 시도
        if (session.getCalendarEventId() != null) {
            googleCalendarService.updateEvent(
                    session.getCalendarEventId(),
                    request.getTitle(),
                    session.getSessionDate(),
                    session.getStartTime(),
                    session.getEndTime()
            );
        }

        return VideoMeetingResponse.from(vm);
    }

    public VideoMeetingResponse findBySessionId(Long userId, Long sessionId) {
        MentoringSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return null;
        }
        assertParticipant(session, userId);
        return videoMeetingRepository.findBySessionId(sessionId)
                .map(VideoMeetingResponse::from)
                .orElse(null);
    }

    // 세션의 멘토 또는 멘티 본인만 화상회의 정보를 읽거나 수정할 수 있다 (IDOR 방지).
    private void assertParticipant(MentoringSession session, Long userId) {
        if (!userId.equals(session.getMenteeId()) && !userId.equals(session.getMentorId())) {
            throw new com.devmatch.exception.ForbiddenOperationException("해당 세션의 참여자만 접근할 수 있습니다.");
        }
    }
}

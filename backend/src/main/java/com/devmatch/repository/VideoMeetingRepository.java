package com.devmatch.repository;

import com.devmatch.entity.VideoMeeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoMeetingRepository extends JpaRepository<VideoMeeting, Long> {
    Optional<VideoMeeting> findBySessionId(Long sessionId);
}

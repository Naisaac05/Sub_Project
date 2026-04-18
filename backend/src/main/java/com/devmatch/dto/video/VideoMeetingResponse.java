package com.devmatch.dto.video;

import com.devmatch.entity.VideoMeeting;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VideoMeetingResponse {
    private Long id;
    private Long sessionId;
    private String platform;
    private String title;
    private String url;
    private LocalDateTime createdAt;

    public static VideoMeetingResponse from(VideoMeeting entity) {
        if (entity == null) return null;
        return VideoMeetingResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .platform(entity.getPlatform())
                .title(entity.getTitle())
                .url(entity.getUrl())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

package com.devmatch.dto.video;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoMeetingRequest {
    private Long sessionId;
    private String platform;
    private String url;
}

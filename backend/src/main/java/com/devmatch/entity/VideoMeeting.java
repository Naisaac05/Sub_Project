package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_meetings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VideoMeeting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId; // reference to LMS session

    @Column(name = "platform", nullable = false)
    private String platform; // e.g., "Zoom", "Google Meet"

    @Column(name = "title")
    private String title;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

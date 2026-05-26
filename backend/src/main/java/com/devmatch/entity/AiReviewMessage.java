package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_review_messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_review_stream_request_terminal", columnNames = "stream_request_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiReviewMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AiReviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiReviewMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AiReviewMessageMode mode;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(length = 80)
    private String aiRoute;

    @Column(length = 500)
    private String aiResolvedQuery;

    @Column(length = 40)
    private String aiCorrectionType;

    @Column(length = 120)
    private String aiMatchedConceptId;

    @Column(length = 40)
    private String aiAnswerStyle;

    @Column(length = 500)
    private String aiQualityFlags;

    @Column(length = 80)
    private String aiCandidateId;

    @Column(name = "stream_request_id", length = 80)
    private String streamRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stream_terminal_status", length = 20)
    private AiReviewStreamTerminalStatus streamTerminalStatus;

    private Integer aiLatencyMs;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiReviewEvaluation evaluation;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

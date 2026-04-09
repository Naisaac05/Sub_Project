package com.devmatch.dto.lms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private int progressRate;
    private int attendanceRate;
    private long dDay;
    private AssignmentStats assignmentStats;
    private NextSessionInfo nextSession;
    private List<ActivityItem> recentActivities;
    private MentorInfo mentorInfo;
    private CommunicationLinks communicationLinks;

    @Getter @AllArgsConstructor @Builder
    public static class AssignmentStats {
        private long total;
        private long submitted;
        private long reviewed;
    }

    @Getter @AllArgsConstructor @Builder
    public static class NextSessionInfo {
        private Long id;
        private String date;
        private String startTime;
        private String endTime;
        private String meetLink;
        private String category;
    }

    @Getter @AllArgsConstructor @Builder
    public static class ActivityItem {
        private String type;
        private String title;
        private String createdAt;
    }

    @Getter @AllArgsConstructor @Builder
    public static class MentorInfo {
        private String name;
        private List<String> specialty;
        private String email;
    }

    @Getter @AllArgsConstructor @Builder
    public static class CommunicationLinks {
        private String discord;
        private String jitsiMeet;
    }
}

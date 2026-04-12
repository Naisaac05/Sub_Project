package com.devmatch.service;

import com.devmatch.dto.lms.DashboardResponse;
import com.devmatch.dto.lms.EnrollmentResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsDashboardService {

    private final LmsAccessService lmsAccessService;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final AssignmentRepository assignmentRepository;
    private final MentoringSessionRepository sessionRepository;
    private final LearningNoteRepository noteRepository;
    private final MentorProfileRepository mentorProfileRepository;

    public DashboardResponse getDashboard(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);

        // 커리큘럼 로딩 (1회) — 진도율, discordUrl, 종료일 공통 사용
        Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId).orElse(null);
        int progressRate = 0;
        String discordUrl = null;
        String mentoringEndDate = null;
        if (curriculum != null) {
            if (curriculum.getTotalWeeks() > 0) {
                long completedWeeks = weekRepository.countByCurriculumIdAndIsCompletedTrue(curriculum.getId());
                progressRate = (int) ((completedWeeks * 100) / curriculum.getTotalWeeks());
            }
            discordUrl = curriculum.getDiscordUrl();
            mentoringEndDate = curriculum.getEndDate() != null ? curriculum.getEndDate().toString() : null;
        }

        // 출석률: 완료 세션 / 전체 세션 (CANCELLED 제외)
        List<MentoringSession> sessions = sessionRepository
                .findByMenteeIdOrMentorIdOrderBySessionDateDesc(
                        matching.getMentee().getId(), matching.getMentor().getId());
        // matchingId로 필터
        List<MentoringSession> matchingSessions = sessions.stream()
                .filter(s -> s.getMatchingId().equals(matchingId))
                .toList();
        long totalSessions = matchingSessions.stream()
                .filter(s -> s.getStatus() != SessionStatus.CANCELLED)
                .count();
        long completedSessions = matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .count();
        int attendanceRate = totalSessions > 0 ? (int) ((completedSessions * 100) / totalSessions) : 0;

        // 과제 통계
        List<Assignment> assignments = assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId);
        long totalAssignments = assignments.size();
        long submittedAssignments = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.SUBMITTED || a.getStatus() == AssignmentStatus.REVIEWED)
                .count();
        long reviewedAssignments = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.REVIEWED)
                .count();

        // 다음 세션
        DashboardResponse.NextSessionInfo nextSession = matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED)
                .filter(s -> !s.getSessionDate().isBefore(LocalDate.now()))
                .min(Comparator.comparing(MentoringSession::getSessionDate)
                        .thenComparing(MentoringSession::getStartTime))
                .map(s -> DashboardResponse.NextSessionInfo.builder()
                        .id(s.getId())
                        .date(s.getSessionDate().toString())
                        .startTime(s.getStartTime().toString())
                        .endTime(s.getEndTime().toString())
                        .meetLink(s.getMeetLink())
                        .category(s.getCategory())
                        .build())
                .orElse(null);

        // 최근 활동 — 각 도메인에서 최근 3건 → 합산 후 시간순 5건
        List<DashboardResponse.ActivityItem> activities = new ArrayList<>();

        assignments.stream().limit(3).forEach(a ->
                activities.add(DashboardResponse.ActivityItem.builder()
                        .type("ASSIGNMENT")
                        .title(a.getTitle())
                        .createdAt(a.getCreatedAt().toString())
                        .build()));

        noteRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId).stream().limit(3).forEach(n ->
                activities.add(DashboardResponse.ActivityItem.builder()
                        .type("NOTE")
                        .title(n.getTitle())
                        .createdAt(n.getCreatedAt().toString())
                        .build()));

        matchingSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .limit(3)
                .forEach(s -> activities.add(DashboardResponse.ActivityItem.builder()
                        .type("SESSION")
                        .title(s.getCategory() + " 세션 완료")
                        .createdAt(s.getUpdatedAt().toString())
                        .build()));

        activities.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        List<DashboardResponse.ActivityItem> topActivities = activities.stream().limit(5).toList();

        // 멘토 정보
        User mentor = matching.getMentor();
        MentorProfile mentorProfile = mentorProfileRepository.findByUserId(mentor.getId()).orElse(null);
        DashboardResponse.MentorInfo mentorInfo = DashboardResponse.MentorInfo.builder()
                .name(mentor.getName())
                .specialty(mentorProfile != null ? mentorProfile.getSpecialty() : List.of())
                .email(mentor.getEmail())
                .build();

        // Jitsi Meet 링크 — 다음 세션의 meetLink
        String jitsiMeet = nextSession != null ? nextSession.getMeetLink() : null;

        return DashboardResponse.builder()
                .progressRate(progressRate)
                .attendanceRate(attendanceRate)
                .mentoringEndDate(mentoringEndDate)
                .assignmentStats(DashboardResponse.AssignmentStats.builder()
                        .total(totalAssignments)
                        .submitted(submittedAssignments)
                        .reviewed(reviewedAssignments)
                        .build())
                .nextSession(nextSession)
                .recentActivities(topActivities)
                .mentorInfo(mentorInfo)
                .communicationLinks(DashboardResponse.CommunicationLinks.builder()
                        .discord(discordUrl)
                        .jitsiMeet(jitsiMeet)
                        .build())
                .build();
    }

    public EnrollmentResponse getEnrollment(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);
        return EnrollmentResponse.from(matching);
    }
}

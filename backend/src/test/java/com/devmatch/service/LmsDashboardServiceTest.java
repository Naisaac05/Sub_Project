package com.devmatch.service;

import com.devmatch.dto.lms.DashboardResponse;
import com.devmatch.entity.Assignment;
import com.devmatch.entity.AssignmentStatus;
import com.devmatch.entity.Curriculum;
import com.devmatch.entity.LearningNote;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MentoringSession;
import com.devmatch.entity.SessionStatus;
import com.devmatch.entity.User;
import com.devmatch.repository.AssignmentRepository;
import com.devmatch.repository.CurriculumRepository;
import com.devmatch.repository.CurriculumWeekRepository;
import com.devmatch.repository.LearningNoteRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.MentoringSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * LMS 대시보드 집계 로직 검증.
 *
 * <p>이 서비스도 테스트가 없었다. 특히 아래 경로들은 과거 스키마가 nullable 이던 시절의
 * 방어 코드({@code getCreatedAt() != null ? ... : "N/A"} 등)를 걷어낸 자리라,
 * 이 테스트가 회귀를 막는다.
 * <ul>
 *   <li>{@code curriculum.getEndDate().toString()} — 종료일</li>
 *   <li>활동 목록의 {@code createdAt} — 감사 필드 직접 참조</li>
 *   <li>"N/A" 분기를 제거한 최신순 정렬</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LmsDashboardServiceTest {

    @Mock private LmsAccessService lmsAccessService;
    @Mock private CurriculumRepository curriculumRepository;
    @Mock private CurriculumWeekRepository weekRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private MentoringSessionRepository sessionRepository;
    @Mock private LearningNoteRepository noteRepository;
    @Mock private MentorProfileRepository mentorProfileRepository;

    private static final Long MATCHING_ID = 50L;

    private LmsDashboardService service() {
        return new LmsDashboardService(lmsAccessService, curriculumRepository, weekRepository,
                assignmentRepository, sessionRepository, noteRepository, mentorProfileRepository);
    }

    private void givenAccess() {
        User mentee = User.builder().id(10L).name("김멘티").email("mentee@test.com").build();
        User mentor = User.builder().id(20L).name("박멘토").email("mentor@test.com").build();
        Matching m = Matching.builder().id(MATCHING_ID).mentee(mentee).mentor(mentor).build();
        when(lmsAccessService.validateAccess(anyLong(), any())).thenReturn(m);
        when(mentorProfileRepository.findByUserId(20L)).thenReturn(Optional.empty());
    }

    /**
     * updatedAt 은 반드시 채운다 — @LastModifiedDate 로 DB 가 NOT NULL 을 보장하므로
     * 값이 비어 있는 세션은 실제로 존재할 수 없다. 픽스처도 그 제약을 따라야 한다.
     */
    private MentoringSession session(Long id, SessionStatus status, LocalDate date) {
        return MentoringSession.builder()
                .id(id).matchingId(MATCHING_ID).menteeId(10L).mentorId(20L)
                .category("BACKEND").sessionDate(date)
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(20, 0))
                .status(status).meetLink("https://meet.test/" + id)
                .updatedAt(date.atTime(20, 0))
                .build();
    }

    private Assignment assignment(Long id, AssignmentStatus status, LocalDateTime createdAt) {
        return Assignment.builder()
                .id(id).matchingId(MATCHING_ID).mentorId(20L)
                .title("과제" + id).status(status).createdAt(createdAt)
                .build();
    }

    private void givenEmptyCollections() {
        when(assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(MATCHING_ID)).thenReturn(List.of());
        when(sessionRepository.findByMenteeIdOrMentorIdOrderBySessionDateDesc(10L, 20L)).thenReturn(List.of());
        when(noteRepository.findByMatchingIdOrderByCreatedAtDesc(MATCHING_ID)).thenReturn(List.of());
        when(curriculumRepository.findByMatchingId(MATCHING_ID)).thenReturn(Optional.empty());
    }

    // ===== 커리큘럼 기반 필드 =====

    @Test
    void 커리큘럼이_있으면_진도율과_종료일이_채워진다() {
        givenAccess();
        givenEmptyCollections();
        Curriculum c = Curriculum.builder()
                .id(1L).matchingId(MATCHING_ID).title("커리큘럼")
                .totalWeeks(8).endDate(LocalDate.of(2026, 10, 31))
                .discordUrl("https://discord.gg/test")
                .build();
        when(curriculumRepository.findByMatchingId(MATCHING_ID)).thenReturn(Optional.of(c));
        when(weekRepository.countByCurriculumIdAndIsCompletedTrue(1L)).thenReturn(2L);

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        assertThat(res.getProgressRate()).isEqualTo(25);              // 2/8
        assertThat(res.getMentoringEndDate()).isEqualTo("2026-10-31"); // endDate 직접 참조
        assertThat(res.getCommunicationLinks().getDiscord()).isEqualTo("https://discord.gg/test");
    }

    @Test
    void 커리큘럼이_없으면_진도율0_종료일null() {
        givenAccess();
        givenEmptyCollections();

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        assertThat(res.getProgressRate()).isZero();
        assertThat(res.getMentoringEndDate()).isNull();
    }

    // ===== 출석률 =====

    @Test
    void 출석률은_취소세션을_모수에서_제외한다() {
        givenAccess();
        givenEmptyCollections();
        when(sessionRepository.findByMenteeIdOrMentorIdOrderBySessionDateDesc(10L, 20L)).thenReturn(List.of(
                session(1L, SessionStatus.COMPLETED, LocalDate.of(2026, 7, 1)),
                session(2L, SessionStatus.COMPLETED, LocalDate.of(2026, 7, 8)),
                session(3L, SessionStatus.SCHEDULED, LocalDate.of(2026, 12, 1)),
                session(4L, SessionStatus.CANCELLED, LocalDate.of(2026, 7, 15))   // 모수 제외
        ));

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        // 완료 2 / (전체 4 - 취소 1 = 3) = 66%
        assertThat(res.getAttendanceRate()).isEqualTo(66);
    }

    @Test
    void 세션이_없으면_출석률은_0이고_다음세션은_null() {
        givenAccess();
        givenEmptyCollections();

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        assertThat(res.getAttendanceRate()).isZero();
        assertThat(res.getNextSession()).isNull();
    }

    // ===== 과제 통계 =====

    @Test
    void 과제통계는_제출과_검토를_구분해_집계한다() {
        givenAccess();
        givenEmptyCollections();
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);
        when(assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(MATCHING_ID)).thenReturn(List.of(
                assignment(1L, AssignmentStatus.ASSIGNED, now),
                assignment(2L, AssignmentStatus.SUBMITTED, now),
                assignment(3L, AssignmentStatus.REVIEWED, now)
        ));

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        assertThat(res.getAssignmentStats().getTotal()).isEqualTo(3);
        assertThat(res.getAssignmentStats().getSubmitted()).isEqualTo(2); // SUBMITTED + REVIEWED
        assertThat(res.getAssignmentStats().getReviewed()).isEqualTo(1);
    }

    // ===== 최근 활동 (방어 코드 제거 지점) =====

    @Test
    void 최근활동은_createdAt_최신순으로_정렬되고_5건으로_제한된다() {
        givenAccess();
        givenEmptyCollections();
        when(assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(MATCHING_ID)).thenReturn(List.of(
                assignment(1L, AssignmentStatus.ASSIGNED, LocalDateTime.of(2026, 7, 1, 9, 0)),
                assignment(2L, AssignmentStatus.ASSIGNED, LocalDateTime.of(2026, 7, 10, 9, 0)),
                assignment(3L, AssignmentStatus.ASSIGNED, LocalDateTime.of(2026, 7, 5, 9, 0))
        ));
        when(noteRepository.findByMatchingIdOrderByCreatedAtDesc(MATCHING_ID)).thenReturn(List.of(
                LearningNote.builder().id(1L).matchingId(MATCHING_ID).authorId(10L)
                        .title("노트").content("내용")
                        .createdAt(LocalDateTime.of(2026, 7, 20, 9, 0)).build()
        ));

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        List<DashboardResponse.ActivityItem> activities = res.getRecentActivities();
        assertThat(activities).hasSizeLessThanOrEqualTo(5);
        // 가장 최근(7/20 노트)이 맨 앞
        assertThat(activities.get(0).getType()).isEqualTo("NOTE");
        // createdAt 이 내림차순인지 확인 — "N/A" 분기를 제거한 정렬의 회귀 방지
        List<String> timestamps = activities.stream()
                .map(DashboardResponse.ActivityItem::getCreatedAt).toList();
        assertThat(timestamps).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    // ===== 멘토 정보 =====

    @Test
    void 멘토프로필이_없어도_이름과_이메일은_채워진다() {
        givenAccess();
        givenEmptyCollections();

        DashboardResponse res = service().getDashboard(10L, MATCHING_ID);

        assertThat(res.getMentorInfo().getName()).isEqualTo("박멘토");
        assertThat(res.getMentorInfo().getEmail()).isEqualTo("mentor@test.com");
        assertThat(res.getMentorInfo().getCourseKeys()).isEmpty();
    }
}

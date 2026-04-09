package com.devmatch.service;

import com.devmatch.dto.lms.CertificateEligibilityResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private static final int REQUIRED_PROGRESS = 80;
    private static final int REQUIRED_ATTENDANCE = 80;
    private static final int REQUIRED_ASSIGNMENT_RATE = 70;

    private final LmsAccessService lmsAccessService;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final AssignmentRepository assignmentRepository;
    private final MentoringSessionRepository sessionRepository;

    public CertificateEligibilityResponse checkEligibility(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);
        int progressRate = calculateProgressRate(matchingId);
        int attendanceRate = calculateAttendanceRate(matching);
        int assignmentSubmitRate = calculateAssignmentSubmitRate(matchingId);
        boolean eligible = progressRate >= REQUIRED_PROGRESS
                && attendanceRate >= REQUIRED_ATTENDANCE
                && assignmentSubmitRate >= REQUIRED_ASSIGNMENT_RATE;
        return CertificateEligibilityResponse.builder()
                .eligible(eligible).progressRate(progressRate)
                .attendanceRate(attendanceRate).assignmentSubmitRate(assignmentSubmitRate)
                .requiredProgress(REQUIRED_PROGRESS).requiredAttendance(REQUIRED_ATTENDANCE)
                .requiredAssignmentRate(REQUIRED_ASSIGNMENT_RATE).build();
    }

    public byte[] generatePdf(Long userId, Long matchingId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);
        CertificateEligibilityResponse eligibility = checkEligibility(userId, matchingId);
        if (!eligibility.isEligible()) {
            throw new RuntimeException("수료 자격 미달");
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();
            Font titleFont = new Font(Font.HELVETICA, 36, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, 18, Font.NORMAL);
            Font smallFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            document.add(new Paragraph("\n\n"));
            Paragraph title = new Paragraph("Certificate of Completion", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("\n\n"));
            Paragraph name = new Paragraph(matching.getMentee().getName(), bodyFont);
            name.setAlignment(Element.ALIGN_CENTER);
            document.add(name);
            document.add(new Paragraph("\n"));
            Paragraph desc = new Paragraph(
                    "has successfully completed the mentoring program in "
                            + matching.getCategory() + " with mentor "
                            + matching.getMentor().getName(), bodyFont);
            desc.setAlignment(Element.ALIGN_CENTER);
            document.add(desc);
            document.add(new Paragraph("\n\n"));
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Paragraph date = new Paragraph("Date: " + dateStr, smallFont);
            date.setAlignment(Element.ALIGN_CENTER);
            document.add(date);
            Paragraph stats = new Paragraph(
                    String.format("Progress: %d%% | Attendance: %d%% | Assignments: %d%%",
                            eligibility.getProgressRate(), eligibility.getAttendanceRate(),
                            eligibility.getAssignmentSubmitRate()), smallFont);
            stats.setAlignment(Element.ALIGN_CENTER);
            document.add(stats);
            document.add(new Paragraph("\n"));
            Paragraph brand = new Paragraph("DevMatch - Skill-Based Mentor Matching Platform", smallFont);
            brand.setAlignment(Element.ALIGN_CENTER);
            document.add(brand);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 생성 실패: " + e.getMessage());
        }
    }

    private int calculateProgressRate(Long matchingId) {
        return curriculumRepository.findByMatchingId(matchingId).map(c -> {
            if (c.getTotalWeeks() == 0) return 0;
            long completed = weekRepository.countByCurriculumIdAndIsCompletedTrue(c.getId());
            return (int) ((completed * 100) / c.getTotalWeeks());
        }).orElse(0);
    }

    private int calculateAttendanceRate(Matching matching) {
        List<MentoringSession> sessions = sessionRepository
                .findByMenteeIdOrMentorIdOrderBySessionDateDesc(
                        matching.getMentee().getId(), matching.getMentor().getId());
        List<MentoringSession> ms = sessions.stream()
                .filter(s -> s.getMatchingId().equals(matching.getId())).toList();
        long total = ms.stream().filter(s -> s.getStatus() != SessionStatus.CANCELLED).count();
        long completed = ms.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        return total > 0 ? (int) ((completed * 100) / total) : 0;
    }

    private int calculateAssignmentSubmitRate(Long matchingId) {
        long total = assignmentRepository.countByMatchingId(matchingId);
        if (total == 0) return 0;
        long submitted = assignmentRepository.countByMatchingIdAndStatusIn(matchingId,
                List.of(AssignmentStatus.SUBMITTED, AssignmentStatus.REVIEWED));
        return (int) ((submitted * 100) / total);
    }
}

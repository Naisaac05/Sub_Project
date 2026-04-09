package com.devmatch.service;

import com.devmatch.dto.lms.*;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MockInterview;
import com.devmatch.entity.Resume;
import com.devmatch.exception.ResumeNotFoundException;
import com.devmatch.repository.MockInterviewRepository;
import com.devmatch.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareerService {

    private final ResumeRepository resumeRepository;
    private final MockInterviewRepository mockInterviewRepository;
    private final LmsAccessService lmsAccessService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public ResumeResponse uploadResume(Long userId, Long matchingId, MultipartFile file) {
        Matching matching = lmsAccessService.validateMenteeAccess(userId, matchingId);
        long currentVersion = resumeRepository.countByMatchingId(matchingId);
        int newVersion = (int) currentVersion + 1;
        String fileName = file.getOriginalFilename();
        String storedName = UUID.randomUUID() + "_" + fileName;
        Path uploadPath = Paths.get(uploadDir, "resumes");
        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), uploadPath.resolve(storedName));
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 실패: " + e.getMessage());
        }
        Resume resume = Resume.builder()
                .menteeId(matching.getMentee().getId()).matchingId(matchingId)
                .version(newVersion).fileUrl("/uploads/resumes/" + storedName)
                .fileName(fileName).uploadedAt(LocalDateTime.now()).build();
        return ResumeResponse.from(resumeRepository.save(resume));
    }

    public List<ResumeResponse> getResumes(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return resumeRepository.findByMatchingIdOrderByVersionDesc(matchingId)
                .stream().map(ResumeResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public ResumeResponse feedbackResume(Long userId, Long resumeId, ResumeFeedbackRequest request) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResumeNotFoundException("이력서를 찾을 수 없습니다: " + resumeId));
        lmsAccessService.validateMentorAccess(userId, resume.getMatchingId());
        resume.addFeedback(request.getMentorFeedback());
        return ResumeResponse.from(resume);
    }

    @Transactional
    public MockInterviewResponse createMockInterview(Long userId, MockInterviewCreateRequest request) {
        lmsAccessService.validateAccess(userId, request.getMatchingId());
        MockInterview mi = MockInterview.builder()
                .matchingId(request.getMatchingId()).interviewDate(request.getInterviewDate())
                .topic(request.getTopic()).questionsAndAnswers(request.getQuestionsAndAnswers())
                .mentorFeedback(request.getMentorFeedback()).rating(request.getRating()).build();
        return MockInterviewResponse.from(mockInterviewRepository.save(mi));
    }

    public List<MockInterviewResponse> getMockInterviews(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return mockInterviewRepository.findByMatchingIdOrderByInterviewDateDesc(matchingId)
                .stream().map(MockInterviewResponse::from).collect(Collectors.toList());
    }
}

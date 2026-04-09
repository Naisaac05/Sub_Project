package com.devmatch.service;

import com.devmatch.dto.lms.*;
import com.devmatch.entity.*;
import com.devmatch.exception.AssignmentNotFoundException;
import com.devmatch.repository.AssignmentRepository;
import com.devmatch.repository.AssignmentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class LmsAssignmentService {
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LmsAccessService lmsAccessService;

    @Transactional
    public AssignmentResponse create(Long userId, AssignmentCreateRequest request) {
        Matching matching = lmsAccessService.validateAccess(userId, request.getMatchingId());
        Assignment assignment = Assignment.builder()
                .matchingId(request.getMatchingId()).mentorId(matching.getMentor().getId())
                .type(request.getType()).title(request.getTitle()).description(request.getDescription())
                .dueDate(request.getDueDate())
                .referenceUrls(request.getReferenceUrls() != null ? request.getReferenceUrls() : Collections.emptyList())
                .build();
        return AssignmentResponse.from(assignmentRepository.save(assignment));
    }

    public List<AssignmentResponse> getList(Long userId, Long matchingId, String type) {
        lmsAccessService.validateAccess(userId, matchingId);
        List<Assignment> assignments;
        if (type != null && !type.isBlank()) {
            assignments = assignmentRepository.findByMatchingIdAndTypeOrderByCreatedAtDesc(matchingId, AssignmentType.valueOf(type));
        } else {
            assignments = assignmentRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId);
        }
        return assignments.stream().map(AssignmentResponse::from).collect(Collectors.toList());
    }

    public AssignmentResponse getDetail(Long userId, Long assignmentId) {
        Assignment assignment = findAssignment(assignmentId);
        lmsAccessService.validateAccess(userId, assignment.getMatchingId());
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse submit(Long userId, Long assignmentId, SubmissionRequest request) {
        Assignment assignment = findAssignment(assignmentId);
        Matching matching = lmsAccessService.validateMenteeAccess(userId, assignment.getMatchingId());
        AssignmentSubmission submission = AssignmentSubmission.builder()
                .assignment(assignment).menteeId(matching.getMentee().getId())
                .submissionUrl(request.getSubmissionUrl()).submissionNote(request.getSubmissionNote())
                .submittedAt(LocalDateTime.now()).build();
        submissionRepository.save(submission);
        assignment.submit();
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse feedback(Long userId, Long assignmentId, FeedbackRequest request) {
        Assignment assignment = findAssignment(assignmentId);
        lmsAccessService.validateMentorAccess(userId, assignment.getMatchingId());
        AssignmentSubmission submission = submissionRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException("제출물을 찾을 수 없습니다. assignmentId: " + assignmentId));
        submission.addFeedback(request.getFeedbackContent(), request.getGrade());
        assignment.reviewed();
        return AssignmentResponse.from(assignment);
    }

    private Assignment findAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new AssignmentNotFoundException("과제를 찾을 수 없습니다: " + id));
    }
}

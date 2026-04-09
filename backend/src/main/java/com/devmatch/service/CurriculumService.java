package com.devmatch.service;

import com.devmatch.dto.lms.CurriculumCreateRequest;
import com.devmatch.dto.lms.CurriculumResponse;
import com.devmatch.entity.Curriculum;
import com.devmatch.entity.CurriculumWeek;
import com.devmatch.exception.CurriculumNotFoundException;
import com.devmatch.repository.CurriculumRepository;
import com.devmatch.repository.CurriculumWeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {
    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final LmsAccessService lmsAccessService;

    @Transactional
    public CurriculumResponse create(Long userId, CurriculumCreateRequest request) {
        lmsAccessService.validateMentorAccess(userId, request.getMatchingId());
        Curriculum curriculum = Curriculum.builder()
                .matchingId(request.getMatchingId())
                .title(request.getTitle()).description(request.getDescription())
                .totalWeeks(request.getTotalWeeks())
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .discordUrl(request.getDiscordUrl())
                .build();
        Curriculum saved = curriculumRepository.save(curriculum);
        if (request.getWeeks() != null) {
            for (CurriculumCreateRequest.WeekRequest weekReq : request.getWeeks()) {
                CurriculumWeek week = CurriculumWeek.builder()
                        .curriculum(saved)
                        .weekNumber(weekReq.getWeekNumber())
                        .title(weekReq.getTitle()).description(weekReq.getDescription())
                        .topics(weekReq.getTopics() != null ? weekReq.getTopics() : Collections.emptyList())
                        .resources(weekReq.getResources() != null ? weekReq.getResources() : Collections.emptyList())
                        .build();
                saved.addWeek(week);
            }
            curriculumRepository.save(saved);
        }
        return CurriculumResponse.from(saved);
    }

    public CurriculumResponse getByMatchingId(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        Curriculum curriculum = curriculumRepository.findByMatchingId(matchingId)
                .orElseThrow(() -> new CurriculumNotFoundException("커리큘럼을 찾을 수 없습니다. matchingId: " + matchingId));
        return CurriculumResponse.from(curriculum);
    }

    @Transactional
    public CurriculumResponse update(Long userId, Long curriculumId, CurriculumCreateRequest request) {
        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new CurriculumNotFoundException("커리큘럼을 찾을 수 없습니다: " + curriculumId));
        lmsAccessService.validateMentorAccess(userId, curriculum.getMatchingId());
        curriculum.update(request.getTitle(), request.getDescription(), request.getTotalWeeks(),
                request.getStartDate(), request.getEndDate(), request.getDiscordUrl());
        return CurriculumResponse.from(curriculum);
    }

    @Transactional
    public void toggleWeekComplete(Long userId, Long weekId) {
        CurriculumWeek week = weekRepository.findById(weekId)
                .orElseThrow(() -> new CurriculumNotFoundException("주차를 찾을 수 없습니다: " + weekId));
        lmsAccessService.validateMenteeAccess(userId, week.getCurriculum().getMatchingId());
        week.toggleComplete();
    }
}

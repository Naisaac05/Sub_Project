package com.devmatch.service;

import com.devmatch.dto.lms.CurriculumCreateRequest;
import com.devmatch.dto.lms.CurriculumLimitResponse;
import com.devmatch.dto.lms.CurriculumResponse;
import com.devmatch.entity.Curriculum;
import com.devmatch.entity.CurriculumWeek;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.CurriculumNotFoundException;
import com.devmatch.exception.CurriculumWeekLimitException;
import com.devmatch.repository.CurriculumRepository;
import com.devmatch.repository.CurriculumWeekRepository;
import com.devmatch.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {
    private static final int WEEKS_PER_MONTH = 4;
    private static final int FALLBACK_MAX_WEEKS = 4;

    private final CurriculumRepository curriculumRepository;
    private final CurriculumWeekRepository weekRepository;
    private final LmsAccessService lmsAccessService;
    private final PaymentRepository paymentRepository;

    @Transactional
    public CurriculumResponse create(Long userId, CurriculumCreateRequest request) {
        lmsAccessService.validateMentorAccess(userId, request.getMatchingId());
        int maxWeeks = resolveMaxWeeks(request.getMatchingId());
        enforceWeekLimit(request.getTotalWeeks(),
                request.getWeeks() != null ? request.getWeeks().size() : 0, maxWeeks);
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
        int maxWeeks = resolveMaxWeeks(curriculum.getMatchingId());
        enforceWeekLimit(request.getTotalWeeks(),
                request.getWeeks() != null ? request.getWeeks().size() : 0, maxWeeks);
        curriculum.update(request.getTitle(), request.getDescription(), request.getTotalWeeks(),
                request.getStartDate(), request.getEndDate(), request.getDiscordUrl());
        if (request.getWeeks() != null) {
            syncWeeks(curriculum, request.getWeeks());
        }
        return CurriculumResponse.from(curriculum);
    }

    public CurriculumLimitResponse getLimit(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        Optional<Payment> payment = paymentRepository.findByMatchingId(matchingId)
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED);
        if (payment.isEmpty()) {
            return CurriculumLimitResponse.builder()
                    .maxWeeks(FALLBACK_MAX_WEEKS)
                    .monthsBundled(0)
                    .paymentDate(null)
                    .hasConfirmedPayment(false)
                    .build();
        }
        Payment p = payment.get();
        int months = p.getMonthsBundled() != null ? p.getMonthsBundled() : 1;
        return CurriculumLimitResponse.builder()
                .maxWeeks(months * WEEKS_PER_MONTH)
                .monthsBundled(months)
                .paymentDate(p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null)
                .hasConfirmedPayment(true)
                .build();
    }

    private int resolveMaxWeeks(Long matchingId) {
        return paymentRepository.findByMatchingId(matchingId)
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .map(p -> (p.getMonthsBundled() != null ? p.getMonthsBundled() : 1) * WEEKS_PER_MONTH)
                .orElse(FALLBACK_MAX_WEEKS);
    }

    private void enforceWeekLimit(Integer totalWeeks, int requestedWeekCount, int maxWeeks) {
        if (totalWeeks != null && totalWeeks > maxWeeks) {
            throw new CurriculumWeekLimitException(
                    "결제된 개월 수(" + (maxWeeks / WEEKS_PER_MONTH) + "개월)에 해당하는 최대 "
                            + maxWeeks + "주차까지만 설정할 수 있습니다. 요청한 총 주차: " + totalWeeks);
        }
        if (requestedWeekCount > maxWeeks) {
            throw new CurriculumWeekLimitException(
                    "결제된 개월 수(" + (maxWeeks / WEEKS_PER_MONTH) + "개월)에 해당하는 최대 "
                            + maxWeeks + "주차까지만 추가할 수 있습니다. 요청한 주차 개수: " + requestedWeekCount);
        }
    }

    private void syncWeeks(Curriculum curriculum, List<CurriculumCreateRequest.WeekRequest> weekReqs) {
        Map<Integer, CurriculumWeek> existing = curriculum.getWeeks().stream()
                .collect(Collectors.toMap(CurriculumWeek::getWeekNumber, Function.identity()));
        Set<Integer> requested = new HashSet<>();
        for (CurriculumCreateRequest.WeekRequest req : weekReqs) {
            requested.add(req.getWeekNumber());
            List<String> topics = req.getTopics() != null ? req.getTopics() : Collections.emptyList();
            List<String> resources = req.getResources() != null ? req.getResources() : Collections.emptyList();
            CurriculumWeek week = existing.get(req.getWeekNumber());
            if (week != null) {
                week.updateContent(req.getWeekNumber(), req.getTitle(), req.getDescription(), topics, resources);
            } else {
                CurriculumWeek newWeek = CurriculumWeek.builder()
                        .curriculum(curriculum)
                        .weekNumber(req.getWeekNumber())
                        .title(req.getTitle())
                        .description(req.getDescription())
                        .topics(topics)
                        .resources(resources)
                        .build();
                curriculum.addWeek(newWeek);
            }
        }
        curriculum.getWeeks().removeIf(w -> !requested.contains(w.getWeekNumber()));
    }

    @Transactional
    public void toggleWeekComplete(Long userId, Long weekId) {
        CurriculumWeek week = weekRepository.findById(weekId)
                .orElseThrow(() -> new CurriculumNotFoundException("주차를 찾을 수 없습니다: " + weekId));
        lmsAccessService.validateMenteeAccess(userId, week.getCurriculum().getMatchingId());
        week.toggleComplete();
    }
}

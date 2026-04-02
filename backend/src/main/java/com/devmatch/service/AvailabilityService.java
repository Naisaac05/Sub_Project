package com.devmatch.service;

import com.devmatch.dto.session.AvailabilityRequest;
import com.devmatch.dto.session.AvailabilityResponse;
import com.devmatch.entity.MentorAvailability;
import com.devmatch.repository.MentorAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private final MentorAvailabilityRepository availabilityRepository;

    /**
     * 멘토 가용 시간 추가
     */
    @Transactional
    public AvailabilityResponse addAvailability(Long mentorId, AvailabilityRequest request) {
        // 중복 확인
        boolean exists = availabilityRepository.existsByMentorIdAndDayOfWeekAndStartTimeAndEndTime(
                mentorId, request.getDayOfWeek(), request.getStartTime(), request.getEndTime());

        if (exists) {
            throw new IllegalArgumentException("이미 등록된 가용 시간입니다");
        }

        MentorAvailability availability = MentorAvailability.builder()
                .mentorId(mentorId)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();

        MentorAvailability saved = availabilityRepository.save(availability);
        return AvailabilityResponse.from(saved);
    }

    /**
     * 내 가용 시간 목록 (멘토용)
     */
    public List<AvailabilityResponse> getMyAvailability(Long mentorId) {
        return availabilityRepository.findByMentorIdAndIsActiveTrue(mentorId)
                .stream()
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 멘토의 가용 시간 조회 (멘티용)
     */
    public List<AvailabilityResponse> getMentorAvailability(Long mentorId) {
        return availabilityRepository.findByMentorIdAndIsActiveTrue(mentorId)
                .stream()
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 가용 시간 삭제 (비활성화)
     */
    @Transactional
    public void deleteAvailability(Long mentorId, Long availabilityId) {
        MentorAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("가용 시간을 찾을 수 없습니다: " + availabilityId));

        if (!availability.getMentorId().equals(mentorId)) {
            throw new IllegalArgumentException("본인의 가용 시간만 삭제할 수 있습니다");
        }

        availability.deactivate();
    }
}

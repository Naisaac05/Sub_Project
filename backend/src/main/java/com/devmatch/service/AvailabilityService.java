package com.devmatch.service;

import com.devmatch.dto.session.AvailabilityRequest;
import com.devmatch.dto.session.AvailabilityResponse;
import com.devmatch.entity.MentorAvailability;
import com.devmatch.entity.User;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.MentorAvailabilityRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private final MentorAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    @Transactional
    public AvailabilityResponse addAvailability(Long mentorId, AvailabilityRequest request) {
        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        MentorAvailability availability = MentorAvailability.builder()
                .mentor(mentor)
                .dayOfWeek(DayOfWeek.valueOf(request.getDayOfWeek().toUpperCase()))
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();

        availability = availabilityRepository.save(availability);
        return AvailabilityResponse.from(availability);
    }

    public List<AvailabilityResponse> getMyAvailabilities(Long mentorId) {
        return availabilityRepository.findByMentorId(mentorId)
                .stream()
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    public List<AvailabilityResponse> getMentorAvailabilities(Long mentorId) {
        return availabilityRepository.findByMentorIdAndIsActiveTrue(mentorId)
                .stream()
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAvailability(Long mentorId, Long availabilityId) {
        MentorAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new UserNotFoundException("가용 시간을 찾을 수 없습니다"));

        if (!availability.getMentor().getId().equals(mentorId)) {
            throw new UserNotFoundException("본인의 가용 시간만 삭제할 수 있습니다");
        }

        availabilityRepository.delete(availability);
    }
}

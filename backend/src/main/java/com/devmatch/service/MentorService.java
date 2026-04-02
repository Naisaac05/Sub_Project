package com.devmatch.service;

import com.devmatch.dto.mentor.MentorApplyRequest;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.User;
import com.devmatch.exception.AlreadyAppliedException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorService {

    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public MentorProfileResponse apply(Long userId, MentorApplyRequest request) {
        if (mentorProfileRepository.existsByUserId(userId)) {
            throw new AlreadyAppliedException("이미 멘토 신청을 하셨습니다");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        MentorProfile profile = MentorProfile.builder()
                .user(user)
                .specialty(request.getSpecialty())
                .careerYears(request.getCareerYears())
                .company(request.getCompany())
                .bio(request.getBio())
                .build();

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        return MentorProfileResponse.from(savedProfile);
    }

    public MentorProfileResponse getMyMentorProfile(Long userId) {
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("멘토 프로필을 찾을 수 없습니다"));
        return MentorProfileResponse.from(profile);
    }
}

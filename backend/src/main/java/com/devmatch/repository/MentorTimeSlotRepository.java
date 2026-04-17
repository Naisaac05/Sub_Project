package com.devmatch.repository;

import com.devmatch.entity.MentorTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface MentorTimeSlotRepository extends JpaRepository<MentorTimeSlot, Long> {

    List<MentorTimeSlot> findByMatchingIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            Long matchingId, LocalDate start, LocalDate end);

    List<MentorTimeSlot> findByMatchingIdAndSlotDateAndIsBookedFalseOrderByStartTimeAsc(
            Long matchingId, LocalDate date);

    List<MentorTimeSlot> findByMatchingIdAndSlotDateAndIsBookedFalseAndProposedByMenteeFalseOrderByStartTimeAsc(
            Long matchingId, LocalDate date);

    List<MentorTimeSlot> findByMatchingIdAndProposedByMenteeTrueOrderBySlotDateAscStartTimeAsc(Long matchingId);

    boolean existsByMatchingIdAndSlotDateAndStartTimeAndEndTime(
            Long matchingId, LocalDate date, LocalTime startTime, LocalTime endTime);
}

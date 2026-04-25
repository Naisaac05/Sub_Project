package com.devmatch.repository;

import com.devmatch.entity.Faq;
import com.devmatch.entity.FaqCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    /** 공개 페이지용 — published=true, 카테고리·순서대로 정렬. */
    List<Faq> findByPublishedTrueOrderByCategoryAscOrderIndexAsc();

    /** 어드민 목록 — published 무관, 카테고리·순서대로 정렬. */
    List<Faq> findAllByOrderByCategoryAscOrderIndexAsc();

    /** 새 FAQ 생성 시 order_index 자동 할당용 — 해당 카테고리 내 마지막 항목. */
    Optional<Faq> findFirstByCategoryOrderByOrderIndexDesc(FaqCategory category);
}

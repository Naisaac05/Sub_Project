package com.devmatch.service;

import com.devmatch.dto.faq.FaqCreateRequest;
import com.devmatch.dto.faq.FaqResponse;
import com.devmatch.dto.faq.FaqUpdateRequest;
import com.devmatch.entity.Faq;
import com.devmatch.repository.FaqRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FaqService {

    private final FaqRepository faqRepository;

    @Transactional(readOnly = true)
    public List<FaqResponse> listPublic() {
        return faqRepository.findByPublishedTrueOrderByCategoryAscOrderIndexAsc()
                .stream().map(FaqResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FaqResponse> listAll() {
        return faqRepository.findAllByOrderByCategoryAscOrderIndexAsc()
                .stream().map(FaqResponse::from).toList();
    }

    public FaqResponse create(FaqCreateRequest req) {
        int nextOrder = faqRepository.findFirstByCategoryOrderByOrderIndexDesc(req.category())
                .map(f -> f.getOrderIndex() + 1)
                .orElse(0);
        Faq saved = faqRepository.save(Faq.builder()
                .category(req.category())
                .question(req.question())
                .answer(req.answer())
                .orderIndex(nextOrder)
                .published(req.publishedOrDefault())
                .build());
        return FaqResponse.from(saved);
    }

    public FaqResponse update(Long id, FaqUpdateRequest req) {
        Faq faq = faqRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("FAQ not found: " + id));
        if (req.category() != null)    faq.setCategory(req.category());
        if (req.question() != null)    faq.setQuestion(req.question());
        if (req.answer() != null)      faq.setAnswer(req.answer());
        if (req.orderIndex() != null)  faq.setOrderIndex(req.orderIndex());
        if (req.published() != null)   faq.setPublished(req.published());
        return FaqResponse.from(faqRepository.save(faq));
    }

    public void delete(Long id) {
        Faq faq = faqRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("FAQ not found: " + id));
        faqRepository.delete(faq);
    }
}

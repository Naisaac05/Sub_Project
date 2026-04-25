package com.devmatch.service;

import com.devmatch.dto.faq.FaqCreateRequest;
import com.devmatch.dto.faq.FaqResponse;
import com.devmatch.dto.faq.FaqUpdateRequest;
import com.devmatch.entity.Faq;
import com.devmatch.entity.FaqCategory;
import com.devmatch.repository.FaqRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock FaqRepository faqRepository;
    @InjectMocks FaqService service;

    @Test
    void create_assigns_orderIndex_as_max_plus_one_within_category() {
        var existing = Faq.builder()
                .id(1L).category(FaqCategory.MENTORING)
                .question("Q").answer("A").orderIndex(3).published(true).build();
        when(faqRepository.findFirstByCategoryOrderByOrderIndexDesc(FaqCategory.MENTORING))
                .thenReturn(Optional.of(existing));
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new FaqCreateRequest(FaqCategory.MENTORING, "새 질문", "새 답변", null);
        FaqResponse res = service.create(req);

        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);
        verify(faqRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderIndex()).isEqualTo(4);
        assertThat(captor.getValue().isPublished()).isTrue();  // 기본 TRUE
        assertThat(res.category()).isEqualTo(FaqCategory.MENTORING);
    }

    @Test
    void create_first_in_category_starts_at_zero() {
        when(faqRepository.findFirstByCategoryOrderByOrderIndexDesc(FaqCategory.PAYMENT))
                .thenReturn(Optional.empty());
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new FaqCreateRequest(FaqCategory.PAYMENT, "Q", "A", false));

        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);
        verify(faqRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderIndex()).isEqualTo(0);
        assertThat(captor.getValue().isPublished()).isFalse();
    }

    @Test
    void update_only_changes_non_null_fields() {
        var existing = Faq.builder()
                .id(7L).category(FaqCategory.TEST)
                .question("orig Q").answer("orig A")
                .orderIndex(2).published(true).build();
        when(faqRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        // question 만 변경
        var req = new FaqUpdateRequest(null, "new Q", null, null, null);
        service.update(7L, req);

        assertThat(existing.getQuestion()).isEqualTo("new Q");
        assertThat(existing.getAnswer()).isEqualTo("orig A");
        assertThat(existing.getOrderIndex()).isEqualTo(2);
        assertThat(existing.isPublished()).isTrue();
        assertThat(existing.getCategory()).isEqualTo(FaqCategory.TEST);
    }

    @Test
    void update_can_toggle_published() {
        var existing = Faq.builder()
                .id(8L).category(FaqCategory.MENTORING)
                .question("Q").answer("A").orderIndex(0).published(true).build();
        when(faqRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(faqRepository.save(any(Faq.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(8L, new FaqUpdateRequest(null, null, null, null, false));

        assertThat(existing.isPublished()).isFalse();
    }

    @Test
    void update_throws_when_not_found() {
        when(faqRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L,
                new FaqUpdateRequest(null, "Q", null, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listPublic_filters_published_and_orders() {
        var f1 = Faq.builder().id(1L).category(FaqCategory.SERVICE_INTRO)
                .question("Q1").answer("A1").orderIndex(0).published(true).build();
        var f2 = Faq.builder().id(2L).category(FaqCategory.TEST)
                .question("Q2").answer("A2").orderIndex(0).published(true).build();
        when(faqRepository.findByPublishedTrueOrderByCategoryAscOrderIndexAsc())
                .thenReturn(List.of(f1, f2));

        List<FaqResponse> res = service.listPublic();

        assertThat(res).hasSize(2);
        assertThat(res.get(0).id()).isEqualTo(1L);
    }

    @Test
    void delete_removes_entity() {
        var existing = Faq.builder().id(5L).category(FaqCategory.MENTOR_APPLY)
                .question("Q").answer("A").orderIndex(0).published(true).build();
        when(faqRepository.findById(5L)).thenReturn(Optional.of(existing));

        service.delete(5L);

        verify(faqRepository).delete(existing);
    }
}

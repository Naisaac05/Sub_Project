package com.devmatch.repository.spec;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 관리자 결제 목록 조회 필터용 JPA Specification 빌더.
 *
 * <p>사용자 이름/이메일 기반 검색은 이 Specification 이 직접 처리하지 않는다.
 * 서비스 레이어에서 `UserRepository` 로 matched userId 집합을 먼저 구한 뒤
 * {@link #userIdIn(Collection)} 를 결합해 호출한다 (Task 7 참조).
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {}

    public static Specification<Payment> withFilter(PaymentStatus status,
                                                    String q,
                                                    LocalDateTime from,
                                                    LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (toExclusive != null) {
                ps.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            if (q != null && !q.isBlank()) {
                // orderId 부분 일치. 사용자 이름/이메일 검색은 서비스 레이어에서 userIdIn 으로 결합.
                ps.add(cb.like(root.get("orderId"), "%" + q.trim() + "%"));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public static Specification<Payment> userIdIn(Collection<Long> userIds) {
        return (root, query, cb) -> {
            if (userIds == null || userIds.isEmpty()) {
                return cb.disjunction(); // 빈 집합 = 매칭 없음
            }
            return root.get("userId").in(userIds);
        };
    }
}

package com.devmatch.repository.spec;

import com.devmatch.entity.Post;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 관리자 게시물 목록 조회 필터용 JPA Specification 빌더.
 *
 * <p>검색어 `q` 는 제목(title) + 내용(content) LIKE OR 로 처리한다.
 * 작성자 이름 기반 검색은 서비스 레이어에서 `UserRepository` 로 matched userId 집합을
 * 구해 {@link #authorIdIn(Collection)} 와 AND 결합하는 방식으로 확장할 수 있다.
 */
public final class PostSpecifications {

    private PostSpecifications() {}

    public static Specification<Post> withFilter(String category,
                                                 String q,
                                                 LocalDateTime from,
                                                 LocalDateTime toExclusive,
                                                 boolean includeDeleted) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!includeDeleted) {
                ps.add(cb.equal(root.get("deleted"), false));
            }
            if (category != null && !category.isBlank()) {
                ps.add(cb.equal(root.get("category"), category.trim()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (toExclusive != null) {
                ps.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("content"), like)
                ));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public static Specification<Post> authorIdIn(Collection<Long> authorIds) {
        return (root, query, cb) -> {
            if (authorIds == null || authorIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("author").get("id").in(authorIds);
        };
    }
}

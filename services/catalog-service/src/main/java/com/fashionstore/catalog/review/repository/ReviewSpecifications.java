package com.fashionstore.catalog.review.repository;

import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Locale;

public final class ReviewSpecifications {

    private ReviewSpecifications() {
    }

    public static Specification<Review> forAdmin(Integer rating, ReviewStatus status) {
        Specification<Review> filter = (root, query, cb) -> cb.conjunction();
        if (rating != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("rating"), rating));
        }
        if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return filter;
    }

    /** Khớp tên sản phẩm (đã đổi sang danh sách id) hoặc nội dung đánh giá. */
    public static Specification<Review> matchesKeyword(String keyword, List<String> productIds) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            var commentMatches = cb.like(cb.lower(root.get("comment")), pattern);
            return productIds.isEmpty() ? commentMatches : cb.or(commentMatches, root.get("productId").in(productIds));
        };
    }
}

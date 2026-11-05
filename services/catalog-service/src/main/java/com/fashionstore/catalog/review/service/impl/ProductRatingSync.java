package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Tính lại điểm sao hiển thị trên sản phẩm sau mỗi lần thêm/sửa/xoá/ẩn đánh giá, trong transaction của lần ghi đó. */
@Component
@RequiredArgsConstructor
public class ProductRatingSync {

    private static final int AVERAGE_SCALE = 1;

    private final ReviewRepository reviewRepository;
    private final ProductReviewTarget productReviewTarget;

    public void refresh(String productId) {
        reviewRepository.lockProductRating(productId);
        ReviewRepository.RatingStats stats = reviewRepository.getVisibleStats(productId);
        BigDecimal average = stats.getAverage() == null
                ? BigDecimal.ZERO.setScale(AVERAGE_SCALE)
                : BigDecimal.valueOf(stats.getAverage()).setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);
        productReviewTarget.updateRating(productId, average, Math.toIntExact(stats.getTotal()));
    }
}

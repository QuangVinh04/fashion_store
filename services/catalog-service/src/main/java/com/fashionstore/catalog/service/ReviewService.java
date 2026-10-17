package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.review.CreateReviewRequest;
import com.fashionstore.catalog.dto.review.ProductReviewSummaryResponse;
import com.fashionstore.catalog.dto.review.ReviewResponse;
import com.fashionstore.catalog.entity.enumeration.ReviewStatus;
import com.fashionstore.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(String productId, CreateReviewRequest request);

    ProductReviewSummaryResponse getProductReviews(String productId, Integer ratingFilter, Pageable pageable);

    PageResponse<List<ReviewResponse>> getAdminReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable);

    default PageResponse<List<ReviewResponse>> getAdminReviews(Integer rating, Pageable pageable) {
        return getAdminReviews(null, rating, null, pageable);
    }

    ReviewResponse updateReviewStatus(String reviewId, ReviewStatus status);

    ReviewResponse replyReview(String reviewId, String replyComment);

    ReviewResponse deleteReply(String reviewId);

    void adminDeleteReview(String reviewId);

    void deleteReview(String productId, String reviewId);
}

package com.fashionstore.catalog.review.service;

import com.fashionstore.catalog.review.dto.CreateReviewRequest;
import com.fashionstore.catalog.review.dto.ProductReviewSummaryResponse;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.dto.UpdateReviewRequest;
import com.fashionstore.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Đánh giá phía khách: xem công khai và thao tác trên đánh giá của chính mình. */
public interface ReviewService {

    ReviewResponse createReview(String productId, CreateReviewRequest request);

    ReviewResponse updateReview(String productId, String reviewId, UpdateReviewRequest request);

    void deleteReview(String productId, String reviewId);

    ProductReviewSummaryResponse getProductReviews(String productId, Integer ratingFilter, Pageable pageable);

    PageResponse<List<ReviewResponse>> getMyReviews(String orderId, Pageable pageable);
}

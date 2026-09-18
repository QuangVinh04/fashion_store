package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.review.CreateReviewRequest;
import com.fashionstore.catalog.dto.review.ProductReviewSummaryResponse;
import com.fashionstore.catalog.dto.review.ReviewResponse;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponse createReview(String productId, CreateReviewRequest request);

    ProductReviewSummaryResponse getProductReviews(String productId, Integer ratingFilter, Pageable pageable);

    void deleteReview(String productId, String reviewId);
}

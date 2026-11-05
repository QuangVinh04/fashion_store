package com.fashionstore.catalog.review.service;

import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Kiểm duyệt đánh giá; quyền review:read / review:manage kiểm tra ở controller. */
public interface AdminReviewService {

    PageResponse<List<ReviewResponse>> getReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable);

    ReviewResponse updateStatus(String reviewId, ReviewStatus status);

    ReviewResponse reply(String reviewId, String replyComment);

    ReviewResponse deleteReply(String reviewId);

    void delete(String reviewId);
}

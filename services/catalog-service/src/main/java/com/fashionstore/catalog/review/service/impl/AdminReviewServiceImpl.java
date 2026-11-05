package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.review.client.IdentityUserClient;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.review.exception.ReviewErrorCode;
import com.fashionstore.catalog.review.mapper.ReviewMapper;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import com.fashionstore.catalog.review.repository.ReviewSpecifications;
import com.fashionstore.catalog.review.service.AdminReviewService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminReviewServiceImpl implements AdminReviewService {

    ReviewRepository reviewRepository;
    ProductReviewTarget productReviewTarget;
    IdentityUserClient identityUserClient;
    CurrentUserProvider currentUserProvider;
    ProductRatingSync productRatingSync;
    ReviewMapper reviewMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ReviewResponse>> getReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable) {
        Specification<Review> filter = ReviewSpecifications.forAdmin(rating, status);
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            filter = filter.and(ReviewSpecifications.matchesKeyword(term, productReviewTarget.idsByNameContaining(term)));
        }
        Page<Review> page = reviewRepository.findAll(filter, pageable);
        Set<String> productIds = page.getContent().stream().map(Review::getProductId).collect(Collectors.toSet());
        Map<String, String> productNames = productReviewTarget.namesByIds(productIds);
        Map<String, Optional<IdentityUserClient.UserInfoResponse>> customers = new HashMap<>();
        List<ReviewResponse> items = page.getContent().stream()
                .map(review -> toAdminResponse(review, productNames.get(review.getProductId()),
                        customers.computeIfAbsent(review.getUserId(), identityUserClient::getUserById).orElse(null)))
                .toList();
        return PageResponse.<List<ReviewResponse>>builder()
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalPage(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .items(items)
                .build();
    }

    @Override
    @Transactional
    public ReviewResponse updateStatus(String reviewId, ReviewStatus status) {
        Review review = findReview(reviewId);
        review.setStatus(status);
        review = reviewRepository.save(review);
        productRatingSync.refresh(review.getProductId());
        log.info("[Review] Updated review {} status to {}", reviewId, status);
        return toAdminResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse reply(String reviewId, String replyComment) {
        Review review = findReview(reviewId);
        String staffId = currentUserProvider.getCurrentUserId();
        review.setReplyComment(replyComment);
        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(staffId);
        review = reviewRepository.save(review);
        log.info("[Review] Staff {} replied to review {}", staffId, reviewId);
        return toAdminResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse deleteReply(String reviewId) {
        Review review = findReview(reviewId);
        review.setReplyComment(null);
        review.setRepliedAt(null);
        review.setRepliedBy(null);
        review = reviewRepository.save(review);
        log.info("[Review] Deleted reply of review {}", reviewId);
        return toAdminResponse(review);
    }

    @Override
    @Transactional
    public void delete(String reviewId) {
        Review review = findReview(reviewId);
        reviewRepository.delete(review);
        productRatingSync.refresh(review.getProductId());
        log.info("[Review] Admin deleted review {}", reviewId);
    }

    private Review findReview(String reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    private ReviewResponse toAdminResponse(Review review) {
        String productName = productReviewTarget.namesByIds(Set.of(review.getProductId())).get(review.getProductId());
        return toAdminResponse(review, productName, identityUserClient.getUserById(review.getUserId()).orElse(null));
    }

    // Thông tin liên hệ lấy mới từ identity; identity lỗi thì giữ tên đã lưu lúc viết đánh giá.
    private ReviewResponse toAdminResponse(Review review, String productName,
                                           IdentityUserClient.UserInfoResponse customer) {
        ReviewResponse response = reviewMapper.toResponse(review, productName);
        if (customer == null) {
            return response;
        }
        response.setCustomerName(customer.fullName());
        response.setCustomerEmail(customer.email());
        response.setCustomerPhone(customer.phone());
        return response;
    }
}

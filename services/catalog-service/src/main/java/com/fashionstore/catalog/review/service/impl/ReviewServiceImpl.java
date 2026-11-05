package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.media.api.ReviewImage;
import com.fashionstore.catalog.media.api.ReviewImages;
import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.review.client.IdentityUserClient;
import com.fashionstore.catalog.review.client.OrderClient;
import com.fashionstore.catalog.review.dto.CreateReviewRequest;
import com.fashionstore.catalog.review.dto.ProductReviewSummaryResponse;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.dto.UpdateReviewRequest;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewMedia;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.review.exception.ReviewErrorCode;
import com.fashionstore.catalog.review.mapper.ReviewMapper;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import com.fashionstore.catalog.review.service.ReviewService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReviewServiceImpl implements ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    ReviewRepository reviewRepository;
    ProductReviewTarget productReviewTarget;
    ReviewImages reviewImages;
    OrderClient orderClient;
    CurrentUserProvider currentUserProvider;
    IdentityUserClient identityUserClient;
    ProductRatingSync productRatingSync;
    ReviewMapper reviewMapper;

    @Override
    @Transactional
    public ReviewResponse createReview(String productId, CreateReviewRequest request) {
        String userId = requireUserId();
        productReviewTarget.requireExists(productId);
        String orderId = verifiedOrderId(userId, productId, request.getOrderId());
        if (reviewRepository.existsByProductIdAndUserIdAndOrderId(productId, userId, orderId)) {
            throw new AppException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        List<ReviewImage> images = reviewImages.requireOwned(userId, nullToEmpty(request.getMediaIds()));
        Review review = Review.builder()
                .productId(productId)
                .userId(userId)
                .orderId(orderId)
                .reviewerName(reviewerName(userId))
                .rating(request.getRating())
                .comment(trim(request.getComment()))
                .verifiedPurchase(true)
                .status(ReviewStatus.VISIBLE)
                .fitRating(request.getFitRating())
                .customerHeight(request.getCustomerHeight())
                .customerWeight(request.getCustomerWeight())
                .build();
        replaceImages(review, images);
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.warn("[Review] Concurrent duplicate review for product {} user {} order {}", productId, userId, orderId);
            throw new AppException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        productRatingSync.refresh(productId);
        log.info("[Review] User {} created review {} for product {} in order {}", userId, review.getId(), productId, orderId);
        return reviewMapper.toResponse(review, productName(productId));
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(String productId, String reviewId, UpdateReviewRequest request) {
        String userId = requireUserId();
        Review review = findOwnReview(productId, reviewId, userId);
        List<ReviewImage> images = reviewImages.requireOwned(userId, nullToEmpty(request.mediaIds()));
        review.setRating(request.rating());
        review.setComment(trim(request.comment()));
        review.setFitRating(request.fitRating());
        review.setCustomerHeight(request.customerHeight());
        review.setCustomerWeight(request.customerWeight());
        replaceImages(review, images);
        review = reviewRepository.save(review);
        productRatingSync.refresh(productId);
        log.info("[Review] User {} updated review {}", userId, reviewId);
        return reviewMapper.toResponse(review, productName(productId));
    }

    @Override
    @Transactional
    public void deleteReview(String productId, String reviewId) {
        String userId = requireUserId();
        Review review = findOwnReview(productId, reviewId, userId);
        reviewRepository.delete(review);
        productRatingSync.refresh(productId);
        log.info("[Review] User {} deleted review {} of product {}", userId, reviewId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReviewSummaryResponse getProductReviews(String productId, Integer ratingFilter, Pageable pageable) {
        productReviewTarget.requireExists(productId);
        Page<Review> page = ratingFilter == null
                ? reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.VISIBLE, pageable)
                : reviewRepository.findByProductIdAndRatingAndStatus(productId, ratingFilter, ReviewStatus.VISIBLE, pageable);
        ReviewRepository.RatingStats stats = reviewRepository.getVisibleStats(productId);
        return ProductReviewSummaryResponse.builder()
                .averageRating(roundedAverage(stats.getAverage()))
                .totalReviews(stats.getTotal())
                .ratingCounts(ratingCounts(productId))
                .reviews(toPage(page, page.getContent().stream().map(reviewMapper::toPublicResponse).toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ReviewResponse>> getMyReviews(String orderId, Pageable pageable) {
        String userId = requireUserId();
        Page<Review> page = orderId == null
                ? reviewRepository.findByUserId(userId, pageable)
                : reviewRepository.findByUserIdAndOrderId(userId, orderId, pageable);
        Map<String, String> productNames = productReviewTarget.namesByIds(
                page.getContent().stream().map(Review::getProductId).collect(Collectors.toSet()));
        return toPage(page, page.getContent().stream()
                .map(review -> reviewMapper.toResponse(review, productNames.get(review.getProductId())))
                .toList());
    }

    private String requireUserId() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private String verifiedOrderId(String userId, String productId, String requestedOrderId) {
        VerifyPurchaseResponse result = orderClient.verifyPurchase(
                new VerifyPurchaseRequest(userId, requestedOrderId, productReviewTarget.variantIds(productId)));
        if (!result.purchased()) {
            throw new AppException(ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        return result.orderId();
    }

    private Review findOwnReview(String productId, String reviewId, String userId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(found -> found.getProductId().equals(productId))
                .orElseThrow(() -> new AppException(ReviewErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw new AppException(ReviewErrorCode.REVIEW_ACTION_FORBIDDEN);
        }
        return review;
    }

    // Snapshot lúc viết: trang công khai không phải gọi identity cho từng đánh giá.
    private String reviewerName(String userId) {
        return identityUserClient.getUserById(userId).map(IdentityUserClient.UserInfoResponse::fullName).orElse(null);
    }

    private static void replaceImages(Review review, List<ReviewImage> images) {
        review.getMediaList().clear();
        for (int index = 0; index < images.size(); index++) {
            ReviewImage image = images.get(index);
            review.getMediaList().add(ReviewMedia.builder()
                    .review(review)
                    .mediaId(image.mediaId())
                    .url(image.url())
                    .displayOrder(index)
                    .build());
        }
    }

    private String productName(String productId) {
        return productReviewTarget.namesByIds(Set.of(productId)).get(productId);
    }

    private Map<Integer, Long> ratingCounts(String productId) {
        Map<Integer, Long> counts = new TreeMap<>();
        for (int rating = MIN_RATING; rating <= MAX_RATING; rating++) {
            counts.put(rating, 0L);
        }
        reviewRepository.countVisibleByRating(productId).forEach(row -> counts.put(row.getRating(), row.getTotal()));
        return counts;
    }

    private static double roundedAverage(Double average) {
        return average == null ? 0.0 : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static PageResponse<List<ReviewResponse>> toPage(Page<Review> page, List<ReviewResponse> items) {
        return PageResponse.<List<ReviewResponse>>builder()
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalPage(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .items(items)
                .build();
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

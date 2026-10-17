package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.client.IdentityUserClient;
import com.fashionstore.catalog.client.OrderClient;
import com.fashionstore.catalog.dto.review.CreateReviewRequest;
import com.fashionstore.catalog.dto.review.ProductReviewSummaryResponse;
import com.fashionstore.catalog.dto.review.ReviewResponse;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductVariant;
import com.fashionstore.catalog.entity.Review;
import com.fashionstore.catalog.entity.ReviewMedia;
import com.fashionstore.catalog.entity.enumeration.ReviewStatus;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.repository.ProductVariantRepository;
import com.fashionstore.catalog.repository.ReviewRepository;
import com.fashionstore.catalog.service.ReviewService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReviewServiceImpl implements ReviewService {

    ReviewRepository reviewRepository;
    ProductRepository productRepository;
    ProductVariantRepository productVariantRepository;
    OrderClient orderClient;
    CurrentUserProvider currentUserProvider;
    IdentityUserClient identityUserClient;

    @Override
    @Transactional
    public ReviewResponse createReview(String productId, CreateReviewRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<String> variantIds = productVariantRepository.findByProductId(productId)
                .stream()
                .map(ProductVariant::getId)
                .toList();

        VerifyPurchaseResponse verifyResult = orderClient.verifyPurchase(
                new VerifyPurchaseRequest(userId, request.getOrderId(), variantIds)
        );

        if (!verifyResult.purchased()) {
            throw new AppException(ProductErrorCode.REVIEW_NOT_ELIGIBLE);
        }

        String verifiedOrderId = verifyResult.orderId();

        if (reviewRepository.existsByProductIdAndUserIdAndOrderId(productId, userId, verifiedOrderId)) {
            throw new AppException(ProductErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .product(product)
                .userId(userId)
                .orderId(verifiedOrderId)
                .rating(request.getRating())
                .comment(request.getComment() != null ? request.getComment().trim() : null)
                .verifiedPurchase(true)
                .status(ReviewStatus.VISIBLE)
                .fitRating(request.getFitRating())
                .customerHeight(request.getCustomerHeight())
                .customerWeight(request.getCustomerWeight())
                .mediaList(new ArrayList<>())
                .build();

        if (request.getMediaUrls() != null && !request.getMediaUrls().isEmpty()) {
            int order = 0;
            for (String url : request.getMediaUrls()) {
                if (url != null && !url.isBlank()) {
                    ReviewMedia media = ReviewMedia.builder()
                            .review(review)
                            .url(url.trim())
                            .mediaType("IMAGE")
                            .displayOrder(order++)
                            .build();
                    review.getMediaList().add(media);
                }
            }
        }

        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Review] Concurrent duplicate review attempt for product {} user {} order {}",
                    productId, userId, verifiedOrderId);
            throw new AppException(ProductErrorCode.REVIEW_ALREADY_EXISTS);
        }

        log.info("[Review] User {} created review {} for product {} in order {}",
                userId, review.getId(), productId, verifiedOrderId);

        return toResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReviewSummaryResponse getProductReviews(String productId, Integer ratingFilter, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        Page<Review> page = (ratingFilter != null && ratingFilter >= 1 && ratingFilter <= 5)
                ? reviewRepository.findByProductIdAndRatingAndStatus(productId, ratingFilter, ReviewStatus.VISIBLE, pageable)
                : reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.VISIBLE, pageable);

        Double avg = reviewRepository.getAverageRatingByProductId(productId);
        double averageRating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
        long totalReviews = reviewRepository.countByProductIdAndStatus(productId, ReviewStatus.VISIBLE);

        Map<Integer, Long> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingCounts.put(i, 0L);
        }
        List<Object[]> grouped = reviewRepository.countReviewsGroupByRating(productId);
        for (Object[] row : grouped) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                Integer r = ((Number) row[0]).intValue();
                Long count = ((Number) row[1]).longValue();
                ratingCounts.put(r, count);
            }
        }

        List<ReviewResponse> items = page.getContent().stream()
                .map(this::toPublicResponse)
                .toList();

        PageResponse<List<ReviewResponse>> pageResponse = PageResponse.<List<ReviewResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(page.getTotalPages())
                .items(items)
                .build();

        return ProductReviewSummaryResponse.builder()
                .averageRating(averageRating)
                .totalReviews(totalReviews)
                .ratingCounts(ratingCounts)
                .reviews(pageResponse)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ReviewResponse>> getAdminReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable) {
        Page<Review> page = reviewRepository.findAllForAdmin(keyword, rating, status, pageable);

        List<ReviewResponse> items = page.getContent().stream()
                .map(this::toAdminResponse)
                .toList();

        return PageResponse.<List<ReviewResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .items(items)
                .build();
    }

    @Override
    @Transactional
    public ReviewResponse updateReviewStatus(String reviewId, ReviewStatus status) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ProductErrorCode.REVIEW_NOT_FOUND));

        review.setStatus(status);
        review = reviewRepository.save(review);
        log.info("[Review] Updated review {} status to {}", reviewId, status);
        return toAdminResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse replyReview(String reviewId, String replyComment) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ProductErrorCode.REVIEW_NOT_FOUND));

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
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ProductErrorCode.REVIEW_NOT_FOUND));

        review.setReplyComment(null);
        review.setRepliedAt(null);
        review.setRepliedBy(null);
        review = reviewRepository.save(review);
        log.info("[Review] Deleted reply for review {}", reviewId);
        return toAdminResponse(review);
    }

    @Override
    @Transactional
    public void adminDeleteReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ProductErrorCode.REVIEW_NOT_FOUND));
        reviewRepository.delete(review);
        log.info("[Review] Admin deleted review {}", reviewId);
    }

    @Override
    @Transactional
    public void deleteReview(String productId, String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ProductErrorCode.REVIEW_NOT_FOUND));

        if (!review.getProduct().getId().equals(productId)) {
            throw new AppException(ProductErrorCode.REVIEW_NOT_FOUND);
        }

        String currentUserId = currentUserProvider.getCurrentUserId();
        boolean isAdmin = hasRole("ROLE_ADMIN") || hasRole("ADMIN");

        if (!review.getUserId().equals(currentUserId) && !isAdmin) {
            throw new AppException(ProductErrorCode.REVIEW_ACTION_FORBIDDEN);
        }

        reviewRepository.delete(review);
        log.info("[Review] Deleted review {} for product {}", reviewId, productId);
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(role));
    }

    private ReviewResponse toResponse(Review review) {
        List<String> mediaUrls = review.getMediaList() != null
                ? review.getMediaList().stream().map(ReviewMedia::getUrl).toList()
                : List.of();

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .productName(review.getProduct() != null ? review.getProduct().getName() : null)
                .userId(review.getUserId())
                .orderId(review.getOrderId())
                .rating(review.getRating())
                .comment(review.getComment())
                .verifiedPurchase(review.getVerifiedPurchase())
                .status(review.getStatus())
                .replyComment(review.getReplyComment())
                .repliedAt(review.getRepliedAt())
                .repliedBy(review.getRepliedBy())
                .fitRating(review.getFitRating())
                .customerHeight(review.getCustomerHeight())
                .customerWeight(review.getCustomerWeight())
                .mediaUrls(mediaUrls)
                .createdAt(review.getCreatedAt())
                .build();
    }

    private ReviewResponse toAdminResponse(Review review) {
        ReviewResponse res = toResponse(review);
        if (review.getUserId() != null) {
            identityUserClient.getUserById(review.getUserId()).ifPresent(user -> {
                res.setCustomerName(user.fullName());
                res.setCustomerEmail(user.email());
                res.setCustomerPhone(user.phone());
            });
        }
        return res;
    }

    private ReviewResponse toPublicResponse(Review review) {
        List<String> mediaUrls = review.getMediaList() != null
                ? review.getMediaList().stream().map(ReviewMedia::getUrl).toList()
                : List.of();

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .userId(maskUserId(review.getUserId()))
                .orderId(null)
                .rating(review.getRating())
                .comment(review.getComment())
                .verifiedPurchase(review.getVerifiedPurchase())
                .status(review.getStatus())
                .replyComment(review.getReplyComment())
                .repliedAt(review.getRepliedAt())
                .fitRating(review.getFitRating())
                .customerHeight(review.getCustomerHeight())
                .customerWeight(review.getCustomerWeight())
                .mediaUrls(mediaUrls)
                .createdAt(review.getCreatedAt())
                .build();
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "Anonymous";
        }
        int len = userId.length();
        if (len <= 2) {
            return userId.charAt(0) + "***";
        }
        return userId.charAt(0) + "***" + userId.charAt(len - 1);
    }
}

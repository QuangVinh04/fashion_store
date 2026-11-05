package com.fashionstore.catalog.review.mapper;

import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewMedia;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewMapper {

    public static final String ANONYMOUS_REVIEWER = "Khách hàng";
    private static final String MASK = "***";

    /** Góc nhìn chủ đánh giá và admin: đủ orderId, userId, tên thật. */
    public ReviewResponse toResponse(Review review, String productName) {
        return baseResponse(review)
                .productName(productName)
                .userId(review.getUserId())
                .customerName(review.getReviewerName())
                .orderId(review.getOrderId())
                .repliedBy(review.getRepliedBy())
                .build();
    }

    /** Trang sản phẩm công khai: không lộ orderId, người trả lời, userId và tên đầy đủ. */
    public ReviewResponse toPublicResponse(Review review) {
        return baseResponse(review)
                .userId(mask(review.getUserId(), "Anonymous"))
                .customerName(maskName(review.getReviewerName()))
                .build();
    }

    public static String maskName(String name) {
        return mask(name == null ? null : name.trim(), ANONYMOUS_REVIEWER);
    }

    private static String mask(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if (value.length() == 1) {
            return value + MASK;
        }
        return value.charAt(0) + MASK + value.charAt(value.length() - 1);
    }

    private ReviewResponse.ReviewResponseBuilder baseResponse(Review review) {
        List<String> mediaUrls = review.getMediaList().stream().map(ReviewMedia::getUrl).toList();
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
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
                .updatedAt(review.getUpdatedAt())
                .editedAt(review.getEditedAt());
    }
}

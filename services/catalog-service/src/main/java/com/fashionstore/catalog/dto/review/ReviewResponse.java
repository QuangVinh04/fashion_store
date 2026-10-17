package com.fashionstore.catalog.dto.review;

import com.fashionstore.catalog.entity.enumeration.FitRating;
import com.fashionstore.catalog.entity.enumeration.ReviewStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewResponse {

    String id;
    String productId;
    String productName;
    String userId;
    String customerName;
    String customerEmail;
    String customerPhone;
    String orderId;
    Integer rating;
    String comment;
    Boolean verifiedPurchase;
    ReviewStatus status;
    String replyComment;
    LocalDateTime repliedAt;
    String repliedBy;
    FitRating fitRating;
    Integer customerHeight;
    Integer customerWeight;
    List<String> mediaUrls;
    LocalDateTime createdAt;
}

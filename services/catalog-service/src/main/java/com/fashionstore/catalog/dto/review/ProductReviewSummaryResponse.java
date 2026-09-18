package com.fashionstore.catalog.dto.review;

import com.fashionstore.common.dto.PageResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductReviewSummaryResponse {

    Double averageRating;
    Long totalReviews;
    Map<Integer, Long> ratingCounts;
    PageResponse<List<ReviewResponse>> reviews;
}

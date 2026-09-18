package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.review.CreateReviewRequest;
import com.fashionstore.catalog.dto.review.ProductReviewSummaryResponse;
import com.fashionstore.catalog.dto.review.ReviewResponse;
import com.fashionstore.catalog.service.ReviewService;
import com.fashionstore.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/v1/products/{id}/reviews")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Review", description = "Quản lý đánh giá sản phẩm (Product Review)")
public class ReviewController {

    ReviewService reviewService;

    @Operation(summary = "Đánh giá sản phẩm",
            description = "Chỉ cho phép khách hàng đã mua và nhận sản phẩm thành công (DELIVERED) đánh giá. Mỗi đơn hàng chỉ được đánh giá 1 lần.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable("id") String productId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Review created successfully")
                .data(reviewService.createReview(productId, request))
                .build();
    }

    @Operation(summary = "Lấy danh sách đánh giá của sản phẩm",
            description = "Công khai. Trả về thống kê đánh giá trung bình, phân bố số sao (1-5 sao) và danh sách reviews phân trang.")
    @GetMapping
    public ApiResponse<ProductReviewSummaryResponse> getProductReviews(
            @PathVariable("id") String productId,
            @Parameter(description = "Lọc theo số sao đánh giá (1-5)")
            @RequestParam(required = false)
            @jakarta.validation.constraints.Min(value = 1, message = "Rating must be at least 1")
            @jakarta.validation.constraints.Max(value = 5, message = "Rating must be at most 5")
            Integer rating,
            @ParameterObject
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new com.fashionstore.common.exception.AppException(com.fashionstore.common.exception.ErrorCode.VALIDATION_FAILED);
        }
        return ApiResponse.<ProductReviewSummaryResponse>builder()
                .message("Get product reviews successfully")
                .data(reviewService.getProductReviews(productId, rating, pageable))
                .build();
    }

    @Operation(summary = "Xóa đánh giá",
            description = "Chính chủ đánh giá hoặc ADMIN mới có quyền xóa.")
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @PathVariable("id") String productId,
            @PathVariable("reviewId") String reviewId
    ) {
        reviewService.deleteReview(productId, reviewId);
        return ApiResponse.<Void>builder()
                .message("Review deleted successfully")
                .build();
    }
}

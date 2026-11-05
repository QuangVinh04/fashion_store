package com.fashionstore.catalog.review.controller;

import com.fashionstore.catalog.review.dto.CreateReviewRequest;
import com.fashionstore.catalog.review.dto.ProductReviewSummaryResponse;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.dto.UpdateReviewRequest;
import com.fashionstore.catalog.review.service.ReviewService;
import com.fashionstore.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{id}/reviews")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Review", description = "Đánh giá sản phẩm: xem công khai, khách đã mua viết / sửa / xoá đánh giá của mình")
public class ReviewController {

    ReviewService reviewService;

    @Operation(summary = "Đánh giá sản phẩm",
            description = "Chỉ khách đã nhận hàng (đơn `DELIVERED` / `COMPLETED`) mới đánh giá được; mỗi sản phẩm một lần mỗi đơn. "
                    + "`mediaIds`: ảnh upload qua `POST /api/v1/files/reviews/presign` + `/complete` của chính khách. "
                    + "Mã lỗi: `1003` (404) sản phẩm không tồn tại, `1036` (409) đã đánh giá, `1037` (400) chưa mua, "
                    + "`6001` (404) / `6002` (400) ảnh không hợp lệ.")
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

    @Operation(summary = "Sửa đánh giá của mình",
            description = "Thay nguyên nội dung: trường bỏ trống là xoá (ảnh, độ vừa, số đo). Đánh giá đang bị ẩn vẫn ẩn. "
                    + "Mã lỗi: `1035` (404), `1038` (403) không phải đánh giá của mình.")
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable("id") String productId,
            @PathVariable("reviewId") String reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Review updated successfully")
                .data(reviewService.updateReview(productId, reviewId, request))
                .build();
    }

    @Operation(summary = "Lấy danh sách đánh giá của sản phẩm",
            description = "Công khai. Điểm trung bình, phân bố 1–5 sao và danh sách đánh giá đang hiện. "
                    + "`customerName` và `userId` đã che bớt.")
    @GetMapping
    public ApiResponse<ProductReviewSummaryResponse> getProductReviews(
            @PathVariable("id") String productId,
            @Parameter(description = "Lọc theo số sao đánh giá (1-5)")
            @RequestParam(required = false)
            @Min(value = 1, message = "Rating must be at least 1")
            @Max(value = 5, message = "Rating must be at most 5")
            Integer rating,
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<ProductReviewSummaryResponse>builder()
                .message("Get product reviews successfully")
                .data(reviewService.getProductReviews(productId, rating, pageable))
                .build();
    }

    @Operation(summary = "Xoá đánh giá của mình",
            description = "Chỉ chủ đánh giá. Nhân viên xoá qua `DELETE /api/v1/admin/reviews/{id}`. Mã lỗi: `1035` (404), `1038` (403).")
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

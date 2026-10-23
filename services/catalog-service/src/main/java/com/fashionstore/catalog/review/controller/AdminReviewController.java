package com.fashionstore.catalog.review.controller;

import com.fashionstore.catalog.review.dto.ReplyReviewRequest;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.dto.UpdateReviewStatusRequest;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.review.service.ReviewService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin Review", description = "Quản lý đánh giá sản phẩm dành cho quản trị viên")
public class AdminReviewController {

    ReviewService reviewService;

    @Operation(summary = "Lấy danh sách đánh giá toàn hệ thống dành cho admin")
    @GetMapping
    public ApiResponse<PageResponse<List<ReviewResponse>>> list(
            @Parameter(description = "Từ khóa tìm kiếm theo tên sản phẩm, tên khách hoặc nội dung")
            @RequestParam(required = false)
            String keyword,
            @Parameter(description = "Lọc theo số sao đánh giá (1-5)")
            @RequestParam(required = false)
            @Min(value = 1, message = "Rating must be at least 1")
            @Max(value = 5, message = "Rating must be at most 5")
            Integer rating,
            @Parameter(description = "Lọc theo trạng thái kiểm duyệt (VISIBLE, HIDDEN)")
            @RequestParam(required = false)
            ReviewStatus status,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        return ApiResponse.<PageResponse<List<ReviewResponse>>>builder()
                .message("Get admin reviews successfully")
                .data(reviewService.getAdminReviews(keyword, rating, status, pageable))
                .build();
    }

    @Operation(summary = "Cập nhật trạng thái kiểm duyệt (Ẩn / Hiện)")
    @PatchMapping("/{id}/status")
    public ApiResponse<ReviewResponse> updateStatus(
            @PathVariable("id") String reviewId,
            @Valid @RequestBody UpdateReviewStatusRequest request
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Review status updated successfully")
                .data(reviewService.updateReviewStatus(reviewId, request.getStatus()))
                .build();
    }

    @Operation(summary = "Gửi hoặc cập nhật phản hồi của Shop cho đánh giá")
    @PostMapping("/{id}/reply")
    public ApiResponse<ReviewResponse> reply(
            @PathVariable("id") String reviewId,
            @Valid @RequestBody ReplyReviewRequest request
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Reply posted successfully")
                .data(reviewService.replyReview(reviewId, request.getReply()))
                .build();
    }

    @Operation(summary = "Xóa phản hồi của Shop")
    @DeleteMapping("/{id}/reply")
    public ApiResponse<ReviewResponse> deleteReply(
            @PathVariable("id") String reviewId
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Reply deleted successfully")
                .data(reviewService.deleteReply(reviewId))
                .build();
    }

    @Operation(summary = "Xóa đánh giá vĩnh viễn (Admin)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteReview(
            @PathVariable("id") String reviewId
    ) {
        reviewService.adminDeleteReview(reviewId);
        return ApiResponse.<Void>builder()
                .message("Review deleted successfully by admin")
                .build();
    }
}

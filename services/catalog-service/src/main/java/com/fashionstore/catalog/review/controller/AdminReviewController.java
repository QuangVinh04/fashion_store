package com.fashionstore.catalog.review.controller;

import com.fashionstore.catalog.review.dto.ReplyReviewRequest;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.dto.UpdateReviewStatusRequest;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.review.service.AdminReviewService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.security.PermissionNames;
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

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin Review", description = "Quản lý đánh giá sản phẩm dành cho quản trị viên")
public class AdminReviewController {

    AdminReviewService adminReviewService;

    @Operation(summary = "Lấy danh sách đánh giá toàn hệ thống dành cho admin")
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionNames.REVIEW_READ + "')")
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
        return ApiResponse.<PageResponse<List<ReviewResponse>>>builder()
                .message("Get admin reviews successfully")
                .data(adminReviewService.getReviews(keyword, rating, status, pageable))
                .build();
    }

    @Operation(summary = "Cập nhật trạng thái kiểm duyệt (Ẩn / Hiện)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionNames.REVIEW_MANAGE + "')")
    public ApiResponse<ReviewResponse> updateStatus(
            @PathVariable("id") String reviewId,
            @Valid @RequestBody UpdateReviewStatusRequest request
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Review status updated successfully")
                .data(adminReviewService.updateStatus(reviewId, request.getStatus()))
                .build();
    }

    @Operation(summary = "Gửi hoặc cập nhật phản hồi của Shop cho đánh giá")
    @PostMapping("/{id}/reply")
    @PreAuthorize("hasAuthority('" + PermissionNames.REVIEW_MANAGE + "')")
    public ApiResponse<ReviewResponse> reply(
            @PathVariable("id") String reviewId,
            @Valid @RequestBody ReplyReviewRequest request
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Reply posted successfully")
                .data(adminReviewService.reply(reviewId, request.getReply()))
                .build();
    }

    @Operation(summary = "Xóa phản hồi của Shop")
    @DeleteMapping("/{id}/reply")
    @PreAuthorize("hasAuthority('" + PermissionNames.REVIEW_MANAGE + "')")
    public ApiResponse<ReviewResponse> deleteReply(
            @PathVariable("id") String reviewId
    ) {
        return ApiResponse.<ReviewResponse>builder()
                .message("Reply deleted successfully")
                .data(adminReviewService.deleteReply(reviewId))
                .build();
    }

    @Operation(summary = "Xóa đánh giá vĩnh viễn (Admin)", description = "Muốn giữ dấu vết thì ẩn (`PATCH /{id}/status`) thay vì xoá.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionNames.REVIEW_MANAGE + "')")
    public ApiResponse<Void> deleteReview(
            @PathVariable("id") String reviewId
    ) {
        adminReviewService.delete(reviewId);
        return ApiResponse.<Void>builder()
                .message("Review deleted successfully by admin")
                .build();
    }
}

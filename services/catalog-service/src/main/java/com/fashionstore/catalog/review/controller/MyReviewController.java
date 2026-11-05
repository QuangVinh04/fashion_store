package com.fashionstore.catalog.review.controller;

import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.service.ReviewService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews/me")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Review", description = "Đánh giá sản phẩm: xem công khai, khách đã mua viết / sửa / xoá đánh giá của mình")
public class MyReviewController {

    ReviewService reviewService;

    @Operation(summary = "Đánh giá của tôi",
            description = "Gồm cả đánh giá đang bị ẩn (`status=HIDDEN`). Truyền `orderId` để biết sản phẩm nào trong đơn đã được đánh giá.")
    @GetMapping
    public ApiResponse<PageResponse<List<ReviewResponse>>> getMyReviews(
            @Parameter(description = "Chỉ lấy đánh giá của đơn này") @RequestParam(required = false) String orderId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<ReviewResponse>>>builder()
                .message("Get my reviews successfully")
                .data(reviewService.getMyReviews(orderId, pageable))
                .build();
    }
}

package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.wishlist.WishlistCheckResponse;
import com.fashionstore.catalog.dto.wishlist.WishlistItemResponse;
import com.fashionstore.catalog.service.WishlistService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Wishlist", description = "Quản lý danh sách sản phẩm yêu thích (Wishlist)")
public class WishlistController {

    WishlistService wishlistService;

    @Operation(summary = "Thêm sản phẩm vào danh sách yêu thích",
            description = "Yêu cầu đăng nhập. Thao tác idempotent: nếu sản phẩm đã có trong danh sách thì vẫn trả về thành công.")
    @PostMapping("/{productId}")
    public org.springframework.http.ResponseEntity<ApiResponse<WishlistItemResponse>> addToWishlist(@PathVariable("productId") String productId) {
        com.fashionstore.catalog.dto.wishlist.WishlistAddResult result = wishlistService.addToWishlist(productId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "Product added to wishlist successfully" : "Product already in wishlist";
        return org.springframework.http.ResponseEntity.status(status).body(
                ApiResponse.<WishlistItemResponse>builder()
                        .message(message)
                        .data(result.item())
                        .build()
        );
    }

    @Operation(summary = "Xóa sản phẩm khỏi danh sách yêu thích",
            description = "Yêu cầu đăng nhập. Thao tác idempotent.")
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> removeFromWishlist(@PathVariable("productId") String productId) {
        wishlistService.removeFromWishlist(productId);
        return ApiResponse.<Void>builder()
                .message("Product removed from wishlist successfully")
                .build();
    }

    @Operation(summary = "Lấy danh sách sản phẩm yêu thích của tôi",
            description = "Yêu cầu đăng nhập. Trả về danh sách sản phẩm yêu thích phân trang.")
    @GetMapping
    public ApiResponse<PageResponse<List<WishlistItemResponse>>> getMyWishlist(
            @ParameterObject
            @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<WishlistItemResponse>>>builder()
                .message("Get wishlist successfully")
                .data(wishlistService.getMyWishlist(pageable))
                .build();
    }

    @Operation(summary = "Kiểm tra sản phẩm đã nằm trong danh sách yêu thích chưa")
    @GetMapping("/check/{productId}")
    public ApiResponse<WishlistCheckResponse> checkInWishlist(@PathVariable("productId") String productId) {
        return ApiResponse.<WishlistCheckResponse>builder()
                .message("Check wishlist status completed")
                .data(wishlistService.checkInWishlist(productId))
                .build();
    }
}

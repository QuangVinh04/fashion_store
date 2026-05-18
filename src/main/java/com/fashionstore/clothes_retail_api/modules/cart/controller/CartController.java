package com.fashionstore.clothes_retail_api.modules.cart.controller;

import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.modules.cart.dto.AddToCartRequest;
import com.fashionstore.clothes_retail_api.modules.cart.dto.CartResponse;
import com.fashionstore.clothes_retail_api.modules.cart.dto.UpdateCartRequest;
import com.fashionstore.clothes_retail_api.modules.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartController {

    CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getMyCart() {
        return ApiResponse.<CartResponse>builder()
                .message("Lấy giỏ hàng thành công")
                .data(cartService.getMyCart())
                .build();
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addToCart(@Valid @RequestBody AddToCartRequest request) {
        return ApiResponse.<CartResponse>builder()
                .message("Thêm vào giỏ hàng thành công")
                .data(cartService.addToCart(request))
                .build();
    }

    @PutMapping("/items/{id}")
    public ApiResponse<CartResponse> updateCartItem(@PathVariable("id") String id,
                                                    @Valid @RequestBody UpdateCartRequest request) {
        return ApiResponse.<CartResponse>builder()
                .message("Cập nhật giỏ hàng thành công")
                .data(cartService.updateCartItem(id, request))
                .build();
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> removeCartItem(@PathVariable("id") String id) {
        cartService.removeCartItem(id);
        return ApiResponse.<Void>builder()
                .message("Xóa sản phẩm khỏi giỏ hàng thành công")
                .build();
    }

    @DeleteMapping
    public ApiResponse<Void> clearCart() {
        cartService.clearCart();
        return ApiResponse.<Void>builder()
                .message("Xóa toàn bộ giỏ hàng thành công")
                .build();
    }
}

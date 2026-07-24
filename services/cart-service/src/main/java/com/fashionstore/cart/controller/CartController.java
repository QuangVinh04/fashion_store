package com.fashionstore.cart.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.cart.dto.cart.AddToCartRequest;
import com.fashionstore.cart.dto.cart.CartResponse;
import com.fashionstore.cart.dto.cart.UpdateCartRequest;
import com.fashionstore.cart.service.CartService;
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
                .message("Lay gio hang thanh cong")
                .data(cartService.getMyCart())
                .build();
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addToCart(@Valid @RequestBody AddToCartRequest request) {
        return ApiResponse.<CartResponse>builder()
                .message("Them vao gio hang thanh cong")
                .data(cartService.addToCart(request))
                .build();
    }

    @PutMapping("/items/{id}")
    public ApiResponse<CartResponse> updateCartItem(@PathVariable String id,
                                                    @Valid @RequestBody UpdateCartRequest request) {
        return ApiResponse.<CartResponse>builder()
                .message("Cap nhat gio hang thanh cong")
                .data(cartService.updateCartItem(id, request))
                .build();
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<CartResponse> removeCartItem(@PathVariable String id) {
        return ApiResponse.<CartResponse>builder()
                .message("Xoa san pham khoi gio hang thanh cong")
                .data(cartService.removeCartItem(id))
                .build();
    }

    @DeleteMapping
    public ApiResponse<CartResponse> clearCart() {
        return ApiResponse.<CartResponse>builder()
                .message("Xoa toan bo gio hang thanh cong")
                .data(cartService.clearCart())
                .build();
    }
}

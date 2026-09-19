package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.MergeCartInternalRequest;
import com.fashionstore.order.service.CartService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cart")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalCartController {

    CartService cartService;

    @PostMapping("/merge")
    public ApiResponse<CartResponse> mergeCart(@Valid @RequestBody MergeCartInternalRequest request) {
        CartResponse response = cartService.mergeCartForUser(request.getUserId(), request.getAnonymousId());
        return ApiResponse.<CartResponse>builder()
                .message("Merge cart successfully")
                .data(response)
                .build();
    }
}

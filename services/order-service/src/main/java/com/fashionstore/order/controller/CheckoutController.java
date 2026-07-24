package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.dto.UpdateCheckoutRequest;
import com.fashionstore.order.service.CheckoutService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkouts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutController {

    CheckoutService checkoutService;

    @PostMapping
    public ApiResponse<CheckoutResponse> createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Create checkout successfully")
                .data(checkoutService.createCheckout(request))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CheckoutResponse> getCheckoutById(@PathVariable("id") String id) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Get checkout successfully")
                .data(checkoutService.getCheckoutById(id))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<CheckoutResponse> updateCheckout(@PathVariable("id") String id,
                                                        @RequestBody UpdateCheckoutRequest request) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Update checkout successfully")
                .data(checkoutService.updateCheckout(id, request))
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<List<CheckoutResponse>> getMyCheckouts() {
        return ApiResponse.<List<CheckoutResponse>>builder()
                .message("Get my checkouts successfully")
                .data(checkoutService.getMyCheckouts())
                .build();
    }
}

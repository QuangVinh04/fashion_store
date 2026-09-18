package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import com.fashionstore.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Order", description = "Các API nội bộ phục vụ giao tiếp giữa các microservices")
public class InternalOrderController {

    OrderService orderService;

    @Operation(summary = "Xác thực khách hàng đã mua và nhận sản phẩm thành công (dùng cho Review)")
    @PostMapping("/verify-purchase")
    public ApiResponse<VerifyPurchaseResponse> verifyPurchase(@RequestBody VerifyPurchaseRequest request) {
        return ApiResponse.<VerifyPurchaseResponse>builder()
                .message("Verify purchase status completed")
                .data(orderService.verifyPurchase(request))
                .build();
    }
}

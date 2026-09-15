package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.PromotionPreviewResponse;
import com.fashionstore.order.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "Customer - Promotions", description = "Kiểm tra mã khuyến mãi cho khách hàng")
@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionController {

    PromotionService promotionService;

    @Operation(summary = "Kiểm tra tính hợp lệ và xem trước số tiền giảm của mã voucher",
            description = "Dùng cho giao diện trước khi đặt hàng. Trả về valid=true/false và discountAmount.")
    @GetMapping("/validate")
    public ApiResponse<PromotionPreviewResponse> validatePromotion(
            @Parameter(description = "Mã khuyến mãi") @RequestParam String code,
            @Parameter(description = "Tạm tính đơn hàng (subtotal)") @RequestParam(required = false) BigDecimal subtotal
    ) {
        BigDecimal effectiveSubtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        return ApiResponse.<PromotionPreviewResponse>builder()
                .message("Validate promotion successfully")
                .data(promotionService.validatePromotionForCustomer(code, effectiveSubtotal))
                .build();
    }
}

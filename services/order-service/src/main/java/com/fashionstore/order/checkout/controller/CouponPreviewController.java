package com.fashionstore.order.checkout.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.checkout.service.CheckoutService;
import com.fashionstore.order.promotion.dto.AvailablePromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionPreviewResponse;
import com.fashionstore.order.promotion.dto.PromotionTarget;
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
import java.util.List;

/** Lives in the checkout feature because the preview needs the cart, and promotion must not depend on cart. */
@Tag(name = "Customer - Promotions", description = "Kiểm tra mã khuyến mãi cho khách hàng")
@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CouponPreviewController {

    CheckoutService checkoutService;

    @Operation(summary = "Kiểm tra tính hợp lệ và xem trước số tiền giảm của mã voucher",
            description = """
                    Dùng cho giao diện trước khi đặt hàng. Mã được tính trên **giỏ hàng đang hoạt động** của
                    người dùng hiện tại (phạm vi sản phẩm/danh mục và hạn mức mỗi người được kiểm tra giống checkout),
                    trả về valid=true/false và discountAmount.""")
    @GetMapping("/validate")
    public ApiResponse<PromotionPreviewResponse> validatePromotion(
            @Parameter(description = "Mã khuyến mãi") @RequestParam String code,
            @Parameter(description = "Không còn được dùng: tạm tính lấy từ giỏ hàng.", deprecated = true)
            @RequestParam(required = false) BigDecimal subtotal
    ) {
        return ApiResponse.<PromotionPreviewResponse>builder()
                .message("Validate promotion successfully")
                .data(checkoutService.previewCoupon(code))
                .build();
    }

    @Operation(summary = "Danh sách mã khuyến mãi công khai cho giỏ hàng hiện tại",
            description = """
                    Chỉ gồm mã đang hoạt động, đang trong hạn, đánh dấu công khai và còn lượt (tổng và của bạn).
                    Mỗi mã có `eligible`: false kèm `reasonCode`/`reason` khi giỏ hiện tại chưa dùng được
                    (chưa đủ giá trị tối thiểu hoặc không có sản phẩm thuộc phạm vi). `estimatedDiscount` chỉ có
                    với mã sản phẩm; mã vận chuyển phụ thuộc phí ship nên chỉ biết sau khi áp vào checkout.
                    `target` bỏ trống thì trả cả hai loại.""")
    @GetMapping("/available")
    public ApiResponse<List<AvailablePromotionResponse>> availablePromotions(
            @Parameter(description = "PRODUCT hoặc SHIPPING; bỏ trống = tất cả") @RequestParam(required = false) PromotionTarget target
    ) {
        return ApiResponse.<List<AvailablePromotionResponse>>builder()
                .message("Get available promotions successfully")
                .data(checkoutService.availableCoupons(target))
                .build();
    }
}

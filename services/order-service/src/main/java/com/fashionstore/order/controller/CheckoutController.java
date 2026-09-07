package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.dto.UpdateCheckoutRequest;
import com.fashionstore.order.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Checkout", description = """
        Bản chụp giá của một giỏ hàng tại một thời điểm, sống 30 phút (`app.checkout.ttl-minutes`).
        Hết hạn thì scanner chuyển sang EXPIRED và không đặt được đơn từ nó nữa.""")
@RestController
@RequestMapping("/api/v1/checkouts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutController {

    CheckoutService checkoutService;

    @Operation(
            summary = "Mở checkout từ giỏ hàng hiện tại",
            description = """
                    Trước khi chụp giá, toàn bộ giỏ được xác nhận lại với catalog: variant còn bán không, giá
                    hiện tại bao nhiêu, kho còn đủ không. Giá đổi từ lúc bỏ vào giỏ thì **lấy giá mới** và ghi
                    lại vào giỏ — nên tổng tiền ở đây có thể khác trang giỏ hàng, hãy hiển thị lại theo response này.

                    Phí ship: 25.000 (STANDARD) / 40.000 (EXPRESS), miễn phí khi subtotal ≥ 500.000.
                    Coupon: chỉ `WELCOME10` (10%, tối đa 50.000).

                    Mã lỗi: `3002` giỏ trống (400) · `1004` (404) · `1006` (409) · `1005` (400) ·
                    `3003` dòng giỏ cũ thiếu snapshot, cần thêm lại (409) · `9006` (502) ·
                    `5002` payment provider không hỗ trợ (415) · `4005` số tiền không hợp lệ (400).""")
    @PostMapping
    public ApiResponse<CheckoutResponse> createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Create checkout successfully")
                .data(checkoutService.createCheckout(request))
                .build();
    }

    @Operation(summary = "Xem một checkout của mình",
            description = "Mã lỗi: `4003` không tìm thấy checkout của user này (404).")
    @GetMapping("/{id}")
    public ApiResponse<CheckoutResponse> getCheckoutById(@PathVariable("id") String id) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Get checkout successfully")
                .data(checkoutService.getCheckoutById(id))
                .build();
    }

    @Operation(
            summary = "Đổi phương thức thanh toán / vận chuyển / coupon",
            description = """
                    Chỉ các field gửi lên mới bị thay đổi; phí ship và giảm giá được tính lại theo subtotal đã
                    chụp (subtotal không đổi — muốn đổi hàng thì sửa giỏ rồi mở checkout mới).

                    Mã lỗi: `4003` (404) · `4004` checkout đã COMPLETED/CANCELLED/EXPIRED (400) ·
                    `5002` (415) · `4005` (400).""")
    @PutMapping("/{id}")
    public ApiResponse<CheckoutResponse> updateCheckout(@PathVariable("id") String id,
                                                        @RequestBody UpdateCheckoutRequest request) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Update checkout successfully")
                .data(checkoutService.updateCheckout(id, request))
                .build();
    }

    @Operation(
            summary = "Huỷ một checkout chưa sinh đơn",
            description = """
                    Idempotent: huỷ hai lần trả về cùng kết quả. Checkout đã sinh đơn thì việc huỷ thuộc về
                    đơn (`POST /api/v1/orders/{id}/cancel`), không thuộc về checkout.

                    Mã lỗi: `4003` (404) · `4004` đã COMPLETED hoặc đã có đơn (400).""")
    @PostMapping("/{id}/cancel")
    public ApiResponse<CheckoutResponse> cancelCheckout(@PathVariable("id") String id) {
        return ApiResponse.<CheckoutResponse>builder()
                .message("Cancel checkout successfully")
                .data(checkoutService.cancelCheckout(id))
                .build();
    }

    @Operation(summary = "Danh sách checkout của mình", description = "Mới nhất trước, không phân trang.")
    @GetMapping("/me")
    public ApiResponse<List<CheckoutResponse>> getMyCheckouts() {
        return ApiResponse.<List<CheckoutResponse>>builder()
                .message("Get my checkouts successfully")
                .data(checkoutService.getMyCheckouts())
                .build();
    }
}

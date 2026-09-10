package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Order", description = """
        Đơn hàng của người dùng đang đăng nhập. Đơn mới nằm ở `PENDING` trong lúc saga giữ kho + thu tiền,
        chỉ sang `CONFIRMED` khi saga đóng thành công — nên các endpoint ghi ở đây trả về "đã nhận yêu cầu",
        không phải "đã hoàn tất".""")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {

    OrderService orderService;

    @Operation(
            summary = "Đặt hàng từ một checkout",
            description = """
                    Chốt checkout thành đơn (`PENDING`), mở saga: giữ kho → thu tiền → chốt kho. Đơn xác nhận
                    xong thì các dòng đã mua tự rời khỏi giỏ hàng.

                    Một checkout chỉ sinh được đúng một đơn; gọi lại trả về đơn đã có thay vì lỗi.

                    Mã lỗi: `4003` không tìm thấy checkout (404) · `4004` checkout đã CANCELLED/EXPIRED (400).""")
    @PostMapping("/{checkoutId}")
    public ApiResponse<OrderResponse> createOrder(@PathVariable String checkoutId,
                                                 @Parameter(description = """
                                                         Khoá chống tạo trùng. Gửi lại cùng một key trả về đúng
                                                         đơn đã tạo, không tạo đơn thứ hai. Bỏ trống thì
                                                         `checkoutId` được dùng làm khoá.""")
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Place order successfully")
                .data(orderService.createOrder(checkoutId, idempotencyKey, request))
                .build();
    }

    @Operation(summary = "Danh sách đơn của mình",
            description = "Mặc định 10 đơn/trang, mới nhất trước. Lọc theo `status` nếu cần.")
    @GetMapping
    public ApiResponse<PageResponse<List<OrderSummaryResponse>>> getMyOrders(
            @Parameter(description = "Lọc theo trạng thái đơn; bỏ trống là lấy tất cả")
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<OrderSummaryResponse>>>builder()
                .message("Get my orders successfully")
                .data(orderService.getMyOrders(status, pageable))
                .build();
    }

    @Operation(summary = "Xem một đơn của mình",
            description = "Đơn của user khác cũng trả `4001` (404) — không tiết lộ sự tồn tại của đơn.")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getMyOrderById(@PathVariable("id") String id) {
        return ApiResponse.<OrderResponse>builder()
                .message("Get order successfully")
                .data(orderService.getMyOrderById(id))
                .build();
    }

    /**
     * Đơn còn trong saga thì đây là yêu cầu bù trừ: response có thể vẫn là PENDING, đơn chỉ về CANCELLED
     * sau khi kho được nhả và tiền được hủy.
     */
    @Operation(
            summary = "Khách tự huỷ đơn",
            description = """
                    Chỉ huỷ được khi đơn còn `PENDING`, và đây là **yêu cầu bù trừ**: response có thể vẫn là
                    `PENDING`, đơn chỉ về `CANCELLED` sau khi kho được nhả và tiền được huỷ. Nếu tiền đã thu
                    và không huỷ được ở cổng thanh toán thì saga đi tiếp và đơn vẫn thành `CONFIRMED` —
                    lúc đó là chuyện hoàn tiền, không phải huỷ. Huỷ hai lần trả cùng kết quả.

                    Mã lỗi: `4001` (404) · `4006` đơn đã qua PENDING, không huỷ được nữa (409).""")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelMyOrder(@PathVariable("id") String id,
                                                    @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Cancel order request accepted")
                .data(orderService.cancelMyOrder(id, request))
                .build();
    }

    /**
     * Chỉ nhận cho đơn đã DELIVERED. Tiền chưa được hoàn ngay ở đây — admin xác nhận hoàn tiền riêng
     * qua {@code PUT /{id}/status} với {@code REFUNDED}.
     */
    @Operation(
            summary = "Khách yêu cầu trả hàng",
            description = """
                    Chỉ nhận cho đơn đã `DELIVERED`; trước đó dùng đường huỷ đơn. Đơn chuyển `RETURNED`, tiền
                    chưa hoàn ở bước này — admin xác nhận hoàn tiền qua `PUT /{id}/status` với `REFUNDED`.

                    Mã lỗi: `4001` (404) · `4008` đơn chưa DELIVERED (409).""")
    @PostMapping("/{id}/return-request")
    public ApiResponse<OrderResponse> requestReturn(@PathVariable("id") String id,
                                                    @Valid @RequestBody(required = false) ReturnOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Return request accepted")
                .data(orderService.requestReturn(id, request))
                .build();
    }

    @Operation(
            summary = "Cập nhật trạng thái đơn (ADMIN)",
            description = """
                    Yêu cầu role `ADMIN`. Chỉ đi theo đúng chuỗi vận hành:
                    `CONFIRMED → PROCESSING → PACKED → SHIPPING → DELIVERED → COMPLETED`, và
                    `DELIVERED → RETURNED → REFUNDED`. Đơn `PENDING` đang trong saga, admin không can thiệp giữa chừng.

                    `REFUNDED` không set trực tiếp: nó phát lệnh hoàn tiền cho payment-service, đơn giữ
                    `RETURNED` cho tới khi có xác nhận `payment.refunded` về.

                    Mã lỗi: `4001` (404) · `4002` chuyển trạng thái không hợp lệ (400).""")
    @PutMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateOrderStatus(@PathVariable("id") String id,
                                                        @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Update order status successfully")
                .data(orderService.updateOrderStatus(id, request))
                .build();
    }
}

package com.fashionstore.order.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.order.dto.OrderResponse;
import com.fashionstore.order.saga.dto.OrderSagaResponse;
import com.fashionstore.order.order.dto.OrderStatusHistoryResponse;
import com.fashionstore.order.order.dto.OrderSummaryResponse;
import com.fashionstore.order.saga.entity.OrderSagaType;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.service.OrderService;
import com.fashionstore.order.returns.service.ReturnService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Khu vực vận hành. Phân quyền ADMIN nằm ở tầng service ({@code @PreAuthorize}) để logic và luật
 * truy cập đi cùng nhau, controller chỉ định tuyến.
 */
@Tag(
        name = "Admin - Orders",
        description =
                """
Khu vực vận hành, mọi endpoint yêu cầu role `ADMIN` (kiểm ở tầng service bằng `@PreAuthorize`).
Token thiếu quyền nhận 403.""")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminOrderController {

    OrderService orderService;
    ReturnService returnService;
    com.fashionstore.order.shipment.service.ShipmentService shipmentService;
    com.fashionstore.order.saga.service.OrderCancellationService orderCancellationService;
    com.fashionstore.order.saga.service.OrderSagaTimeoutService orderSagaTimeoutService;

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody com.fashionstore.order.saga.dto.CancelOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .data(orderCancellationService.requestAdmin(orderId, request))
                .build();
    }

    @PostMapping("/{orderId}/cancellation/retry")
    public ApiResponse<OrderResponse> retryCancellation(
            @PathVariable String orderId,
            @Valid @RequestBody com.fashionstore.order.saga.dto.CancelOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .data(orderCancellationService.retryAdmin(orderId, request))
                .build();
    }

    @Operation(
            summary = "Thử lại saga checkout đã FAILED (ADMIN)",
            description =
                    """
Saga checkout FAILED nghĩa là một bước bù trừ hoặc chốt kho đã cạn lần thử hoặc bị từ chối, đơn vẫn
`PENDING` và tiền/kho có thể đang treo. Endpoint đặt lại bộ đếm, đưa saga về đúng bước đang kẹt và
phát lại command của bước đó để luồng bù trừ chạy tiếp. Admin cần kiểm tra nguyên nhân
(`GET /{orderId}/saga`) trước: nếu lỗi do dữ liệu thì retry sẽ lại FAILED.

Mã lỗi: `4001` (404) · `4007` đơn không có saga checkout (404) · `4034` saga không ở trạng thái FAILED
hoặc bước hiện tại không có command để phát lại (409).""")
    @PostMapping("/{orderId}/saga/retry")
    public ApiResponse<OrderSagaResponse> retryCheckoutSaga(@PathVariable String orderId) {
        orderSagaTimeoutService.retryFailedCheckout(orderId);
        return ApiResponse.<OrderSagaResponse>builder()
                .message("Retry order saga successfully")
                .data(orderService.getOrderSaga(orderId, OrderSagaType.CHECKOUT))
                .build();
    }

    @PostMapping("/{orderId}/cancellations/{cancellationId}/verify-carrier")
    public ApiResponse<OrderResponse> verifyCarrierCancellation(
            @PathVariable String orderId,
            @PathVariable String cancellationId,
            @Valid @RequestBody
                    com.fashionstore.order.saga.dto.VerifyCancellationCarrierRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .data(orderCancellationService.verifyCarrier(orderId, cancellationId, request))
                .build();
    }

    @GetMapping("/{orderId}/shipment")
    public ApiResponse<com.fashionstore.order.shipment.dto.ShipmentResponse> getShipment(
            @PathVariable String orderId) {
        return ApiResponse.<com.fashionstore.order.shipment.dto.ShipmentResponse>builder()
                .data(shipmentService.getShipmentForAdmin(orderId))
                .build();
    }

    @Operation(
            summary = "Tìm đơn theo user và/hoặc trạng thái (ADMIN)",
            description =
                    "Mặc định 20 đơn/trang, mới nhất trước. Bỏ trống cả hai filter là quét toàn bộ"
                        + " đơn.")
    @GetMapping
    public ApiResponse<PageResponse<List<OrderSummaryResponse>>> searchOrders(
            @Parameter(description = "Id người dùng chủ đơn") @RequestParam(required = false)
                    String userId,
            @Parameter(description = "Lọc theo trạng thái đơn") @RequestParam(required = false)
                    OrderStatus status,
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "createdAt",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.<PageResponse<List<OrderSummaryResponse>>>builder()
                .message("Search orders successfully")
                .data(orderService.searchOrders(userId, status, pageable))
                .build();
    }

    @Operation(
            summary = "Xem một đơn bất kỳ (ADMIN)",
            description = "Mã lỗi: `4001` không tìm thấy đơn (404).")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .message("Get order successfully")
                .data(orderService.getOrderById(orderId))
                .build();
    }

    /**
     * Chỗ duy nhất state saga lộ ra ngoài, dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED.
     */
    @Operation(
            summary = "Trạng thái saga của một đơn (ADMIN)",
            description =
                    """
Chỗ duy nhất state của saga lộ ra ngoài: bước hiện tại, deadline của bước, mã/lý do thất bại,
số lần retry, id giữ kho và id thanh toán. Dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED.

Mã lỗi: `4007` đơn này không có saga, ví dụ đơn tạo trước khi có saga (404).""")
    @GetMapping("/{orderId}/saga")
    public ApiResponse<OrderSagaResponse> getOrderSaga(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "CHECKOUT") OrderSagaType type) {
        return ApiResponse.<OrderSagaResponse>builder()
                .message("Get order saga successfully")
                .data(orderService.getOrderSaga(orderId, type))
                .build();
    }

    @Operation(
            summary = "Xem lịch sử trạng thái của một đơn (ADMIN)",
            description = "Trả về danh sách các lần đổi trạng thái của đơn hàng, mới nhất trước.")
    @GetMapping("/{orderId}/history")
    public ApiResponse<List<OrderStatusHistoryResponse>> getOrderHistory(
            @PathVariable String orderId) {
        return ApiResponse.<List<OrderStatusHistoryResponse>>builder()
                .message("Get order history successfully")
                .data(orderService.getOrderHistory(orderId))
                .build();
    }
}

package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import org.springdoc.core.annotations.ParameterObject;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Khu vực vận hành. Phân quyền ADMIN nằm ở tầng service ({@code @PreAuthorize}) để logic và luật truy cập
 * đi cùng nhau, controller chỉ định tuyến.
 */
@Tag(name = "Admin - Orders", description = """
        Khu vực vận hành, mọi endpoint yêu cầu role `ADMIN` (kiểm ở tầng service bằng `@PreAuthorize`).
        Token thiếu quyền nhận 403.""")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminOrderController {

    OrderService orderService;

    @Operation(summary = "Tìm đơn theo user và/hoặc trạng thái (ADMIN)",
            description = "Mặc định 20 đơn/trang, mới nhất trước. Bỏ trống cả hai filter là quét toàn bộ đơn.")
    @GetMapping
    public ApiResponse<PageResponse<List<OrderSummaryResponse>>> searchOrders(
            @Parameter(description = "Id người dùng chủ đơn") @RequestParam(required = false) String userId,
            @Parameter(description = "Lọc theo trạng thái đơn") @RequestParam(required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<OrderSummaryResponse>>>builder()
                .message("Search orders successfully")
                .data(orderService.searchOrders(userId, status, pageable))
                .build();
    }

    @Operation(summary = "Xem một đơn bất kỳ (ADMIN)",
            description = "Mã lỗi: `4001` không tìm thấy đơn (404).")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .message("Get order successfully")
                .data(orderService.getOrderById(orderId))
                .build();
    }

    /** Chỗ duy nhất state saga lộ ra ngoài, dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED. */
    @Operation(
            summary = "Trạng thái saga của một đơn (ADMIN)",
            description = """
                    Chỗ duy nhất state của saga lộ ra ngoài: bước hiện tại, deadline của bước, mã/lý do thất bại,
                    số lần retry, id giữ kho và id thanh toán. Dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED.

                    Mã lỗi: `4007` đơn này không có saga, ví dụ đơn tạo trước khi có saga (404).""")
    @GetMapping("/{orderId}/saga")
    public ApiResponse<OrderSagaResponse> getOrderSaga(@PathVariable String orderId) {
        return ApiResponse.<OrderSagaResponse>builder()
                .message("Get order saga successfully")
                .data(orderService.getOrderSaga(orderId))
                .build();
    }
}

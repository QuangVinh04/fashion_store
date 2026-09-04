package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.service.OrderService;
import lombok.AccessLevel;
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
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminOrderController {

    OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<List<OrderSummaryResponse>>> searchOrders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<OrderSummaryResponse>>>builder()
                .message("Search orders successfully")
                .data(orderService.searchOrders(userId, status, pageable))
                .build();
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrderById(@PathVariable String orderId) {
        return ApiResponse.<OrderResponse>builder()
                .message("Get order successfully")
                .data(orderService.getOrderById(orderId))
                .build();
    }

    /** Chỗ duy nhất state saga lộ ra ngoài, dùng khi cần chẩn đoán một saga đang kẹt hoặc đã FAILED. */
    @GetMapping("/{orderId}/saga")
    public ApiResponse<OrderSagaResponse> getOrderSaga(@PathVariable String orderId) {
        return ApiResponse.<OrderSagaResponse>builder()
                .message("Get order saga successfully")
                .data(orderService.getOrderSaga(orderId))
                .build();
    }
}

package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.service.OrderService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {

    OrderService orderService;

    @PostMapping("/{checkoutId}")
    public ApiResponse<OrderResponse> createOrder(@PathVariable String checkoutId,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Place order successfully")
                .data(orderService.createOrder(checkoutId, idempotencyKey, request))
                .build();
    }

    @GetMapping
    public ApiResponse<PageResponse<List<OrderSummaryResponse>>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<OrderSummaryResponse>>>builder()
                .message("Get my orders successfully")
                .data(orderService.getMyOrders(status, pageable))
                .build();
    }

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
    @PostMapping("/{id}/return-request")
    public ApiResponse<OrderResponse> requestReturn(@PathVariable("id") String id,
                                                    @Valid @RequestBody(required = false) ReturnOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Return request accepted")
                .data(orderService.requestReturn(id, request))
                .build();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateOrderStatus(@PathVariable("id") String id,
                                                        @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Update order status successfully")
                .data(orderService.updateOrderStatus(id, request))
                .build();
    }
}

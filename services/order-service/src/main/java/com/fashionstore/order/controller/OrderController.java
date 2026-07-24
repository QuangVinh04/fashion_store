package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {

    OrderService orderService;

    @PostMapping("/checkout/{checkoutId}")
    public ApiResponse<OrderResponse> placeOrder(@PathVariable String checkoutId,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .message("Place order successfully")
                .data(orderService.placeOrder(checkoutId, idempotencyKey, request))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getMyOrderById(@PathVariable("id") String id) {
        return ApiResponse.<OrderResponse>builder()
                .message("Get order successfully")
                .data(orderService.getMyOrderById(id))
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

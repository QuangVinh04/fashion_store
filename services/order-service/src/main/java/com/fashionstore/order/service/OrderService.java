package com.fashionstore.order.service;

import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;

public interface OrderService {
    OrderResponse placeOrder(String checkoutId, String idempotencyKey, CreateOrderRequest request);

    OrderResponse getMyOrderById(String orderId);

    OrderResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request);
}

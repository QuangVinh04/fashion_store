package com.fashionstore.order.dashboard.dto;

import com.fashionstore.order.order.dto.OrderStatusCountResponse;

import java.util.List;

public record OperationsDashboardResponse(
        Long totalOrders,
        List<OrderStatusCountResponse> orderByStatus,
        Long lowStockCount,
        List<TopProductQuantityResponse> topProducts
) {
}

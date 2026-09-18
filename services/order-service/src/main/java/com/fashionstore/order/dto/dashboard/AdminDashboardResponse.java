package com.fashionstore.order.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private Long lowStockCount;
    private List<RevenueByDayResponse> revenueByDay;
    private List<OrderStatusCountResponse> orderByStatus;
    private List<TopProductResponse> topProducts;
}
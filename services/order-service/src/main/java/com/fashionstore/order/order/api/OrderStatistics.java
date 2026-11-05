package com.fashionstore.order.order.api;

import com.fashionstore.order.order.dto.OrderStatusCountResponse;
import com.fashionstore.order.order.dto.TopProductResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only order aggregates for reporting. Revenue only counts orders past confirmation. */
public interface OrderStatistics {

    long countOrders();

    BigDecimal totalRevenue();

    List<OrderStatusCountResponse> countByStatus();

    List<DailyRevenue> revenueByDay(LocalDateTime since);

    List<TopProductResponse> topSellingProducts(int limit);
}

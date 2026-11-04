package com.fashionstore.order.dashboard.service;

import com.fashionstore.order.dashboard.dto.AdminDashboardResponse;
import com.fashionstore.order.dashboard.dto.OperationsDashboardResponse;

public interface AdminDashboardService {
    AdminDashboardResponse getDashboardStats(int days, int lowStockThreshold);

    OperationsDashboardResponse getOperationsStats(int days, int lowStockThreshold);
}
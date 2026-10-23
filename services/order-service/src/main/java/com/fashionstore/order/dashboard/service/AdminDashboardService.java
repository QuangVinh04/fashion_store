package com.fashionstore.order.dashboard.service;

import com.fashionstore.order.dashboard.dto.AdminDashboardResponse;

public interface AdminDashboardService {
    AdminDashboardResponse getDashboardStats(int days, int lowStockThreshold);
}
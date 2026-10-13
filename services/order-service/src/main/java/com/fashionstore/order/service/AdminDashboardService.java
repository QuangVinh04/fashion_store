package com.fashionstore.order.service;

import com.fashionstore.order.dto.AdminDashboardResponse;

public interface AdminDashboardService {
    AdminDashboardResponse getDashboardStats(int days, int lowStockThreshold);
}
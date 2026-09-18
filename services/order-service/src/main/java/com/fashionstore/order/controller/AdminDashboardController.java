package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.dashboard.AdminDashboardResponse;
import com.fashionstore.order.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Dashboard", description = "Báo cáo số liệu tổng quan kinh doanh (Doanh thu, Đơn hàng, Top sản phẩm, Cảnh báo kho)")
@Validated
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardController {

    AdminDashboardService adminDashboardService;

    @Operation(summary = "Lấy dữ liệu tổng quan kinh doanh (ADMIN)",
            description = "Trả về doanh thu theo ngày, số lượng đơn theo trạng thái, top 10 sản phẩm bán chạy và số lượng variant sắp hết hàng.")
    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @Parameter(description = "Số ngày thống kê doanh thu gần nhất (mặc định: 30)")
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "days must be at least 1") int days,
            @Parameter(description = "Ngưỡng tồn kho cảnh báo sắp hết hàng (mặc định: 10)")
            @RequestParam(defaultValue = "10") @Min(value = 0, message = "lowStockThreshold must be at least 0") int lowStockThreshold
    ) {
        return ApiResponse.<AdminDashboardResponse>builder()
                .message("Get dashboard statistics successfully")
                .data(adminDashboardService.getDashboardStats(days, lowStockThreshold))
                .build();
    }
}
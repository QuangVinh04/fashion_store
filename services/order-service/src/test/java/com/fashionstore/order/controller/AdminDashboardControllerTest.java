package com.fashionstore.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.dto.dashboard.AdminDashboardResponse;
import com.fashionstore.order.dto.dashboard.OrderStatusCountResponse;
import com.fashionstore.order.dto.dashboard.RevenueByDayResponse;
import com.fashionstore.order.dto.dashboard.TopProductResponse;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(adminDashboardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDashboard_returns200AndData() throws Exception {
        AdminDashboardResponse mockResponse = AdminDashboardResponse.builder()
                .totalRevenue(new BigDecimal("25000000"))
                .totalOrders(100L)
                .lowStockCount(5L)
                .revenueByDay(List.of(new RevenueByDayResponse(LocalDate.of(2026, 9, 18), new BigDecimal("10000000"), 20L)))
                .orderByStatus(List.of(new OrderStatusCountResponse(OrderStatus.CONFIRMED, 50L)))
                .topProducts(List.of(new TopProductResponse("var-1", "Áo Polo Nam", 80L, new BigDecimal("16000000"))))
                .build();

        when(adminDashboardService.getDashboardStats(eq(30), eq(10))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .param("days", "30")
                        .param("lowStockThreshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Get dashboard statistics successfully"))
                .andExpect(jsonPath("$.data.totalRevenue").value(25000000))
                .andExpect(jsonPath("$.data.totalOrders").value(100))
                .andExpect(jsonPath("$.data.lowStockCount").value(5))
                .andExpect(jsonPath("$.data.topProducts[0].productName").value("Áo Polo Nam"));

        verify(adminDashboardService).getDashboardStats(eq(30), eq(10));
    }
}
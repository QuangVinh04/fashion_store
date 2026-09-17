package com.fashionstore.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.dto.OrderStatusHistoryResponse;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getOrderHistory_returns200AndHistoryList() throws Exception {
        OrderStatusHistoryResponse history = OrderStatusHistoryResponse.builder()
                .id("hist-admin-1")
                .orderId("ord-1")
                .fromStatus(OrderStatus.CONFIRMED)
                .toStatus(OrderStatus.PACKED)
                .action("SHIPMENT_CREATED")
                .changedBy("admin-1")
                .reason("Packed package")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getOrderHistory("ord-1")).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/admin/orders/ord-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("hist-admin-1"))
                .andExpect(jsonPath("$.data[0].action").value("SHIPMENT_CREATED"))
                .andExpect(jsonPath("$.data[0].fromStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].toStatus").value("PACKED"));
    }
}

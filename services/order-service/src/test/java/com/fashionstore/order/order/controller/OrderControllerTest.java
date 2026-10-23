package com.fashionstore.order.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.order.dto.OrderStatusHistoryResponse;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.service.OrderService;
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
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @Mock
    private com.fashionstore.order.returns.service.ReturnService returnService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService, returnService,
                org.mockito.Mockito.mock(com.fashionstore.order.order.service.OrderReceiptService.class),
                org.mockito.Mockito.mock(com.fashionstore.order.saga.service.OrderCancellationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getReturnEvidence_returnsSignedUrl() throws Exception {
        when(returnService.getMyReturnEvidenceReadUrl("ord-1", "media-1"))
                .thenReturn("https://signed.example/evidence");

        mockMvc.perform(get("/api/v1/orders/ord-1/return-request/evidence/media-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data").value("https://signed.example/evidence"));
    }

    @Test
    void customerCannotCancelAReturnRequestByCallingTheApiDirectly() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/orders/ord-1/return-request/cancel"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getHandler()).isNull())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.web.servlet.NoHandlerFoundException.class));

        org.mockito.Mockito.verifyNoInteractions(returnService, orderService);
    }

    @Test
    void getMyOrderHistory_returns200AndHistoryList() throws Exception {
        OrderStatusHistoryResponse history = OrderStatusHistoryResponse.builder()
                .id("hist-1")
                .orderId("ord-1")
                .fromStatus(OrderStatus.PENDING)
                .toStatus(OrderStatus.CONFIRMED)
                .action("ORDER_CONFIRMED")
                .changedBy("SYSTEM")
                .reason("Inventory confirmed")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getMyOrderHistory("ord-1")).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/orders/ord-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("hist-1"))
                .andExpect(jsonPath("$.data[0].action").value("ORDER_CONFIRMED"))
                .andExpect(jsonPath("$.data[0].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].toStatus").value("CONFIRMED"));
    }
}

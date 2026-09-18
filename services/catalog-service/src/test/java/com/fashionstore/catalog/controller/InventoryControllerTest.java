package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.CheckStockRequest;
import com.fashionstore.catalog.dto.CheckStockResponse;
import com.fashionstore.catalog.dto.InventoryResponse;
import com.fashionstore.catalog.dto.ReleaseStockRequest;
import com.fashionstore.catalog.dto.ReserveStockRequest;
import com.fashionstore.catalog.dto.ReserveStockResponse;
import com.fashionstore.catalog.dto.UpdateStockRequest;
import com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse;
import com.fashionstore.catalog.dto.inventory.LowStockItemResponse;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InventoryService inventoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventoryService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getLowStock_returns200() throws Exception {
        LowStockItemResponse item = LowStockItemResponse.builder()
                .variantId("var-1")
                .productId("prod-1")
                .productName("Sản phẩm A")
                .sku("SKU-1")
                .quantity(3)
                .reservedQuantity(1)
                .availableQuantity(2)
                .threshold(10)
                .build();

        PageResponse<List<LowStockItemResponse>> pageResponse = PageResponse.<List<LowStockItemResponse>>builder()
                .pageNo(0)
                .pageSize(20)
                .totalPage(1)
                .items(List.of(item))
                .build();

        when(inventoryService.getLowStock(eq(10), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Get low stock inventory successfully"))
                .andExpect(jsonPath("$.data.items[0].variantId").value("var-1"))
                .andExpect(jsonPath("$.data.items[0].availableQuantity").value(2));

        verify(inventoryService).getLowStock(eq(10), any(Pageable.class));
    }

    @Test
    void getLedger_returns200() throws Exception {
        InventoryLedgerResponse item = InventoryLedgerResponse.builder()
                .id("led-1")
                .variantId("var-1")
                .type(InventoryLedgerType.CONFIRM)
                .quantity(5)
                .refOrderId("ord-1")
                .createdBy("ADMIN")
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<List<InventoryLedgerResponse>> pageResponse = PageResponse.<List<InventoryLedgerResponse>>builder()
                .pageNo(0)
                .pageSize(20)
                .totalPage(1)
                .items(List.of(item))
                .build();

        when(inventoryService.getLedger(eq("var-1"), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/inventory/ledger")
                        .param("variantId", "var-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Get inventory ledger successfully"))
                .andExpect(jsonPath("$.data.items[0].type").value("CONFIRM"));

        verify(inventoryService).getLedger(eq("var-1"), any(Pageable.class));
    }

    @Test
    void confirmStock_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/confirm/order-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock confirmed successfully"));

        verify(inventoryService).confirmStock("order-100");
    }

    @Test
    void restock_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/restock/order-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock restocked successfully"));

        verify(inventoryService).restock("order-200");
    }
}
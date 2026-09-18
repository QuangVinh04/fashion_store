package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse;
import com.fashionstore.catalog.dto.inventory.LowStockItemResponse;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminInventoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminInventoryController(inventoryService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getLowStock_returns200AndList() throws Exception {
        LowStockItemResponse item = LowStockItemResponse.builder()
                .variantId("var-1")
                .productId("prod-1")
                .productName("Áo Polo Basic")
                .sku("POLO-BLACK-M")
                .quantity(5)
                .reservedQuantity(2)
                .availableQuantity(3)
                .threshold(5)
                .build();

        PageResponse<List<LowStockItemResponse>> pageResponse = PageResponse.<List<LowStockItemResponse>>builder()
                .pageNo(0)
                .pageSize(20)
                .totalPage(1)
                .items(List.of(item))
                .build();

        when(inventoryService.getLowStock(eq(5), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/admin/inventory/low-stock")
                        .param("threshold", "5")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Get low stock inventory successfully"))
                .andExpect(jsonPath("$.data.items[0].variantId").value("var-1"))
                .andExpect(jsonPath("$.data.items[0].sku").value("POLO-BLACK-M"))
                .andExpect(jsonPath("$.data.items[0].availableQuantity").value(3))
                .andExpect(jsonPath("$.data.items[0].threshold").value(5));

        verify(inventoryService).getLowStock(eq(5), any(Pageable.class));
    }

    @Test
    void getLowStock_defaultThreshold_uses10() throws Exception {
        PageResponse<List<LowStockItemResponse>> pageResponse = PageResponse.<List<LowStockItemResponse>>builder()
                .pageNo(0)
                .pageSize(20)
                .totalPage(0)
                .items(List.of())
                .build();

        when(inventoryService.getLowStock(eq(10), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/admin/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Get low stock inventory successfully"))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(inventoryService).getLowStock(eq(10), any(Pageable.class));
    }

    @Test
    void getLedger_returns200AndList() throws Exception {
        InventoryLedgerResponse item = InventoryLedgerResponse.builder()
                .id("led-1")
                .variantId("var-1")
                .type(InventoryLedgerType.RESERVE)
                .quantity(2)
                .refOrderId("order-100")
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

        mockMvc.perform(get("/admin/inventory/ledger")
                        .param("variantId", "var-1")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Get inventory ledger successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("led-1"))
                .andExpect(jsonPath("$.data.items[0].variantId").value("var-1"))
                .andExpect(jsonPath("$.data.items[0].type").value("RESERVE"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].refOrderId").value("order-100"));

        verify(inventoryService).getLedger(eq("var-1"), any(Pageable.class));
    }
}

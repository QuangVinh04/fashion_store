package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.config.InternalTokenAuthFilter;
import com.fashionstore.catalog.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalInventoryControllerSecurityTest {

    private static final String SECRET_TOKEN = "fashion-store-internal-secret-token";

    private MockMvc mockMvc;

    @Mock
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        InternalTokenAuthFilter filter = new InternalTokenAuthFilter(SECRET_TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalInventoryController(inventoryService))
                .addFilters(filter)
                .build();
    }

    @Test
    void countLowStock_withoutInternalToken_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/internal/inventory/low-stock/count")
                        .param("threshold", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void countLowStock_withWrongInternalToken_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/internal/inventory/low-stock/count")
                        .param("threshold", "10")
                        .header("X-Internal-Token", "wrong-secret-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void countLowStock_withCorrectInternalToken_returns200Ok() throws Exception {
        when(inventoryService.countLowStock(10)).thenReturn(5L);

        mockMvc.perform(get("/internal/inventory/low-stock/count")
                        .param("threshold", "10")
                        .header("X-Internal-Token", SECRET_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }
}

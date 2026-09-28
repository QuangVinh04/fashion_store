package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.ProductResponse;
import com.fashionstore.catalog.dto.ProductAdvanceSearchRequest;
import com.fashionstore.catalog.dto.SizeChartResponse;
import com.fashionstore.catalog.service.ProductService;
import com.fashionstore.catalog.service.SizeChartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies product-facing controller behavior that depends on optional catalog associations.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private SizeChartService sizeChartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService, sizeChartService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    @Test
    void paginationSizeDoesNotBecomeApparelSizeFilter() throws Exception {
        mockMvc.perform(get("/api/v1/products/advance-search").param("size", "12").param("page", "1"))
                .andExpect(status().isOk());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<ProductAdvanceSearchRequest> filters = ArgumentCaptor.forClass(ProductAdvanceSearchRequest.class);
        verify(productService).advanceSearchWithRequest(page.capture(), filters.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(12);
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(filters.getValue().getSize()).isNull();
    }

    @Test
    void apparelSizeFilterCanBeCombinedWithPaginationSize() throws Exception {
        mockMvc.perform(get("/api/v1/products/advance-search").param("size", "12").param("sizeFilter", "M"))
                .andExpect(status().isOk());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<ProductAdvanceSearchRequest> filters = ArgumentCaptor.forClass(ProductAdvanceSearchRequest.class);
        verify(productService).advanceSearchWithRequest(page.capture(), filters.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(12);
        assertThat(filters.getValue().getSize()).isEqualTo("M");
    }

    @Test
    void getSizeChartReturnsSuccessWithoutCallingServiceWhenProductHasNoChart() throws Exception {
        when(productService.getProductById("product-1"))
                .thenReturn(ProductResponse.builder().id("product-1").sizeChartId(null).build());

        mockMvc.perform(get("/api/v1/products/product-1/size-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(sizeChartService);
    }

    @Test
    void getSizeChartReturnsSuccessWithoutCallingServiceWhenChartIdIsBlank() throws Exception {
        when(productService.getProductById("product-1"))
                .thenReturn(ProductResponse.builder().id("product-1").sizeChartId(" ").build());

        mockMvc.perform(get("/api/v1/products/product-1/size-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(sizeChartService);
    }

    @Test
    void getSizeChartReturnsAssignedChart() throws Exception {
        when(productService.getProductById("product-1"))
                .thenReturn(ProductResponse.builder().id("product-1").sizeChartId("chart-1").build());
        when(sizeChartService.getById("chart-1"))
                .thenReturn(SizeChartResponse.builder().id("chart-1").name("Men tops").build());

        mockMvc.perform(get("/api/v1/products/product-1/size-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("chart-1"))
                .andExpect(jsonPath("$.data.name").value("Men tops"));

        verify(sizeChartService).getById("chart-1");
    }
}

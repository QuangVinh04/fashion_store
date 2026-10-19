package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.ProductResponse;
import com.fashionstore.catalog.dto.ProductAdvanceSearchRequest;
import com.fashionstore.catalog.dto.AdminProductFilter;
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

    @Test
    void mvpFiltersArePassedToRealProductSearch() throws Exception {
        mockMvc.perform(get("/api/v1/products/advance-search").param("productType", "SHIRT")
                .param("categoryId", "c1").param("minRating", "4")
                .param("minPrice", "100000").param("maxPrice", "500000"))
                .andExpect(status().isOk());
        ArgumentCaptor<ProductAdvanceSearchRequest> filters = ArgumentCaptor.forClass(ProductAdvanceSearchRequest.class);
        verify(productService).advanceSearchWithRequest(org.mockito.ArgumentMatchers.any(Pageable.class), filters.capture());
        assertThat(filters.getValue().getProductType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.ProductType.SHIRT);
        assertThat(filters.getValue().getCategoryId()).isEqualTo("c1");
        assertThat(filters.getValue().getMinRating()).isEqualTo(4);
        assertThat(filters.getValue().getMinPrice()).isEqualByComparingTo("100000");
        assertThat(filters.getValue().getMaxPrice()).isEqualByComparingTo("500000");
    }

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
    void adminFiltersArePassedAlongWithPagination() throws Exception {
        mockMvc.perform(get("/admin/products").param("page", "1").param("size", "10")
                .param("keyword", "shirt").param("status", "DRAFT").param("brandId", "b1")
                .param("categoryId", "c1").param("minPrice", "100"))
                .andExpect(status().isOk());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<AdminProductFilter> filters = ArgumentCaptor.forClass(AdminProductFilter.class);
        verify(productService).getAllProductsForBackoffice(page.capture(), filters.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(filters.getValue().getKeyword()).isEqualTo("shirt");
        assertThat(filters.getValue().getBrandId()).isEqualTo("b1");
        assertThat(filters.getValue().getMinPrice()).isEqualByComparingTo("100");
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
    void adminDetailUsesTheBackofficeLookup() throws Exception {
        when(productService.getProductByIdForBackoffice("draft-1"))
                .thenReturn(ProductResponse.builder().id("draft-1").build());

        mockMvc.perform(get("/admin/products/draft-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("draft-1"));
        verify(productService).getProductByIdForBackoffice("draft-1");
        org.mockito.Mockito.verify(productService, org.mockito.Mockito.never()).getProductById("draft-1");
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

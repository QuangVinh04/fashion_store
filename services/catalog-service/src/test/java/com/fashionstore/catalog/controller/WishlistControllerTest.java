package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.wishlist.WishlistAddResult;
import com.fashionstore.catalog.dto.wishlist.WishlistCheckResponse;
import com.fashionstore.catalog.dto.wishlist.WishlistItemResponse;
import com.fashionstore.catalog.service.WishlistService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WishlistControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WishlistController(wishlistService))
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addToWishlist_whenNewlyCreated_returns201Created() throws Exception {
        WishlistItemResponse item = WishlistItemResponse.builder()
                .productId("prod-1")
                .name("Áo Thun Nam")
                .salePrice(new BigDecimal("199000"))
                .addedAt(LocalDateTime.now())
                .build();

        when(wishlistService.addToWishlist("prod-1")).thenReturn(new WishlistAddResult(item, true));

        mockMvc.perform(post("/api/v1/wishlist/prod-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value("prod-1"))
                .andExpect(jsonPath("$.message").value("Product added to wishlist successfully"));
    }

    @Test
    void addToWishlist_whenAlreadyExists_returns200Ok() throws Exception {
        WishlistItemResponse item = WishlistItemResponse.builder()
                .productId("prod-1")
                .name("Áo Thun Nam")
                .salePrice(new BigDecimal("199000"))
                .addedAt(LocalDateTime.now())
                .build();

        when(wishlistService.addToWishlist("prod-1")).thenReturn(new WishlistAddResult(item, false));

        mockMvc.perform(post("/api/v1/wishlist/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value("prod-1"))
                .andExpect(jsonPath("$.message").value("Product already in wishlist"));
    }

    @Test
    void removeFromWishlist_returns200Ok() throws Exception {
        mockMvc.perform(delete("/api/v1/wishlist/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product removed from wishlist successfully"));

        verify(wishlistService).removeFromWishlist("prod-1");
    }

    @Test
    void getMyWishlist_returns200Ok() throws Exception {
        WishlistItemResponse item = WishlistItemResponse.builder()
                .productId("prod-1")
                .name("Áo Thun Nam")
                .build();

        PageResponse<List<WishlistItemResponse>> pageResponse = PageResponse.<List<WishlistItemResponse>>builder()
                .pageNo(0)
                .pageSize(12)
                .totalPage(1)
                .items(List.of(item))
                .build();

        when(wishlistService.getMyWishlist(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/wishlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value("prod-1"));
    }

    @Test
    void checkInWishlist_returns200Ok() throws Exception {
        when(wishlistService.checkInWishlist("prod-1")).thenReturn(
                WishlistCheckResponse.builder().inWishlist(true).build()
        );

        mockMvc.perform(get("/api/v1/wishlist/check/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inWishlist").value(true));
    }
}

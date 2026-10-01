package com.fashionstore.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CartService cartService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMyCart_returns200AndCartData() throws Exception {
        CartResponse response = CartResponse.builder()
                .id("cart-1")
                .userId("user-1")
                .totalPrice(BigDecimal.valueOf(100000))
                .items(new ArrayList<>())
                .build();

        when(cartService.getMyCart()).thenReturn(response);

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("cart-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"));
    }

    @Test
    void getMyCart_withoutToken_doesNotIssueAnonymousCookie() throws Exception {
        when(cartService.getMyCart()).thenReturn(CartResponse.builder()
                .userId("user-1")
                .totalPrice(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}

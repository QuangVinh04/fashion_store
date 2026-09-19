package com.fashionstore.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.MergeCartRequest;
import com.fashionstore.order.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void getMyCart_firstGuestRequest_setsAnonymousCookie() throws Exception {
        CartResponse response = CartResponse.builder()
                .id("cart-1")
                .userId("anon:mock-id")
                .totalPrice(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();

        when(cartService.getMyCart()).thenReturn(response);

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("anonymous_id="),
                                org.hamcrest.Matchers.containsString("Max-Age=2592000")
                        )
                )));
    }

    @Test
    void mergeCart_withRequestBody_callsServiceWithBodyId() throws Exception {
        String validUuid = "123e4567-e89b-12d3-a456-426614174000";
        MergeCartRequest request = MergeCartRequest.builder()
                .anonymousId(validUuid)
                .build();

        CartResponse response = CartResponse.builder()
                .id("cart-1")
                .userId("user-1")
                .totalPrice(BigDecimal.valueOf(250000))
                .items(new ArrayList<>())
                .build();

        when(cartService.mergeCart(eq(validUuid))).thenReturn(response);

        mockMvc.perform(post("/api/v1/cart/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("cart-1"))
                .andExpect(jsonPath("$.message").value("Gộp giỏ hàng thành công"));

        verify(cartService).mergeCart(validUuid);
    }

    @Test
    void mergeCart_withHeader_callsServiceWithHeaderId() throws Exception {
        String validUuid = "223e4567-e89b-12d3-a456-426614174000";
        CartResponse response = CartResponse.builder()
                .id("cart-1")
                .userId("user-1")
                .totalPrice(BigDecimal.valueOf(250000))
                .items(new ArrayList<>())
                .build();

        when(cartService.mergeCart(eq(validUuid))).thenReturn(response);

        mockMvc.perform(post("/api/v1/cart/merge")
                        .header("X-Anonymous-Id", validUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("cart-1"))
                .andExpect(jsonPath("$.message").value("Gộp giỏ hàng thành công"));

        verify(cartService).mergeCart(validUuid);
    }

    @Test
    void mergeCart_withCookie_callsServiceWithCookieId() throws Exception {
        String validUuid = "333e4567-e89b-12d3-a456-426614174000";
        CartResponse response = CartResponse.builder()
                .id("cart-1")
                .userId("user-1")
                .totalPrice(BigDecimal.valueOf(250000))
                .items(new ArrayList<>())
                .build();

        when(cartService.mergeCart(eq(validUuid))).thenReturn(response);

        mockMvc.perform(post("/api/v1/cart/merge")
                        .cookie(new jakarta.servlet.http.Cookie("anonymous_id", validUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("cart-1"))
                .andExpect(jsonPath("$.message").value("Gộp giỏ hàng thành công"));

        verify(cartService).mergeCart(validUuid);
    }
}

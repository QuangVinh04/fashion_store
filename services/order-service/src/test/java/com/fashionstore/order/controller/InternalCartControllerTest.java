package com.fashionstore.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.MergeCartInternalRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalCartControllerTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CartService cartService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalCartController(cartService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mergeCart_success_callsServiceAndReturns200() throws Exception {
        MergeCartInternalRequest request = new MergeCartInternalRequest("user-100", "123e4567-e89b-12d3-a456-426614174000");

        CartResponse response = CartResponse.builder()
                .id("cart-100")
                .userId("user-100")
                .totalPrice(BigDecimal.valueOf(500000))
                .items(new ArrayList<>())
                .build();

        when(cartService.mergeCartForUser(eq("user-100"), eq("123e4567-e89b-12d3-a456-426614174000"))).thenReturn(response);

        mockMvc.perform(post("/internal/v1/cart/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("cart-100"))
                .andExpect(jsonPath("$.message").value("Merge cart successfully"));

        verify(cartService).mergeCartForUser("user-100", "123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void mergeCart_missingFields_returns400() throws Exception {
        MergeCartInternalRequest invalid = new MergeCartInternalRequest(null, "");

        mockMvc.perform(post("/internal/v1/cart/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}

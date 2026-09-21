package com.fashionstore.order.controller;

import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import com.fashionstore.common.security.InternalTokenAuthFilter;
import com.fashionstore.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalOrderControllerSecurityTest {

    private static final String SECRET_TOKEN = "fashion-store-internal-secret-token";

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        InternalTokenAuthFilter filter = new InternalTokenAuthFilter(SECRET_TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalOrderController(orderService))
                .addFilters(filter)
                .build();
    }

    @Test
    void verifyPurchase_withoutInternalToken_returns403Forbidden() throws Exception {
        VerifyPurchaseRequest request = new VerifyPurchaseRequest("user-1", "ord-1", List.of("var-1"));

        mockMvc.perform(post("/internal/v1/orders/verify-purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void verifyPurchase_withWrongInternalToken_returns403Forbidden() throws Exception {
        VerifyPurchaseRequest request = new VerifyPurchaseRequest("user-1", "ord-1", List.of("var-1"));

        mockMvc.perform(post("/internal/v1/orders/verify-purchase")
                        .header("X-Internal-Token", "invalid-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void verifyPurchase_withCorrectInternalToken_returns200Ok() throws Exception {
        VerifyPurchaseRequest request = new VerifyPurchaseRequest("user-1", "ord-1", List.of("var-1"));
        when(orderService.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "ord-1"));

        mockMvc.perform(post("/internal/v1/orders/verify-purchase")
                        .header("X-Internal-Token", SECRET_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchased").value(true))
                .andExpect(jsonPath("$.data.orderId").value("ord-1"));
    }
}

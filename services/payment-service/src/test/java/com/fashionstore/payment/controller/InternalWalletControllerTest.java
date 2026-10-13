package com.fashionstore.payment.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.payment.config.security.SecurityConfig;
import com.fashionstore.payment.dto.RefundWalletRequest;
import com.fashionstore.payment.dto.RefundWalletResponse;
import com.fashionstore.payment.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@WebMvcTest(InternalWalletController.class)
@ContextConfiguration(
        classes = {
            InternalWalletController.class,
            SecurityConfig.class,
            GlobalExceptionHandler.class
        })
@ImportAutoConfiguration(CommonSecurityAutoConfiguration.class)
class InternalWalletControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WalletService refunds;
    @MockitoBean JwtDecoder decoder;

    private static final String REQUEST =
            """
{"amount":150000,"returnId":"ret-1","orderId":"ord-1","reason":"Return accepted","reasonType":"RETURN_REFUND"}
""";

    @BeforeEach
    void tokens() {
        when(decoder.decode("service"))
                .thenReturn(
                        Jwt.withTokenValue("service")
                                .header("alg", "RS256")
                                .subject("service")
                                .claim("realm_access", Map.of("roles", List.of("INTERNAL_CALLER")))
                                .build());
        when(decoder.decode("buyer"))
                .thenReturn(
                        Jwt.withTokenValue("buyer")
                                .header("alg", "RS256")
                                .subject("buyer")
                                .claim("realm_access", Map.of("roles", List.of("USER")))
                                .build());
    }

    @Test
    void anonymousCannotRefund() throws Exception {
        mvc.perform(
                        post("/internal/wallets/user-1/refund")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(refunds);
    }

    @Test
    void buyerCannotRefund() throws Exception {
        mvc.perform(
                        post("/internal/wallets/user-1/refund")
                                .header("Authorization", "Bearer buyer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REQUEST))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refunds);
    }

    @Test
    void internalCallerReceivesFinancialReceiptAndPassesStableIdentifiers() throws Exception {
        var request =
                new RefundWalletRequest(
                        new BigDecimal("150000"), "ret-1", "ord-1", "Return accepted");
        when(refunds.refundReturn("user-1", request))
                .thenReturn(new RefundWalletResponse("ledger-1", "user-1", 150000, 350000));
        mvc.perform(
                        post("/internal/wallets/user-1/refund")
                                .header("Authorization", "Bearer service")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntryId").value("ledger-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.newBalance").value(350000));
        verify(refunds).refundReturn("user-1", request);
    }

    @Test
    void zeroAmountCannotReachFinancialService() throws Exception {
        mvc.perform(
                        post("/internal/wallets/user-1/refund")
                                .header("Authorization", "Bearer service")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REQUEST.replace("150000", "0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(refunds);
    }
}

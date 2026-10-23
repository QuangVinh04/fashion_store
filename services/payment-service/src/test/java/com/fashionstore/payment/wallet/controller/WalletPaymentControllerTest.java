package com.fashionstore.payment.wallet.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fashionstore.payment.shared.config.security.SecurityConfig;
import com.fashionstore.payment.wallet.dto.WalletPaymentSession;
import com.fashionstore.payment.wallet.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletPaymentController.class)
@ContextConfiguration(
        classes = {
            WalletPaymentController.class,
            SecurityConfig.class,
            com.fashionstore.common.exception.GlobalExceptionHandler.class
        })
@ImportAutoConfiguration(
        com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration.class)
class WalletPaymentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WalletService service;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void token() {
        when(decoder.decode("owner-token"))
                .thenReturn(
                        Jwt.withTokenValue("owner-token")
                                .header("alg", "RS256")
                                .subject("owner")
                                .build());
    }

    @Test
    void anonymousCannotConfirm() throws Exception {
        mvc.perform(
                        post("/api/v1/payments/p1/wallet-confirm")
                                .contentType("application/json")
                                .content("{\"attemptGeneration\":0}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void pageReadCallsSessionOnly() throws Exception {
        when(service.getSession("p1")).thenReturn(session());
        mvc.perform(
                        get("/api/v1/payments/p1/wallet-session")
                                .header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("80000"));
        verify(service).getSession("p1");
        verifyNoMoreInteractions(service);
    }

    @Test
    void confirmationIgnoresClientAmountAndOwner() throws Exception {
        when(service.confirm("p1", 0)).thenReturn(session());
        mvc.perform(
                        post("/api/v1/payments/p1/wallet-confirm")
                                .header("Authorization", "Bearer owner-token")
                                .contentType("application/json")
                                .content(
                                        "{\"attemptGeneration\":0,\"amount\":1,\"userId\":\"attacker\"}"))
                .andExpect(status().isOk());
        verify(service).confirm("p1", 0);
    }

    @Test
    void negativeCompatibilityFieldRejectedBeforeService() throws Exception {
        mvc.perform(
                        post("/api/v1/payments/p1/wallet-confirm")
                                .header("Authorization", "Bearer owner-token")
                                .contentType("application/json")
                                .content("{\"attemptGeneration\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private WalletPaymentSession session() {
        return new WalletPaymentSession(
                "p1", "order", 0, "PENDING", "VND", "80000", "100000", null, true, true);
    }
}

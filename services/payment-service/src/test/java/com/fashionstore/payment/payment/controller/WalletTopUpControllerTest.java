package com.fashionstore.payment.payment.controller;

import com.fashionstore.payment.payment.service.WalletTopUpService;
import com.fashionstore.payment.shared.config.security.SecurityConfig;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletTopUpController.class)
@ContextConfiguration(classes={WalletTopUpController.class,SecurityConfig.class,
        com.fashionstore.common.exception.GlobalExceptionHandler.class})
@ImportAutoConfiguration(com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration.class)
class WalletTopUpControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WalletTopUpService topUpService;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach void token() {
        when(jwtDecoder.decode("owner-token")).thenReturn(Jwt.withTokenValue("owner-token")
                .header("alg","RS256").subject("owner-1").build());
    }
    @Test void invalidTopUpRequestDoesNotCallProvider() throws Exception {
        mvc.perform(post("/api/v1/wallet/top-ups").header("Authorization","Bearer owner-token")
                .contentType("application/json").content("{\"amount\":-1,\"operationId\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(topUpService);
    }
}

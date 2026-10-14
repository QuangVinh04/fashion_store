package com.fashionstore.order.controller;

import com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.config.security.SecurityConfig;
import com.fashionstore.order.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShipmentController.class)
@ContextConfiguration(classes = {ShipmentController.class, SecurityConfig.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(CommonSecurityAutoConfiguration.class)
class GhnWebhookSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean ShipmentService shipments;
    @MockitoBean GhnProperties properties;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void token() {
        when(properties.getWebhookToken()).thenReturn("test-only-ghn-secret");
    }

    @Test
    void carrierCanUseItsWebhookSecretWithoutAKeycloakJwt() throws Exception {
        mvc.perform(post("/api/v1/shipping/ghn/webhook").header("X-Webhook-Token", "test-only-ghn-secret")
                .contentType("application/json").content("{\"ShopID\":123,\"OrderCode\":\"GHN-1\",\"Status\":\"delivered\"}"))
                .andExpect(status().isOk());
        verify(shipments).handleGhnCallback(any());
    }

    @Test
    void wrongSecretCannotReachShipmentBusinessLogic() throws Exception {
        mvc.perform(post("/api/v1/shipping/ghn/webhook").header("X-Webhook-Token", "wrong")
                .contentType("application/json").content("{\"ShopID\":123,\"OrderCode\":\"GHN-1\",\"Status\":\"delivered\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(shipments);
    }
}

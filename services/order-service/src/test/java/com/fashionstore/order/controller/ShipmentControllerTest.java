package com.fashionstore.order.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.dto.ghn.GhnWebhookPayload;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.service.ShipmentService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShipmentControllerTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    ShipmentService shipmentService;

    GhnProperties ghnProperties;

    @BeforeEach
    void setUp() {
        ghnProperties = new GhnProperties();
        ghnProperties.setWebhookToken("fashion-store-ghn-webhook-secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new ShipmentController(shipmentService, ghnProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createShipment_returns200AndShipmentData() throws Exception {
        ShipmentResponse response = ShipmentResponse.builder()
                .id("ship-1")
                .orderId("ord-1")
                .provider(ShipmentProvider.GHN)
                .trackingCode("TRACK123")
                .status(ShipmentStatus.PENDING)
                .fee(BigDecimal.valueOf(30000))
                .build();

        when(shipmentService.createShipment(eq("ord-1"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/ord-1/shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateShipmentRequest.builder().build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ship-1"))
                .andExpect(jsonPath("$.data.trackingCode").value("TRACK123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getShipment_returns200AndShipmentData() throws Exception {
        ShipmentResponse response = ShipmentResponse.builder()
                .id("ship-1")
                .orderId("ord-1")
                .provider(ShipmentProvider.GHN)
                .trackingCode("TRACK123")
                .status(ShipmentStatus.SHIPPING)
                .build();

        when(shipmentService.getShipmentByOrderId("ord-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/ord-1/shipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ship-1"))
                .andExpect(jsonPath("$.data.trackingCode").value("TRACK123"))
                .andExpect(jsonPath("$.data.status").value("SHIPPING"));
    }

    @Test
    void handleGhnCallback_withValidToken_returns200() throws Exception {
        GhnWebhookPayload payload = GhnWebhookPayload.builder()
                .orderCode("GHN123")
                .status("delivered")
                .build();

        mockMvc.perform(post("/internal/shipments/ghn-callback")
                        .header("Token", "fashion-store-ghn-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Processed GHN callback successfully"));

        verify(shipmentService).handleGhnCallback(any(GhnWebhookPayload.class));
    }

    @Test
    void handleGhnCallback_withValidXWebhookTokenHeader_returns200() throws Exception {
        GhnWebhookPayload payload = GhnWebhookPayload.builder()
                .orderCode("GHN123")
                .status("delivering")
                .build();

        mockMvc.perform(post("/internal/shipments/ghn-callback")
                        .header("X-Webhook-Token", "fashion-store-ghn-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Processed GHN callback successfully"));

        verify(shipmentService).handleGhnCallback(any(GhnWebhookPayload.class));
    }

    @Test
    void handleGhnCallback_withMissingToken_returns401() throws Exception {
        GhnWebhookPayload payload = GhnWebhookPayload.builder()
                .orderCode("GHN123")
                .status("delivered")
                .build();

        mockMvc.perform(post("/internal/shipments/ghn-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2005));

        verify(shipmentService, never()).handleGhnCallback(any(GhnWebhookPayload.class));
    }

    @Test
    void handleGhnCallback_withInvalidToken_returns401() throws Exception {
        GhnWebhookPayload payload = GhnWebhookPayload.builder()
                .orderCode("GHN123")
                .status("delivered")
                .build();

        mockMvc.perform(post("/internal/shipments/ghn-callback")
                        .header("Token", "wrong-secret-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2005));

        verify(shipmentService, never()).handleGhnCallback(any(GhnWebhookPayload.class));
    }
}


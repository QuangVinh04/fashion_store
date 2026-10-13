package com.fashionstore.order.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fashionstore.order.config.security.SecurityConfig;
import com.fashionstore.order.dto.*;
import com.fashionstore.order.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

@WebMvcTest(AdminReturnController.class)
@ContextConfiguration(
        classes = {
            AdminReturnController.class,
            SecurityConfig.class,
            com.fashionstore.common.exception.GlobalExceptionHandler.class
        })
@ImportAutoConfiguration(
        com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration.class)
class AdminReturnControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReturnService returnService;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        when(decoder.decode("customer-token"))
                .thenReturn(
                        Jwt.withTokenValue("customer-token")
                                .header("alg", "RS256")
                                .subject("customer")
                                .build());
        when(decoder.decode("admin-token"))
                .thenReturn(
                        Jwt.withTokenValue("admin-token")
                                .header("alg", "RS256")
                                .subject("admin")
                                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                                .build());
    }

    @Test
    void anonymousCannotReadReturns() throws Exception {
        mvc.perform(get("/api/v1/admin/orders/returns/r1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(returnService);
    }

    @Test
    void customerCannotPerformAnyAdminMutation() throws Exception {
        for (String action : List.of("approve", "reject", "receive", "refund"))
            mvc.perform(
                            post("/api/v1/admin/orders/returns/r1/" + action)
                                    .header("Authorization", "Bearer customer-token")
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(returnService);
    }

    @Test
    void adminCanGetReturnDetails() throws Exception {
        when(returnService.getReturnRequestById("r1"))
                .thenReturn(ReturnRequestResponse.builder().id("r1").build());
        mvc.perform(
                        get("/api/v1/admin/orders/returns/r1")
                                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("r1"));
    }

    @Test
    void adminCanOpenAVerifiedLostShipmentRefundCase() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/orders/returns/lost/order-1")
                                .header("Authorization", "Bearer admin-token")
                                .contentType("application/json")
                                .content(
                                        "{\"reason\":\"Carrier loss"
                                            + " verified\",\"mediaIds\":[\"evidence-1\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanOpenACarrierReturnForWarehouseInspection() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/orders/returns/carrier/order-1")
                                .header("Authorization", "Bearer admin-token")
                                .contentType("application/json")
                                .content(
                                        "{\"reason\":\"Carrier returned the"
                                            + " parcel\",\"mediaIds\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminMustProvideAQualityCheckBeforeReceivingTheReturn() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/orders/returns/r1/receive")
                                .header("Authorization", "Bearer admin-token")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(returnService);
    }

    @Test
    void adminCanSearchReturns() throws Exception {
        when(returnService.searchReturnRequests(any(), any()))
                .thenReturn(
                        com.fashionstore.common.dto.PageResponse
                                .<List<ReturnRequestResponse>>builder()
                                .pageNo(0)
                                .pageSize(20)
                                .totalPage(1)
                                .totalElements(1L)
                                .items(List.of())
                                .build());
        mvc.perform(
                        get("/api/v1/admin/orders/returns")
                                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void refundAcceptanceDoesNotClaimMoneyWasCredited() throws Exception {
        when(returnService.refundReturn("ret-1", null))
                .thenReturn(
                        ReturnRequestResponse.builder()
                                .id("ret-1")
                                .status(
                                        com.fashionstore.order.entity.enumeration
                                                .ReturnRequestStatus.RECEIVED)
                                .refundStartedAt(java.time.Instant.now())
                                .build());
        mvc.perform(
                        post("/api/v1/admin/orders/returns/ret-1/refund")
                                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.refundLedgerEntryId").isEmpty());
    }

    @Test
    void confirmedRefundReplayReturnsOk() throws Exception {
        when(returnService.refundReturn("ret-1", null))
                .thenReturn(
                        ReturnRequestResponse.builder()
                                .id("ret-1")
                                .status(
                                        com.fashionstore.order.entity.enumeration
                                                .ReturnRequestStatus.REFUNDED)
                                .refundLedgerEntryId("ledger-1")
                                .build());
        mvc.perform(
                        post("/api/v1/admin/orders/returns/ret-1/refund")
                                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundLedgerEntryId").value("ledger-1"));
    }
}

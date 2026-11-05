package com.fashionstore.order.order.fulfillment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.shipment.client.GhnClient;
import com.fashionstore.order.shipment.dto.GhnWebhookPayload;
import com.fashionstore.order.order.fulfillment.dto.ShipmentResponse;
import com.fashionstore.order.shipment.dto.VerifiedGhnShipment;
import com.fashionstore.order.order.fulfillment.entity.ShipmentStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@ExtendWith(MockitoExtension.class)
class ShipmentSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    @Mock ShipmentService shipmentService;

    @Mock GhnClient ghnClient;

    ShipmentSyncService sync;

    @BeforeEach
    void setUp() {
        sync = new ShipmentSyncService(shipmentService, ghnClient, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void replaysTheVerifiedCarrierStatusAsAWebhook() {
        var before = shipment(ShipmentStatus.PENDING);
        var after = shipment(ShipmentStatus.PICKED);
        when(shipmentService.getShipmentForAdmin("order-1")).thenReturn(before, after);
        when(ghnClient.getVerifiedShipment("GHN1")).thenReturn(carrier("picked", null));

        var result = sync.syncFromCarrier("order-1");

        var payload = ArgumentCaptor.forClass(GhnWebhookPayload.class);
        verify(shipmentService).handleGhnCallback(payload.capture());
        assertThat(payload.getValue().getOrderCode()).isEqualTo("GHN1");
        assertThat(payload.getValue().getShopId()).isEqualTo("123");
        assertThat(payload.getValue().getClientOrderCode()).isEqualTo("ORD123");
        assertThat(payload.getValue().getStatus()).isEqualTo("picked");
        assertThat(payload.getValue().getTime()).isEqualTo(NOW.toString());
        assertThat(result).isSameAs(after);
    }

    @Test
    void usesTheCarrierCollectionTimeAsTheDeliveryTime() {
        var collectedAt = Instant.parse("2026-10-31T09:30:00Z");
        when(shipmentService.getShipmentForAdmin("order-1"))
                .thenReturn(shipment(ShipmentStatus.SHIPPING));
        when(ghnClient.getVerifiedShipment("GHN1")).thenReturn(carrier("delivered", collectedAt));

        sync.syncFromCarrier("order-1");

        var payload = ArgumentCaptor.forClass(GhnWebhookPayload.class);
        verify(shipmentService).handleGhnCallback(payload.capture());
        assertThat(payload.getValue().getTime()).isEqualTo(collectedAt.toString());
    }

    @Test
    void doesNotCallTheCarrierWhenTheOrderHasNoShipment() {
        when(shipmentService.getShipmentForAdmin("order-1"))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> sync.syncFromCarrier("order-1")).isInstanceOf(AppException.class);

        verify(ghnClient, never()).getVerifiedShipment(any());
        verify(shipmentService, never()).handleGhnCallback(any());
    }

    private ShipmentResponse shipment(ShipmentStatus status) {
        return ShipmentResponse.builder()
                .orderId("order-1")
                .trackingCode("GHN1")
                .ghnOrderCode("GHN1")
                .status(status)
                .build();
    }

    private VerifiedGhnShipment carrier(String status, Instant collectedAt) {
        return new VerifiedGhnShipment(
                "GHN1",
                "123",
                "ORD123",
                status,
                BigDecimal.ZERO,
                collectedAt != null,
                collectedAt,
                false,
                null);
    }
}

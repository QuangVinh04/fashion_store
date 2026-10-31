package com.fashionstore.order.shipment.service;

import com.fashionstore.order.shipment.client.GhnClient;
import com.fashionstore.order.shipment.dto.GhnWebhookPayload;
import com.fashionstore.order.shipment.dto.ShipmentResponse;
import com.fashionstore.order.shipment.dto.VerifiedGhnShipment;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Kéo trạng thái thật của vận đơn từ GHN khi webhook bị mất, rồi chạy qua đúng đường xử lý webhook
 * để đơn đổi trạng thái theo cùng một luật.
 */
@Service
@RequiredArgsConstructor
public class ShipmentSyncService {

    private final ShipmentService shipmentService;
    private final GhnClient ghnClient;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMIN')")
    public ShipmentResponse syncFromCarrier(String orderId) {
        ShipmentResponse shipment = shipmentService.getShipmentForAdmin(orderId);
        String carrierCode =
                shipment.getGhnOrderCode() != null
                        ? shipment.getGhnOrderCode()
                        : shipment.getTrackingCode();
        VerifiedGhnShipment carrier = ghnClient.getVerifiedShipment(carrierCode);
        shipmentService.handleGhnCallback(toWebhook(carrierCode, carrier));
        return shipmentService.getShipmentForAdmin(orderId);
    }

    /** GHN detail không trả giờ của sự kiện nên dùng giờ thu COD, nếu không có thì giờ hiện tại. */
    private GhnWebhookPayload toWebhook(String carrierCode, VerifiedGhnShipment carrier) {
        Instant eventTime =
                carrier.codCollectedAt() != null ? carrier.codCollectedAt() : clock.instant();
        return GhnWebhookPayload.builder()
                .shopId(carrier.shopId())
                .orderCode(carrierCode)
                .clientOrderCode(carrier.clientOrderCode())
                .status(carrier.status())
                .time(eventTime.toString())
                .build();
    }
}

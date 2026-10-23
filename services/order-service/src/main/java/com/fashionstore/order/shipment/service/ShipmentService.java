package com.fashionstore.order.shipment.service;

import com.fashionstore.order.shipment.dto.CreateShipmentRequest;
import com.fashionstore.order.shipment.dto.ShipmentResponse;
import com.fashionstore.order.shipment.dto.GhnWebhookPayload;

public interface ShipmentService {
    ShipmentResponse createShipment(String orderId, CreateShipmentRequest request);
    ShipmentResponse getShipmentByOrderId(String orderId);
    ShipmentResponse getShipmentForAdmin(String orderId);
    void handleGhnCallback(GhnWebhookPayload payload);
}

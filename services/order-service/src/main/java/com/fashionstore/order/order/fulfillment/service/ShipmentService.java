package com.fashionstore.order.order.fulfillment.service;

import com.fashionstore.order.order.fulfillment.dto.CreateShipmentRequest;
import com.fashionstore.order.order.fulfillment.dto.ShipmentResponse;
import com.fashionstore.order.shipment.dto.GhnWebhookPayload;

public interface ShipmentService {
    ShipmentResponse createShipment(String orderId, CreateShipmentRequest request);
    ShipmentResponse getShipmentByOrderId(String orderId);
    ShipmentResponse getShipmentForAdmin(String orderId);
    void handleGhnCallback(GhnWebhookPayload payload);
}

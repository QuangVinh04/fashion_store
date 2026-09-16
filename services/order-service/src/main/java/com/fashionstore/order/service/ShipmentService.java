package com.fashionstore.order.service;

import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.dto.ghn.GhnWebhookPayload;

public interface ShipmentService {
    ShipmentResponse createShipment(String orderId, CreateShipmentRequest request);
    ShipmentResponse getShipmentByOrderId(String orderId);
    void handleGhnCallback(GhnWebhookPayload payload);
}

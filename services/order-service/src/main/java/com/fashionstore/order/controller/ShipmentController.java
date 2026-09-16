package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.dto.ghn.GhnWebhookPayload;
import com.fashionstore.order.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shipment", description = "Quản lý vận đơn và tích hợp giao vận GHN")
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShipmentController {

    ShipmentService shipmentService;
    GhnProperties ghnProperties;

    @Operation(summary = "Admin tạo vận đơn GHN cho đơn hàng")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/orders/{orderId}/shipment")
    public ApiResponse<ShipmentResponse> createShipment(
            @PathVariable String orderId,
            @RequestBody(required = false) CreateShipmentRequest request
    ) {
        return ApiResponse.<ShipmentResponse>builder()
                .message("Create shipment successfully")
                .data(shipmentService.createShipment(orderId, request))
                .build();
    }

    @Operation(summary = "Khách hàng tra cứu vận đơn theo mã đơn hàng")
    @GetMapping("/api/v1/orders/{orderId}/shipment")
    public ApiResponse<ShipmentResponse> getShipment(@PathVariable String orderId) {
        return ApiResponse.<ShipmentResponse>builder()
                .message("Get shipment successfully")
                .data(shipmentService.getShipmentByOrderId(orderId))
                .build();
    }

    @Operation(summary = "Webhook nội bộ tiếp nhận trạng thái từ GHN")
    @PostMapping("/internal/shipments/ghn-callback")
    public ApiResponse<Void> handleGhnCallback(
            @RequestHeader(value = "Token", required = false) String token,
            @RequestHeader(value = "X-Webhook-Token", required = false) String webhookTokenHeader,
            @RequestBody GhnWebhookPayload payload
    ) {
        String expectedToken = ghnProperties.getWebhookToken();
        String providedToken = (token != null && !token.isBlank()) ? token : webhookTokenHeader;

        if (expectedToken == null || expectedToken.isBlank() || providedToken == null || !expectedToken.equals(providedToken)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        shipmentService.handleGhnCallback(payload);
        return ApiResponse.<Void>builder()
                .message("Processed GHN callback successfully")
                .build();
    }
}


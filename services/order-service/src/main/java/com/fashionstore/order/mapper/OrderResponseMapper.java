package com.fashionstore.order.mapper;

import com.fashionstore.order.dto.*;
import com.fashionstore.order.entity.Order;

/** Shared snapshot mapping without a dependency on an order command service. */
public final class OrderResponseMapper {
    private OrderResponseMapper() {}

    public static OrderResponse create(Order order, String checkoutId) {
        var items =
                order.getItems().stream()
                        .map(
                                item ->
                                        OrderItemResponse.builder()
                                                .orderItemId(item.getId())
                                                .variantId(item.getVariantId())
                                                .productName(item.getProductName())
                                                .size(item.getSize())
                                                .color(item.getColor())
                                                .unitPrice(item.getUnitPrice())
                                                .quantity(item.getQuantity())
                                                .lineTotal(item.getLineTotal())
                                                .build())
                        .toList();
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .checkoutId(checkoutId)
                .shipmentCreationPending(order.getShipmentInitiationToken() != null)
                .status(order.getStatus())
                .paymentId(order.getPaymentId())
                .paymentMethod(order.getPaymentMethod())
                .paymentProvider(order.getPaymentProvider())
                .paymentUrl(order.getPaymentUrl())
                .currency(order.getCurrency())
                .cancelReason(order.getCancelReason())
                .recipientName(
                        order.getAddress() == null ? null : order.getAddress().getRecipientName())
                .recipientPhone(
                        order.getAddress() == null ? null : order.getAddress().getRecipientPhone())
                .shippingAddress(order.getShippingAddress())
                .shippingProvider(order.getShippingProvider())
                .trackingCode(order.getTrackingCode())
                .subtotalAmount(order.getSubtotalAmount())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}

package com.fashionstore.order.dto;

import com.fashionstore.order.entity.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderResponse {
    String id;
    String orderCode;
    String checkoutId;
    OrderStatus status;
    String paymentId;
    String sagaFailureReason;
    String recipientName;
    String recipientPhone;
    String shippingAddress;
    String shippingProvider;
    String trackingCode;
    BigDecimal subtotalAmount;
    BigDecimal discountAmount;
    BigDecimal shippingFee;
    BigDecimal totalAmount;
    List<OrderItemResponse> items;
}

package com.fashionstore.order.dto;

import com.fashionstore.order.model.enumeration.OrderStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    PaymentMethod paymentMethod;
    PaymentProvider paymentProvider;
    String currency;
    /** Lý do hủy viết cho người đọc; mã lỗi kỹ thuật của saga không lộ ra API. */
    String cancelReason;
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
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

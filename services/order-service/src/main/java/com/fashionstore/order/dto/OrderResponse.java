package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.OrderStatus;
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
    boolean canCancel;
    boolean shipmentCreationPending;
    OrderCancellationResponse cancellation;
    String id;
    String orderCode;
    String checkoutId;
    OrderStatus status;
    String paymentId;
    PaymentMethod paymentMethod;
    PaymentProvider paymentProvider;
    String paymentUrl;
    java.time.Instant deliveredAt;
    java.time.Instant completedAt;
    String completionSource;
    java.time.Instant returnEligibleUntil;
    boolean canRequestReturn;
    boolean canConfirmReceipt;
    String activeReturnId;
    String deliveryEvidenceIssue;
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

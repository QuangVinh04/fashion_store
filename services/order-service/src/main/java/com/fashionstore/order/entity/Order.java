package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(name = "orders")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order extends BaseEntity {

    @Column(name = "order_code", nullable = false, unique = true, length = 30)
    String orderCode;

    @Column(name = "user_id", nullable = false)
    String userId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    String idempotencyKey;

    @Column(name = "inventory_reservation_id", length = 36)
    String inventoryReservationId;

    @Column(name = "payment_id", length = 36)
    String paymentId;

    @Column(name = "saga_failure_reason", length = 500)
    String sagaFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_target_status", length = 30)
    OrderStatus compensationTargetStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    OrderStatus status = OrderStatus.PENDING_INVENTORY;

    @Column(name = "recipient_name", nullable = false, length = 120)
    String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    String recipientPhone;

    @Column(name = "shipping_address", nullable = false, length = 500)
    String shippingAddress;

    @Column(name = "shipping_provider", length = 100)
    String shippingProvider;

    @Column(name = "tracking_code", length = 100)
    String trackingCode;

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal totalAmount;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    Checkout checkout;
}

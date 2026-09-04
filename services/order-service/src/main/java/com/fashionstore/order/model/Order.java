package com.fashionstore.order.model;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.model.enumeration.OrderStatus;
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

    // ----- Snapshot dữ liệu thanh toán (copy từ Checkout lúc tạo đơn) -----

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 30)
    PaymentProvider paymentProvider;

    /** ISO-4217. Thiếu field này thì lệnh authorize gửi số tiền trần, không an toàn. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    String currency = "VND";

    @Column(name = "payment_id", length = 36)
    String paymentId;

    @Column(name = "cancel_reason", length = 500)
    String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    OrderStatus status = OrderStatus.PENDING;

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

    @Column(name = "checkout_id", nullable = false, unique = true)
    String checkoutId;


    // Saga chỉ chạm vào orders tại ba thời điểm: tạo đơn (PENDING), saga xong (CONFIRMED),
    // saga bù trừ xong (CANCELLED). Ngoài ba mốc đó, tiến độ điều phối nằm hết ở OrderSaga.

    public void confirm(String paymentId) {
        this.status = OrderStatus.CONFIRMED;
        this.paymentId = paymentId;
        this.cancelReason = null;
    }

    /** @param reason câu viết cho người đọc; mã lỗi kỹ thuật nằm ở OrderSaga.failureCode. */
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
    }
}

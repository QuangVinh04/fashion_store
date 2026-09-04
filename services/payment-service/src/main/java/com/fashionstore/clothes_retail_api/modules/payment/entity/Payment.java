package com.fashionstore.product.modules.payment.entity;

import com.fashionstore.common.persistence.AuditedEntity;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "payment")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment extends AuditedEntity {

    @Column(name = "order_id", nullable = false, unique = true)
    String orderId;

    @Column(name = "user_id", nullable = false)
    String userId;

    /** correlationId của saga đã yêu cầu thanh toán, để mọi event trả về đúng saga instance. */
    @Column(name = "saga_id", length = 36)
    String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    /** Đơn vị tiền tệ của {@code amount}, echo lại từ AuthorizePaymentCommand. */
    @Column(name = "currency", length = 3)
    String currency;

    @Column(name = "provider_amount", precision = 19, scale = 2)
    BigDecimal providerAmount;

    @Column(name = "provider_currency", length = 3)
    String providerCurrency;

    @Column(name = "transaction_id", unique = true, length = 120)
    String transactionId;

    @Column(name = "merchant_reference", unique = true, length = 100)
    String merchantReference;

    @Column(name = "failure_reason", length = 500)
    String failureReason;

    @Column(name = "paid_at")
    LocalDateTime paidAt;
}

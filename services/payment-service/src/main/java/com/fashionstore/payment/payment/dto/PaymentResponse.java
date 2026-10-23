package com.fashionstore.payment.payment.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentResponse {
    String id;
    String orderId;
    PaymentMethod method;
    PaymentProvider provider;
    PaymentStatus status;
    BigDecimal amount;
    BigDecimal providerAmount;
    String providerCurrency;
    String merchantReference;
    String transactionId;
    String failureReason;
    LocalDateTime paidAt;
    /** Hạn thanh toán; quá hạn thì saga tự huỷ đơn. */
    java.time.Instant authorizationExpiresAt;
}

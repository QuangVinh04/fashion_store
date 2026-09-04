package com.fashionstore.order.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.model.enumeration.OrderStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bản rút gọn dùng cho danh sách. Không kèm item: nạp collection cùng lúc với phân trang buộc Hibernate
 * phải phân trang trong bộ nhớ, và danh sách đơn cũng không cần tới từng dòng hàng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSummaryResponse {
    String id;
    String orderCode;
    OrderStatus status;
    PaymentMethod paymentMethod;
    PaymentProvider paymentProvider;
    String currency;
    BigDecimal totalAmount;
    String cancelReason;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

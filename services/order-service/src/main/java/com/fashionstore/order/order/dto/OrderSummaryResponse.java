package com.fashionstore.order.order.dto;

import com.fashionstore.order.returns.dto.ReturnRefundStatus;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.returns.entity.ReturnRequestKind;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
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
 * Bản rút gọn dùng cho danh sách. Danh sách khách nạp item bằng truy vấn theo các ID đã phân trang,
 * không fetch collection cùng truy vấn phân trang. Danh sách admin giữ bản rút gọn không có item.
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
    @Builder.Default
    java.util.List<OrderItemResponse> items = java.util.List.of();
    String returnRequestId;
    ReturnRequestStatus returnStatus;
    ReturnRequestKind returnKind;
    ReturnRefundStatus returnRefundStatus;
    BigDecimal expectedRefundAmount;
    BigDecimal actualRefundAmount;
    boolean canRequestReturn;
}

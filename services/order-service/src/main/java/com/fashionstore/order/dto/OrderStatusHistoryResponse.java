package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.OrderStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderStatusHistoryResponse {
    String id;
    String orderId;
    OrderStatus fromStatus;
    OrderStatus toStatus;
    String action;
    String changedBy;
    String reason;
    LocalDateTime createdAt;
}

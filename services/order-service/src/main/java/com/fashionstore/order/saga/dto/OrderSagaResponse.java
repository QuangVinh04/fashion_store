package com.fashionstore.order.saga.dto;

import com.fashionstore.order.saga.entity.OrderSagaStatus;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.saga.entity.OrderSagaType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Chỗ duy nhất state saga lộ ra ngoài, chỉ cho ADMIN, phục vụ việc chẩn đoán khi một saga rơi vào
 * FAILED.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderSagaResponse {
    String sagaId;
    String orderId;
    OrderSagaType type;
    OrderSagaStatus status;
    OrderSagaStep currentStep;
    String inventoryReservationId;
    String paymentId;
    String failureCode;
    String failureReason;
    int retryCount;
    LocalDateTime stepDeadline;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime completedAt;
}

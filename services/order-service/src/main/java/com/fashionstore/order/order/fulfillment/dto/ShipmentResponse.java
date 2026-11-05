package com.fashionstore.order.order.fulfillment.dto;

import com.fashionstore.order.order.fulfillment.entity.ShipmentProvider;
import com.fashionstore.order.order.fulfillment.entity.ShipmentStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShipmentResponse {
    String id;
    String orderId;
    ShipmentProvider provider;
    String trackingCode;
    String ghnOrderCode;
    BigDecimal fee;
    Integer weightGram;
    ShipmentStatus status;
    String codStatus;
    BigDecimal codAmount;
    Instant codCollectedAt;
    Instant codTransferredAt;
    Instant deliveredAt;
    Instant handedOffAt;
    String podUrl;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

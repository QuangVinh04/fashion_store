package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

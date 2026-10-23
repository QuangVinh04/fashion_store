package com.fashionstore.order.shipment.dto;

import com.fashionstore.order.shipment.entity.ShipmentProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateShipmentRequest {
    @Builder.Default
    ShipmentProvider provider = ShipmentProvider.GHN;
    String note;
}


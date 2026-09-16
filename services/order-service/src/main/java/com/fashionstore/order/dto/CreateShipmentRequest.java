package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.ShipmentProvider;
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
    Integer toDistrictId;
    String toWardCode;
}


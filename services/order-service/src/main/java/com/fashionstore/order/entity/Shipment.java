package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@Entity
@Table(name = "shipment")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Shipment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    ShipmentProvider provider;

    @Column(name = "tracking_code", length = 100)
    String trackingCode;

    @Column(name = "ghn_order_code", length = 100)
    String ghnOrderCode;

    @Column(name = "to_district_id")
    Integer toDistrictId;

    @Column(name = "to_ward_code", length = 20)
    String toWardCode;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "weight_gram")
    Integer weightGram;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    ShipmentStatus status = ShipmentStatus.PENDING;
}

package com.fashionstore.catalog.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="order_inventory_cancellation") @Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class OrderInventoryCancellation extends BaseEntity {
    @Column(name="cancellation_id",nullable=false,unique=true,length=36) private String cancellationId;
    @Column(name="order_id",nullable=false,unique=true,length=36) private String orderId;
    @Column(name="reservation_id",nullable=false,unique=true,length=36) private String reservationId;
    @Column(name="original_status",nullable=false,length=30) private String originalStatus;
}

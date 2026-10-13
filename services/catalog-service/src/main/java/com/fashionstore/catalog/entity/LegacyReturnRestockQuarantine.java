package com.fashionstore.catalog.entity;
import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
@Entity @Table(name="legacy_return_restock_quarantine") @Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor @FieldDefaults(level=AccessLevel.PRIVATE)
public class LegacyReturnRestockQuarantine extends BaseEntity {
    @Column(name="message_id",nullable=false,unique=true) String messageId;
    @Column(name="order_id",nullable=false) String orderId;
    @Column(nullable=false,columnDefinition="text") String payload;
    @Column(nullable=false,length=30) String status;
    @Column(name="received_at",nullable=false) java.time.Instant receivedAt;
    @Column(name="resolved_by",length=120) String resolvedBy;
    @Column(name="resolved_at") java.time.Instant resolvedAt;
    @Column(name="resolution_reason",length=500) String resolutionReason;
    @Column(name="inventory_operation_id",length=36) String inventoryOperationId;
}

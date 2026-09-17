package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
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

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "return_request")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnRequest extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    Order order;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    String reason;

    @Column(name = "images", columnDefinition = "TEXT")
    String images;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    ReturnRequestStatus status;

    @Column(name = "reject_reason", length = 500)
    String rejectReason;

    @Column(name = "reviewed_by", length = 120)
    String reviewedBy;

    @Column(name = "reviewed_at")
    LocalDateTime reviewedAt;
}

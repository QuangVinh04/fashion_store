package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestKind;
import com.fashionstore.order.entity.enumeration.ReturnShipmentStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnRequestResponse {

    String id;
    ReturnRequestKind kind;
    String verificationBy;
    List<String> verificationEvidenceIds;
    String orderId;
    String orderCode;
    String recipientName;
    String userId;
    String reason;
    List<String> images;
    ReturnRequestStatus status;
    String rejectReason;
    String reviewedBy;
    LocalDateTime reviewedAt;
    Instant approvedAt;
    Instant receivedAt;
    Instant closedAt;
    String approvedRefundAmount;
    String refundLedgerEntryId;
    String receivedBy;
    Boolean qcPassed;
    String qcNote;
    List<String> qcEvidenceIds;
    Instant refundStartedAt;
    String refundFailureReason;
    Instant sendBackBy;
    /** Mã vận đơn GHN chiều về (khách -> kho); null khi chưa duyệt hoặc không đi qua GHN. */
    String returnTrackingCode;
    ReturnShipmentStatus returnShipmentStatus;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

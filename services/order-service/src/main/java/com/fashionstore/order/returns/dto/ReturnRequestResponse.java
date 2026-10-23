package com.fashionstore.order.returns.dto;

import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import com.fashionstore.order.returns.entity.ReturnRequestKind;
import com.fashionstore.order.returns.entity.ReturnShipmentStatus;
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
    ReturnRefundStatus refundStatus;
    /** Destination of refunds under the current wallet-only policy. */
    String refundDestination;
    /** Estimate under the whole-order refund policy; not a promise of payment. */
    String expectedRefundAmount;
    /** Confirmed amount only; null until a refund ledger receipt is recorded. */
    String actualRefundAmount;
    String currency;
    /** Order-service confirmation time, not the wallet's exact posting time. */
    Instant refundedAt;
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
    /** Carrier of the return shipment, independent of the outbound shipment. */
    String returnShippingProvider;
    ReturnShipmentStatus returnShipmentStatus;
    /** Lý do lượt lấy hàng gần nhất thất bại, để admin chọn tạo lại hay hủy phiếu. */
    String returnShipmentFailureCode;
    String returnShipmentFailureReason;
    Instant returnShipmentFailedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

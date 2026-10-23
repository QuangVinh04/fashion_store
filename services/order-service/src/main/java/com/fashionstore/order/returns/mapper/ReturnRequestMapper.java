package com.fashionstore.order.returns.mapper;

import com.fashionstore.order.returns.dto.ReturnRequestResponse;
import com.fashionstore.order.returns.dto.ReturnRefundStatus;
import com.fashionstore.order.returns.entity.ReturnRequest;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** Pure response/JSON mapping; business deadlines are supplied by the calling service. */
@Slf4j
public final class ReturnRequestMapper {
    private ReturnRequestMapper() {}

    public static String serializeImages(ObjectMapper objectMapper, List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> filtered =
                images.stream().filter(img -> img != null && !img.isBlank()).toList();
        if (filtered.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            log.warn("[Return] Failed to serialize images list: {}", e.getMessage());
            return String.join(",", filtered);
        }
    }

    public static List<String> deserializeImages(ObjectMapper objectMapper, String imagesStr) {
        if (imagesStr == null || imagesStr.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imagesStr, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(imagesStr.split(","));
        }
    }

    public static ReturnRequestResponse toResponse(
            ReturnRequest request, ObjectMapper objectMapper, Instant sendBackBy) {
        ReturnRefundStatus refundStatus = refundStatus(request);
        BigDecimal expectedRefundAmount = request.getApprovedRefundAmount() != null
                ? request.getApprovedRefundAmount()
                : request.getOrder() == null ? null : request.getOrder().getTotalAmount();
        return ReturnRequestResponse.builder()
                .id(request.getId())
                .kind(request.getKind())
                .verificationBy(request.getVerificationBy())
                .verificationEvidenceIds(
                        deserializeImages(objectMapper, request.getVerificationEvidenceIds()))
                .orderId(request.getOrder() != null ? request.getOrder().getId() : null)
                .orderCode(request.getOrder() != null ? request.getOrder().getOrderCode() : null)
                .recipientName(
                        request.getOrder() == null || request.getOrder().getAddress() == null
                                ? null
                                : request.getOrder().getAddress().getRecipientName())
                .userId(request.getUserId())
                .reason(request.getReason())
                .images(deserializeImages(objectMapper, request.getImages()))
                .status(request.getStatus())
                .rejectReason(request.getRejectReason())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(request.getReviewedAt())
                .approvedAt(request.getApprovedAt())
                .receivedAt(request.getReceivedAt())
                .closedAt(request.getClosedAt())
                .refundStatus(refundStatus)
                .refundDestination("FASHION_STORE_WALLET")
                .expectedRefundAmount(money(expectedRefundAmount))
                .actualRefundAmount(refundStatus == ReturnRefundStatus.COMPLETED
                        ? money(request.getApprovedRefundAmount()) : null)
                .currency(request.getOrder() == null ? null : request.getOrder().getCurrency())
                .refundedAt(refundStatus == ReturnRefundStatus.COMPLETED ? request.getClosedAt() : null)
                .receivedBy(request.getReceivedBy())
                .qcPassed(request.getQcPassed())
                .qcNote(request.getQcNote())
                .qcEvidenceIds(deserializeImages(objectMapper, request.getQcEvidenceIds()))
                .refundStartedAt(request.getRefundStartedAt())
                .refundFailureReason(request.getRefundFailureReason())
                .sendBackBy(sendBackBy)
                .approvedRefundAmount(
                        request.getApprovedRefundAmount() != null
                                ? request.getApprovedRefundAmount().toPlainString()
                                : null)
                .refundLedgerEntryId(request.getRefundLedgerEntryId())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private static ReturnRefundStatus refundStatus(ReturnRequest request) {
        return refundStatus(request.getStatus(), request.getRefundLedgerEntryId(),
                request.getRefundFailureReason(), request.getRefundStartedAt(), request.getClosedAt());
    }

    public static ReturnRefundStatus refundStatus(ReturnRequestStatus status, String ledgerEntryId,
            String failureReason, Instant startedAt, Instant closedAt) {
        boolean hasReceipt = hasText(ledgerEntryId);
        if (status == ReturnRequestStatus.REFUNDED && hasReceipt) {
            return ReturnRefundStatus.COMPLETED;
        }
        if (status == ReturnRequestStatus.REFUNDED
                || status == ReturnRequestStatus.REFUND_PENDING
                || status == ReturnRequestStatus.LEGACY_REVIEW_REQUIRED
                || hasReceipt
                || hasText(failureReason)
                || startedAt != null && closedAt != null) {
            return ReturnRefundStatus.REVIEW_REQUIRED;
        }
        return startedAt == null
                ? ReturnRefundStatus.NOT_STARTED : ReturnRefundStatus.PROCESSING;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}

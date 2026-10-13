package com.fashionstore.order.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.order.OrderDeliveredEvent;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent;
import com.fashionstore.order.client.CatalogFeignClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.dto.CancelReturnRequest;
import com.fashionstore.order.dto.CreateFulfillmentReturnRequest;
import com.fashionstore.order.dto.ReceiveReturnRequest;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestKind;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.mapper.ReturnRequestMapper;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ReturnRequestRepository;
import com.fashionstore.order.repository.ShipmentRepository;
import com.fashionstore.order.service.OrderReceiptService;
import com.fashionstore.order.service.ReturnService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReturnServiceImpl implements ReturnService {

    ReturnRequestRepository returnRequestRepository;
    OrderRepository orderRepository;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    CurrentUserProvider currentUserProvider;
    ObjectMapper objectMapper;
    CatalogFeignClient catalogFeignClient;
    PlatformTransactionManager transactionManager;
    Clock clock;
    GhnClient ghnClient;
    ShipmentRepository shipmentRepository;
    OutboxEventRecorder outboxEventRecorder;

    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(String orderId, ReturnOrderRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        Order order =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new AppException(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED);
        }

        if (order.getDeliveredAt() == null
                || order.getCompletedAt() != null
                || !clock.instant()
                        .isBefore(order.getDeliveredAt().plus(OrderReceiptService.RETURN_WINDOW))) {
            throw new AppException(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED);
        }

        Optional<ReturnRequest> existingOpt =
                returnRequestRepository.findByOrderIdForUpdate(orderId);
        if (existingOpt.isPresent()) {
            ReturnRequest existing = existingOpt.get();
            if (existing.getStatus() == ReturnRequestStatus.REQUESTED
                    || existing.getStatus() == ReturnRequestStatus.PENDING) {
                log.info(
                        "[Return] Return request for order {} already requested, returning"
                                + " existing",
                        orderId);
                return toResponse(existing);
            }
            throw new AppException(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED);
        }

        String reason =
                (request == null || request.getReason() == null || request.getReason().isBlank())
                        ? "Khách hàng yêu cầu trả hàng"
                        : request.getReason().trim();

        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId(userId)
                        .reason(reason)
                        .images(
                                ReturnRequestMapper.serializeImages(
                                        objectMapper, request != null ? request.getImages() : null))
                        .status(ReturnRequestStatus.REQUESTED)
                        .build();
        returnRequest = returnRequestRepository.save(returnRequest);

        OrderStatusHistory history =
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(OrderStatus.DELIVERED)
                        .toStatus(OrderStatus.DELIVERED)
                        .action("RETURN_REQUESTED")
                        .changedBy(userId)
                        .reason(reason)
                        .build();
        orderStatusHistoryRepository.save(history);

        log.info("[Return] Created return request {} for order {}", returnRequest.getId(), orderId);
        return toResponse(returnRequest);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse approveReturn(String returnRequestId) {
        String adminId = currentUserProvider.getCurrentUserId();
        String orderId =
                returnRequestRepository.findOrderIdByReturnId(returnRequestId).orElse(null);
        if (orderId != null) {
            orderRepository.findByIdForUpdate(orderId);
        }
        ReturnRequest returnRequest =
                returnRequestRepository
                        .findByIdForUpdate(returnRequestId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (returnRequest.getOrder() != null
                && returnRequest.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        }

        if (returnRequest.getStatus() == ReturnRequestStatus.APPROVED
                || returnRequest.getStatus() == ReturnRequestStatus.RETURN_APPROVED) {
            return toResponse(returnRequest);
        }
        if (returnRequest.getStatus() != ReturnRequestStatus.REQUESTED
                && returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new AppException(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED);
        }

        returnRequest.setStatus(ReturnRequestStatus.APPROVED);
        returnRequest.setReviewedBy(adminId);
        returnRequest.setReviewedAt(LocalDateTime.now(clock));
        returnRequest.setApprovedAt(clock.instant());
        returnRequest = returnRequestRepository.save(returnRequest);

        if (returnRequest.getOrder() != null) {
            OrderStatusHistory history =
                    OrderStatusHistory.builder()
                            .order(returnRequest.getOrder())
                            .fromStatus(OrderStatus.DELIVERED)
                            .toStatus(OrderStatus.DELIVERED)
                            .action("RETURN_APPROVED")
                            .changedBy(adminId)
                            .reason("Admin phê duyệt yêu cầu trả hàng")
                            .build();
            orderStatusHistoryRepository.save(history);
        }

        log.info(
                "[Return] Approved return request {} for order {}",
                returnRequestId,
                returnRequest.getOrder() != null ? returnRequest.getOrder().getId() : null);
        return toResponse(returnRequest);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse rejectReturn(String returnRequestId, RejectReturnRequest request) {
        String adminId = currentUserProvider.getCurrentUserId();
        if (request == null
                || request.getRejectReason() == null
                || request.getRejectReason().isBlank()
                || request.getRejectReason().length() > 500) {
            throw new AppException(OrderErrorCode.RETURN_REJECT_REASON_REQUIRED);
        }

        String orderId =
                returnRequestRepository.findOrderIdByReturnId(returnRequestId).orElse(null);
        if (orderId != null) {
            orderRepository.findByIdForUpdate(orderId);
        }
        ReturnRequest returnRequest =
                returnRequestRepository
                        .findByIdForUpdate(returnRequestId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (returnRequest.getStatus() == ReturnRequestStatus.REJECTED) {
            return toResponse(returnRequest);
        }
        if (returnRequest.getRefundStartedAt() != null
                || returnRequest.getStatus() != ReturnRequestStatus.REQUESTED
                        && returnRequest.getStatus() != ReturnRequestStatus.PENDING
                        && !(returnRequest.getStatus() == ReturnRequestStatus.RECEIVED
                                && Boolean.FALSE.equals(returnRequest.getQcPassed()))) {
            throw new AppException(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED);
        }

        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        returnRequest.setRejectReason(request.getRejectReason().trim());
        returnRequest.setReviewedBy(adminId);
        returnRequest.setReviewedAt(LocalDateTime.now(clock));
        returnRequest.setClosedAt(clock.instant());
        returnRequest = returnRequestRepository.save(returnRequest);

        if (returnRequest.getOrder() != null) {
            OrderStatusHistory history =
                    OrderStatusHistory.builder()
                            .order(returnRequest.getOrder())
                            .fromStatus(returnRequest.getOrder().getStatus())
                            .toStatus(returnRequest.getOrder().getStatus())
                            .action("RETURN_REJECTED")
                            .changedBy(adminId)
                            .reason(request.getRejectReason().trim())
                            .build();
            orderStatusHistoryRepository.save(history);
        }

        log.info(
                "[Return] Rejected return request {} for order {}",
                returnRequestId,
                returnRequest.getOrder() != null ? returnRequest.getOrder().getId() : null);
        return toResponse(returnRequest);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse receiveReturn(
            String returnRequestId, ReceiveReturnRequest request) {
        String adminId = currentUserProvider.getCurrentUserId();
        if (request == null
                || request.qcPassed() == null
                || request.qcNote() == null
                || request.qcNote().isBlank()
                || request.qcNote().length() > 500) {
            throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
        }
        String evidenceJson = verifyAdminEvidence(request.mediaIds(), adminId, !request.qcPassed());
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            String orderId =
                                    returnRequestRepository
                                            .findOrderIdByReturnId(returnRequestId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .RETURN_REQUEST_NOT_FOUND));
                            orderRepository
                                    .findByIdForUpdate(orderId)
                                    .orElseThrow(
                                            () -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
                            ReturnRequest returned =
                                    returnRequestRepository
                                            .findByIdForUpdate(returnRequestId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .RETURN_REQUEST_NOT_FOUND));
                            if (returned.getStatus() == ReturnRequestStatus.RECEIVED) {
                                if (!request.qcPassed().equals(returned.getQcPassed())
                                        || !request.qcNote().trim().equals(returned.getQcNote())
                                        || !java.util.Objects.equals(
                                                evidenceJson, returned.getQcEvidenceIds())) {
                                    throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
                                }
                                return toResponse(returned);
                            }
                            if (returned.getStatus() != ReturnRequestStatus.APPROVED
                                            && returned.getStatus()
                                                    != ReturnRequestStatus.RETURN_APPROVED
                                    || returned.getKind() == ReturnRequestKind.VERIFIED_LOSS
                                    || returned.getKind() == ReturnRequestKind.CUSTOMER_RETURN
                                            && returned.getOrder().getStatus()
                                                    != OrderStatus.DELIVERED
                                    || returned.getKind() == ReturnRequestKind.CARRIER_RETURN
                                            && returned.getOrder().getStatus()
                                                    != OrderStatus.RETURNED) {
                                throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
                            }
                            returned.setStatus(ReturnRequestStatus.RECEIVED);
                            returned.setReceivedAt(clock.instant());
                            returned.setReceivedBy(adminId);
                            returned.setQcPassed(request.qcPassed());
                            returned.setQcNote(request.qcNote().trim());
                            returned.setQcEvidenceIds(evidenceJson);
                            returnRequestRepository.save(returned);
                            orderStatusHistoryRepository.save(
                                    OrderStatusHistory.builder()
                                            .order(returned.getOrder())
                                            .fromStatus(OrderStatus.DELIVERED)
                                            .toStatus(OrderStatus.DELIVERED)
                                            .action("RETURN_RECEIVED")
                                            .changedBy(adminId)
                                            .reason(
                                                    request.qcPassed()
                                                            ? "Return received, QC passed"
                                                            : "Return received, QC failed")
                                            .build());
                            return toResponse(returned);
                        });
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse cancelReturn(String returnRequestId, CancelReturnRequest request) {
        String adminId = currentUserProvider.getCurrentUserId();
        if (request == null
                || request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new AppException(OrderErrorCode.RETURN_REJECT_REASON_REQUIRED);
        }
        String orderId =
                returnRequestRepository
                        .findOrderIdByReturnId(returnRequestId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        ReturnRequest returned =
                returnRequestRepository
                        .findByIdForUpdate(returnRequestId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        if (returned.getStatus() == ReturnRequestStatus.CANCELLED) {
            return toResponse(returned);
        }
        if (returned.getKind() == ReturnRequestKind.VERIFIED_LOSS) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        if (returned.getReceivedAt() != null
                || returned.getRefundStartedAt() != null
                || returned.getStatus() != ReturnRequestStatus.REQUESTED
                        && returned.getStatus() != ReturnRequestStatus.APPROVED
                        && returned.getStatus() != ReturnRequestStatus.PENDING
                        && returned.getStatus() != ReturnRequestStatus.RETURN_APPROVED) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        returned.setStatus(ReturnRequestStatus.CANCELLED);
        returned.setRejectReason(request.reason().trim());
        returned.setReviewedBy(adminId);
        returned.setReviewedAt(LocalDateTime.now(clock));
        returned.setClosedAt(clock.instant());
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .order(returned.getOrder())
                        .fromStatus(OrderStatus.DELIVERED)
                        .toStatus(OrderStatus.DELIVERED)
                        .action("RETURN_CANCELLED")
                        .changedBy(adminId)
                        .reason(request.reason().trim())
                        .build());
        returnRequestRepository.save(returned);
        return toResponse(returned);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse refundReturn(String returnRequestId, BigDecimal requestedAmount) {
        String adminId = currentUserProvider.getCurrentUserId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ReturnRequestResponse prepared =
                transaction.execute(
                        status -> {
                            String orderId =
                                    returnRequestRepository
                                            .findOrderIdByReturnId(returnRequestId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .RETURN_REQUEST_NOT_FOUND));
                            Order order =
                                    orderRepository
                                            .findByIdForUpdate(orderId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .ORDER_NOT_FOUND));
                            ReturnRequest returned =
                                    returnRequestRepository
                                            .findByIdForUpdate(returnRequestId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .RETURN_REQUEST_NOT_FOUND));
                            if (returned.getStatus() == ReturnRequestStatus.REFUNDED) {
                                return toResponse(returned);
                            }
                            boolean loss = returned.getKind() == ReturnRequestKind.VERIFIED_LOSS;
                            boolean carrier =
                                    returned.getKind() == ReturnRequestKind.CARRIER_RETURN;
                            if (carrier && order.getPaymentMethod() == PaymentMethod.COD) {
                                var shipment =
                                        shipmentRepository
                                                .findByOrderIdForUpdate(orderId)
                                                .orElseThrow();
                                if (shipment.getCodCollectedAt() == null) {
                                    throw new AppException(
                                            OrderErrorCode.RETURN_REFUND_AMOUNT_INVALID);
                                }
                                // A later verified COD collection can make an earlier no-funds
                                // closure refundable.
                                if (returned.getRefundStartedAt() == null
                                        && returned.getApprovedRefundAmount() != null
                                        && returned.getApprovedRefundAmount().signum() == 0
                                        && Boolean.TRUE.equals(returned.getQcPassed())
                                        && returned.getReceivedAt() != null) {
                                    returned.setApprovedRefundAmount(null);
                                    returned.setStatus(ReturnRequestStatus.RECEIVED);
                                    returned.setClosedAt(null);
                                    returned.setRejectReason(null);
                                }
                            }
                            if (loss
                                    ? returned.getStatus() != ReturnRequestStatus.APPROVED
                                            || returned.getVerificationBy() == null
                                            || ReturnRequestMapper.deserializeImages(
                                                            objectMapper,
                                                            returned.getVerificationEvidenceIds())
                                                    .isEmpty()
                                            || order.getStatus() != OrderStatus.LOST
                                    : returned.getStatus() != ReturnRequestStatus.RECEIVED
                                            || !Boolean.TRUE.equals(returned.getQcPassed())
                                            || order.getStatus()
                                                    != (carrier
                                                            ? OrderStatus.RETURNED
                                                            : OrderStatus.DELIVERED)) {
                                throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
                            }
                            if (returned.getClosedAt() != null) {
                                throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
                            }
                            BigDecimal fullAmount = order.getTotalAmount();
                            if (!"VND".equals(order.getCurrency())
                                    || fullAmount == null
                                    || fullAmount.signum() <= 0
                                    || fullAmount.stripTrailingZeros().scale() > 0
                                    || requestedAmount != null
                                            && requestedAmount.compareTo(fullAmount) != 0
                                    || returned.getApprovedRefundAmount() != null
                                            && returned.getApprovedRefundAmount()
                                                            .compareTo(fullAmount)
                                                    != 0) {
                                throw new AppException(OrderErrorCode.RETURN_REFUND_AMOUNT_INVALID);
                            }
                            try {
                                fullAmount.longValueExact();
                            } catch (ArithmeticException invalidAmount) {
                                throw new AppException(OrderErrorCode.RETURN_REFUND_AMOUNT_INVALID);
                            }
                            if (returned.getRefundStartedAt() == null) {
                                returned.setRefundStartedAt(clock.instant());
                                returned.setReviewedAt(LocalDateTime.now(clock));
                                returned.setReviewedBy(adminId);
                            }
                            if (order.getPaymentId() == null || order.getPaymentId().isBlank()) {
                                throw new AppException(OrderErrorCode.RETURN_REFUND_AMOUNT_INVALID);
                            }
                            returned.setApprovedRefundAmount(fullAmount);
                            returned.setRefundFailureReason(null);
                            returnRequestRepository.saveAndFlush(returned);
                            String reasonType =
                                    returned.getKind() == ReturnRequestKind.CUSTOMER_RETURN
                                            ? "RETURN_REFUND"
                                            : returned.getKind().name();
                            outboxEventRecorder.saveMessage(
                                    orderId,
                                    EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                    EventEnvelope.v1(
                                            EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                                            orderId,
                                            returned.getId(),
                                            new RefundOrderFundsCommand(
                                                    "return:" + returned.getId(),
                                                    orderId,
                                                    order.getPaymentId(),
                                                    fullAmount,
                                                    "VND",
                                                    reasonType,
                                                    returned.getId())));
                            return toResponse(returned);
                        });
        if (prepared.getStatus() == ReturnRequestStatus.REFUNDED) {
            return prepared;
        }

        return prepared;
    }

    @Override
    @Transactional
    public void refundCredited(OrderFundsRefundCreditedEvent reply) {
        Order order =
                orderRepository
                        .findByIdForUpdate(reply.orderId())
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        ReturnRequest returned =
                returnRequestRepository
                        .findByIdForUpdate(reply.sourceId())
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        String reasonType =
                returned.getKind() == ReturnRequestKind.CUSTOMER_RETURN
                        ? "RETURN_REFUND"
                        : returned.getKind().name();
        if (!Objects.equals(returned.getOrder().getId(), reply.orderId())
                || !Objects.equals(order.getPaymentId(), reply.paymentId())
                || !Objects.equals("return:" + returned.getId(), reply.refundId())
                || !Objects.equals(reasonType, reply.reasonType())
                || returned.getRefundStartedAt() == null
                || returned.getApprovedRefundAmount() == null
                || reply.amount() == null
                || returned.getApprovedRefundAmount().compareTo(reply.amount()) != 0
                || order.getTotalAmount() == null
                || order.getTotalAmount().compareTo(reply.amount()) != 0
                || reply.ledgerEntryId() == null
                || reply.ledgerEntryId().isBlank()
                || reply.ledgerEntryId().length() > 36) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        if (returned.getStatus() == ReturnRequestStatus.REFUNDED) {
            if (!Objects.equals(returned.getRefundLedgerEntryId(), reply.ledgerEntryId())) {
                throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
            }
            return;
        }
        OrderStatus expectedOrder =
                returned.getKind() == ReturnRequestKind.VERIFIED_LOSS
                        ? OrderStatus.LOST
                        : returned.getKind() == ReturnRequestKind.CARRIER_RETURN
                                ? OrderStatus.RETURNED
                                : OrderStatus.DELIVERED;
        ReturnRequestStatus expectedReturn =
                returned.getKind() == ReturnRequestKind.VERIFIED_LOSS
                        ? ReturnRequestStatus.APPROVED
                        : ReturnRequestStatus.RECEIVED;
        if (order.getStatus() != expectedOrder
                || returned.getStatus() != expectedReturn
                || returned.getClosedAt() != null
                || returned.getKind() != ReturnRequestKind.VERIFIED_LOSS
                        && !Boolean.TRUE.equals(returned.getQcPassed())) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        returned.setStatus(ReturnRequestStatus.REFUNDED);
        returned.setRefundLedgerEntryId(reply.ledgerEntryId());
        returned.setRefundFailureReason(null);
        returned.setClosedAt(clock.instant());
        returnRequestRepository.save(returned);
        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(expectedOrder)
                        .toStatus(OrderStatus.REFUNDED)
                        .action("RETURN_REFUNDED")
                        .changedBy("SYSTEM")
                        .reason(
                                "Wallet refund confirmed: "
                                        + reply.amount().toPlainString()
                                        + " VND")
                        .build());
    }

    @Override
    @Transactional
    public void refundRejected(OrderFundsRefundRejectedEvent reply) {
        Order order =
                orderRepository
                        .findByIdForUpdate(reply.orderId())
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        ReturnRequest returned =
                returnRequestRepository
                        .findByIdForUpdate(reply.sourceId())
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        String reasonType =
                returned.getKind() == ReturnRequestKind.CUSTOMER_RETURN
                        ? "RETURN_REFUND"
                        : returned.getKind().name();
        if (!Objects.equals(returned.getOrder().getId(), reply.orderId())
                || !Objects.equals(order.getPaymentId(), reply.paymentId())
                || !Objects.equals("return:" + returned.getId(), reply.refundId())
                || !Objects.equals(reasonType, reply.reasonType())
                || returned.getRefundStartedAt() == null
                || reply.code() == null
                || reply.code().isBlank()
                || reply.reason() == null
                || reply.reason().isBlank()) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        // A delayed rejection must never undo an already confirmed financial receipt.
        if (returned.getStatus() == ReturnRequestStatus.REFUNDED) return;
        if (returned.getClosedAt() != null
                || returned.getStatus()
                        != (returned.getKind() == ReturnRequestKind.VERIFIED_LOSS
                                ? ReturnRequestStatus.APPROVED
                                : ReturnRequestStatus.RECEIVED)) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        String failure = reply.code() + ": " + reply.reason();
        failure = failure.substring(0, Math.min(500, failure.length()));
        if (Objects.equals(returned.getRefundFailureReason(), failure)) return;
        returned.setRefundFailureReason(failure);
        returnRequestRepository.save(returned);
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(order.getStatus())
                        .toStatus(order.getStatus())
                        .action("RETURN_REFUND_REJECTED")
                        .changedBy("SYSTEM")
                        .reason(failure)
                        .build());
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse createFulfillmentReturn(
            String orderId, ReturnRequestKind kind, CreateFulfillmentReturnRequest request) {
        String adminId = currentUserProvider.getCurrentUserId();
        if (kind == null
                || kind == ReturnRequestKind.CUSTOMER_RETURN
                || request == null
                || request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        String evidence =
                verifyAdminEvidence(
                        request.mediaIds(), adminId, kind == ReturnRequestKind.VERIFIED_LOSS);
        var snapshot =
                shipmentRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.SHIPMENT_REQUIRED));
        String tracking =
                snapshot.getGhnOrderCode() == null
                        ? snapshot.getTrackingCode()
                        : snapshot.getGhnOrderCode();
        var verified = ghnClient.getVerifiedShipment(tracking);
        String requiredCarrierStatus =
                kind == ReturnRequestKind.VERIFIED_LOSS ? "lost" : "returned";
        if (verified == null
                || !requiredCarrierStatus.equals(verified.status())
                || !tracking.equals(verified.orderCode())) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        return new TransactionTemplate(transactionManager)
                .execute(
                        transaction -> {
                            Order order =
                                    orderRepository
                                            .findByIdForUpdate(orderId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .ORDER_NOT_FOUND));
                            var existing =
                                    returnRequestRepository
                                            .findByOrderIdForUpdate(orderId)
                                            .orElse(null);
                            if (existing != null) {
                                if (existing.getKind() != kind
                                        || !request.reason().trim().equals(existing.getReason())
                                        || !Objects.equals(
                                                evidence, existing.getVerificationEvidenceIds())) {
                                    throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
                                }
                                return toResponse(existing);
                            }
                            var shipment =
                                    shipmentRepository
                                            .findByOrderIdForUpdate(orderId)
                                            .orElseThrow();
                            if (!Objects.equals(snapshot.getId(), shipment.getId())
                                    || !Objects.equals(
                                            order.getOrderCode(), verified.clientOrderCode())
                                    || !tracking.equals(shipment.getGhnOrderCode())
                                            && !tracking.equals(shipment.getTrackingCode())
                                    || !Set.of(
                                                    OrderStatus.CONFIRMED,
                                                    OrderStatus.PROCESSING,
                                                    OrderStatus.PACKED,
                                                    OrderStatus.SHIPPING,
                                                    OrderStatus.RETURNED,
                                                    OrderStatus.LOST)
                                            .contains(order.getStatus())) {
                                throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                            }
                            boolean unpaidCod =
                                    order.getPaymentMethod() == PaymentMethod.COD
                                            && !verified.codCollected();
                            if (unpaidCod && kind == ReturnRequestKind.VERIFIED_LOSS) {
                                throw new AppException(OrderErrorCode.RETURN_REFUND_AMOUNT_INVALID);
                            }
                            if (order.getPaymentMethod() == PaymentMethod.COD) {
                                if (verified.codAmount() == null
                                        || verified.codAmount().compareTo(order.getTotalAmount())
                                                != 0) {
                                    throw new AppException(
                                            OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                                }
                                if (verified.codCollected()
                                        && shipment.getCodCollectedAt() == null) {
                                    if (verified.codCollectedAt() == null
                                            || verified.codCollectedAt()
                                                    .isAfter(clock.instant().plusSeconds(300))) {
                                        throw new AppException(
                                                OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                                    }
                                    shipment.setCodAmount(verified.codAmount());
                                    shipment.setCodCollectedAt(verified.codCollectedAt());
                                    shipment.setCodStatus("COLLECTED");
                                    outboxEventRecorder.saveMessage(
                                            orderId,
                                            EventTypes.ORDER_DELIVERED,
                                            EventEnvelope.v1(
                                                    EventTypes.ORDER_DELIVERED,
                                                    orderId,
                                                    orderId,
                                                    new OrderDeliveredEvent(
                                                            orderId,
                                                            order.getDeliveredAt() == null
                                                                    ? null
                                                                    : LocalDateTime.ofInstant(
                                                                            order.getDeliveredAt(),
                                                                            ZoneOffset.UTC),
                                                            shipment.getCodAmount(),
                                                            shipment.getCodCollectedAt(),
                                                            tracking,
                                                            verified.shopId())));
                                }
                            } else if (verified.codAmount() == null
                                    || verified.codAmount().signum() != 0) {
                                throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                            }
                            OrderStatus before = order.getStatus();
                            order.setStatus(
                                    kind == ReturnRequestKind.VERIFIED_LOSS
                                            ? OrderStatus.LOST
                                            : OrderStatus.RETURNED);
                            shipment.setStatus(
                                    kind == ReturnRequestKind.VERIFIED_LOSS
                                            ? ShipmentStatus.LOST
                                            : ShipmentStatus.RETURNED);
                            orderRepository.save(order);
                            shipmentRepository.save(shipment);
                            ReturnRequest returned =
                                    returnRequestRepository.saveAndFlush(
                                            ReturnRequest.builder()
                                                    .order(order)
                                                    .userId(order.getUserId())
                                                    .kind(kind)
                                                    .reason(request.reason().trim())
                                                    .status(ReturnRequestStatus.APPROVED)
                                                    .approvedAt(clock.instant())
                                                    .reviewedAt(LocalDateTime.now(clock))
                                                    .reviewedBy(adminId)
                                                    .verificationBy(adminId)
                                                    .verificationEvidenceIds(evidence)
                                                    .approvedRefundAmount(
                                                            unpaidCod ? BigDecimal.ZERO : null)
                                                    .build());
                            orderStatusHistoryRepository.save(
                                    OrderStatusHistory.builder()
                                            .order(order)
                                            .fromStatus(before)
                                            .toStatus(order.getStatus())
                                            .action(kind.name() + "_VERIFIED")
                                            .changedBy(adminId)
                                            .reason(request.reason().trim())
                                            .build());
                            return toResponse(returned);
                        });
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse closeUnpaidCarrierReturn(
            String returnRequestId, CancelReturnRequest request) {
        String adminId = currentUserProvider.getCurrentUserId();
        if (request == null
                || request.reason() == null
                || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new AppException(OrderErrorCode.RETURN_REJECT_REASON_REQUIRED);
        }
        String orderId =
                returnRequestRepository.findOrderIdByReturnId(returnRequestId).orElseThrow();
        var shipment = shipmentRepository.findByOrderId(orderId).orElseThrow();
        String tracking =
                shipment.getGhnOrderCode() == null
                        ? shipment.getTrackingCode()
                        : shipment.getGhnOrderCode();
        var verified = ghnClient.getVerifiedShipment(tracking);
        if (verified == null || verified.codCollected() || !"returned".equals(verified.status())) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        return new TransactionTemplate(transactionManager)
                .execute(
                        transaction -> {
                            Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
                            ReturnRequest returned =
                                    returnRequestRepository
                                            .findByIdForUpdate(returnRequestId)
                                            .orElseThrow();
                            var currentShipment =
                                    shipmentRepository
                                            .findByOrderIdForUpdate(orderId)
                                            .orElseThrow();
                            if (returned.getKind() != ReturnRequestKind.CARRIER_RETURN
                                    || order.getPaymentMethod() != PaymentMethod.COD
                                    || order.getStatus() != OrderStatus.RETURNED
                                    || currentShipment.getCodCollectedAt() != null
                                    || !tracking.equals(verified.orderCode())
                                    || !Objects.equals(
                                            order.getOrderCode(), verified.clientOrderCode())
                                    || returned.getRefundStartedAt() != null
                                    || !Boolean.TRUE.equals(returned.getQcPassed())
                                    || returned.getReceivedAt() == null) {
                                throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
                            }
                            if (returned.getStatus() == ReturnRequestStatus.CANCELLED) {
                                return toResponse(returned);
                            }
                            if (returned.getStatus() != ReturnRequestStatus.RECEIVED) {
                                throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
                            }
                            returned.setStatus(ReturnRequestStatus.CANCELLED);
                            returned.setClosedAt(clock.instant());
                            returned.setApprovedRefundAmount(BigDecimal.ZERO);
                            String fullReason =
                                    "No COD collected; no refund due. " + request.reason().trim();
                            returned.setRejectReason(
                                    fullReason.length() > 500
                                            ? fullReason.substring(0, 500)
                                            : fullReason);
                            returned.setReviewedBy(adminId);
                            returned.setReviewedAt(LocalDateTime.now(clock));
                            returnRequestRepository.save(returned);
                            orderStatusHistoryRepository.save(
                                    OrderStatusHistory.builder()
                                            .order(order)
                                            .fromStatus(order.getStatus())
                                            .toStatus(order.getStatus())
                                            .action("CARRIER_RETURN_CLOSED_NO_REFUND")
                                            .changedBy(adminId)
                                            .reason(request.reason().trim())
                                            .build());
                            return toResponse(returned);
                        });
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public String getVerificationEvidenceReadUrl(String returnRequestId, String mediaId) {
        ReturnRequest returned = returnRequestRepository.findById(returnRequestId).orElseThrow();
        if (returned.getVerificationBy() == null
                || !ReturnRequestMapper.deserializeImages(
                                objectMapper, returned.getVerificationEvidenceIds())
                        .contains(mediaId)) {
            throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
        }
        var response = catalogFeignClient.getEvidenceReadUrl(mediaId, returned.getVerificationBy());
        if (response == null
                || response.getCode() != 1000
                || response.getData() == null
                || response.getData().isBlank()) {
            throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
        }
        return response.getData();
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public String getQcEvidenceReadUrl(String returnRequestId, String mediaId) {
        ReturnRequest returned =
                returnRequestRepository
                        .findById(returnRequestId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        if (returned.getReceivedBy() == null
                || !ReturnRequestMapper.deserializeImages(objectMapper, returned.getQcEvidenceIds())
                        .contains(mediaId)) {
            throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
        }
        var response = catalogFeignClient.getEvidenceReadUrl(mediaId, returned.getReceivedBy());
        if (response == null
                || response.getCode() != 1000
                || response.getData() == null
                || response.getData().isBlank()) {
            throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
        }
        return response.getData();
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getReturnRequestByOrderId(String orderId) {
        String currentUserId = currentUserProvider.getCurrentUserId();
        ReturnRequest returnRequest =
                returnRequestRepository
                        .findByOrderId(orderId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (!returnRequest.getUserId().equals(currentUserId)) {
            throw new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND);
        }

        return toResponse(returnRequest);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse getReturnRequestById(String returnRequestId) {
        ReturnRequest returnRequest =
                returnRequestRepository
                        .findById(returnRequestId)
                        .orElseThrow(
                                () -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        return toResponse(returnRequest);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<List<ReturnRequestResponse>> searchReturnRequests(
            ReturnRequestStatus status, Pageable pageable) {
        Page<ReturnRequest> page =
                status != null
                        ? returnRequestRepository.findByStatus(status, pageable)
                        : returnRequestRepository.findAll(pageable);

        List<ReturnRequestResponse> content =
                page.getContent().stream().map(this::toResponse).toList();

        return PageResponse.<List<ReturnRequestResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(page.getTotalPages())
                .items(content)
                .build();
    }

    private String verifyAdminEvidence(List<String> mediaIds, String actorId, boolean required) {
        List<String> evidence = mediaIds == null ? List.of() : new java.util.ArrayList<>(mediaIds);
        if (evidence.size() > 5
                || required && evidence.isEmpty()
                || evidence.stream().distinct().count() != evidence.size()) {
            throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
        }
        for (String id : evidence) {
            if (id == null || id.isBlank() || id.length() > 36) {
                throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
            }
            var response = catalogFeignClient.getReturnEvidence(id, actorId);
            var metadata = response == null ? null : response.getData();
            if (response == null
                    || response.getCode() != 1000
                    || metadata == null
                    || !id.equals(metadata.id())
                    || !actorId.equals(metadata.ownerId())
                    || !"RETURN_EVIDENCE".equals(metadata.purpose())
                    || !"PRIVATE".equals(metadata.visibility())
                    || !"ACTIVE".equals(metadata.status())
                    || metadata.contentType() == null
                    || !metadata.contentType().startsWith("image/")) {
                throw new AppException(OrderErrorCode.RETURN_EVIDENCE_INVALID);
            }
        }
        return ReturnRequestMapper.serializeImages(objectMapper, evidence);
    }

    private ReturnRequestResponse toResponse(ReturnRequest request) {
        Instant sendBackBy =
                request.getKind() != ReturnRequestKind.CUSTOMER_RETURN
                                || request.getApprovedAt() == null
                        ? null
                        : request.getApprovedAt().plus(OrderReceiptService.RETURN_WINDOW);
        return ReturnRequestMapper.toResponse(request, objectMapper, sendBackBy);
    }
}

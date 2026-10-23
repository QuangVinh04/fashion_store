package com.fashionstore.payment.payment.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.contracts.payment.command.RefundWalletSagaCommand;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent;
import com.fashionstore.contracts.payment.event.WalletSagaCompensatedEvent;
import com.fashionstore.contracts.payment.event.WalletSagaCompensationRejectedEvent;
import com.fashionstore.payment.payment.dto.RefundWalletResponse;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentRefund;
import com.fashionstore.payment.payment.entity.PaymentRefundStatus;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import com.fashionstore.payment.wallet.exception.WalletErrorCode;
import com.fashionstore.payment.wallet.service.WalletLedger;
import com.fashionstore.payment.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** Order refunds and wallet-saga compensations, always credited to the buyer's wallet. */
@Service
@RequiredArgsConstructor
public class OrderFundsRefundService {

    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final WalletLedger ledger;
    private final OutboxEventRecorder outbox;

    @Transactional
    public void refundOrderFunds(
            RefundOrderFundsCommand command, String messageId, String correlationId) {
        RefundWalletResponse result;
        try {
            if (command.paymentId() == null
                    || command.reasonType() == null
                    || !Set.of(
                                    "PAID_CANCELLATION",
                                    "RETURN_REFUND",
                                    "VERIFIED_LOSS",
                                    "CARRIER_RETURN",
                                    "LATE_ONLINE",
                                    "SAGA_COMPENSATION")
                            .contains(command.reasonType())) {
                throw new AppException(WalletErrorCode.SOURCE_INVALID);
            }
            result =
                    creditOrderRefund(
                            null,
                            command.orderId(),
                            command.paymentId(),
                            command.amount(),
                            command.currency(),
                            command.reasonType(),
                            command.sourceId(),
                            command.refundId(),
                            command.reasonType());
        } catch (AppException rejected) {
            outbox.saveMessage(
                    command.orderId(),
                    EventTypes.ORDER_FUNDS_REFUND_REJECTED,
                    EventEnvelope.v1(
                            EventTypes.ORDER_FUNDS_REFUND_REJECTED,
                            command.orderId(),
                            correlationId,
                            new OrderFundsRefundRejectedEvent(
                                    command.refundId(),
                                    command.orderId(),
                                    command.paymentId(),
                                    command.reasonType(),
                                    command.sourceId(),
                                    String.valueOf(rejected.getErrorCode().getCode()),
                                    rejected.getMessage())));
            return;
        }
        outbox.saveMessage(
                command.orderId(),
                EventTypes.ORDER_FUNDS_REFUND_CREDITED,
                EventEnvelope.v1(
                        EventTypes.ORDER_FUNDS_REFUND_CREDITED,
                        command.orderId(),
                        correlationId,
                        new OrderFundsRefundCreditedEvent(
                                command.refundId(),
                                command.orderId(),
                                command.paymentId(),
                                command.reasonType(),
                                command.sourceId(),
                                result.ledgerEntryId(),
                                BigDecimal.valueOf(result.amount()))));
    }

    @Transactional
    public void compensate(
            RefundWalletSagaCommand command, String messageId, String correlationId) {
        RefundWalletResponse result;
        try {
            if (command.generation() != 0 || command.paymentId() == null) {
                throw new AppException(WalletErrorCode.SOURCE_INVALID);
            }
            result =
                    creditOrderRefund(
                            null,
                            command.orderId(),
                            command.paymentId(),
                            command.amount(),
                            command.currency(),
                            "WALLET_SAGA_COMPENSATION",
                            command.paymentId(),
                            command.compensationId(),
                            command.reason());
        } catch (AppException rejected) {
            outbox.saveMessage(
                    command.orderId(),
                    EventTypes.WALLET_SAGA_COMPENSATION_REJECTED,
                    EventEnvelope.v1(
                            EventTypes.WALLET_SAGA_COMPENSATION_REJECTED,
                            command.orderId(),
                            correlationId,
                            new WalletSagaCompensationRejectedEvent(
                                    command.compensationId(),
                                    command.orderId(),
                                    command.paymentId(),
                                    String.valueOf(rejected.getErrorCode().getCode()),
                                    rejected.getMessage())));
            return;
        }
        outbox.saveMessage(
                command.orderId(),
                EventTypes.WALLET_SAGA_COMPENSATION_COMPLETED,
                EventEnvelope.v1(
                        EventTypes.WALLET_SAGA_COMPENSATION_COMPLETED,
                        command.orderId(),
                        correlationId,
                        new WalletSagaCompensatedEvent(
                                command.compensationId(),
                                command.orderId(),
                                command.paymentId(),
                                0,
                                result.ledgerEntryId())));
    }

    /**
     * All refund paths share the payment lock, captured amount and the same audit/ledger
     * transaction.
     */
    private RefundWalletResponse creditOrderRefund(
            String userId,
            String orderId,
            String expectedPaymentId,
            BigDecimal amount,
            String currency,
            String reasonType,
            String sourceId,
            String businessRefundId,
            String reason) {
        if (orderId == null
                || orderId.isBlank()
                || sourceId == null
                || sourceId.isBlank()
                || sourceId.length() > 160
                || businessRefundId == null
                || businessRefundId.isBlank()
                || businessRefundId.length() > 160
                || reason == null
                || reason.isBlank()
                || reason.length() > 500
                || !"VND".equals(currency)) {
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
        }
        long amountVnd = WalletService.toVnd(amount);
        Payment payment =
                payments.findByOrderIdForUpdate(orderId)
                        .orElseThrow(() -> new AppException(WalletErrorCode.SOURCE_INVALID));
        if (payment.getType() != PaymentType.ORDER
                || !"VND".equals(payment.getCurrency())
                || userId != null && !Objects.equals(userId, payment.getUserId())
                || expectedPaymentId != null
                        && !Objects.equals(expectedPaymentId, payment.getId())) {
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
        }
        if (amount.compareTo(payment.getAmount()) != 0) {
            throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        }
        String operationId = "wallet-refund:" + payment.getId();
        PaymentRefund existing = refunds.findByIdempotencyKey(operationId).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getBusinessRefundId(), businessRefundId)
                    || !Objects.equals(existing.getSourceId(), sourceId)
                    || !Objects.equals(existing.getReasonType(), reasonType)
                    || existing.getAmount().compareTo(amount) != 0
                    || existing.getStatus() != PaymentRefundStatus.COMPLETED
                    || !"WALLET".equals(existing.getDestination())) {
                throw new AppException(WalletErrorCode.OPERATION_CONFLICT);
            }
            WalletLedgerEntry entry = ledger.findEntry(existing.getLedgerEntryId()).orElseThrow();
            return new RefundWalletResponse(
                    entry.getId(), entry.getUserId(), entry.getAmount(), entry.getBalanceAfter());
        }
        if (refunds.findByBusinessRefundId(businessRefundId).isPresent()) {
            throw new AppException(WalletErrorCode.OPERATION_CONFLICT);
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED || payment.getPaidAt() == null) {
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
        }
        if (payment.getMethod() == PaymentMethod.COD && payment.getOperationId() == null) {
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
        }
        if (payment.getMethod() == PaymentMethod.ONLINE
                && ((payment.getTransactionId() == null && payment.getMerchantReference() == null)
                        || !"VND".equals(payment.getProviderCurrency())
                        || payment.getProviderAmount() == null
                        || payment.getProviderAmount().compareTo(amount) != 0)) {
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
        }
        if (payment.getMethod() == PaymentMethod.WALLET
                || "WALLET_SAGA_COMPENSATION".equals(reasonType)) {
            WalletLedgerEntry debit =
                    ledger.findEntryByOperationId("wallet-pay:" + payment.getId()).orElse(null);
            if (payment.getMethod() != PaymentMethod.WALLET
                    || debit == null
                    || !"DEBIT".equals(debit.getDirection())
                    || !Objects.equals(debit.getUserId(), payment.getUserId())
                    || debit.getAmount() != amountVnd) {
                throw new AppException(WalletErrorCode.SOURCE_INVALID);
            }
        }
        BigDecimal reserved = refunds.sumReservedAmountByPaymentId(payment.getId());
        if (reserved.add(amount).compareTo(payment.getAmount()) > 0) {
            throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        }

        WalletLedgerEntry credit =
                ledger.credit(
                        payment.getUserId(),
                        amountVnd,
                        "ORDER_REFUND",
                        payment.getId(),
                        operationId);
        refunds.saveAndFlush(
                PaymentRefund.builder()
                        .payment(payment)
                        .orderId(orderId)
                        .amount(amount)
                        .currency(currency)
                        .provider(payment.getProvider())
                        .destination("WALLET")
                        .businessRefundId(businessRefundId)
                        .reasonType(reasonType)
                        .sourceId(sourceId)
                        .ledgerEntryId(credit.getId())
                        .idempotencyKey(operationId)
                        .reason(reason)
                        .budgetReserved(true)
                        .status(PaymentRefundStatus.COMPLETED)
                        .completedAt(LocalDateTime.now())
                        .build());
        payment.setStatus(PaymentStatus.REFUNDED);
        payments.save(payment);
        return new RefundWalletResponse(
                credit.getId(), credit.getUserId(), credit.getAmount(), credit.getBalanceAfter());
    }
}

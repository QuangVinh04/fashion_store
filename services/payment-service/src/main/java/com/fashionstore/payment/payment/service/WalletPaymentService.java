package com.fashionstore.payment.payment.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.payment.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.payment.dto.WalletPaymentSession;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.wallet.service.WalletLedger;
import com.fashionstore.payment.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

/** Order payments settled from the internal wallet (method=WALLET, provider=INTERNAL). */
@Service
@RequiredArgsConstructor
public class WalletPaymentService {

    private final PaymentRepository payments;
    private final WalletLedger ledger;
    private final CurrentUserProvider currentUser;
    private final OutboxEventRecorder outbox;

    @Value("${payment.wallet.checkout-origin:http://localhost:3000}")
    private String checkoutOrigin;

    @Transactional
    public PaymentInitiationResult prepare(Payment snapshot) {
        Payment payment =
                payments.findByIdForUpdate(snapshot.getId())
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        assertWallet(payment);
        if (payment.getAuthorizationExpiresAt() == null)
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        if (payment.getPaymentUrl() == null)
            payment.setPaymentUrl(
                    checkoutOrigin + "/checkout/wallet-pay?paymentId=" + payment.getId());
        if (payment.getStatus() == PaymentStatus.PENDING) {
            outbox.saveMessage(
                    payment.getOrderId(),
                    EventTypes.PAYMENT_INITIATED,
                    EventEnvelope.v1(
                            EventTypes.PAYMENT_INITIATED,
                            payment.getOrderId(),
                            correlation(payment),
                            new PaymentInitiatedEvent(
                                    payment.getOrderId(),
                                    payment.getId(),
                                    payment.getPaymentUrl(),
                                    0)));
        }
        return PaymentInitiationResult.builder()
                .paymentUrl(payment.getPaymentUrl())
                .providerAmount(payment.getAmount())
                .providerCurrency(payment.getCurrency())
                .build();
    }

    @Transactional(readOnly = true)
    public WalletPaymentSession getSession(String id) {
        Payment payment =
                payments.findById(id)
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        assertOwner(payment);
        assertWallet(payment);
        return session(payment);
    }

    @Transactional
    public WalletPaymentSession confirm(String id, int generation) {
        Payment payment =
                payments.findByIdForUpdate(id)
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        assertOwner(payment);
        assertWallet(payment);
        if (payment.getStatus() == PaymentStatus.COMPLETED) return session(payment);
        if (payment.getStatus() != PaymentStatus.PENDING
                || payment.getAuthorizationExpiresAt() == null
                || !Instant.now().isBefore(payment.getAuthorizationExpiresAt())) {
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        long amount = WalletService.toVnd(payment.getAmount());
        ledger.debit(payment.getUserId(), amount, "ORDER_PAYMENT", id, "wallet-pay:" + id);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        outbox.saveMessage(
                payment.getOrderId(),
                EventTypes.PAYMENT_COMPLETED,
                EventEnvelope.v1(
                        EventTypes.PAYMENT_COMPLETED,
                        payment.getOrderId(),
                        correlation(payment),
                        new PaymentSuccessEvent(payment.getOrderId(), id, 0)));
        return session(payment);
    }

    /** Internal saga cancellation; the command handler emits its reply in the same transaction. */
    @Transactional
    public boolean cancelPending(String id) {
        Payment payment =
                payments.findByIdForUpdate(id)
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        assertWallet(payment);
        if (payment.getStatus() != PaymentStatus.PENDING) return false;
        payment.setStatus(PaymentStatus.CANCELLED);
        return true;
    }

    private WalletPaymentSession session(Payment payment) {
        long balance = ledger.getBalance(payment.getUserId()), amount = WalletService.toVnd(payment.getAmount());
        boolean pending =
                payment.getStatus() == PaymentStatus.PENDING
                        && payment.getAuthorizationExpiresAt() != null
                        && Instant.now().isBefore(payment.getAuthorizationExpiresAt());
        return new WalletPaymentSession(
                payment.getId(),
                payment.getOrderId(),
                0,
                payment.getStatus().name(),
                "VND",
                Long.toString(amount),
                Long.toString(balance),
                payment.getAuthorizationExpiresAt(),
                pending && balance >= amount,
                false);
    }

    private void assertWallet(Payment payment) {
        if (payment.getMethod() != PaymentMethod.WALLET
                || payment.getProvider() != PaymentProvider.INTERNAL
                || !"VND".equals(payment.getCurrency()))
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
    }

    private void assertOwner(Payment payment) {
        if (!payment.getUserId().equals(currentUser.getCurrentUserId()))
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    private String correlation(Payment payment) {
        return payment.getSagaId() == null ? payment.getOrderId() : payment.getSagaId();
    }
}

package com.fashionstore.payment.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.contracts.payment.command.RefundWalletSagaCommand;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.contracts.payment.event.WalletSagaCompensatedEvent;
import com.fashionstore.contracts.payment.event.WalletSagaCompensationRejectedEvent;
import com.fashionstore.payment.dto.CreateWalletTopUpRequest;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.RefundWalletResponse;
import com.fashionstore.payment.dto.WalletPaymentSession;
import com.fashionstore.payment.dto.WalletTopUpResponse;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.WalletAccount;
import com.fashionstore.payment.entity.WalletLedgerEntry;
import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.event.OutboxEventRecorder;
import com.fashionstore.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.exception.WalletErrorCode;
import com.fashionstore.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.repository.WalletAccountRepository;
import com.fashionstore.payment.repository.WalletLedgerEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import vn.payos.PayOS;
import vn.payos.exception.NotFoundException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletAccountRepository accounts;
    private final WalletLedgerEntryRepository entries;
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final CurrentUserProvider currentUser;
    private final OutboxEventRecorder outbox;
    private final JdbcTemplate jdbcTemplate;
    private final PayOS payOSClient;
    private final PlatformTransactionManager transactionManager;

    @Value("${payment.wallet.checkout-origin:http://localhost:3000}")
    private String checkoutOrigin;

    @Value("${payment.wallet.top-up-enabled:true}")
    private boolean topUpEnabled;

    @Value("${payment.wallet.min-top-up-amount:10000}")
    private long minimum;

    @Value("${payment.wallet.max-top-up-amount:5000000}")
    private long maximum;

    @Value("${payment.wallet.top-up-ttl-minutes:15}")
    private long ttlMinutes;

    @Value("${payment.wallet.top-up-return-url:http://localhost:3000/profile/wallet/top-up}")
    private String returnUrl;

    @Value("${payment.wallet.top-up-cancel-url:http://localhost:3000/profile/wallet/top-up}")
    private String cancelUrl;

    // ==========================================
    // 1. SỔ CÁI & SỐ DƯ (LEDGER & BALANCE)
    // ==========================================

    @Transactional
    public WalletLedgerEntry credit(
            String userId, long amount, String sourceType, String sourceId, String operationId) {
        return mutate("CREDIT", userId, amount, sourceType, sourceId, operationId);
    }

    @Transactional
    public WalletLedgerEntry debit(
            String userId, long amount, String sourceType, String sourceId, String operationId) {
        return mutate("DEBIT", userId, amount, sourceType, sourceId, operationId);
    }

    @Transactional(readOnly = true)
    public long getBalance(String userId) {
        validate(userId, 255);
        return accounts.findByUserId(userId).map(WalletAccount::getBalance).orElse(0L);
    }

    public static long toVnd(BigDecimal amount) {
        if (amount == null) throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        try {
            long value = amount.longValueExact();
            if (value <= 0) throw new AppException(WalletErrorCode.AMOUNT_INVALID);
            return value;
        } catch (ArithmeticException e) {
            throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        }
    }

    private WalletLedgerEntry mutate(
            String direction,
            String userId,
            long amount,
            String sourceType,
            String sourceId,
            String operationId) {
        validate(userId, 255);
        validate(sourceType, 50);
        validate(sourceId, 120);
        validate(operationId, 160);
        if (amount <= 0) throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        accounts.createIfAbsent(UUID.randomUUID().toString(), userId);
        WalletAccount account = accounts.findByUserIdForUpdate(userId).orElseThrow();
        WalletLedgerEntry existing = entries.findByOperationId(operationId).orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)
                    || !existing.getDirection().equals(direction)
                    || existing.getAmount() != amount
                    || !existing.getSourceType().equals(sourceType)
                    || !existing.getSourceId().equals(sourceId))
                throw new AppException(WalletErrorCode.OPERATION_CONFLICT);
            return existing;
        }
        long next;
        if ("DEBIT".equals(direction)) {
            if (account.getBalance() < amount)
                throw new AppException(WalletErrorCode.INSUFFICIENT_BALANCE);
            next = account.getBalance() - amount;
        } else {
            try {
                next = Math.addExact(account.getBalance(), amount);
            } catch (ArithmeticException e) {
                throw new AppException(WalletErrorCode.AMOUNT_INVALID);
            }
        }
        account.setBalance(next);
        accounts.save(account);
        try {
            return entries.saveAndFlush(
                    WalletLedgerEntry.builder()
                            .accountId(account.getId())
                            .userId(userId)
                            .direction(direction)
                            .amount(amount)
                            .balanceAfter(next)
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .operationId(operationId)
                            .build());
        } catch (DataIntegrityViolationException e) {
            throw new AppException(WalletErrorCode.OPERATION_CONFLICT, e);
        }
    }

    private static void validate(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
    }

    // ==========================================
    // 2. THANH TOÁN BẰNG VÍ (WALLET PAYMENT)
    // ==========================================

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
        long amount = toVnd(payment.getAmount());
        debit(payment.getUserId(), amount, "ORDER_PAYMENT", id, "wallet-pay:" + id);
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
        long balance = getBalance(payment.getUserId()), amount = toVnd(payment.getAmount());
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

    // ==========================================
    // 3. NẠP TIỀN VÀO VÍ (WALLET TOP-UP)
    // ==========================================

    @Transactional(propagation = Propagation.NEVER)
    public WalletTopUpResponse createTopUp(CreateWalletTopUpRequest request) {
        if (!topUpEnabled) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        if (request == null
                || request.operationId() == null
                || request.operationId().isBlank()
                || request.operationId().length() > 160) {
            throw new AppException(WalletErrorCode.OPERATION_CONFLICT);
        }
        long amountVnd = toVnd(request.amount());
        if (amountVnd < minimum || amountVnd > maximum) {
            throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        }
        String userId = currentUser.getCurrentUserId();
        String token = UUID.randomUUID().toString();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // Persist the provider reference before HTTP. A timeout can then be recovered with the same
        // reference.
        Payment payment =
                transaction.execute(
                        status -> {
                            Payment current =
                                    payments.findByUserIdAndOperationId(
                                                    userId, request.operationId())
                                            .orElse(null);
                            if (current != null) {
                                current = payments.findByIdForUpdate(current.getId()).orElseThrow();
                                if (current.getType() != PaymentType.TOP_UP
                                        || current.getAmount().compareTo(request.amount()) != 0) {
                                    throw new AppException(WalletErrorCode.OPERATION_CONFLICT);
                                }
                                if (current.getStatus() == PaymentStatus.COMPLETED
                                        || current.getPaymentUrl() != null) {
                                    return current;
                                }
                                if (current.getStatus() == PaymentStatus.FAILED
                                        || current.getStatus() == PaymentStatus.CANCELLED
                                        || current.getAuthorizationExpiresAt() == null
                                        || !Instant.now()
                                                .isBefore(current.getAuthorizationExpiresAt())) {
                                    throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
                                }
                                if (current.getStatus() == PaymentStatus.INITIATING
                                        && current.getInitiationStartedAt() != null
                                        && current.getInitiationStartedAt()
                                                .isAfter(LocalDateTime.now().minusMinutes(2))) {
                                    throw new AppException(
                                            PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
                                }
                            } else {
                                Long reference =
                                        jdbcTemplate.queryForObject(
                                                "select nextval('payos_reference_seq')",
                                                Long.class);
                                current =
                                        Payment.builder()
                                                .type(PaymentType.TOP_UP)
                                                .userId(userId)
                                                .operationId(request.operationId())
                                                .method(PaymentMethod.ONLINE)
                                                .provider(PaymentProvider.PAYOS)
                                                .amount(BigDecimal.valueOf(amountVnd))
                                                .currency("VND")
                                                .providerCurrency("VND")
                                                .providerAmount(BigDecimal.valueOf(amountVnd))
                                                .merchantReference(String.valueOf(reference))
                                                .authorizationExpiresAt(
                                                        Instant.now().plusSeconds(ttlMinutes * 60))
                                                .build();
                            }
                            current.setStatus(PaymentStatus.INITIATING);
                            current.setInitiationToken(token);
                            current.setInitiationStartedAt(LocalDateTime.now());
                            current.setInitiationAttempts(current.getInitiationAttempts() + 1);
                            return payments.saveAndFlush(current);
                        });
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getPaymentUrl() != null) {
            return WalletTopUpResponse.from(payment);
        }

        String paymentUrl;
        try {
            PaymentLink recovered = null;
            long reference = Long.parseLong(payment.getMerchantReference());
            if (payment.getInitiationAttempts() > 1) {
                try {
                    recovered = payOSClient.paymentRequests().get(reference);
                } catch (NotFoundException notCreated) {
                    // Provider confirmed there is no link; creating with the same reference is
                    // safe.
                }
            }
            if (recovered != null) {
                if (recovered.getOrderCode() == null
                        || recovered.getOrderCode() != reference
                        || recovered.getAmount() == null
                        || recovered.getAmount() != amountVnd
                        || recovered.getStatus() == PaymentLinkStatus.CANCELLED
                        || recovered.getStatus() == PaymentLinkStatus.EXPIRED
                        || recovered.getId() == null) {
                    throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
                }
                paymentUrl = "https://pay.payos.vn/web/" + recovered.getId();
            } else {
                CreatePaymentLinkRequest linkRequest =
                        CreatePaymentLinkRequest.builder()
                                .orderCode(reference)
                                .amount(amountVnd)
                                .description("Nap vi tai khoan")
                                .returnUrl(returnUrl + "?topUpId=" + payment.getId())
                                .cancelUrl(cancelUrl + "?topUpId=" + payment.getId())
                                .expiredAt(payment.getAuthorizationExpiresAt().getEpochSecond())
                                .build();
                CreatePaymentLinkResponse response =
                        payOSClient.paymentRequests().create(linkRequest);
                paymentUrl = response == null ? null : response.getCheckoutUrl();
            }
            if (paymentUrl == null || paymentUrl.isBlank()) {
                throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
            }
        } catch (Exception failure) {
            transaction.executeWithoutResult(
                    status -> {
                        Payment current = payments.findByIdForUpdate(payment.getId()).orElseThrow();
                        if (current.getStatus() == PaymentStatus.INITIATING
                                && token.equals(current.getInitiationToken())) {
                            current.setStatus(PaymentStatus.INITIATION_UNKNOWN);
                            current.setFailureReason(
                                    "Provider result unknown; retry with the same operationId");
                            payments.save(current);
                        }
                    });
            log.warn("Top-up link initiation failed for payment {}", payment.getId());
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, failure);
        }

        Payment completed =
                transaction.execute(
                        status -> {
                            Payment current =
                                    payments.findByIdForUpdate(payment.getId()).orElseThrow();
                            if (token.equals(current.getInitiationToken())) {
                                current.setPaymentUrl(paymentUrl);
                                if (current.getStatus() == PaymentStatus.INITIATING) {
                                    current.setStatus(PaymentStatus.PENDING);
                                    current.setFailureReason(null);
                                }
                                payments.save(current);
                            }
                            return current;
                        });
        return WalletTopUpResponse.from(completed);
    }

    public WalletTopUpResponse create(CreateWalletTopUpRequest request) {
        return createTopUp(request);
    }

    @Transactional(readOnly = true)
    public WalletTopUpResponse getTopUp(String id) {
        Payment payment =
                payments.findByIdAndUserId(id, currentUser.getCurrentUserId())
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getType() != PaymentType.TOP_UP) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        return WalletTopUpResponse.from(payment);
    }

    public WalletTopUpResponse get(String id) {
        return getTopUp(id);
    }

    // ==========================================
    // 4. HOÀN TIỀN VÀ BỒI THƯỜNG (REFUND & COMPENSATE)
    // ==========================================

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
        long amountVnd = toVnd(amount);
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
            WalletLedgerEntry entry = entries.findById(existing.getLedgerEntryId()).orElseThrow();
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
                    entries.findByOperationId("wallet-pay:" + payment.getId()).orElse(null);
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
                credit(
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

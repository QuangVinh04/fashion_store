package com.fashionstore.payment.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.payment.client.PaymentHandler;
import com.fashionstore.payment.payment.client.PaymentHandlerRegistry;
import com.fashionstore.payment.payment.dto.CallbackOutcome;
import com.fashionstore.payment.payment.dto.CreateWalletTopUpRequest;
import com.fashionstore.payment.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.payment.service.PaymentTransactionService;
import com.fashionstore.payment.wallet.service.WalletService;
import com.fashionstore.payment.payment.service.impl.CallbackPaymentServiceImpl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@DataJpaTest(properties = "payment.wallet.top-up-enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PaymentTransactionService.class,
    CallbackPaymentServiceImpl.class,
    WalletService.class,
    com.fashionstore.payment.wallet.service.WalletLedger.class,
    com.fashionstore.payment.payment.service.WalletPaymentService.class,
    com.fashionstore.payment.payment.service.WalletTopUpService.class,
    com.fashionstore.payment.payment.service.OrderFundsRefundService.class,
    com.fashionstore.payment.payment.event.OrderDeliveredEventListener.class,
    com.fashionstore.payment.payment.service.impl.PaymentStateServiceImpl.class,
    PaymentFinancialPostgresIntegrationTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@org.junit.jupiter.api.Tag("external-infrastructure")
class PaymentFinancialPostgresIntegrationTest {

    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>("postgres:16-alpine")
                    .withEnv("POSTGRES_DB", "finance_test")
                    .withEnv("POSTGRES_USER", "finance_test")
                    .withEnv("POSTGRES_PASSWORD", "test-only-password")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
    }

    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired CallbackPaymentServiceImpl callbacks;
    @Autowired com.fashionstore.payment.payment.service.OrderFundsRefundService refundService;
    @Autowired com.fashionstore.payment.payment.service.WalletTopUpService topUpService;
    @Autowired PaymentTransactionService database;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean CurrentUserProvider currentUser;
    @MockitoBean vn.payos.PayOS payos;
    @MockitoBean PaymentHandlerRegistry handlers;
    @MockitoBean com.fashionstore.payment.payment.mapper.PaymentResponseMapper paymentMapper;
    @Autowired com.fashionstore.payment.payment.event.OrderDeliveredEventListener deliveryEvents;
    @MockitoBean OutboxEventRecorder outbox;

    private static final String REFUND_CREDITED =
            com.fashionstore.contracts.common.EventTypes.ORDER_FUNDS_REFUND_CREDITED;
    private static final String REFUND_REJECTED =
            com.fashionstore.contracts.common.EventTypes.ORDER_FUNDS_REFUND_REJECTED;

    /** Đường hoàn ví thật: RefundOrderFundsCommand -> OrderFundsRefundService; từ chối nghiệp vụ ra event rejected. */
    private void refund(String orderId, BigDecimal amount, String returnId) {
        String paymentId =
                payments.findByOrderId(orderId).map(Payment::getId).orElse("missing-payment");
        var command =
                new com.fashionstore.contracts.payment.command.RefundOrderFundsCommand(
                        "return:" + returnId,
                        orderId,
                        paymentId,
                        amount,
                        "VND",
                        "RETURN_REFUND",
                        returnId);
        refundService.refundOrderFunds(command, UUID.randomUUID().toString(), returnId);
    }

    private java.util.List<org.mockito.invocation.Invocation> refundInvocations() {
        return org.mockito.Mockito.mockingDetails(outbox).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("saveMessage"))
                .filter(
                        call ->
                                REFUND_CREDITED.equals(call.getArgument(1))
                                        || REFUND_REJECTED.equals(call.getArgument(1)))
                .toList();
    }

    private java.util.List<String> refundEvents() {
        return refundInvocations().stream().map(call -> (String) call.getArgument(1)).toList();
    }

    private String lastRefundEvent() {
        var events = refundEvents();
        return events.get(events.size() - 1);
    }

    private String lastCreditedLedgerEntry() {
        var invocations = refundInvocations();
        var last = invocations.get(invocations.size() - 1);
        assertThat((String) last.getArgument(1)).isEqualTo(REFUND_CREDITED);
        var envelope = (com.fashionstore.contracts.common.EventEnvelope<?>) last.getArgument(2);
        return ((com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent)
                        envelope.payload())
                .ledgerEntryId();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:postgresql://"
                                + POSTGRES.getHost()
                                + ":"
                                + POSTGRES.getMappedPort(5432)
                                + "/finance_test");
        registry.add("spring.datasource.username", () -> "finance_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void webhookCommitsPaymentAndWalletTogetherWithBankReference() {
        Payment payment = topUp();
        PaymentHandler handler = mock(PaymentHandler.class);
        when(handlers.get(PaymentProvider.PAYOS)).thenReturn(handler);
        when(handler.verifyCallback(any(), any())).thenReturn(receipt(payment, "VND"));

        assertThat(
                        callbacks
                                .processCallback(PaymentProvider.PAYOS, Map.of(), "signed-body")
                                .outcome())
                .isEqualTo(CallbackOutcome.APPLIED);
        assertThat(balance(payment)).isEqualTo(100000);
        Payment persisted = payments.findById(payment.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(persisted.getTransactionId()).isEqualTo("bank:" + payment.getId());
        assertThat(persisted.getPaidAt()).isNotNull();
    }

    @Test
    void foreignCurrencyDoesNotCreditWalletOrFailTheOriginalPayment() {
        Payment payment = topUp();
        assertThat(
                        callbacks
                                .applyVerifiedResult(PaymentProvider.PAYOS, receipt(payment, "USD"))
                                .outcome())
                .isEqualTo(CallbackOutcome.AMOUNT_INVALID);
        assertThat(balance(payment)).isZero();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void anotherProviderCannotCreditPayosTopUp() {
        Payment payment = topUp();
        assertThat(
                        callbacks
                                .applyVerifiedResult(PaymentProvider.VNPAY, receipt(payment, "VND"))
                                .outcome())
                .isEqualTo(CallbackOutcome.PAYMENT_NOT_FOUND);
        assertThat(balance(payment)).isZero();
    }

    @Test
    void cancelledOnlinePaymentStillRecordsVerifiedLateFunds() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        payment.setMerchantReference(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setPaidAt(null);
        payments.saveAndFlush(payment);
        var result =
                database.applyVerifiedCallbackResult(
                        PaymentProvider.PAYOS, receipt(payment, "VND"));
        assertThat(result.outcome()).isEqualTo(CallbackOutcome.APPLIED);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payments.findById(payment.getId()).orElseThrow().getPaidAt()).isNotNull();
    }

    @Test
    void onlineSagaCompensationUsesTheSameCapturedFundsBudget() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        String sagaId = UUID.randomUUID().toString();
        var command =
                new com.fashionstore.contracts.payment.command.RefundOrderFundsCommand(
                        "saga-online:" + payment.getId(),
                        payment.getOrderId(),
                        payment.getId(),
                        payment.getAmount(),
                        "VND",
                        "SAGA_COMPENSATION",
                        sagaId);
        refundService.refundOrderFunds(command, UUID.randomUUID().toString(), sagaId);
        assertThat(balance(payment)).isEqualTo(100000);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
        assertThat(lastRefundEvent()).isEqualTo(REFUND_REJECTED);
        assertThat(balance(payment)).isEqualTo(100000);
    }

    @Test
    void verifiedCodCollectionSettlesPaymentAndCanBeRefundedOnce() {
        Payment payment = capturedOrder(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.COD_PENDING);
        payment.setPaidAt(null);
        payments.saveAndFlush(payment);
        var collectedAt = java.time.Instant.parse("2026-10-01T03:00:00Z");
        var event =
                new com.fashionstore.contracts.order.OrderDeliveredEvent(
                        payment.getOrderId(),
                        java.time.LocalDateTime.ofInstant(collectedAt, java.time.ZoneOffset.UTC),
                        payment.getAmount(),
                        collectedAt,
                        "GHN-1",
                        "123");
        var envelope =
                com.fashionstore.contracts.common.EventEnvelope.v1(
                        com.fashionstore.contracts.common.EventTypes.ORDER_DELIVERED,
                        payment.getOrderId(),
                        payment.getOrderId(),
                        event);
        deliveryEvents.handle(envelope, UUID.randomUUID().toString());
        Payment collected = payments.findById(payment.getId()).orElseThrow();
        assertThat(collected.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(collected.getOperationId()).isEqualTo("ghn:123:GHN-1");
        assertThat(collected.getPaidAt()).isNotNull();
        refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
        deliveryEvents.handle(envelope, UUID.randomUUID().toString());
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(balance(payment)).isEqualTo(100000);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from payment_refund where payment_id = ?",
                                Long.class,
                                payment.getId()))
                .isEqualTo(1);
    }

    @Test
    void invalidCodEvidenceRollsBackInboxAndAllowsCorrectedMessageToRetry() {
        Payment payment = capturedOrder(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.COD_PENDING);
        payment.setPaidAt(null);
        payments.saveAndFlush(payment);
        String messageId = UUID.randomUUID().toString();
        var collectedAt = java.time.Instant.parse("2026-10-01T03:00:00Z");
        var wrong =
                new com.fashionstore.contracts.order.OrderDeliveredEvent(
                        payment.getOrderId(),
                        null,
                        new BigDecimal("900000"),
                        collectedAt,
                        "GHN-1",
                        "123");
        assertThatThrownBy(
                        () ->
                                deliveryEvents.handle(
                                        com.fashionstore.contracts.common.EventEnvelope.v1(
                                                com.fashionstore.contracts.common.EventTypes
                                                        .ORDER_DELIVERED,
                                                payment.getOrderId(),
                                                payment.getOrderId(),
                                                wrong),
                                        messageId))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processed_message where message_id = ?",
                                Long.class,
                                messageId))
                .isZero();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COD_PENDING);
        var correct =
                new com.fashionstore.contracts.order.OrderDeliveredEvent(
                        payment.getOrderId(),
                        null,
                        payment.getAmount(),
                        collectedAt,
                        "GHN-1",
                        "123");
        deliveryEvents.handle(
                com.fashionstore.contracts.common.EventEnvelope.v1(
                        com.fashionstore.contracts.common.EventTypes.ORDER_DELIVERED,
                        payment.getOrderId(),
                        payment.getOrderId(),
                        correct),
                messageId);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processed_message where message_id = ?",
                                Long.class,
                                messageId))
                .isEqualTo(1);
    }

    @Test
    void twoDifferentRefundRequestsCannotSpendTheSameCapturedFunds() throws Exception {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> attempt =
                () -> {
                    start.await();
                    refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
                    return true;
                };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertThat(refundEvents()).containsExactlyInAnyOrder(REFUND_CREDITED, REFUND_REJECTED);
            assertThat(balance(payment)).isEqualTo(100000);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from payment_refund where payment_id = ?",
                                    Long.class,
                                    payment.getId()))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refundAuditAndCreditRollBackTogether() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        var transaction =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
                                            throw new IllegalStateException(
                                                    "later order transaction failed");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(balance(payment)).isZero();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from payment_refund where payment_id = ?",
                                Long.class,
                                payment.getId()))
                .isZero();
    }

    @Test
    void paidCancellationCommandReplaysUsingBusinessIdentityAcrossMessages() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        String cancellationId = UUID.randomUUID().toString();
        var command =
                new com.fashionstore.contracts.payment.command.RefundOrderFundsCommand(
                        "cancel:" + cancellationId,
                        payment.getOrderId(),
                        payment.getId(),
                        payment.getAmount(),
                        "VND",
                        "PAID_CANCELLATION",
                        cancellationId);
        refundService.refundOrderFunds(command, "message-one", "cancellation");
        refundService.refundOrderFunds(command, "message-two", "cancellation");
        assertThat(balance(payment)).isEqualTo(100000);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from payment_refund where payment_id = ?",
                                Long.class,
                                payment.getId()))
                .isEqualTo(1);
        org.mockito.Mockito.verify(outbox, org.mockito.Mockito.times(2))
                .saveMessage(
                        org.mockito.ArgumentMatchers.eq(payment.getOrderId()),
                        org.mockito.ArgumentMatchers.eq(
                                com.fashionstore.contracts.common.EventTypes
                                        .ORDER_FUNDS_REFUND_CREDITED),
                        any());
    }

    @Test
    void wholeOrderRefundHasOneAuditAndOneCreditEvenWhenRetried() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        String returnId = UUID.randomUUID().toString();
        refund(payment.getOrderId(), payment.getAmount(), returnId);
        String firstLedgerEntry = lastCreditedLedgerEntry();
        refund(payment.getOrderId(), payment.getAmount(), returnId);
        assertThat(lastCreditedLedgerEntry()).isEqualTo(firstLedgerEntry);
        assertThat(balance(payment)).isEqualTo(100000);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from payment_refund where payment_id = ?",
                                Long.class,
                                payment.getId()))
                .isEqualTo(1);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
        assertThat(lastRefundEvent()).isEqualTo(REFUND_REJECTED);
        assertThat(balance(payment)).isEqualTo(100000);
    }

    @Test
    void pendingProviderRefundBlocksASecondWalletRefund() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        jdbc.update(
                "insert into payment_refund"
                    + " (id,payment_id,order_id,amount,provider,status,idempotency_key) values"
                    + " (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                "PAYOS",
                "PENDING",
                UUID.randomUUID().toString());
        refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
        assertThat(lastRefundEvent()).isEqualTo(REFUND_REJECTED);
        assertThat(balance(payment)).isZero();
    }

    @Test
    void nonexistentOrderCannotCreateMoneyThroughReturnRefund() {
        String userId = UUID.randomUUID().toString();
        refund("missing-order", new BigDecimal("100000"), UUID.randomUUID().toString());
        assertThat(lastRefundEvent()).isEqualTo(REFUND_REJECTED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from wallet_ledger_entry where user_id = ?",
                                Long.class,
                                userId))
                .isZero();
    }

    @Test
    void refundCannotExceedCapturedAmount() {
        Payment payment = capturedOrder(PaymentMethod.ONLINE);
        refund(payment.getOrderId(), new BigDecimal("900000"), UUID.randomUUID().toString());
        assertThat(lastRefundEvent()).isEqualTo(REFUND_REJECTED);
        assertThat(balance(payment)).isZero();
    }

    @Test
    void codNotCollectedCannotBeRefunded() {
        Payment payment = capturedOrder(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.COD_PENDING);
        payment.setPaidAt(null);
        payments.saveAndFlush(payment);
        refund(payment.getOrderId(), payment.getAmount(), UUID.randomUUID().toString());
        assertThat(lastRefundEvent()).isEqualTo(REFUND_REJECTED);
        assertThat(balance(payment)).isZero();
    }

    @Test
    void topUpTimeoutKeepsReferenceAndRetryRecoversExistingProviderLink() {
        String userId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        when(currentUser.getCurrentUserId()).thenReturn(userId);
        var provider =
                mock(vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService.class);
        when(payos.paymentRequests()).thenReturn(provider);
        when(provider.create(any())).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> topUpService.createTopUp(new CreateWalletTopUpRequest(100000, operationId)))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        Payment saved = payments.findByUserIdAndOperationId(userId, operationId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.INITIATION_UNKNOWN);
        assertThat(saved.getMerchantReference()).isNotBlank();
        var link = mock(vn.payos.model.v2.paymentRequests.PaymentLink.class);
        when(link.getOrderCode()).thenReturn(Long.parseLong(saved.getMerchantReference()));
        when(link.getAmount()).thenReturn(100000L);
        when(link.getId()).thenReturn("recovered-link");
        when(link.getStatus())
                .thenReturn(vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING);
        when(provider.get(Long.parseLong(saved.getMerchantReference()))).thenReturn(link);

        var result = topUpService.createTopUp(new CreateWalletTopUpRequest(100000, operationId));
        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.paymentUrl()).endsWith("/recovered-link");
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(1)).create(any());
        assertThat(
                        payments.findByUserIdAndOperationId(userId, operationId)
                                .orElseThrow()
                                .getMerchantReference())
                .isEqualTo(saved.getMerchantReference());
    }

    @Test
    void bankReferenceCannotFundTwoDifferentTopUps() {
        Payment first = topUp();
        Payment second = topUp();
        callbacks.applyVerifiedResult(PaymentProvider.PAYOS, receipt(first, "VND"));
        var repeated =
                PaymentCallbackResult.builder()
                        .signatureValid(true)
                        .merchantReference(second.getMerchantReference())
                        .providerTransactionId("bank:" + first.getId())
                        .amount(second.getAmount())
                        .currency("VND")
                        .status(PaymentStatus.COMPLETED)
                        .build();

        assertThat(callbacks.applyVerifiedResult(PaymentProvider.PAYOS, repeated).outcome())
                .isEqualTo(CallbackOutcome.INVALID_REQUEST);
        assertThat(balance(second)).isZero();
    }

    @Test
    void failedParentTransactionRollsBackPaymentCreditAndBalance() {
        Payment payment = topUp();
        var transaction =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            database.applyVerifiedCallbackResult(
                                                    PaymentProvider.PAYOS, receipt(payment, "VND"));
                                            throw new IllegalStateException(
                                                    "later persistence failure");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(balance(payment)).isZero();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void concurrentDuplicateCallbacksCreditOnce() throws Exception {
        Payment payment = topUp();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first =
                    executor.submit(
                            () -> {
                                start.await();
                                return callbacks.applyVerifiedResult(
                                        PaymentProvider.PAYOS, receipt(payment, "VND"));
                            });
            var second =
                    executor.submit(
                            () -> {
                                start.await();
                                return callbacks.applyVerifiedResult(
                                        PaymentProvider.PAYOS, receipt(payment, "VND"));
                            });
            start.countDown();
            assertThat(
                            java.util.List.of(
                                    first.get(15, TimeUnit.SECONDS).outcome(),
                                    second.get(15, TimeUnit.SECONDS).outcome()))
                    .containsExactlyInAnyOrder(
                            CallbackOutcome.APPLIED, CallbackOutcome.ALREADY_PROCESSED);
            assertThat(balance(payment)).isEqualTo(100000);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from wallet_ledger_entry where user_id = ?",
                                    Long.class,
                                    payment.getUserId()))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Payment topUp() {
        return payments.saveAndFlush(
                Payment.builder()
                        .type(PaymentType.TOP_UP)
                        .userId(UUID.randomUUID().toString())
                        .operationId(UUID.randomUUID().toString())
                        .method(PaymentMethod.ONLINE)
                        .provider(PaymentProvider.PAYOS)
                        .merchantReference(UUID.randomUUID().toString())
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .status(PaymentStatus.PENDING)
                        .build());
    }

    private Payment capturedOrder(PaymentMethod method) {
        return payments.saveAndFlush(
                Payment.builder()
                        .type(PaymentType.ORDER)
                        .orderId(UUID.randomUUID().toString())
                        .userId(UUID.randomUUID().toString())
                        .method(method)
                        .provider(
                                method == PaymentMethod.COD
                                        ? PaymentProvider.COD
                                        : PaymentProvider.PAYOS)
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .providerAmount(new BigDecimal("100000"))
                        .providerCurrency("VND")
                        .transactionId(UUID.randomUUID().toString())
                        .paidAt(java.time.LocalDateTime.now())
                        .status(PaymentStatus.COMPLETED)
                        .build());
    }

    private PaymentCallbackResult receipt(Payment payment, String currency) {
        return PaymentCallbackResult.builder()
                .signatureValid(true)
                .merchantReference(payment.getMerchantReference())
                .providerTransactionId("bank:" + payment.getId())
                .amount(payment.getAmount())
                .currency(currency)
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    private long balance(Payment payment) {
        return jdbc.queryForObject(
                "select coalesce((select balance from wallet_account where user_id = ?), 0)",
                Long.class,
                payment.getUserId());
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class Configuration {
        @org.springframework.context.annotation.Bean
        ProcessedMessageService processedMessages(JdbcTemplate jdbc) {
            return new ProcessedMessageService(com.fashionstore.common.redis.RedisService.disabled(), jdbc);
        }

        @org.springframework.context.annotation.Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        }
    }
}

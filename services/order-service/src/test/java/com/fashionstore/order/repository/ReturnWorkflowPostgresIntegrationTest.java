package com.fashionstore.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.ReceiveReturnRequest;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnEvidenceMetadata;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.service.OrderNotificationService;
import com.fashionstore.order.service.OrderReceiptService;
import com.fashionstore.order.service.ReturnService;
import com.fashionstore.order.service.impl.ReturnServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    ReturnServiceImpl.class,
    OrderReceiptService.class,
    com.fashionstore.order.service.impl.ShipmentServiceImpl.class,
    ReturnWorkflowPostgresIntegrationTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@org.junit.jupiter.api.Tag("external-infrastructure")
class ReturnWorkflowPostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>("postgres:16-alpine")
                    .withEnv("POSTGRES_DB", "return_test")
                    .withEnv("POSTGRES_USER", "return_test")
                    .withEnv("POSTGRES_PASSWORD", "test-only-password")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
    }

    @Autowired ReturnService service;
    @Autowired OrderReceiptService receipts;
    @Autowired OrderRepository orders;
    @Autowired ReturnRequestRepository returns;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fashionstore.order.service.ShipmentService shipmentService;
    @Autowired ShipmentRepository shipments;
    @Autowired com.fashionstore.order.repository.ReturnShipmentRepository returnShipments;
    @MockitoBean com.fashionstore.order.client.GhnClient ghn;
    @MockitoBean com.fashionstore.order.service.ReturnPickupService returnPickupService;
    @MockitoBean com.fashionstore.order.client.CatalogClient productCatalog;
    @MockitoBean com.fashionstore.order.config.GhnProperties ghnProperties;
    @MockitoBean com.fashionstore.order.service.OrderCancellationSagaService cancellations;
    @MockitoBean com.fashionstore.order.service.ShipmentCreationTransactionService shipmentCreation;
    @MockitoBean CurrentUserProvider currentUser;
    @MockitoBean OutboxEventRecorder outbox;
    @MockitoBean OrderNotificationService notifications;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:postgresql://"
                                + POSTGRES.getHost()
                                + ":"
                                + POSTGRES.getMappedPort(5432)
                                + "/return_test");
        registry.add("spring.datasource.username", () -> "return_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void actor() {
        when(currentUser.getCurrentUserId()).thenReturn("customer-1");
        when(ghnProperties.getShopId()).thenReturn("123");
    }

    @AfterEach
    void returnsNeverRestockInventoryAutomatically() {
        verify(productCatalog, never()).restock(anyString());
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void sevenDayPolicyMatchesCreateProjectionAndAutomaticCompletion() {
        Order dayFour = order(NOW.minusSeconds(4 * 24 * 3600));
        assertThat(receipts.canRequestReturn(dayFour, NOW)).isTrue();
        assertThat(
                        service.createReturnRequest(
                                        dayFour.getId(),
                                        ReturnOrderRequest.builder().reason("Wrong item").build())
                                .getStatus())
                .isEqualTo(ReturnRequestStatus.REQUESTED);
        receipts.completeExpired(NOW);
        assertThat(orders.findById(dayFour.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);

        Order daySeven = order(NOW.minusSeconds(7 * 24 * 3600));
        assertThat(receipts.canRequestReturn(daySeven, NOW)).isFalse();
        assertThatThrownBy(
                        () ->
                                service.createReturnRequest(
                                        daySeven.getId(),
                                        ReturnOrderRequest.builder().reason("Return").build()))
                .isInstanceOf(AppException.class);
        receipts.completeExpired(NOW);
        assertThat(orders.findById(daySeven.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void approvalStoresTheGhnReturnShipmentOnceEvenWhenRepeated() {
        Order delivered = order(NOW.minusSeconds(2 * 24 * 3600));
        var requested =
                service.createReturnRequest(
                        delivered.getId(),
                        ReturnOrderRequest.builder().reason("Wrong size").build());
        when(ghn.createReturnOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn("GHN-RET-1");

        var first = service.approveReturn(requested.getId());
        var again = service.approveReturn(requested.getId());

        assertThat(first.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(first.getReturnTrackingCode()).isEqualTo("GHN-RET-1");
        assertThat(again.getReturnTrackingCode()).isEqualTo("GHN-RET-1");
        verify(ghn, org.mockito.Mockito.times(1))
                .createReturnOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from return_shipment where return_request_id = ?"
                                        + " and ghn_order_code = 'GHN-RET-1' and status = 'READY_TO_PICK'",
                                Long.class,
                                requested.getId()))
                .isEqualTo(1);
    }

    @Test
    void pickupReconcileAndLegacyExpiryQueriesTargetTheRightReturns() {
        Instant cutoff = NOW.minusSeconds(7 * 24 * 3600);
        var overdue = approvedReturn(NOW.minusSeconds(2 * 24 * 3600));
        overdue.setApprovedAt(NOW.minusSeconds(8 * 24 * 3600));
        returns.saveAndFlush(overdue);
        var fresh = approvedReturn(NOW.minusSeconds(2 * 24 * 3600));
        var legacy = approvedReturn(NOW.minusSeconds(2 * 24 * 3600));
        legacy.setApprovedAt(NOW.minusSeconds(8 * 24 * 3600));
        returns.saveAndFlush(legacy);
        var overdueShipment =
                returnShipments.saveAndFlush(
                        com.fashionstore.order.entity.ReturnShipment.builder()
                                .returnRequest(overdue)
                                .ghnOrderCode("GHN-OVERDUE-" + overdue.getId())
                                .build());
        returnShipments.saveAndFlush(
                com.fashionstore.order.entity.ReturnShipment.builder()
                        .returnRequest(fresh)
                        .ghnOrderCode("GHN-FRESH-" + fresh.getId())
                        .build());
        var page = org.springframework.data.domain.PageRequest.of(0, 100);

        assertThat(returnShipments.findPickupOverdueReturnIds(cutoff, 5, page))
                .contains(overdue.getId())
                .doesNotContain(fresh.getId(), legacy.getId());
        assertThat(returnShipments.findOpenForReconcile(NOW, page))
                .contains(overdue.getId(), fresh.getId())
                .doesNotContain(legacy.getId());
        // Phiếu duyệt từ trước khi có đơn trả GHN vẫn hết hạn theo cách cũ; phiếu có đơn GHN thì không.
        assertThat(returns.findUnsentCustomerReturnIds(cutoff))
                .contains(legacy.getId())
                .doesNotContain(overdue.getId(), fresh.getId());

        overdueShipment.setLastReconciledAt(NOW);
        overdueShipment.setCancelAttempts(5);
        returnShipments.saveAndFlush(overdueShipment);
        assertThat(returnShipments.findOpenForReconcile(NOW, page)).doesNotContain(overdue.getId());
        assertThat(returnShipments.findPickupOverdueReturnIds(cutoff, 5, page)).doesNotContain(overdue.getId());
        assertThat(returnShipments.countPickupCancelExhausted(5, cutoff)).isGreaterThanOrEqualTo(1);
        assertThat(returnShipments.countNeedingAttention()).isGreaterThanOrEqualTo(0);
        assertThat(returns.countAwaitingReviewSince(java.time.LocalDateTime.now().plusDays(1))).isGreaterThanOrEqualTo(0);
        assertThat(returnShipments.countInTransitSince(NOW)).isGreaterThanOrEqualTo(0);
        assertThat(returnShipments.countArrivedUnreceivedSince(NOW)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aRecreatedPickupIsOverdueFromItsOwnRequestNotFromTheApproval() {
        Instant cutoff = NOW.minusSeconds(7 * 24 * 3600);
        var recreated = approvedReturn(NOW.minusSeconds(2 * 24 * 3600));
        recreated.setApprovedAt(NOW.minusSeconds(8 * 24 * 3600));
        returns.saveAndFlush(recreated);
        returnShipments.saveAndFlush(
                com.fashionstore.order.entity.ReturnShipment.builder()
                        .returnRequest(recreated)
                        .ghnOrderCode("GHN-AGAIN-" + recreated.getId())
                        .pickupRequestedAt(NOW.minusSeconds(24 * 3600))
                        .build());

        assertThat(
                        returnShipments.findPickupOverdueReturnIds(
                                cutoff, 5, org.springframework.data.domain.PageRequest.of(0, 100)))
                .doesNotContain(recreated.getId());
    }

    @Test
    void activeReturnFreezesCompletionUntilRejectedAfterFailedQualityCheck() {
        ReturnRequest returned = approvedReturn(NOW.minusSeconds(8 * 24 * 3600));
        String evidenceId = UUID.randomUUID().toString();
        when(productCatalog.getReturnEvidence(evidenceId, "customer-1"))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return ApiResponse.<ReturnEvidenceMetadata>builder()
                                    .data(
                                            new ReturnEvidenceMetadata(
                                                    evidenceId,
                                                    "customer-1",
                                                    "RETURN_EVIDENCE",
                                                    "ACTIVE",
                                                    "PRIVATE",
                                                    "image/jpeg"))
                                    .build();
                        });
        service.receiveReturn(
                returned.getId(),
                new ReceiveReturnRequest(false, "Tags missing", List.of(evidenceId)));
        receipts.completeExpired(NOW);
        assertThat(orders.findById(returned.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
        var rejected =
                service.rejectReturn(
                        returned.getId(),
                        RejectReturnRequest.builder().rejectReason("Tags missing").build());
        assertThat(rejected.getStatus()).isEqualTo(ReturnRequestStatus.REJECTED);
        assertThat(rejected.getClosedAt()).isEqualTo(NOW);
        assertThat(rejected.getQcEvidenceIds()).containsExactly(evidenceId);
        when(productCatalog.getEvidenceReadUrl(evidenceId, "customer-1"))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return ApiResponse.<String>builder()
                                    .data("https://storage.test/private-proof")
                                    .build();
                        });
        assertThat(service.getQcEvidenceReadUrl(returned.getId(), evidenceId))
                .isEqualTo("https://storage.test/private-proof");
        assertThatThrownBy(() -> service.getQcEvidenceReadUrl(returned.getId(), "unrelated-media"))
                .isInstanceOf(AppException.class);
        receipts.completeExpired(NOW);
        assertThat(orders.findById(returned.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void queuedRefundStaysOpenUntilReceiptAndRetryUsesSameIdentity() {
        ReturnRequest returned = approvedReturn(NOW.minusSeconds(24 * 3600));
        service.receiveReturn(
                returned.getId(),
                new ReceiveReturnRequest(true, "Whole order accepted", List.of()));
        service.refundReturn(returned.getId(), null);
        var pending = returns.findById(returned.getId()).orElseThrow();
        assertThat(pending.getRefundStartedAt()).isEqualTo(NOW);
        assertThat(pending.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(pending.getClosedAt()).isNull();
        service.refundReturn(returned.getId(), null);
        var envelopes =
                org.mockito.ArgumentCaptor.forClass(
                        com.fashionstore.contracts.common.EventEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2))
                .saveMessage(
                        org.mockito.ArgumentMatchers.eq(returned.getOrder().getId()),
                        org.mockito.ArgumentMatchers.eq(
                                com.fashionstore.contracts.common.EventTypes
                                        .ORDER_FUNDS_REFUND_REQUESTED),
                        envelopes.capture());
        assertThat(envelopes.getAllValues().get(0).payload())
                .isEqualTo(envelopes.getAllValues().get(1).payload());
        String ledgerId = UUID.randomUUID().toString();
        service.refundCredited(credit(returned, ledgerId, "100000"));
        assertThat(service.refundReturn(returned.getId(), null).getRefundLedgerEntryId())
                .isEqualTo(ledgerId);
        assertThat(returns.findById(returned.getId()).orElseThrow().getStatus())
                .isEqualTo(ReturnRequestStatus.REFUNDED);
    }

    @Test
    void conflictingOrEmptyFinancialRepliesDoNotCompleteReturn() {
        for (String ledger : List.of("", UUID.randomUUID().toString())) {
            ReturnRequest returned = approvedReturn(NOW.minusSeconds(24 * 3600));
            service.receiveReturn(
                    returned.getId(), new ReceiveReturnRequest(true, "QC passed", List.of()));
            service.refundReturn(returned.getId(), null);
            var reply = credit(returned, ledger, ledger.isEmpty() ? "100000" : "1");
            assertThatThrownBy(() -> service.refundCredited(reply))
                    .isInstanceOf(AppException.class);
            assertThat(returns.findById(returned.getId()).orElseThrow().getStatus())
                    .isEqualTo(ReturnRequestStatus.RECEIVED);
            assertThat(orders.findById(returned.getOrder().getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.DELIVERED);
        }
    }

    @Test
    void concurrentRefundRepliesFinishOnce() throws Exception {
        ReturnRequest returned = approvedReturn(NOW.minusSeconds(24 * 3600));
        service.receiveReturn(
                returned.getId(), new ReceiveReturnRequest(true, "QC passed", List.of()));
        service.refundReturn(returned.getId(), null);
        var reply = credit(returned, UUID.randomUUID().toString(), "100000");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Void> credit =
                () -> {
                    start.await();
                    service.refundCredited(reply);
                    return null;
                };
        try {
            var first = executor.submit(credit);
            var second = executor.submit(credit);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertThat(returns.findById(returned.getId()).orElseThrow().getRefundLedgerEntryId())
                    .isEqualTo(reply.ledgerEntryId());
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from order_status_history where order_id=? and"
                                            + " action='RETURN_REFUNDED'",
                                    Long.class,
                                    returned.getOrder().getId()))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent credit(
            ReturnRequest returned, String ledger, String amount) {
        return new com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent(
                "return:" + returned.getId(),
                returned.getOrder().getId(),
                returned.getOrder().getPaymentId(),
                "RETURN_REFUND",
                returned.getId(),
                ledger,
                new BigDecimal(amount));
    }

    @Test
    void carrierCashEvidenceCommitsWithDeliveryAndReplayKeepsOriginalDates() {
        Order order = order(null);
        order.setStatus(OrderStatus.SHIPPING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentProvider(PaymentProvider.COD);
        orders.saveAndFlush(order);
        String tracking = UUID.randomUUID().toString();
        var shipment =
                shipments.saveAndFlush(
                        com.fashionstore.order.entity.Shipment.builder()
                                .order(order)
                                .provider(
                                        com.fashionstore.order.entity.enumeration.ShipmentProvider
                                                .GHN)
                                .trackingCode(tracking)
                                .ghnOrderCode(tracking)
                                .status(
                                        com.fashionstore.order.entity.enumeration.ShipmentStatus
                                                .SHIPPING)
                                .build());
        Instant deliveredAt = NOW.minusSeconds(3600);
        var verified =
                new com.fashionstore.order.dto.VerifiedGhnShipment(
                        tracking,
                        "123",
                        order.getOrderCode(),
                        "delivered",
                        order.getTotalAmount(),
                        true,
                        deliveredAt,
                        false,
                        null);
        when(ghn.getVerifiedShipment(tracking))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return verified;
                        });
        var payload =
                com.fashionstore.order.dto.GhnWebhookPayload.builder()
                        .shopId("123")
                        .orderCode(tracking)
                        .clientOrderCode(order.getOrderCode())
                        .type("switch_status")
                        .status("delivered")
                        .time(deliveredAt.toString())
                        .codAmount(order.getTotalAmount())
                        .build();
        shipmentService.handleGhnCallback(payload);
        payload.setTime(deliveredAt.plusSeconds(60).toString());
        shipmentService.handleGhnCallback(payload);
        assertThat(orders.findById(order.getId()).orElseThrow().getDeliveredAt())
                .isEqualTo(deliveredAt);
        var collected = shipments.findById(shipment.getId()).orElseThrow();
        assertThat(collected.getCodStatus()).isEqualTo("COLLECTED");
        assertThat(collected.getCodCollectedAt()).isEqualTo(deliveredAt);
        assertThat(collected.getDeliveredAt()).isEqualTo(deliveredAt);
        verify(outbox, times(1))
                .saveMessage(
                        org.mockito.ArgumentMatchers.eq(order.getId()),
                        org.mockito.ArgumentMatchers.eq(
                                com.fashionstore.contracts.common.EventTypes.ORDER_DELIVERED),
                        any());

        when(ghn.getVerifiedShipment(tracking))
                .thenReturn(
                        new com.fashionstore.order.dto.VerifiedGhnShipment(
                                tracking,
                                "123",
                                order.getOrderCode(),
                                "delivered",
                                order.getTotalAmount(),
                                true,
                                deliveredAt,
                                true,
                                NOW));
        payload.setType("cod");
        payload.setCodTransferDate(NOW.toString());
        shipmentService.handleGhnCallback(payload);
        shipmentService.handleGhnCallback(payload);
        var transferred = shipments.findById(shipment.getId()).orElseThrow();
        assertThat(transferred.getCodStatus()).isEqualTo("TRANSFERRED");
        assertThat(transferred.getCodTransferredAt()).isEqualTo(NOW);
        assertThat(transferred.getCodCollectedAt()).isEqualTo(deliveredAt);
    }

    @Test
    void wrongCarrierShopOrAmountCannotMarkAnOrderPaidOrDelivered() {
        Order order = order(null);
        order.setStatus(OrderStatus.SHIPPING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentProvider(PaymentProvider.COD);
        orders.saveAndFlush(order);
        String tracking = UUID.randomUUID().toString();
        var shipment =
                shipments.saveAndFlush(
                        com.fashionstore.order.entity.Shipment.builder()
                                .order(order)
                                .provider(
                                        com.fashionstore.order.entity.enumeration.ShipmentProvider
                                                .GHN)
                                .trackingCode(tracking)
                                .ghnOrderCode(tracking)
                                .status(
                                        com.fashionstore.order.entity.enumeration.ShipmentStatus
                                                .SHIPPING)
                                .build());
        var payload =
                com.fashionstore.order.dto.GhnWebhookPayload.builder()
                        .shopId("999")
                        .orderCode(tracking)
                        .status("delivered")
                        .time(NOW.minusSeconds(3600).toString())
                        .codAmount(new BigDecimal("900000"))
                        .build();
        assertThatThrownBy(() -> shipmentService.handleGhnCallback(payload))
                .isInstanceOf(AppException.class);
        verify(ghn, never()).getVerifiedShipment(anyString());
        payload.setShopId("123");
        when(ghn.getVerifiedShipment(tracking))
                .thenReturn(
                        new com.fashionstore.order.dto.VerifiedGhnShipment(
                                tracking,
                                "123",
                                order.getOrderCode(),
                                "delivered",
                                new BigDecimal("900000"),
                                true,
                                NOW.minusSeconds(3600),
                                false,
                                null));
        assertThatThrownBy(() -> shipmentService.handleGhnCallback(payload))
                .isInstanceOf(AppException.class);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPING);
        assertThat(shipments.findById(shipment.getId()).orElseThrow().getCodCollectedAt()).isNull();
        verify(outbox, never()).saveMessage(any(), any(), any());
    }

    @Test
    void uncollectedCarrierFlagDoesNotCreateCashProofAndLaterProofCanBeApplied() {
        Order order = order(null);
        order.setStatus(OrderStatus.SHIPPING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentProvider(PaymentProvider.COD);
        orders.saveAndFlush(order);
        String tracking = UUID.randomUUID().toString();
        var shipment =
                shipments.saveAndFlush(
                        com.fashionstore.order.entity.Shipment.builder()
                                .order(order)
                                .provider(
                                        com.fashionstore.order.entity.enumeration.ShipmentProvider
                                                .GHN)
                                .trackingCode(tracking)
                                .ghnOrderCode(tracking)
                                .status(
                                        com.fashionstore.order.entity.enumeration.ShipmentStatus
                                                .SHIPPING)
                                .build());
        Instant deliveredAt = NOW.minusSeconds(3600);
        when(ghn.getVerifiedShipment(tracking))
                .thenReturn(
                        new com.fashionstore.order.dto.VerifiedGhnShipment(
                                tracking,
                                "123",
                                order.getOrderCode(),
                                "delivered",
                                order.getTotalAmount(),
                                false,
                                null,
                                false,
                                null));
        var payload =
                com.fashionstore.order.dto.GhnWebhookPayload.builder()
                        .shopId("123")
                        .orderCode(tracking)
                        .status("delivered")
                        .time(deliveredAt.toString())
                        .codAmount(order.getTotalAmount())
                        .build();
        shipmentService.handleGhnCallback(payload);
        assertThat(shipments.findById(shipment.getId()).orElseThrow().getCodCollectedAt()).isNull();
        var firstEvent =
                org.mockito.ArgumentCaptor.forClass(
                        com.fashionstore.contracts.common.EventEnvelope.class);
        verify(outbox)
                .saveMessage(
                        org.mockito.ArgumentMatchers.eq(order.getId()),
                        anyString(),
                        firstEvent.capture());
        assertThat(
                        ((com.fashionstore.contracts.order.OrderDeliveredEvent)
                                        firstEvent.getValue().payload())
                                .codCollectedAmount())
                .isNull();
        when(ghn.getVerifiedShipment(tracking))
                .thenReturn(
                        new com.fashionstore.order.dto.VerifiedGhnShipment(
                                tracking,
                                "123",
                                order.getOrderCode(),
                                "delivered",
                                order.getTotalAmount(),
                                true,
                                deliveredAt,
                                false,
                                null));
        shipmentService.handleGhnCallback(payload);
        assertThat(shipments.findById(shipment.getId()).orElseThrow().getCodCollectedAt())
                .isEqualTo(deliveredAt);
        assertThat(orders.findById(order.getId()).orElseThrow().getDeliveredAt())
                .isEqualTo(deliveredAt);
        verify(outbox, times(2))
                .saveMessage(org.mockito.ArgumentMatchers.eq(order.getId()), anyString(), any());
    }

    @Test
    void manualCancellationClosesOnlyAnUnreceivedReturn() {
        ReturnRequest returned = approvedReturn(NOW.minusSeconds(24 * 3600));
        var cancelled =
                service.cancelReturn(
                        returned.getId(),
                        new com.fashionstore.order.dto.CancelReturnRequest(
                                "Customer did not send goods"));
        assertThat(cancelled.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(cancelled.getClosedAt()).isEqualTo(NOW);

        ReturnRequest received = approvedReturn(NOW.minusSeconds(24 * 3600));
        service.receiveReturn(
                received.getId(), new ReceiveReturnRequest(true, "QC passed", List.of()));
        assertThatThrownBy(
                        () ->
                                service.cancelReturn(
                                        received.getId(),
                                        new com.fashionstore.order.dto.CancelReturnRequest(
                                                "Cancel")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void evidenceOwnedByAnotherAccountCannotBeRecordedAsWarehouseProof() {
        ReturnRequest returned = approvedReturn(NOW.minusSeconds(24 * 3600));
        String evidenceId = UUID.randomUUID().toString();
        when(productCatalog.getReturnEvidence(evidenceId, "customer-1"))
                .thenReturn(
                        ApiResponse.<ReturnEvidenceMetadata>builder()
                                .data(
                                        new ReturnEvidenceMetadata(
                                                evidenceId,
                                                "another-user",
                                                "RETURN_EVIDENCE",
                                                "ACTIVE",
                                                "PRIVATE",
                                                "image/jpeg"))
                                .build());
        assertThatThrownBy(
                        () ->
                                service.receiveReturn(
                                        returned.getId(),
                                        new ReceiveReturnRequest(
                                                false, "Failed QC", List.of(evidenceId))))
                .isInstanceOf(AppException.class);
        assertThat(returns.findById(returned.getId()).orElseThrow().getStatus())
                .isEqualTo(ReturnRequestStatus.APPROVED);
    }

    @Test
    void confirmingReceiptClosesTheRightToCreateAReturn() {
        Order order = order(NOW.minusSeconds(24 * 3600));
        receipts.confirmReceipt(order.getId());
        assertThatThrownBy(
                        () ->
                                service.createReturnRequest(
                                        order.getId(),
                                        ReturnOrderRequest.builder().reason("Return").build()))
                .isInstanceOf(AppException.class);
    }

    private ReturnRequest approvedReturn(Instant deliveredAt) {
        Order order = order(deliveredAt);
        return returns.saveAndFlush(
                ReturnRequest.builder()
                        .order(order)
                        .userId("customer-1")
                        .reason("Wrong item")
                        .status(ReturnRequestStatus.APPROVED)
                        .approvedAt(NOW.minusSeconds(3600))
                        .build());
    }

    private Order order(Instant deliveredAt) {
        String key = UUID.randomUUID().toString();
        return orders.saveAndFlush(
                Order.builder()
                        .orderCode("R-" + key.substring(0, 20))
                        .checkoutId(key)
                        .idempotencyKey(key)
                        .paymentId(key)
                        .userId("customer-1")
                        .paymentMethod(PaymentMethod.ONLINE)
                        .paymentProvider(PaymentProvider.PAYOS)
                        .shippingAddress("123 Test Street")
                        .address(
                                ShippingAddress.builder()
                                        .recipientName("Test Customer")
                                        .recipientPhone("0900000000")
                                        .build())
                        .subtotalAmount(new BigDecimal("100000"))
                        .totalAmount(new BigDecimal("100000"))
                        .status(OrderStatus.DELIVERED)
                        .deliveredAt(deliveredAt)
                        .build());
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}

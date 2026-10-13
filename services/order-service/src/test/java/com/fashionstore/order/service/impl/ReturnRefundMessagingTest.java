package com.fashionstore.order.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.order.client.*;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

class ReturnRefundMessagingTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final PaymentFeignClient payment = mock(PaymentFeignClient.class);
    final OutboxEventRecorder outbox = mock(OutboxEventRecorder.class);
    ReturnServiceImpl service;
    Order order;
    ReturnRequest returned;

    @BeforeEach
    void setup() {
        var actor = mock(CurrentUserProvider.class);
        when(actor.getCurrentUserId()).thenReturn("admin");
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service =
                new ReturnServiceImpl(
                        returns,
                        orders,
                        history,
                        actor,
                        new ObjectMapper().findAndRegisterModules(),
                        mock(CatalogFeignClient.class),
                        transactions,
                        Clock.fixed(Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC),
                        mock(GhnClient.class),
                        mock(ShipmentRepository.class),
                        outbox);
        order =
                Order.builder()
                        .userId("buyer")
                        .paymentId("pay-1")
                        .status(OrderStatus.DELIVERED)
                        .currency("VND")
                        .totalAmount(new BigDecimal("100000"))
                        .build();
        order.setId("order-1");
        returned =
                ReturnRequest.builder()
                        .order(order)
                        .userId("buyer")
                        .reason("Faulty item")
                        .status(ReturnRequestStatus.RECEIVED)
                        .qcPassed(true)
                        .build();
        returned.setId("return-1");
        when(returns.findOrderIdByReturnId("return-1")).thenReturn(Optional.of("order-1"));
        when(orders.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(returns.findByIdForUpdate("return-1")).thenReturn(Optional.of(returned));
    }

    @Test
    void approvalEnqueuesRefundAndStaysOpenWithoutCallingPaymentHttp() {
        var response = service.refundReturn("return-1", null);
        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox)
                .saveMessage(
                        eq("order-1"),
                        eq(EventTypes.ORDER_FUNDS_REFUND_REQUESTED),
                        envelope.capture());
        var command = (RefundOrderFundsCommand) envelope.getValue().payload();
        assertThat(command.refundId()).isEqualTo("return:return-1");
        assertThat(command.sourceId()).isEqualTo("return-1");
        assertThat(command.paymentId()).isEqualTo("pay-1");
        assertThat(command.reasonType()).isEqualTo("RETURN_REFUND");
        assertThat(command.amount()).isEqualByComparingTo("100000");
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(response.getRefundStartedAt()).isNotNull();
        assertThat(returned.getClosedAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verifyNoInteractions(payment, history);
    }

    @Test
    void retryKeepsFinancialIdentityAndNeverClosesBeforeReply() {
        service.refundReturn("return-1", null);
        var started = returned.getRefundStartedAt();
        service.refundReturn("return-1", new BigDecimal("100000"));
        var envelopes = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox, times(2))
                .saveMessage(
                        eq("order-1"),
                        eq(EventTypes.ORDER_FUNDS_REFUND_REQUESTED),
                        envelopes.capture());
        assertThat(envelopes.getAllValues().get(0).payload())
                .isEqualTo(envelopes.getAllValues().get(1).payload());
        assertThat(returned.getRefundStartedAt()).isEqualTo(started);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        verifyNoInteractions(payment);
    }

    @Test
    void creditedReplyCompletesOnceAndLateRejectionCannotUndoIt() {
        service.refundReturn("return-1", null);
        var reply = credited("pay-1", "return:return-1", "ledger-1", "100000");
        service.refundCredited(reply);
        service.refundCredited(reply);
        service.refundRejected(
                new com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent(
                        "return:return-1",
                        "order-1",
                        "pay-1",
                        "RETURN_REFUND",
                        "return-1",
                        "OLD_ERROR",
                        "Late rejection"));
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(returned.getRefundLedgerEntryId()).isEqualTo("ledger-1");
        assertThat(returned.getClosedAt()).isNotNull();
        assertThat(returned.getRefundFailureReason()).isNull();
        verify(history, times(1)).save(any());
    }

    @Test
    void invalidReplyCannotCompleteReturnOrOverwriteReceipt() {
        service.refundReturn("return-1", null);
        for (var invalid :
                java.util.List.of(
                        credited("foreign-payment", "return:return-1", "ledger-1", "100000"),
                        credited("pay-1", "foreign-refund", "ledger-1", "100000"),
                        credited("pay-1", "return:return-1", "", "100000"),
                        credited("pay-1", "return:return-1", "ledger-1", "1"))) {
            assertThatThrownBy(() -> service.refundCredited(invalid))
                    .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        }
        assertThat(returned.getClosedAt()).isNull();
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        verifyNoInteractions(history);
        service.refundCredited(credited("pay-1", "return:return-1", "ledger-1", "100000"));
        assertThatThrownBy(
                        () ->
                                service.refundCredited(
                                        credited("pay-1", "return:return-1", "ledger-2", "100000")))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
    }

    @Test
    void rejectionKeepsReturnOpenAndRetryUsesSameIdentity() {
        service.refundReturn("return-1", null);
        var reply =
                new com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent(
                        "return:return-1",
                        "order-1",
                        "pay-1",
                        "RETURN_REFUND",
                        "return-1",
                        "SOURCE_INVALID",
                        "Not paid");
        service.refundRejected(reply);
        service.refundRejected(reply);
        assertThat(returned.getRefundFailureReason()).isEqualTo("SOURCE_INVALID: Not paid");
        assertThat(returned.getClosedAt()).isNull();
        verify(history, times(1)).save(any());
        service.refundReturn("return-1", null);
        assertThat(returned.getRefundFailureReason()).isNull();
        verify(outbox, times(2))
                .saveMessage(eq("order-1"), eq(EventTypes.ORDER_FUNDS_REFUND_REQUESTED), any());
    }

    @Test
    void failedQcOrMissingPaymentCannotEnqueueRefund() {
        returned.setQcPassed(false);
        assertThatThrownBy(() -> service.refundReturn("return-1", null))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        returned.setQcPassed(true);
        order.setPaymentId(null);
        assertThatThrownBy(() -> service.refundReturn("return-1", null))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void unsolicitedCreditAndWrongSourceRejectionAreRejected() {
        assertThatThrownBy(
                        () ->
                                service.refundCredited(
                                        credited("pay-1", "return:return-1", "ledger-1", "100000")))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        service.refundReturn("return-1", null);
        assertThatThrownBy(
                        () ->
                                service.refundRejected(
                                        new com.fashionstore.contracts.payment.event
                                                .OrderFundsRefundRejectedEvent(
                                                "cancel:order-1",
                                                "order-1",
                                                "pay-1",
                                                "PAID_CANCELLATION",
                                                "return-1",
                                                "BAD",
                                                "Wrong source")))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verifyNoInteractions(history);
    }

    private com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent credited(
            String paymentId, String refundId, String ledgerId, String amount) {
        return new com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent(
                refundId,
                "order-1",
                paymentId,
                "RETURN_REFUND",
                "return-1",
                ledgerId,
                new BigDecimal(amount));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = ReturnRequestKind.class,
            names = {"VERIFIED_LOSS", "CARRIER_RETURN"})
    void existingFulfillmentReturnsUseSameQueueAndReceiptPath(ReturnRequestKind kind) {
        returned.setKind(kind);
        order.setStatus(
                kind == ReturnRequestKind.VERIFIED_LOSS ? OrderStatus.LOST : OrderStatus.RETURNED);
        if (kind == ReturnRequestKind.VERIFIED_LOSS) {
            returned.setStatus(ReturnRequestStatus.APPROVED);
            returned.setVerificationBy("admin");
            returned.setVerificationEvidenceIds("[\"evidence\"]");
        }
        service.refundReturn("return-1", null);
        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox)
                .saveMessage(
                        eq("order-1"),
                        eq(EventTypes.ORDER_FUNDS_REFUND_REQUESTED),
                        envelope.capture());
        assertThat(((RefundOrderFundsCommand) envelope.getValue().payload()).reasonType())
                .isEqualTo(kind.name());
        service.refundCredited(
                new com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent(
                        "return:return-1",
                        "order-1",
                        "pay-1",
                        kind.name(),
                        "return-1",
                        "ledger-1",
                        new BigDecimal("100000")));
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
    }
}

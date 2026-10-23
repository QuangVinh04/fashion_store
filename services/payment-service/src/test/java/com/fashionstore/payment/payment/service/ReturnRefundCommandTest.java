package com.fashionstore.payment.payment.service;

import com.fashionstore.payment.wallet.service.WalletLedger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.*;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.payment.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

class ReturnRefundCommandTest {
    final PaymentRepository payments = mock(PaymentRepository.class);
    final PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    final OutboxEventRecorder outbox = mock(OutboxEventRecorder.class);
    final RefundOrderFundsCommand command =
            new RefundOrderFundsCommand(
                    "return:return-1",
                    "order-1",
                    "pay-1",
                    new BigDecimal("100000"),
                    "VND",
                    "RETURN_REFUND",
                    "return-1");
    final WalletLedger ledger = mock(WalletLedger.class);
    OrderFundsRefundService service;

    @BeforeEach
    void setup() {
        service = new OrderFundsRefundService(payments, refunds, ledger, outbox);
    }

    @Test
    void returnRefundCreditsAndPublishesExactReceipt() {
        var payment =
                Payment.builder()
                        .orderId("order-1")
                        .userId("buyer")
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .method(PaymentMethod.COD)
                        .provider(PaymentProvider.COD)
                        .status(PaymentStatus.COMPLETED)
                        .paidAt(LocalDateTime.now())
                        .operationId("ghn:123:GHN-1")
                        .build();
        payment.setId("pay-1");
        when(payments.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));
        when(refunds.sumReservedAmountByPaymentId("pay-1")).thenReturn(BigDecimal.ZERO);
        var credit =
                WalletLedgerEntry.builder()
                        .userId("buyer")
                        .amount(100000)
                        .balanceAfter(150000)
                        .build();
        credit.setId("ledger-1");
        doReturn(credit)
                .when(ledger)
                .credit("buyer", 100000, "ORDER_REFUND", "pay-1", "wallet-refund:pay-1");
        service.refundOrderFunds(command, "message-1", "return-1");
        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox)
                .saveMessage(
                        eq("order-1"),
                        eq(EventTypes.ORDER_FUNDS_REFUND_CREDITED),
                        envelope.capture());
        var reply = (OrderFundsRefundCreditedEvent) envelope.getValue().payload();
        assertThat(reply.refundId()).isEqualTo(command.refundId());
        assertThat(reply.sourceId()).isEqualTo("return-1");
        assertThat(reply.ledgerEntryId()).isEqualTo("ledger-1");
        assertThat(reply.amount()).isEqualByComparingTo("100000");
        verify(refunds)
                .saveAndFlush(
                        argThat(
                                refund ->
                                        "return:return-1".equals(refund.getBusinessRefundId())
                                                && "RETURN_REFUND".equals(refund.getReasonType())));
    }

    @Test
    void technicalDatabaseFailureEscapesForBrokerRetryWithoutBusinessRejection() {
        when(payments.findByOrderIdForUpdate("order-1"))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.refundOrderFunds(command, "message-1", "return-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verifyNoInteractions(outbox);
    }
}

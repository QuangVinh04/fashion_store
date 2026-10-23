package com.fashionstore.payment.payment.event;

import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundWalletSagaCommand;
import com.fashionstore.payment.payment.service.PaymentService;
import com.fashionstore.payment.payment.service.OrderFundsRefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRequestedEventListenerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderFundsRefundService refundService;

    private ObjectMapper objectMapper;
    private PaymentRequestedEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new PaymentRequestedEventListener(
                objectMapper,
                paymentService,
                refundService
        );
    }

    @Test
    void handle_orderFundsRefundRequestedCallsValidatedWalletRefund() {
        var command = new com.fashionstore.contracts.payment.command.RefundOrderFundsCommand(
                "cancel:case-1", "ord-1", "pay-1", new BigDecimal("100000"), "VND", "PAID_CANCELLATION", "case-1");
        listener.handle(EventEnvelope.v1(EventTypes.ORDER_FUNDS_REFUND_REQUESTED, "ord-1", "case-1", command), "msg-refund");
        verify(refundService).refundOrderFunds(command, "msg-refund", "case-1");
    }

    @Test
    void handle_paymentRequested_callsAuthorize() {
        AuthorizePaymentCommand command = new AuthorizePaymentCommand(
                "ord-1", "user-1", "ONLINE", "PAYOS", new BigDecimal("100000"), "VND", "127.0.0.1"
        );
        EventEnvelope<AuthorizePaymentCommand> envelope = EventEnvelope.v1(
                EventTypes.PAYMENT_REQUESTED, "ord-1", "corr-1", command
        );

        listener.handle(envelope, "msg-1");

        verify(paymentService).authorize(any(AuthorizePaymentCommand.class), eq("msg-1"), eq("corr-1"));
    }

    @Test
    void handle_walletSagaCompensationRequested_callsCompensate() {
        RefundWalletSagaCommand command = new RefundWalletSagaCommand(
                "comp-1", "ord-1", "pay-1", 1, new BigDecimal("50000"), "VND", "reason"
        );
        EventEnvelope<RefundWalletSagaCommand> envelope = EventEnvelope.v1(
                EventTypes.WALLET_SAGA_COMPENSATION_REQUESTED, "ord-1", "corr-1", command
        );

        listener.handle(envelope, "msg-1");

        verify(refundService).compensate(any(RefundWalletSagaCommand.class), eq("msg-1"), eq("corr-1"));
    }

    @Test
    void handle_paymentCancellationRequested_callsCancel() {
        CancelPaymentCommand command = new CancelPaymentCommand("ord-1", "pay-1", "reason");
        EventEnvelope<CancelPaymentCommand> envelope = EventEnvelope.v1(
                EventTypes.PAYMENT_CANCELLATION_REQUESTED, "ord-1", "corr-1", command
        );

        listener.handle(envelope, "msg-1");

        verify(paymentService).cancel(any(CancelPaymentCommand.class), eq("msg-1"), eq("corr-1"));
    }
}

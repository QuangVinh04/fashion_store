package com.fashionstore.payment.wallet.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.wallet.dto.CreateWalletTopUpRequest;
import com.fashionstore.payment.wallet.dto.WalletTopUpResponse;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.wallet.repository.WalletAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WalletTopUpServiceTest {

    private PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private PayOS payOSClient = mock(PayOS.class);
    private PaymentRequestsService paymentRequests = mock(PaymentRequestsService.class);

    private WalletService service;
    private org.springframework.transaction.PlatformTransactionManager transactionManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
    private Payment storedPayment;

    @BeforeEach
    void setUp() {
        service = new WalletService(
                mock(WalletAccountRepository.class),
                mock(com.fashionstore.payment.wallet.repository.WalletLedgerEntryRepository.class),
                paymentRepository,
                mock(com.fashionstore.payment.payment.repository.PaymentRefundRepository.class),
                currentUserProvider,
                mock(com.fashionstore.payment.shared.outbox.OutboxEventRecorder.class),
                jdbcTemplate,
                payOSClient,
                transactionManager
        );
        ReflectionTestUtils.setField(service, "topUpEnabled", true);
        ReflectionTestUtils.setField(service, "minimum", 10000L);
        ReflectionTestUtils.setField(service, "maximum", 5000000L);
        ReflectionTestUtils.setField(service, "ttlMinutes", 15L);
        ReflectionTestUtils.setField(service, "returnUrl", "http://localhost:3000/return");
        ReflectionTestUtils.setField(service, "cancelUrl", "http://localhost:3000/cancel");

        when(transactionManager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        when(paymentRepository.findByIdForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(storedPayment));
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(payOSClient.paymentRequests()).thenReturn(paymentRequests);
    }

    @Test
    void create_whenDisabled_throwsException() {
        ReflectionTestUtils.setField(service, "topUpEnabled", false);

        assertThatThrownBy(() -> service.create(new CreateWalletTopUpRequest(new BigDecimal("50000"), "op-1")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void create_whenAmountLessThanMin_throwsException() {
        assertThatThrownBy(() -> service.create(new CreateWalletTopUpRequest(new BigDecimal("5000"), "op-1")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void create_whenSuccess_createsPaymentAndReturnsResponse() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(123456L);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId("pay-1");
            storedPayment = p;
            return p;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatePaymentLinkResponse linkResponse = mock(CreatePaymentLinkResponse.class);
        when(linkResponse.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/123");
        when(paymentRequests.create(any())).thenReturn(linkResponse);

        WalletTopUpResponse response = service.create(new CreateWalletTopUpRequest(new BigDecimal("100000"), "op-1"));

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("pay-1");
        assertThat(response.paymentUrl()).isEqualTo("https://pay.payos.vn/web/123");
        verify(paymentRepository).saveAndFlush(any(Payment.class));
    }

    @Test
    void providerTimeoutIsReportedInsteadOfReturningAnUnusablePendingLink() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(123456L);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            payment.setId("pay-timeout");
            storedPayment = payment;
            return payment;
        });
        when(paymentRequests.create(any())).thenThrow(new IllegalStateException("provider timeout"));

        assertThatThrownBy(() -> service.create(new CreateWalletTopUpRequest(100000L, "op-timeout")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void get_whenFound_returnsResponse() {
        Payment p = Payment.builder()
                .type(com.fashionstore.common.payment.PaymentType.TOP_UP)
                .amount(new BigDecimal("100000"))
                .currency("VND")
                .userId("user-1")
                .build();
        p.setId("pay-1");

        when(paymentRepository.findByIdAndUserId("pay-1", "user-1")).thenReturn(Optional.of(p));

        WalletTopUpResponse response = service.get("pay-1");
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("pay-1");
    }
}

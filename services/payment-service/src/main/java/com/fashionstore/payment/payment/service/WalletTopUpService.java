package com.fashionstore.payment.payment.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.payment.dto.CreateWalletTopUpRequest;
import com.fashionstore.payment.payment.dto.WalletTopUpResponse;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.wallet.exception.WalletErrorCode;
import com.fashionstore.payment.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
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
import java.util.UUID;

/** Wallet top-ups: a TOP_UP payment paid through payOS; the callback credits the wallet. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletTopUpService {

    private final PaymentRepository payments;
    private final CurrentUserProvider currentUser;
    private final JdbcTemplate jdbcTemplate;
    private final PayOS payOSClient;
    private final PlatformTransactionManager transactionManager;

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
        long amountVnd = WalletService.toVnd(request.amount());
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
}

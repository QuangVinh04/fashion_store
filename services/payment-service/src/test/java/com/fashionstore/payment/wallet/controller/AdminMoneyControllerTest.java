package com.fashionstore.payment.wallet.controller;

import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.payment.payment.controller.AdminPaymentController;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fashionstore.payment.shared.config.security.SecurityConfig;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.wallet.entity.WalletAccount;
import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import com.fashionstore.payment.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.wallet.repository.WalletAccountRepository;
import com.fashionstore.payment.wallet.repository.WalletLedgerEntryRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.*;

@WebMvcTest({AdminPaymentController.class, AdminWalletController.class})
@ContextConfiguration(
        classes = {
            AdminPaymentController.class,
            AdminWalletController.class,
            SecurityConfig.class,
            com.fashionstore.common.exception.GlobalExceptionHandler.class
        })
@ImportAutoConfiguration(
        com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration.class)
class AdminMoneyControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentRepository payments;
    @MockitoBean PaymentRefundRepository refunds;
    @MockitoBean WalletAccountRepository wallets;
    @MockitoBean WalletLedgerEntryRepository entries;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        when(decoder.decode("admin"))
                .thenReturn(permissionToken("admin", PermissionNames.PAYMENT_READ, PermissionNames.WALLET_READ));
        when(decoder.decode("wallet-reader")).thenReturn(permissionToken("wallet-reader", PermissionNames.WALLET_READ));
        when(decoder.decode("payment-reader")).thenReturn(permissionToken("payment-reader", PermissionNames.PAYMENT_READ));
        when(decoder.decode("role-admin")).thenReturn(realmRoleToken("role-admin", "ADMIN"));
        when(decoder.decode("no-permission")).thenReturn(permissionToken("no-permission", PermissionNames.RETURN_READ));
        when(decoder.decode("buyer")).thenReturn(realmRoleToken("buyer", "USER"));
    }

    private Jwt realmRoleToken(String subject, String role) {
        return Jwt.withTokenValue(subject)
                .header("alg", "RS256")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }

    private Jwt permissionToken(String subject, String... permissions) {
        return Jwt.withTokenValue(subject)
                .header("alg", "RS256")
                .subject(subject)
                .claim("resource_access", Map.of("fashion-api", Map.of("roles", List.of(permissions))))
                .build();
    }

    @Test
    void walletReaderReadsWalletsButNotPayments() throws Exception {
        when(wallets.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/admin/wallets").header("Authorization", "Bearer wallet-reader"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/payments/order/x").header("Authorization", "Bearer wallet-reader"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void paymentReaderReadsPaymentsButNotWallets() throws Exception {
        mvc.perform(get("/api/v1/admin/payments/order/x").header("Authorization", "Bearer payment-reader"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/wallets").header("Authorization", "Bearer payment-reader"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(wallets);
    }

    @Test
    void roleAdminAndUnrelatedPermissionCannotReadAdminMoney() throws Exception {
        for (String bearer : List.of("role-admin", "no-permission")) {
            mvc.perform(get("/api/v1/admin/wallets").header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/payments/order/x").header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(payments, wallets);
    }

    @Test
    void anonymousMoneyReadIs401() throws Exception {
        mvc.perform(get("/api/v1/admin/payments/order/order")).andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }

    @Test
    void anonymousWalletReadIs401() throws Exception {
        mvc.perform(get("/api/v1/admin/wallets/foreign")).andExpect(status().isUnauthorized());
        verifyNoInteractions(wallets);
    }

    @Test
    void buyerCannotReadAdminMoney() throws Exception {
        mvc.perform(
                        get("/api/v1/admin/payments/order/order")
                                .header("Authorization", "Bearer buyer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
        mvc.perform(get("/api/v1/admin/wallets/foreign").header("Authorization", "Bearer buyer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(wallets);
    }

    @Test
    void adminCanReadOtherBuyersPaymentWithoutProviderSecrets() throws Exception {
        var payment =
                Payment.builder()
                        .orderId("order")
                        .userId("foreign")
                        .method(com.fashionstore.common.payment.PaymentMethod.COD)
                        .provider(com.fashionstore.common.payment.PaymentProvider.COD)
                        .status(
                                com.fashionstore.payment.payment.entity.PaymentStatus
                                        .COD_PENDING)
                        .amount(new BigDecimal("9007199254740993"))
                        .currency("VND")
                        .paymentUrl("secret-url")
                        .initiationToken("secret-token")
                        .build();
        payment.setId("pay");
        when(payments.findByOrderId("order")).thenReturn(Optional.of(payment));
        mvc.perform(
                        get("/api/v1/admin/payments/order/order")
                                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("foreign"))
                .andExpect(jsonPath("$.data.amount").value("9007199254740993"))
                .andExpect(jsonPath("$.data.paymentUrl").doesNotExist())
                .andExpect(jsonPath("$.data.initiationToken").doesNotExist());
    }

    @Test
    void adminWalletBalanceIsExactAndCannotBeWritten() throws Exception {
        when(wallets.findByUserId("foreign"))
                .thenReturn(
                        Optional.of(
                                WalletAccount.builder()
                                        .userId("foreign")
                                        .balance(9007199254740993L)
                                        .build()));
        mvc.perform(get("/api/v1/admin/wallets/foreign").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value("9007199254740993"));
        mvc.perform(post("/api/v1/admin/wallets/foreign").header("Authorization", "Bearer admin"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void filteredWalletListHasExactServerTotalsAndCapsPageSize() throws Exception {
        when(wallets.findByUserId(eq("foreign"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        mvc.perform(
                        get("/api/v1/admin/wallets?userId=foreign&size=9999")
                                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(wallets).findByUserId(eq("foreign"), argThat(page -> page.getPageSize() == 100));
    }

    @Test
    void exactLedgerReadIsBoundToWalletOwner() throws Exception {
        var entry =
                WalletLedgerEntry.builder()
                        .userId("foreign")
                        .direction("CREDIT")
                        .amount(9007199254740993L)
                        .balanceAfter(9007199254740993L)
                        .sourceType("RETURN_REFUND")
                        .sourceId("case")
                        .operationId("refund:case")
                        .build();
        entry.setId("entry");
        when(entries.findById("entry")).thenReturn(Optional.of(entry));
        mvc.perform(
                        get("/api/v1/admin/wallets/foreign/transactions/entry")
                                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("entry"))
                .andExpect(jsonPath("$.data.amount").value("9007199254740993"));
        mvc.perform(
                        get("/api/v1/admin/wallets/other/transactions/entry")
                                .header("Authorization", "Bearer admin"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/admin/wallets/foreign/transactions/entry")
                                .header("Authorization", "Bearer buyer"))
                .andExpect(status().isForbidden());
    }
}

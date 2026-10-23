package com.fashionstore.payment.wallet.controller;

import com.fashionstore.payment.shared.config.security.SecurityConfig;
import com.fashionstore.payment.wallet.repository.WalletLedgerEntryRepository;
import com.fashionstore.payment.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
@ContextConfiguration(classes={WalletController.class,SecurityConfig.class,
        com.fashionstore.common.exception.GlobalExceptionHandler.class})
@ImportAutoConfiguration(com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration.class)
class WalletControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WalletService walletService;
    @MockitoBean WalletLedgerEntryRepository entries;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach void token() {
        when(jwtDecoder.decode("owner-token")).thenReturn(Jwt.withTokenValue("owner-token")
                .header("alg","RS256").subject("owner-1").build());
    }
    @Test void anonymousCannotReadWallet() throws Exception {
        mvc.perform(get("/api/v1/wallet")).andExpect(status().isUnauthorized());
        verifyNoInteractions(walletService);
    }
    @Test void balanceUsesJwtOwnerAndExactIntegerString() throws Exception {
        when(walletService.getBalance("owner-1")).thenReturn(9007199254740993L);
        mvc.perform(get("/api/v1/wallet").header("Authorization","Bearer owner-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value("9007199254740993"))
                .andExpect(jsonPath("$.data.currency").value("VND"));
        verify(walletService).getBalance("owner-1");
    }
    @Test void transactionQueryCannotOverrideOwnerAndCapsPageSize() throws Exception {
        when(entries.findByUserIdOrderByCreatedAtDescIdDesc(eq("owner-1"),any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/wallet/transactions?userId=foreign-user&size=9999")
                .header("Authorization","Bearer owner-token")).andExpect(status().isOk());
        verify(entries).findByUserIdOrderByCreatedAtDescIdDesc(eq("owner-1"),argThat(page -> page.getPageSize()==100));
    }
    @Test void invalidTopUpRequestDoesNotCallProvider() throws Exception {
        mvc.perform(post("/api/v1/wallet/top-ups").header("Authorization","Bearer owner-token")
                .contentType("application/json").content("{\"amount\":-1,\"operationId\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(walletService);
    }
}

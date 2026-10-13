package com.fashionstore.payment.service;

import static org.assertj.core.api.Assertions.*;

import com.fashionstore.common.exception.AppException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class WalletServiceTest {
    @Test
    void exactIntegerVndAccepted() {
        assertThat(WalletService.toVnd(new BigDecimal("100000.00"))).isEqualTo(100000L);
    }

    @Test
    void fractionalAmountRejected() {
        assertThatThrownBy(() -> WalletService.toVnd(new BigDecimal("100000.50")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void overflowRejected() {
        assertThatThrownBy(() -> WalletService.toVnd(new BigDecimal("9223372036854775808")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void nonPositiveAndMissingAmountsRejected() {
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, BigDecimal.valueOf(-1)}) {
            assertThatThrownBy(() -> WalletService.toVnd(amount))
                    .isInstanceOf(AppException.class);
        }
    }
}

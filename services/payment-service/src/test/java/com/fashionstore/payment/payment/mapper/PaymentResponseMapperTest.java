package com.fashionstore.payment.payment.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.payment.payment.entity.Payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class PaymentResponseMapperTest {

    @Test
    void exposesAuthorizationDeadlineSoCustomerKnowsWhenTheOrderExpires() {
        Instant deadline = Instant.parse("2026-10-21T07:35:00Z");
        Payment payment = Payment.builder().authorizationExpiresAt(deadline).build();

        assertThat(new PaymentResponseMapper().toResponse(payment).getAuthorizationExpiresAt())
                .isEqualTo(deadline);
    }
}

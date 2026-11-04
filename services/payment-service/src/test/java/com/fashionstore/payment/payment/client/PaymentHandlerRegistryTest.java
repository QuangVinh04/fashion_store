package com.fashionstore.payment.payment.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentHandlerRegistryTest {

    @Test
    void resolvesHandlerByProvider() {
        PaymentHandler other = mock(PaymentHandler.class);
        when(other.provider()).thenReturn(PaymentProvider.INTERNAL);
        PaymentHandler payos = mock(PaymentHandler.class);
        when(payos.provider()).thenReturn(PaymentProvider.PAYOS);
        PaymentHandlerRegistry registry = new PaymentHandlerRegistry(List.of(other, payos));

        assertThat(registry.get(PaymentProvider.INTERNAL)).isSameAs(other);
        assertThat(registry.get(PaymentProvider.PAYOS)).isSameAs(payos);
    }

    @Test
    void throwsWhenProviderHasNoHandler() {
        PaymentHandlerRegistry registry = new PaymentHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.get(PaymentProvider.PAYOS))
                .isInstanceOf(AppException.class);
    }
}

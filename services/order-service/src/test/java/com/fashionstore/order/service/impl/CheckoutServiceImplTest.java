package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.cart.service.CartService;
import com.fashionstore.order.config.ErrorCode;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.model.Checkout;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.enumeration.CheckoutStatus;
import com.fashionstore.order.model.enumeration.ShippingMethod;
import com.fashionstore.order.repository.CheckoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutServiceImplTest {

    @Mock
    private CheckoutRepository checkoutRepository;

    @Mock
    private CartService cartService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CheckoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckoutServiceImpl(checkoutRepository, cartService, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(checkoutRepository.save(any(Checkout.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cancelsAnOpenCheckout() {
        Checkout checkout = checkout(CheckoutStatus.SUBMITTED);
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));

        CheckoutResponse response = service.cancelCheckout("checkout-1");

        assertEquals(CheckoutStatus.CANCELLED, response.getStatus());
    }

    @Test
    void cancellingTwiceReturnsTheSameResult() {
        Checkout checkout = checkout(CheckoutStatus.CANCELLED);
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));

        CheckoutResponse response = service.cancelCheckout("checkout-1");

        assertEquals(CheckoutStatus.CANCELLED, response.getStatus());
        verify(checkoutRepository, never()).save(any(Checkout.class));
    }

    /** Checkout đã sinh đơn thì việc hủy thuộc về đơn — hủy ở đây sẽ để lại đơn mồ côi. */
    @Test
    void cannotCancelACheckoutThatAlreadyProducedAnOrder() {
        Checkout checkout = checkout(CheckoutStatus.SUBMITTED);
        Order order = Order.builder().build();
        order.setId("order-1");
        checkout.setOrder(order);
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));

        AppException exception = assertThrows(AppException.class, () -> service.cancelCheckout("checkout-1"));

        assertEquals(ErrorCode.CHECKOUT_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void cannotCancelACompletedCheckout() {
        Checkout checkout = checkout(CheckoutStatus.COMPLETED);
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));

        AppException exception = assertThrows(AppException.class, () -> service.cancelCheckout("checkout-1"));

        assertEquals(ErrorCode.CHECKOUT_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void reportsMissingCheckout() {
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> service.cancelCheckout("checkout-1"));

        assertEquals(ErrorCode.CHECKOUT_NOT_FOUND, exception.getErrorCode());
    }

    private Checkout checkout(CheckoutStatus status) {
        Checkout checkout = Checkout.builder()
                .userId("user-1")
                .status(status)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentProvider(PaymentProvider.VNPAY)
                .shippingMethod(ShippingMethod.STANDARD)
                .subtotalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .build();
        checkout.setId("checkout-1");
        return checkout;
    }
}

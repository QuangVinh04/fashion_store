package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.enumeration.CartStatus;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

        assertEquals(OrderErrorCode.CHECKOUT_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void cannotCancelACompletedCheckout() {
        Checkout checkout = checkout(CheckoutStatus.COMPLETED);
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));

        AppException exception = assertThrows(AppException.class, () -> service.cancelCheckout("checkout-1"));

        assertEquals(OrderErrorCode.CHECKOUT_STATUS_INVALID, exception.getErrorCode());
    }

    @Test
    void reportsMissingCheckout() {
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> service.cancelCheckout("checkout-1"));

        assertEquals(OrderErrorCode.CHECKOUT_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * Snapshot của checkout phải dựng được từ chính cart_item trong DB — không phụ thuộc một lượt gọi
     * catalog nào, vì catalog lỗi là product_name null và checkout_item.product_name lại NOT NULL.
     */
    @Test
    void createsCheckoutFromTheCartSnapshotInDatabase() {
        Cart cart = cart(cartItem("item-1", "variant-1", 2, "100000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);

        CheckoutResponse response = service.createCheckout(CreateCheckoutRequest.builder()
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build());

        assertEquals(1, response.getItems().size());
        assertEquals("Basic Tee", response.getItems().get(0).getProductName());
        assertEquals("M", response.getItems().get(0).getSize());
        assertEquals(0, response.getSubtotalAmount().compareTo(new BigDecimal("200000")));
        assertEquals(0, response.getShippingFee().compareTo(new BigDecimal("25000")));
        assertEquals(0, response.getTotalAmount().compareTo(new BigDecimal("225000")));
    }

    /** Dòng cart_item cũ (tạo trước khi có snapshot) không có tên: báo lỗi rõ, không để DB ném NOT NULL. */
    @Test
    void rejectsCheckoutWhenACartItemHasNoProductSnapshot() {
        CartItem stale = cartItem("item-1", "variant-1", 1, "100000");
        stale.setProductName(null);
        when(cartService.revalidateActiveCart()).thenReturn(cart(stale));

        AppException exception = assertThrows(AppException.class, () -> service.createCheckout(
                CreateCheckoutRequest.builder().paymentMethod(PaymentMethod.COD).build()));

        assertEquals(OrderErrorCode.CART_ITEM_STALE, exception.getErrorCode());
        verify(checkoutRepository, never()).save(any(Checkout.class));
    }

    private Cart cart(CartItem... items) {
        Cart cart = Cart.builder()
                .userId("user-1")
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>(List.of(items)))
                .build();
        cart.setId("cart-1");
        cart.getItems().forEach(item -> item.setCart(cart));
        return cart;
    }

    private CartItem cartItem(String id, String variantId, int quantity, String unitPrice) {
        CartItem item = CartItem.builder()
                .variantId(variantId)
                .productId("product-1")
                .productName("Basic Tee")
                .size("M")
                .color("Den")
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
        item.setId(id);
        return item;
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

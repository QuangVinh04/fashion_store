package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.CheckoutResponse;
import com.fashionstore.order.dto.CreateCheckoutRequest;
import com.fashionstore.order.dto.UpdateCheckoutRequest;
import com.fashionstore.order.dto.UserAddressDto;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.CartStatus;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.service.CartService;
import com.fashionstore.order.service.PromotionService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private PromotionService promotionService;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private IdentityClient identityClient;

    @Mock
    private GhnClient ghnClient;

    private CheckoutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckoutServiceImpl(checkoutRepository, cartService, currentUserProvider, promotionService, catalogClient, identityClient, ghnClient);
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

    @Test
    void createsCheckoutWithPromotionDiscount() {
        Cart cart = cart(cartItem("item-1", "variant-1", 2, "100000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);
        when(promotionService.previewDiscount(eq("SALE20"), eq("user-1"), eq(new BigDecimal("200000")), any()))
                .thenReturn(new BigDecimal("40000"));

        CheckoutResponse response = service.createCheckout(CreateCheckoutRequest.builder()
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .couponCode("SALE20")
                .build());

        assertEquals(0, response.getSubtotalAmount().compareTo(new BigDecimal("200000")));
        assertEquals(0, response.getDiscountAmount().compareTo(new BigDecimal("40000")));
        assertEquals(0, response.getShippingFee().compareTo(new BigDecimal("25000")));
        assertEquals(0, response.getTotalAmount().compareTo(new BigDecimal("185000")));
        assertEquals("SALE20", response.getCouponCode());
    }

    @Test
    void createsCheckoutWithEmptyCoupon_returnsZeroDiscount() {
        Cart cart = cart(cartItem("item-1", "variant-1", 2, "100000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);

        CheckoutResponse response = service.createCheckout(CreateCheckoutRequest.builder()
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .couponCode("")
                .build());

        assertEquals(0, response.getDiscountAmount().compareTo(BigDecimal.ZERO));
        verify(promotionService, never()).previewDiscount(any(), any(), any(), any());
    }

    // ----- đổi / sửa địa chỉ trong lúc checkout -----

    /** Khách sửa chính địa chỉ đang chọn ở sổ địa chỉ rồi quay lại: gửi lại cùng addressId là tính lại phí theo nội dung mới. */
    @Test
    void updateCheckout_resendingSameAddressId_recomputesFeeFromCurrentAddress() {
        Checkout checkout = checkout(CheckoutStatus.SUBMITTED);
        checkout.setSubtotalAmount(BigDecimal.valueOf(200000));
        checkout.setShippingFee(BigDecimal.valueOf(25000));
        checkout.setTotalAmount(BigDecimal.valueOf(225000));
        checkout.setAddressId("addr-1");
        checkout.setShippingAddress(snapshot(1444, "20308"));
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));
        when(identityClient.getAddress("user-1", "addr-1")).thenReturn(address(1542, "1A0101"));
        when(ghnClient.calculateFee(eq(1542), eq("1A0101"), any(Integer.class), eq(ShippingMethod.STANDARD)))
                .thenReturn(BigDecimal.valueOf(45000));

        CheckoutResponse response = service.updateCheckout("checkout-1",
                UpdateCheckoutRequest.builder().addressId("addr-1").build());

        assertEquals(0, response.getShippingFee().compareTo(BigDecimal.valueOf(45000)));
        assertEquals(0, response.getTotalAmount().compareTo(BigDecimal.valueOf(245000)));
        assertEquals(1542, checkout.getShippingAddress().getDistrictId());
        assertEquals("1A0101", checkout.getShippingAddress().getWardCode());
    }

    @Test
    void updateCheckout_switchingToAnotherAddress_storesItAndRecomputesFee() {
        Checkout checkout = openCheckout("200000", "addr-1");
        when(identityClient.getAddress("user-1", "addr-2")).thenReturn(address(1542, "1A0101"));
        when(ghnClient.calculateFee(eq(1542), eq("1A0101"), any(Integer.class), eq(ShippingMethod.STANDARD)))
                .thenReturn(BigDecimal.valueOf(45000));

        CheckoutResponse response = service.updateCheckout("checkout-1",
                UpdateCheckoutRequest.builder().addressId("addr-2").build());

        assertEquals("addr-2", response.getAddressId());
        assertEquals("addr-2", checkout.getAddressId());
        assertEquals(1542, checkout.getShippingAddress().getDistrictId());
        assertEquals(0, response.getShippingFee().compareTo(BigDecimal.valueOf(45000)));
        assertEquals(0, response.getTotalAmount().compareTo(BigDecimal.valueOf(245000)));
    }

    /** Chỉ đổi phương thức giao: phí tính lại theo bản chụp đang có, không đọc lại sổ địa chỉ. */
    @Test
    void updateCheckout_changingShippingMethodOnly_recomputesFeeFromSnapshotWithoutCallingIdentity() {
        openCheckout("200000", "addr-1");
        when(ghnClient.calculateFee(eq(1444), eq("20308"), any(Integer.class), eq(ShippingMethod.EXPRESS)))
                .thenReturn(BigDecimal.valueOf(40000));

        CheckoutResponse response = service.updateCheckout("checkout-1",
                UpdateCheckoutRequest.builder().shippingMethod(ShippingMethod.EXPRESS).build());

        assertEquals(ShippingMethod.EXPRESS, response.getShippingMethod());
        assertEquals(0, response.getShippingFee().compareTo(BigDecimal.valueOf(40000)));
        verify(identityClient, never()).getAddress(anyString(), anyString());
    }

    @Test
    void updateCheckout_toAddressOfAnotherUser_throwsAddressNotFound() {
        openCheckout("200000", "addr-1");
        when(identityClient.getAddress("user-1", "addr-of-b"))
                .thenThrow(new AppException(OrderErrorCode.ADDRESS_NOT_FOUND));

        AppException exception = assertThrows(AppException.class, () -> service.updateCheckout("checkout-1",
                UpdateCheckoutRequest.builder().addressId("addr-of-b").build()));

        assertEquals(OrderErrorCode.ADDRESS_NOT_FOUND, exception.getErrorCode());
        verify(checkoutRepository, never()).save(any(Checkout.class));
    }

    @Test
    void updateCheckout_toAddressLackingGhnCodes_rejectsAndKeepsCurrentAddress() {
        Checkout checkout = openCheckout("200000", "addr-1");
        when(identityClient.getAddress("user-1", "addr-legacy")).thenReturn(address(null, null));

        AppException exception = assertThrows(AppException.class, () -> service.updateCheckout("checkout-1",
                UpdateCheckoutRequest.builder().addressId("addr-legacy").build()));

        assertEquals(OrderErrorCode.SHIPPING_ADDRESS_INVALID, exception.getErrorCode());
        assertEquals("addr-1", checkout.getAddressId());
        assertEquals(1444, checkout.getShippingAddress().getDistrictId());
    }

    @Test
    void createsCheckout_withoutAddress_hasNoSnapshotAndFlatFee() {
        Cart cart = cart(cartItem("item-1", "variant-1", 1, "200000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);

        CheckoutResponse response = service.createCheckout(CreateCheckoutRequest.builder()
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build());

        assertEquals(0, response.getShippingFee().compareTo(BigDecimal.valueOf(25000)));
        assertNull(savedCheckout().getShippingAddress());
        verify(identityClient, never()).getAddress(anyString(), anyString());
    }

    @Test
    void updateCheckout_onFinishedCheckout_rejectsWithoutRecalculating() {
        for (CheckoutStatus status : List.of(CheckoutStatus.COMPLETED, CheckoutStatus.CANCELLED, CheckoutStatus.EXPIRED)) {
            Checkout checkout = checkout(status);
            when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));

            AppException exception = assertThrows(AppException.class, () -> service.updateCheckout("checkout-1",
                    UpdateCheckoutRequest.builder().addressId("addr-2").build()));

            assertEquals(OrderErrorCode.CHECKOUT_STATUS_INVALID, exception.getErrorCode(), status.name());
        }
        verify(identityClient, never()).getAddress(anyString(), anyString());
        verify(checkoutRepository, never()).save(any(Checkout.class));
    }

    // ----- địa chỉ luôn được kiểm tra ở checkout, kể cả khi được miễn phí ship -----

    @Test
    void createsCheckout_freeShipping_withAddressLackingGhnCodes_rejectsAtCheckout() {
        Cart cart = cart(cartItem("item-1", "variant-1", 3, "200000")); // 600k subtotal
        when(cartService.revalidateActiveCart()).thenReturn(cart);
        when(identityClient.getAddress("user-1", "addr-legacy")).thenReturn(address(null, null));

        AppException exception = assertThrows(AppException.class, () -> service.createCheckout(CreateCheckoutRequest.builder()
                .addressId("addr-legacy")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build()));

        assertEquals(OrderErrorCode.SHIPPING_ADDRESS_INVALID, exception.getErrorCode());
        verify(checkoutRepository, never()).save(any(Checkout.class));
    }

    @Test
    void createsCheckout_freeShipping_withAddressOfAnotherUser_throwsAddressNotFound() {
        Cart cart = cart(cartItem("item-1", "variant-1", 3, "200000")); // 600k subtotal
        when(cartService.revalidateActiveCart()).thenReturn(cart);
        when(identityClient.getAddress("user-1", "addr-of-b"))
                .thenThrow(new AppException(OrderErrorCode.ADDRESS_NOT_FOUND));

        AppException exception = assertThrows(AppException.class, () -> service.createCheckout(CreateCheckoutRequest.builder()
                .addressId("addr-of-b")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build()));

        assertEquals(OrderErrorCode.ADDRESS_NOT_FOUND, exception.getErrorCode());
        verify(checkoutRepository, never()).save(any(Checkout.class));
    }

    @Test
    void updateCheckout_freeShipping_toAddressLackingGhnCodes_rejects() {
        openCheckout("600000", "addr-1");
        when(identityClient.getAddress("user-1", "addr-legacy")).thenReturn(address(null, null));

        AppException exception = assertThrows(AppException.class, () -> service.updateCheckout("checkout-1",
                UpdateCheckoutRequest.builder().addressId("addr-legacy").build()));

        assertEquals(OrderErrorCode.SHIPPING_ADDRESS_INVALID, exception.getErrorCode());
    }

    private Checkout openCheckout(String subtotal, String addressId) {
        Checkout checkout = checkout(CheckoutStatus.SUBMITTED);
        checkout.setSubtotalAmount(new BigDecimal(subtotal));
        checkout.setTotalAmount(new BigDecimal(subtotal));
        checkout.setAddressId(addressId);
        checkout.setShippingAddress(snapshot(1444, "20308"));
        when(checkoutRepository.findByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));
        return checkout;
    }

    private ShippingAddress snapshot(Integer districtId, String wardCode) {
        return ShippingAddress.builder()
                .recipientName("Nguyen Van A")
                .recipientPhone("0987654321")
                .province("Hồ Chí Minh")
                .district("Quận 1")
                .ward("Bến Nghé")
                .detailAddress("123 Lê Lợi")
                .districtId(districtId)
                .wardCode(wardCode)
                .build();
    }

    private Checkout savedCheckout() {
        org.mockito.ArgumentCaptor<Checkout> captor = org.mockito.ArgumentCaptor.forClass(Checkout.class);
        verify(checkoutRepository).save(captor.capture());
        return captor.getValue();
    }

    private UserAddressDto address(Integer districtId, String wardCode) {
        return UserAddressDto.builder()
                .recipientName("Nguyen Van A")
                .phone("0987654321")
                .province("Hồ Chí Minh")
                .district("Quận 1")
                .ward("Bến Nghé")
                .detailAddress("123 Lê Lợi")
                .districtId(districtId)
                .wardCode(wardCode)
                .build();
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

    @Test
    void createsCheckout_withAddressAndCallsGhnFee() {
        Cart cart = cart(cartItem("item-1", "variant-1", 1, "200000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);

        UserAddressDto address = UserAddressDto.builder()
                .province("Hồ Chí Minh")
                .districtId(1444)
                .wardCode("20308")
                .build();
        when(identityClient.getAddress("user-1", "addr-1")).thenReturn(address);
        when(ghnClient.calculateFee(eq(1444), eq("20308"), any(Integer.class), eq(ShippingMethod.STANDARD)))
                .thenReturn(BigDecimal.valueOf(32000));

        CheckoutResponse response = service.createCheckout(CreateCheckoutRequest.builder()
                .addressId("addr-1")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build());

        assertEquals(0, response.getSubtotalAmount().compareTo(BigDecimal.valueOf(200000)));
        assertEquals(0, response.getShippingFee().compareTo(BigDecimal.valueOf(32000)));
        assertEquals(0, response.getTotalAmount().compareTo(BigDecimal.valueOf(232000)));
        ShippingAddress snapshot = savedCheckout().getShippingAddress();
        assertEquals("Hồ Chí Minh", snapshot.getProvince());
        assertEquals(1444, snapshot.getDistrictId());
        assertEquals("20308", snapshot.getWardCode());
    }

    @Test
    void createsCheckout_whenAddressMissingDistrictOrWard_throwsShippingAddressInvalid() {
        Cart cart = cart(cartItem("item-1", "variant-1", 1, "200000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);

        UserAddressDto invalidAddress = UserAddressDto.builder()
                .province("Hà Nội")
                .districtId(null)
                .wardCode(null)
                .build();
        when(identityClient.getAddress("user-1", "addr-invalid")).thenReturn(invalidAddress);

        AppException exception = assertThrows(AppException.class, () -> service.createCheckout(CreateCheckoutRequest.builder()
                .addressId("addr-invalid")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build()));

        assertEquals(OrderErrorCode.SHIPPING_ADDRESS_INVALID, exception.getErrorCode());
    }

    @Test
    void createsCheckout_freeShippingWhenSubtotalOver500k() {
        Cart cart = cart(cartItem("item-1", "variant-1", 3, "200000")); // 600k subtotal
        when(cartService.revalidateActiveCart()).thenReturn(cart);
        when(identityClient.getAddress("user-1", "addr-1")).thenReturn(address(1444, "20308"));

        CheckoutResponse response = service.createCheckout(CreateCheckoutRequest.builder()
                .addressId("addr-1")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build());

        assertEquals(0, response.getSubtotalAmount().compareTo(BigDecimal.valueOf(600000)));
        assertEquals(0, response.getShippingFee().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getTotalAmount().compareTo(BigDecimal.valueOf(600000)));
        verify(ghnClient, never()).calculateFee(any(), any(), any(Integer.class), any());
        assertEquals("123 Lê Lợi", savedCheckout().getShippingAddress().getDetailAddress());
    }

    @Test
    void createsCheckout_whenGhnFails_throwsUpstreamServiceError() {
        Cart cart = cart(cartItem("item-1", "variant-1", 1, "200000"));
        when(cartService.revalidateActiveCart()).thenReturn(cart);

        UserAddressDto address = UserAddressDto.builder()
                .province("Hồ Chí Minh")
                .districtId(1444)
                .wardCode("20308")
                .build();
        when(identityClient.getAddress("user-1", "addr-1")).thenReturn(address);
        when(ghnClient.calculateFee(any(), any(), any(Integer.class), any()))
                .thenThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR));

        AppException exception = assertThrows(AppException.class, () -> service.createCheckout(CreateCheckoutRequest.builder()
                .addressId("addr-1")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(ShippingMethod.STANDARD)
                .build()));

        assertEquals(ErrorCode.UPSTREAM_SERVICE_ERROR, exception.getErrorCode());
    }

}

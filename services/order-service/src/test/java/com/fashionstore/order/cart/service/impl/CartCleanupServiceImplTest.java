package com.fashionstore.order.cart.service.impl;

import com.fashionstore.order.cart.entity.Cart;
import com.fashionstore.order.cart.entity.CartItem;
import com.fashionstore.order.cart.entity.CartStatus;
import com.fashionstore.order.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCleanupServiceImplTest {

    private static final String USER_ID = "user-1";

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartCleanupServiceImpl service;

    /**
     * Xoá giỏ theo (giỏ đang hoạt động, variantId, số lượng đã đặt) thay vì theo cartItemId đã
     * snapshot: khách xoá rồi thêm lại một sản phẩm là id đổi, mà id đổi thì cách cũ bỏ sót dòng đó
     * vĩnh viễn.
     */
    @Test
    void removesOnlyTheOrderedQuantityAndMatchesByVariant() {
        Cart cart = cart(
                cartItem("cart-item-9", "variant-1", 5), // khách tăng số lượng sau khi checkout
                cartItem("cart-item-2", "variant-2", 1)); // sản phẩm không nằm trong đơn
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));

        service.removeOrderedItems(USER_ID, Map.of("variant-1", 2));

        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(3); // 5 - 2 đã mua
        assertThat(cart.getItems().get(1).getVariantId()).isEqualTo("variant-2");
        verify(cartRepository).save(cart);
    }

    @Test
    void dropsTheCartLineWhenTheWholeQuantityWasOrdered() {
        Cart cart = cart(cartItem("cart-item-1", "variant-1", 2));
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));

        service.removeOrderedItems(USER_ID, Map.of("variant-1", 2));

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }

    @Test
    void doesNothingWithoutAnActiveCart() {
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.empty());

        service.removeOrderedItems(USER_ID, Map.of("variant-1", 2));

        verify(cartRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Cart cart(CartItem... items) {
        Cart cart = Cart.builder()
                .userId(USER_ID)
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>(List.of(items)))
                .build();
        cart.setId("cart-1");
        cart.getItems().forEach(item -> item.setCart(cart));
        return cart;
    }

    private CartItem cartItem(String id, String variantId, int quantity) {
        CartItem item = CartItem.builder()
                .variantId(variantId)
                .productId("product-1")
                .productName("Basic Tee")
                .quantity(quantity)
                .unitPrice(BigDecimal.TEN)
                .build();
        item.setId(id);
        return item;
    }
}

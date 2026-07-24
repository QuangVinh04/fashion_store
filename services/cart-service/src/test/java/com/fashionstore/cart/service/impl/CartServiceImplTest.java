package com.fashionstore.cart.service.impl;

import com.fashionstore.cart.client.inventory.InventoryClient;
import com.fashionstore.cart.client.product.ProductClient;
import com.fashionstore.cart.dto.cart.AddToCartRequest;
import com.fashionstore.cart.dto.cart.CartResponse;
import com.fashionstore.cart.model.Cart;
import com.fashionstore.cart.model.CartItem;
import com.fashionstore.cart.repository.CartItemRepository;
import com.fashionstore.cart.repository.CartRepository;
import com.fashionstore.common.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    CartRepository cartRepository;
    @Mock
    CartItemRepository cartItemRepository;
    @Mock
    ProductClient productClient;
    @Mock
    CurrentUserProvider currentUserProvider;
    @Mock
    InventoryClient inventoryClient;

    @InjectMocks
    CartServiceImpl cartService;

    @Test
    void addsQuantityAndChecksInventoryOnceForNewTotal() {
        Cart cart = Cart.builder()
                .userId("user-1")
                .items(new ArrayList<>())
                .build();
        cart.setId("cart-1");

        CartItem item = CartItem.builder()
                .cart(cart)
                .variantId("variant-1")
                .productId("product-1")
                .productName("Basic Tee")
                .unitPrice(new BigDecimal("20.00"))
                .quantity(2)
                .build();
        item.setId("item-1");
        cart.getItems().add(item);

        ProductVariantSnapshotResponse snapshot = ProductVariantSnapshotResponse.builder()
                .variantId("variant-1")
                .productId("product-1")
                .productName("Basic Tee")
                .price(new BigDecimal("20.00"))
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(cart));
        when(productClient.getProductVariantSnapshot("variant-1")).thenReturn(snapshot);
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1"))
                .thenReturn(Optional.of(item));

        CartResponse response = cartService.addToCart(
                AddToCartRequest.builder()
                        .variantId("variant-1")
                        .quantity(3)
                        .build());

        verify(inventoryClient, times(1)).ensureAvailable("variant-1", 5);
        verify(cartItemRepository).save(item);
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(response.getTotalQuantity()).isEqualTo(5);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("100.00");
    }
}

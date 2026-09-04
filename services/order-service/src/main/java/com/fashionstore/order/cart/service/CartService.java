package com.fashionstore.order.cart.service;

import com.fashionstore.order.cart.dto.cart.AddToCartRequest;
import com.fashionstore.order.cart.dto.cart.CartResponse;
import com.fashionstore.order.cart.dto.cart.UpdateCartRequest;

public interface CartService {
    CartResponse getMyCart();

    CartResponse addToCart(AddToCartRequest request);

    CartResponse updateCartItem(String cartItemId, UpdateCartRequest request);

    CartResponse removeCartItem(String cartItemId);

    CartResponse clearCart();
}

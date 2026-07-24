package com.fashionstore.cart.service;

import com.fashionstore.cart.dto.cart.AddToCartRequest;
import com.fashionstore.cart.dto.cart.CartResponse;
import com.fashionstore.cart.dto.cart.UpdateCartRequest;

public interface CartService {
    CartResponse getMyCart();

    CartResponse addToCart(AddToCartRequest request);

    CartResponse updateCartItem(String cartItemId, UpdateCartRequest request);

    CartResponse removeCartItem(String cartItemId);

    CartResponse clearCart();
}

package com.fashionstore.clothes_retail_api.modules.cart.service;

import com.fashionstore.clothes_retail_api.modules.cart.dto.AddToCartRequest;
import com.fashionstore.clothes_retail_api.modules.cart.dto.CartResponse;
import com.fashionstore.clothes_retail_api.modules.cart.dto.UpdateCartRequest;

public interface CartService {
    CartResponse getMyCart();

    CartResponse addToCart(AddToCartRequest request);

    CartResponse updateCartItem(String cartItemId, UpdateCartRequest request);

    void removeCartItem(String cartItemId);

    void clearCart();
}

package com.fashionstore.order.service;

import com.fashionstore.order.dto.AddToCartRequest;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.UpdateCartRequest;
import com.fashionstore.order.entity.Cart;

public interface CartService {
    CartResponse getMyCart();

    CartResponse addToCart(AddToCartRequest request);

    CartResponse updateCartItem(String cartItemId, UpdateCartRequest request);

    CartResponse removeCartItem(String cartItemId);

    CartResponse clearCart();

    /**
     * Xác nhận lại giỏ đang hoạt động với catalog (còn bán, giá hiện tại, kho còn đủ), ghi lại snapshot
     * rồi trả về giỏ đã cập nhật. Dùng bởi checkout — nơi bản chụp giá bắt đầu có giá trị thu tiền.
     */
    Cart revalidateActiveCart();
}

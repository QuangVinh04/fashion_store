package com.fashionstore.order.cart.service.impl;

import com.fashionstore.order.cart.entity.Cart;
import com.fashionstore.order.cart.entity.CartItem;
import com.fashionstore.order.cart.entity.CartStatus;
import com.fashionstore.order.cart.repository.CartRepository;
import com.fashionstore.order.cart.service.CartCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartCleanupServiceImpl implements CartCleanupService {

    private final CartRepository cartRepository;

    @Override
    @Transactional
    public void removeOrderedItems(String userId, Map<String, Integer> orderedQuantitiesByVariant) {
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            return;
        }
        cart.getItems().removeIf(item -> isFullyOrdered(item, orderedQuantitiesByVariant));
        cartRepository.save(cart);
    }

    /** Lines only partly ordered stay in the cart with the remaining quantity. */
    private boolean isFullyOrdered(CartItem item, Map<String, Integer> orderedQuantitiesByVariant) {
        Integer ordered = orderedQuantitiesByVariant.get(item.getVariantId());
        if (ordered == null) {
            return false;
        }
        if (item.getQuantity() <= ordered) {
            return true;
        }
        item.setQuantity(item.getQuantity() - ordered);
        return false;
    }
}

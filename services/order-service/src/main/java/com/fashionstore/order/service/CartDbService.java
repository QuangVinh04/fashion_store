package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.dto.CheckoutUpdateDto;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.CheckoutItem;
import com.fashionstore.order.entity.enumeration.CartStatus;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.CartItemRepository;
import com.fashionstore.order.repository.CartRepository;
import com.fashionstore.order.repository.CheckoutRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartDbService {
    CartRepository cartRepository;
    CartItemRepository cartItemRepository;

    // TRANSACTION NGẮN #1: Chỉ mở kết nối để đọc giỏ hàng lên RAM rồi đóng lại ngay
    @Transactional(readOnly = true)
    public Cart getActiveCartSnapshot(String userId) {
        // Bắt buộc sử dụng JOIN FETCH c.items trong repo để nạp sẵn danh sách items lên RAM
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_EMPTY));
    }

    // TRANSACTION NGẮN #2: Chỉ mở kết nối để lưu giỏ hàng sau khi đã cập nhật giá mới từ Catalog
    @Transactional
    public Cart saveUpdatedCart(Cart cart) {
        return cartRepository.save(cart);
    }

    @Transactional(readOnly = true)
    public int getCartItemQuantity(String userId, String variantId) {
        Cart existing = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE).orElse(null);
        if (existing == null) {
            return 0;
        }
        return cartItemRepository.findByCartIdAndVariantId(existing.getId(), variantId)
                .map(CartItem::getQuantity)
                .orElse(0);
    }

    @Transactional
    public Cart addOrUpdateCartItem(String userId, String variantId, int addedQuantity, ProductVariantDto variant) {
        Cart cart = getOrCreateActiveCart(userId);
        
        CartItem existingItem = cartItemRepository
                .findByCartIdAndVariantId(cart.getId(), variantId)
                .orElse(null);

        int totalQty = (existingItem != null ? existingItem.getQuantity() : 0) + addedQuantity;

        if (existingItem != null) {
            existingItem.setQuantity(totalQty);
            applySnapshot(existingItem, variant);
        } else {
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .variantId(variantId)
                    .quantity(addedQuantity)
                    .build();
            applySnapshot(cartItem, variant);
            cart.addItem(cartItem);
        }
        cartRepository.save(cart);
        return getActiveCartSnapshot(userId);
    }

    @Transactional(readOnly = true)
    public CartItem getCartItemWithValidation(String cartItemId, String userId) {
        CartItem item = cartItemRepository.findByIdWithCart(cartItemId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_ITEM_NOT_FOUND));
        validateCartOwnership(item.getCart(), userId);
        return item;
    }

    @Transactional
    public Cart updateCartItemQuantity(String cartItemId, String userId, int newQuantity, ProductVariantDto variant) {
        CartItem item = cartItemRepository.findByIdWithCart(cartItemId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_ITEM_NOT_FOUND));
        
        Cart cart = item.getCart();
        validateCartOwnership(cart, userId);

        item.setQuantity(newQuantity);
        applySnapshot(item, variant);

        cartItemRepository.save(item);
        
        // Load lại toàn bộ cart với các items để trả về (tránh LazyInitializationException)
        return getActiveCartSnapshot(userId);
    }

    @Transactional
    public Cart removeCartItem(String cartItemId, String userId) {
        CartItem item = cartItemRepository.findByIdWithCart(cartItemId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_ITEM_NOT_FOUND));

        Cart cart = item.getCart();
        validateCartOwnership(cart, userId);

        cart.removeItem(item);
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart clearCart(String userId) {
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_NOT_ACTIVE));

        cart.getItems().clear();
        return cartRepository.save(cart);
    }

    private void applySnapshot(CartItem item, ProductVariantDto variant) {
        item.setProductId(variant.getProductId());
        item.setProductName(variant.getProductName());
        item.setSize(variant.getSize());
        item.setColor(variant.getColor());
        item.setUnitPrice(variant.effectivePrice());
    }

    private Cart getOrCreateActiveCart(String userId) {
        Cart existing = cartRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() != CartStatus.ACTIVE) {
                log.info("[Cart] Reactivating inactive cart for userId={}, oldStatus={}", userId, existing.getStatus());
                existing.setStatus(CartStatus.ACTIVE);
                existing.getItems().clear();
                return cartRepository.save(existing);
            }
            return existing;
        }
        return createCart(userId);
    }

    private Cart createCart(String userId) {
        try {
            Cart saved = cartRepository.save(Cart.builder()
                    .userId(userId)
                    .status(CartStatus.ACTIVE)
                    .build());
            log.info("[Cart] created new cart — cartId={}, userId={}", saved.getId(), userId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            return cartRepository.findByUserId(userId)
                    .map(c -> {
                        if (c.getStatus() != CartStatus.ACTIVE) {
                            c.setStatus(CartStatus.ACTIVE);
                            c.getItems().clear();
                            return cartRepository.save(c);
                        }
                        return c;
                    })
                    .orElseThrow(() -> e);
        }
    }

    private void validateCartOwnership(Cart cart, String userId) {
        if (!cart.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new AppException(OrderErrorCode.CART_NOT_ACTIVE);
        }
    }
}

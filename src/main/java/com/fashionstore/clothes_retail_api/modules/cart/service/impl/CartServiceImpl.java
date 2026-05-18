package com.fashionstore.clothes_retail_api.modules.cart.service.impl;

import com.fashionstore.clothes_retail_api.common.exception.AppException;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.auth.repository.UserRepository;
import com.fashionstore.clothes_retail_api.modules.cart.dto.AddToCartRequest;
import com.fashionstore.clothes_retail_api.modules.cart.dto.CartItemResponse;
import com.fashionstore.clothes_retail_api.modules.cart.dto.CartResponse;
import com.fashionstore.clothes_retail_api.modules.cart.dto.UpdateCartRequest;
import com.fashionstore.clothes_retail_api.modules.cart.entity.Cart;
import com.fashionstore.clothes_retail_api.modules.cart.entity.CartItem;
import com.fashionstore.clothes_retail_api.modules.cart.repository.CartRepository;
import com.fashionstore.clothes_retail_api.modules.cart.repository.CartItemRepository;
import com.fashionstore.clothes_retail_api.modules.cart.service.CartService;
import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import com.fashionstore.clothes_retail_api.modules.product.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartServiceImpl implements CartService {

    CartRepository cartRepository;
    CartItemRepository cartItemRepository;
    UserRepository userRepository;
    ProductService productService;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        User user = getCurrentUser();
        List<CartItem> items = cartRepository.findByUserId(user.getId())
                .map(Cart::getItems)
                .orElseGet(List::of);
        return toResponse(items);
    }

    @Override
    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);
        ProductVariant variant = productService.getProductVariantById(request.getVariantId());
        validateStock(variant, request.getQuantity());

        CartItem cartItem = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElseGet(() -> CartItem.builder()
                        .cart(cart)
                        .variant(variant)
                        .quantity(0)
                        .build());

        int newQuantity = cartItem.getQuantity() + request.getQuantity();
        validateStock(variant, newQuantity);
        cartItem.setQuantity(newQuantity);
        cartItemRepository.save(cartItem);

        return getMyCart();
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(String cartItemId, UpdateCartRequest request) {
        User user = getCurrentUser();
        Cart cart = getCart(user)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        ProductVariant variant = productService.getProductVariantById(cartItem.getVariant().getId());
        validateStock(variant, request.getQuantity());

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        return getMyCart();
    }

    @Override
    @Transactional
    public void removeCartItem(String cartItemId) {
        User user = getCurrentUser();
        Cart cart = getCart(user)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
        cartItemRepository.delete(cartItem);
    }

    @Override
    @Transactional
    public void clearCart() {
        User user = getCurrentUser();
        getCart(user).ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateStock(ProductVariant variant, Integer requestedQuantity) {
        if (requestedQuantity > variant.getStock()) {
            throw new AppException(ErrorCode.STOCK_INSUFFICIENT);
        }
    }

    private Optional<Cart> getCart(User user) {
        return cartRepository.findByUserId(user.getId());
    }

    private Cart getOrCreateCart(User user) {
        return getCart(user)
                .orElseGet(() -> {
                    Cart cart = cartRepository.save(Cart.builder()
                            .user(user)
                            .isActive(true)
                            .build());
                    user.setCart(cart);
                    return cart;
                });
    }

    private CartResponse toResponse(List<CartItem> items) {
        List<CartItemResponse> responses = items.stream()
                .map(this::toItemResponse)
                .toList();

        Integer totalQuantity = responses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        BigDecimal totalPrice = responses.stream()
                .map(CartItemResponse::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .items(responses)
                .totalQuantity(totalQuantity)
                .totalPrice(totalPrice)
                .build();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        ProductVariant variant = item.getVariant();
        BigDecimal unitPrice = variant.getPrice();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        return CartItemResponse.builder()
                .id(item.getId())
                .variantId(variant.getId())
                .productId(variant.getProduct().getId())
                .productName(variant.getProduct().getName())
                .size(variant.getSize())
                .color(variant.getColor())
                .quantity(item.getQuantity())
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .build();
    }
}

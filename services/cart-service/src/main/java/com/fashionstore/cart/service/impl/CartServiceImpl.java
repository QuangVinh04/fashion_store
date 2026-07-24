package com.fashionstore.cart.service.impl;

import com.fashionstore.cart.dto.inventory.InventoryDto;
import com.fashionstore.cart.dto.product.ProductVariantDto;
import com.fashionstore.cart.model.enumeration.CartStatus;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.cart.exception.CartErrorCode;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.cart.dto.cart.AddToCartRequest;
import com.fashionstore.cart.dto.cart.CartResponse;
import com.fashionstore.cart.dto.cart.UpdateCartRequest;
import com.fashionstore.cart.model.Cart;
import com.fashionstore.cart.model.CartItem;
import com.fashionstore.cart.mapper.CartMapper;
import com.fashionstore.cart.repository.CartItemRepository;
import com.fashionstore.cart.repository.CartRepository;
import com.fashionstore.cart.service.CartService;
import com.fashionstore.cart.client.inventory.InventoryClient;
import com.fashionstore.cart.client.product.ProductClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartServiceImpl implements CartService {

    CartRepository cartRepository;
    CartItemRepository cartItemRepository;
    ProductClient productClient;
    CurrentUserProvider currentUserProvider;
    InventoryClient inventoryClient;
    CartMapper cartMapper;

    @Override
    public CartResponse getMyCart() {
        String userId = currentUserProvider.getCurrentUserId();

        List<Cart> carts = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);

        if (carts.size() > 1) {
            throw new AppException(CartErrorCode.MULTIPLE_ACTIVE_CARTS);
        }

        if (carts.isEmpty()) {
            return CartResponse.builder()
                    .userId(userId)
                    .items(List.of())
                    .totalPrice(BigDecimal.ZERO)
                    .totalQuantity(0)
                    .build();
        }

        Cart cart = carts.getFirst();

        CartResponse response = cartMapper.toCartResponse(cart);
        enrichCartItems(response, cart);

        return response;
    }

    @Override
    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        String userId = currentUserProvider.getCurrentUserId();


        ProductVariantDto variant = productClient.getVariant(request.getVariantId());

        Cart cart = getOrCreateActiveCart(userId);

        CartItem existingItem = cartItemRepository
                .findByCartIdAndVariantId(cart.getId(), request.getVariantId())
                .orElse(null);

        int existingQty = existingItem != null ? existingItem.getQuantity() : 0;
        int totalQty = existingQty + request.getQuantity();

        InventoryDto inventory = inventoryClient
                .getInventoryBatch(request.getVariantId());

        if(!inventory.hasEnoughStock(totalQty)) {
            log.warn("[Cart] insufficient stock — variantId={}, available={}, requested={}",
                    request.getVariantId(), inventory.getQuantityAvailable(), totalQty);
            throw new AppException(CartErrorCode.STOCK_INSUFFICIENT);
        }


        if(existingItem != null) {
            existingItem.setQuantity(totalQty);
            existingItem.setUnitPrice(variant.getPrice());
        }

        else{
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .productId(variant.getProductId())
                    .variantId(variant.getId())
                    .quantity(request.getQuantity())
                    .unitPrice(variant.getPrice())
                    .build();
            cart.addItem(cartItem);
        }
        Cart saved = cartRepository.save(cart);

        CartResponse response = cartMapper.toCartResponse(saved);
        enrichCartItems(response, saved);
        return response;
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(String cartItemId, UpdateCartRequest request) {
        String userId = currentUserProvider.getCurrentUserId();

        CartItem item = cartItemRepository.findByIdWithCart(cartItemId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        Cart cart = item.getCart();
        validateCartOwnership(item.getCart(), userId);

        InventoryDto inventory = inventoryClient
                .getInventoryBatch(item.getVariantId());

        if(!inventory.hasEnoughStock(request.getQuantity())) {
            throw new AppException(CartErrorCode.STOCK_INSUFFICIENT);
        }

        ProductVariantDto variant = productClient.getVariant(item.getVariantId());

        // 3. Cập nhật
        item.setQuantity(request.getQuantity());
        item.setUnitPrice(variant.getPrice()); // sync giá mới nhất

        cartItemRepository.save(item);

        CartResponse response = cartMapper.toCartResponse(cart);
        enrichCartItems(response, cart);
        return response;
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(String cartItemId) {
        String userId = currentUserProvider.getCurrentUserId();

        CartItem item = cartItemRepository.findByIdWithCart(cartItemId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        Cart cart = item.getCart();
        validateCartOwnership(cart, userId);

        cart.removeItem(item);
        Cart saved = cartRepository.save(cart);

        CartResponse response = cartMapper.toCartResponse(saved);
        enrichCartItems(response, saved);
        return response;
    }

    @Override
    @Transactional
    public CartResponse clearCart() {
        String userId = currentUserProvider.getCurrentUserId();
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(CartErrorCode.CART_NOT_ACTIVE));

        cart.getItems().clear();
        cartRepository.save(cart);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(userId)
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .totalQuantity(0)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();

    }


    private void validateCartOwnership(Cart cart, String userId) {
        if (!cart.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new AppException(CartErrorCode.CART_NOT_ACTIVE);
        }
    }


    private void enrichCartItems(CartResponse response, Cart cart) {
        if (cart.getItems().isEmpty()) return;

        try {
            List<String> variantIds = cart.getItems().stream()
                    .map(CartItem::getVariantId)
                    .toList();

            List<ProductVariantDto> variants = productClient.getVariantsBatch(variantIds);

            Map<String, ProductVariantDto> variantMap = variants.stream()
                    .collect(Collectors.toMap(ProductVariantDto::getId, Function.identity()));

            response.getItems().forEach(itemResp -> {
                ProductVariantDto variant = variantMap.get(itemResp.getVariantId());
                if (variant != null) {
                    itemResp.setProductName(variant.getProductName());
                    itemResp.setSize(variant.getSize());
                    itemResp.setColor(variant.getColor());
                    itemResp.setSku(variant.getSku());
                }
            });
        } catch (Exception e) {
            // product-service down → trả về cart data từ DB, không fail cả request
            log.warn("[Cart] Could not enrich cart items from product-service: {}", e.getMessage());
            response.getItems().forEach(item -> item.setAvailable(null)); // null = unknown
        }
    }

    private Cart getOrCreateActiveCart(String userId) {
        List<Cart> activeCarts = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);

        if (!activeCarts.isEmpty()) {
            return activeCarts.stream()
                    .max(Comparator.comparing(Cart::getCreatedAt))
                    .orElseThrow();
        }

        Cart newCart = Cart.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .build();

        Cart saved = cartRepository.save(newCart);
        log.info("[Cart] created new cart — cartId={}, userId={}", saved.getId(), userId);
        return saved;
    }

}

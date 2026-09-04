package com.fashionstore.order.cart.service.impl;

import com.fashionstore.order.cart.client.inventory.InventoryClient;
import com.fashionstore.order.cart.client.product.ProductClient;
import com.fashionstore.order.cart.dto.cart.AddToCartRequest;
import com.fashionstore.order.cart.dto.cart.CartItemResponse;
import com.fashionstore.order.cart.dto.cart.CartResponse;
import com.fashionstore.order.cart.dto.cart.UpdateCartRequest;
import com.fashionstore.order.cart.dto.inventory.StockCheckItem;
import com.fashionstore.order.cart.dto.inventory.StockCheckResult;
import com.fashionstore.order.cart.dto.product.ProductVariantDto;
import com.fashionstore.order.cart.exception.CartErrorCode;
import com.fashionstore.order.cart.mapper.CartMapper;
import com.fashionstore.order.cart.model.Cart;
import com.fashionstore.order.cart.model.CartItem;
import com.fashionstore.order.cart.model.enumeration.CartStatus;
import com.fashionstore.order.cart.repository.CartItemRepository;
import com.fashionstore.order.cart.repository.CartRepository;
import com.fashionstore.order.cart.service.CartService;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE).orElse(null);
        if (cart == null) {
            return CartResponse.builder()
                    .userId(userId)
                    .items(List.of())
                    .totalPrice(BigDecimal.ZERO)
                    .totalQuantity(0)
                    .build();
        }

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

        StockCheckResult stock = inventoryClient.checkStock(
                List.of(new StockCheckItem(request.getVariantId(), totalQty)));
        if (!stock.hasEnoughStock(request.getVariantId(), totalQty)) {
            log.warn("[Cart] insufficient stock — variantId={}, requested={}",
                    request.getVariantId(), totalQty);
            throw new AppException(CartErrorCode.STOCK_INSUFFICIENT);
        }

        if (existingItem != null) {
            existingItem.setQuantity(totalQty);
            existingItem.setUnitPrice(variant.getPrice());
        } else {
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .productId(variant.getProductId())
                    .variantId(variant.getVariantId())
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
        validateCartOwnership(cart, userId);

        StockCheckResult stock = inventoryClient.checkStock(
                List.of(new StockCheckItem(item.getVariantId(), request.getQuantity())));
        if (!stock.hasEnoughStock(item.getVariantId(), request.getQuantity())) {
            throw new AppException(CartErrorCode.STOCK_INSUFFICIENT);
        }

        ProductVariantDto variant = productClient.getVariant(item.getVariantId());

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
        Cart saved = cartRepository.save(cart);

        return cartMapper.toCartResponse(saved);
    }


    private void validateCartOwnership(Cart cart, String userId) {
        if (!cart.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new AppException(CartErrorCode.CART_NOT_ACTIVE);
        }
    }

    /**
     * Enrich cả tên/size/color/sku (từ product-service) lẫn cờ còn hàng hay không (từ inventory-service),
     * mỗi loại đúng 1 lượt gọi cho toàn bộ giỏ hàng thay vì N+1. Cả hai đều có circuit breaker riêng nên
     * một bên chết không kéo bên kia theo — cart luôn hiển thị được từ dữ liệu đã lưu trong DB.
     */
    private void enrichCartItems(CartResponse response, Cart cart) {
        if (cart.getItems().isEmpty()) {
            return;
        }

        List<String> variantIds = cart.getItems().stream()
                .map(CartItem::getVariantId)
                .toList();

        enrichProductInfo(response, variantIds);
        enrichAvailability(response, cart, variantIds);
    }

    private void enrichProductInfo(CartResponse response, List<String> variantIds) {
        try {
            List<ProductVariantDto> variants = productClient.getVariantsBatch(variantIds);
            Map<String, ProductVariantDto> variantMap = variants.stream()
                    .collect(Collectors.toMap(ProductVariantDto::getVariantId, Function.identity()));

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
        }
    }

    private void enrichAvailability(CartResponse response, Cart cart, List<String> variantIds) {
        try {
            List<StockCheckItem> items = cart.getItems().stream()
                    .map(item -> new StockCheckItem(item.getVariantId(), item.getQuantity()))
                    .toList();
            StockCheckResult stock = inventoryClient.checkStock(items);

            response.getItems().forEach(itemResp ->
                    itemResp.setAvailable(stock.hasEnoughStock(itemResp.getVariantId(), itemResp.getQuantity())));
        } catch (Exception e) {
            log.warn("[Cart] Could not check stock availability: {}", e.getMessage());
            response.getItems().forEach(item -> item.setAvailable(null)); // null = không biết được
        }
    }

    private Cart getOrCreateActiveCart(String userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> createCart(userId));
    }

    /**
     * {@code cart.user_id} là unique nên hai request đồng thời tạo cart cho cùng user lần đầu có thể đua
     * nhau ghi — bên thua cuộc bắt {@link DataIntegrityViolationException} rồi đọc lại thay vì để lộ lỗi.
     */
    private Cart createCart(String userId) {
        try {
            Cart saved = cartRepository.save(Cart.builder()
                    .userId(userId)
                    .status(CartStatus.ACTIVE)
                    .build());
            log.info("[Cart] created new cart — cartId={}, userId={}", saved.getId(), userId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                    .orElseThrow(() -> e);
        }
    }
}

package com.fashionstore.order.service.impl;

import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.dto.AddToCartRequest;
import com.fashionstore.order.dto.CartItemResponse;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.UpdateCartRequest;
import com.fashionstore.order.dto.StockCheckItem;
import com.fashionstore.order.dto.StockCheckResult;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.mapper.CartMapper;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.enumeration.CartStatus;
import com.fashionstore.order.repository.CartItemRepository;
import com.fashionstore.order.repository.CartRepository;
import com.fashionstore.order.service.CartService;
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
    CatalogClient catalogClient;
    CurrentUserProvider currentUserProvider;
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

        ProductVariantDto variant = catalogClient.getVariant(request.getVariantId());
        requireOnSale(variant);

        Cart cart = getOrCreateActiveCart(userId);

        CartItem existingItem = cartItemRepository
                .findByCartIdAndVariantId(cart.getId(), request.getVariantId())
                .orElse(null);

        int existingQty = existingItem != null ? existingItem.getQuantity() : 0;
        int totalQty = existingQty + request.getQuantity();

        StockCheckResult stock = catalogClient.checkStock(
                List.of(new StockCheckItem(request.getVariantId(), totalQty)));
        requireStockAnswer(stock);
        if (!stock.hasEnoughStock(request.getVariantId(), totalQty)) {
            log.warn("[Cart] insufficient stock — variantId={}, requested={}",
                    request.getVariantId(), totalQty);
            throw new AppException(OrderErrorCode.STOCK_INSUFFICIENT);
        }

        if (existingItem != null) {
            existingItem.setQuantity(totalQty);
            applySnapshot(existingItem, variant);
        } else {
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .variantId(request.getVariantId())
                    .quantity(request.getQuantity())
                    .build();
            applySnapshot(cartItem, variant);
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
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_ITEM_NOT_FOUND));

        Cart cart = item.getCart();
        validateCartOwnership(cart, userId);

        ProductVariantDto variant = catalogClient.getVariant(item.getVariantId());
        requireOnSale(variant);

        StockCheckResult stock = catalogClient.checkStock(
                List.of(new StockCheckItem(item.getVariantId(), request.getQuantity())));
        requireStockAnswer(stock);
        if (!stock.hasEnoughStock(item.getVariantId(), request.getQuantity())) {
            throw new AppException(OrderErrorCode.STOCK_INSUFFICIENT);
        }

        item.setQuantity(request.getQuantity());
        applySnapshot(item, variant);   // sync lại tên + giá mới nhất

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
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_ITEM_NOT_FOUND));

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
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_NOT_ACTIVE));

        cart.getItems().clear();
        Cart saved = cartRepository.save(cart);

        return cartMapper.toCartResponse(saved);
    }


    /**
     * Không có {@code @Transactional}: đây là 2 lượt gọi mạng, giữ một transaction DB mở trong lúc chờ
     * catalog là đúng cái đã bỏ ở createCheckout. Snapshot mới được ghi bằng một lần save ở cuối.
     */
    @Override
    public Cart revalidateActiveCart() {
        String userId = currentUserProvider.getCurrentUserId();

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(OrderErrorCode.CART_EMPTY));
        List<CartItem> items = cart.getItems();
        if (items.isEmpty()) {
            throw new AppException(OrderErrorCode.CART_EMPTY);
        }

        Map<String, ProductVariantDto> variants = catalogClient
                .getVariantsBatch(items.stream().map(CartItem::getVariantId).toList())
                .stream()
                .collect(Collectors.toMap(ProductVariantDto::getVariantId, Function.identity()));

        for (CartItem item : items) {
            ProductVariantDto variant = variants.get(item.getVariantId());
            if (variant == null) {
                log.warn("[Cart] variant không còn trong catalog — variantId={}", item.getVariantId());
                throw new AppException(OrderErrorCode.PRODUCT_VARIANT_NOT_FOUND);
            }
            if (variant.isUpstreamUnavailable()) {
                throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
            }
            requireOnSale(variant);
            applySnapshot(item, variant);   // giá đổi từ lúc bỏ vào giỏ -> checkout dùng giá mới
        }

        StockCheckResult stock = catalogClient.checkStock(items.stream()
                .map(item -> new StockCheckItem(item.getVariantId(), item.getQuantity()))
                .toList());
        requireStockAnswer(stock);
        for (CartItem item : items) {
            if (!stock.hasEnoughStock(item.getVariantId(), item.getQuantity())) {
                log.warn("[Cart] hết hàng khi mở checkout — variantId={}, cần={}",
                        item.getVariantId(), item.getQuantity());
                throw new AppException(OrderErrorCode.STOCK_INSUFFICIENT);
            }
        }

        return cartRepository.save(cart);
    }

    private void requireOnSale(ProductVariantDto variant) {
        if (!Boolean.TRUE.equals(variant.getActive())) {
            throw new AppException(OrderErrorCode.PRODUCT_VARIANT_INACTIVE);
        }
    }

    /**
     * Fallback của inventory trả "không đủ hàng" cho mọi variant. Trên đường đọc thì coi là "không biết"
     * cũng được, nhưng trên đường ghi thì nói với khách là hết hàng là nói sai — trả 502 cho đúng sự thật.
     */
    private void requireStockAnswer(StockCheckResult stock) {
        if (stock.isUpstreamUnavailable()) {
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }

    /** Ghi lại toàn bộ thông tin sản phẩm vào dòng giỏ hàng — checkout sau này chỉ đọc từ đây. */
    private void applySnapshot(CartItem item, ProductVariantDto variant) {
        item.setProductId(variant.getProductId());
        item.setProductName(variant.getProductName());
        item.setSize(variant.getSize());
        item.setColor(variant.getColor());
        item.setUnitPrice(variant.effectivePrice());
    }

    private void validateCartOwnership(Cart cart, String userId) {
        if (!cart.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new AppException(OrderErrorCode.CART_NOT_ACTIVE);
        }
    }

    /**
     * Enrich cả tên/size/color/sku lẫn cờ còn hàng hay không, mỗi loại đúng 1 lượt gọi cho toàn bộ giỏ
     * hàng thay vì N+1. Catalog chết thì cả hai lượt rơi vào fallback nhưng không lượt nào làm fail
     * request — giỏ vẫn hiển thị được từ snapshot đã lưu trong DB.
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
            List<ProductVariantDto> variants = catalogClient.getVariantsBatch(variantIds);
            Map<String, ProductVariantDto> variantMap = variants.stream()
                    .collect(Collectors.toMap(ProductVariantDto::getVariantId, Function.identity()));

            response.getItems().forEach(itemResp -> {
                ProductVariantDto variant = variantMap.get(itemResp.getVariantId());
                if (variant == null || variant.getProductName() == null) {
                    return;   // catalog không trả được variant này -> giữ snapshot đã lưu trong DB
                }
                itemResp.setProductName(variant.getProductName());
                itemResp.setSize(variant.getSize());
                itemResp.setColor(variant.getColor());
                itemResp.setSku(variant.getSku());
            });
        } catch (Exception e) {
            // catalog down → trả về cart data từ DB, không fail cả request
            log.warn("[Cart] Could not enrich cart items from catalog: {}", e.getMessage());
        }
    }

    private void enrichAvailability(CartResponse response, Cart cart, List<String> variantIds) {
        try {
            List<StockCheckItem> items = cart.getItems().stream()
                    .map(item -> new StockCheckItem(item.getVariantId(), item.getQuantity()))
                    .toList();
            StockCheckResult stock = catalogClient.checkStock(items);
            if (stock.isUpstreamUnavailable()) {
                response.getItems().forEach(item -> item.setAvailable(null)); // null = không biết được
                return;
            }

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

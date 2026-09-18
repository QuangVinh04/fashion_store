package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.dto.wishlist.WishlistAddResult;
import com.fashionstore.catalog.dto.wishlist.WishlistCheckResponse;
import com.fashionstore.catalog.dto.wishlist.WishlistItemResponse;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.WishlistItem;
import com.fashionstore.catalog.entity.WishlistItemId;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.repository.WishlistItemRepository;
import com.fashionstore.catalog.service.WishlistService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WishlistServiceImpl implements WishlistService {

    WishlistItemRepository wishlistItemRepository;
    ProductRepository productRepository;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public WishlistAddResult addToWishlist(String productId) {
        String userId = requireCurrentUserId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));

        WishlistItemId id = new WishlistItemId(userId, productId);
        WishlistItem item = wishlistItemRepository.findById(id).orElse(null);

        if (item != null) {
            log.info("[Wishlist] Product {} already in wishlist for user {}, returning existing", productId, userId);
            return new WishlistAddResult(toResponse(item), false);
        }

        item = WishlistItem.builder()
                .id(id)
                .product(product)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            item = wishlistItemRepository.saveAndFlush(item);
            log.info("[Wishlist] Added product {} to wishlist for user {}", productId, userId);
            return new WishlistAddResult(toResponse(item), true);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Wishlist] Concurrent duplicate addition for product {} and user {}, recovering existing",
                    productId, userId);
            WishlistItem existing = wishlistItemRepository.findById(id).orElse(item);
            return new WishlistAddResult(toResponse(existing), false);
        }
    }

    @Override
    @Transactional
    public void removeFromWishlist(String productId) {
        String userId = requireCurrentUserId();
        wishlistItemRepository.deleteByIdUserIdAndIdProductId(userId, productId);
        log.info("[Wishlist] Removed product {} from wishlist for user {}", productId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<WishlistItemResponse>> getMyWishlist(Pageable pageable) {
        String userId = requireCurrentUserId();
        Page<WishlistItem> page = wishlistItemRepository.findByIdUserId(userId, pageable);

        List<WishlistItemResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<List<WishlistItemResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(page.getTotalPages())
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WishlistCheckResponse checkInWishlist(String productId) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            return WishlistCheckResponse.builder().inWishlist(false).build();
        }

        boolean exists = wishlistItemRepository.existsByIdUserIdAndIdProductId(userId, productId);
        return WishlistCheckResponse.builder().inWishlist(exists).build();
    }

    private String requireCurrentUserId() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        Product p = item.getProduct();
        return WishlistItemResponse.builder()
                .productId(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .basePrice(p.getBasePrice())
                .salePrice(p.getSalePrice())
                .thumbnailUrl(p.getThumbnailUrl())
                .brandName(p.getBrand() != null ? p.getBrand().getName() : null)
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .addedAt(item.getCreatedAt())
                .build();
    }
}

package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.dto.wishlist.WishlistCheckResponse;
import com.fashionstore.catalog.dto.wishlist.WishlistItemResponse;
import com.fashionstore.catalog.entity.Brand;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.WishlistItem;
import com.fashionstore.catalog.entity.WishlistItemId;
import com.fashionstore.catalog.entity.enumeration.ProductStatus;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.repository.ProductRepository;
import com.fashionstore.catalog.repository.WishlistItemRepository;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private WishlistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WishlistServiceImpl(
                wishlistItemRepository,
                productRepository,
                currentUserProvider
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void addToWishlist_whenNotExists_addsSuccessfully() {
        Brand brand = Brand.builder().name("Zara").build();
        Product product = Product.builder()
                .name("Đầm hoa nhí")
                .slug("dam-hoa-nhi")
                .basePrice(new BigDecimal("350000"))
                .salePrice(new BigDecimal("299000"))
                .thumbnailUrl("https://img.jpg")
                .brand(brand)
                .status(ProductStatus.PUBLISHED)
                .build();
        product.setId("prod-1");

        WishlistItemId id = new WishlistItemId("user-1", "prod-1");
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(wishlistItemRepository.findById(id)).thenReturn(Optional.empty());
        when(wishlistItemRepository.saveAndFlush(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));

        com.fashionstore.catalog.dto.wishlist.WishlistAddResult result = service.addToWishlist("prod-1");

        assertThat(result).isNotNull();
        assertThat(result.created()).isTrue();
        WishlistItemResponse response = result.item();
        assertThat(response.getProductId()).isEqualTo("prod-1");
        assertThat(response.getName()).isEqualTo("Đầm hoa nhí");
        assertThat(response.getBrandName()).isEqualTo("Zara");
        assertThat(response.getSalePrice()).isEqualByComparingTo(new BigDecimal("299000"));

        verify(wishlistItemRepository, times(1)).saveAndFlush(any(WishlistItem.class));
    }

    @Test
    void addToWishlist_whenAlreadyExists_isIdempotent() {
        Product product = Product.builder().name("Áo Thun").build();
        product.setId("prod-1");

        WishlistItemId id = new WishlistItemId("user-1", "prod-1");
        WishlistItem existing = WishlistItem.builder()
                .id(id)
                .product(product)
                .createdAt(LocalDateTime.now())
                .build();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(wishlistItemRepository.findById(id)).thenReturn(Optional.of(existing));

        com.fashionstore.catalog.dto.wishlist.WishlistAddResult result = service.addToWishlist("prod-1");

        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        WishlistItemResponse response = result.item();
        assertThat(response.getProductId()).isEqualTo("prod-1");
        verify(wishlistItemRepository, never()).saveAndFlush(any(WishlistItem.class));
    }

    @Test
    void addToWishlist_whenConcurrentRaceCondition_recoversExistingSafely() {
        Product product = Product.builder().name("Áo Thun").build();
        product.setId("prod-1");

        WishlistItemId id = new WishlistItemId("user-1", "prod-1");
        WishlistItem existing = WishlistItem.builder()
                .id(id)
                .product(product)
                .createdAt(LocalDateTime.now())
                .build();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(wishlistItemRepository.findById(id))
                .thenReturn(Optional.empty()) // Lúc đầu chưa có
                .thenReturn(Optional.of(existing)); // Lúc sau bắt được conflict thì có
        when(wishlistItemRepository.saveAndFlush(any(WishlistItem.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        com.fashionstore.catalog.dto.wishlist.WishlistAddResult result = service.addToWishlist("prod-1");

        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        assertThat(result.item().getProductId()).isEqualTo("prod-1");
    }

    @Test
    void addToWishlist_whenProductNotFound_throwsProductNotFound() {
        when(productRepository.findById("prod-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addToWishlist("prod-999"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void removeFromWishlist_isIdempotent() {
        service.removeFromWishlist("prod-1");
        verify(wishlistItemRepository, times(1)).deleteByIdUserIdAndIdProductId("user-1", "prod-1");
    }

    @Test
    void getMyWishlist_returnsPaginatedList() {
        Product product = Product.builder().name("Quần Short").build();
        product.setId("prod-1");

        WishlistItem item = WishlistItem.builder()
                .id(new WishlistItemId("user-1", "prod-1"))
                .product(product)
                .createdAt(LocalDateTime.now())
                .build();

        when(wishlistItemRepository.findByIdUserId(eq("user-1"), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        PageResponse<List<WishlistItemResponse>> response = service.getMyWishlist(PageRequest.of(0, 10));

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo("prod-1");
    }

    @Test
    void checkInWishlist_whenExists_returnsTrue() {
        when(wishlistItemRepository.existsByIdUserIdAndIdProductId("user-1", "prod-1")).thenReturn(true);

        WishlistCheckResponse response = service.checkInWishlist("prod-1");

        assertThat(response.isInWishlist()).isTrue();
    }

    @Test
    void checkInWishlist_whenNotExists_returnsFalse() {
        when(wishlistItemRepository.existsByIdUserIdAndIdProductId("user-1", "prod-2")).thenReturn(false);

        WishlistCheckResponse response = service.checkInWishlist("prod-2");

        assertThat(response.isInWishlist()).isFalse();
    }
}
